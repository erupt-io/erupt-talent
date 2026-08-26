package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.*;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.model.TalentSetting;
import xyz.erupt.talent.model.Resume;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offer approval chain and automated delivery.
 * <p>
 * Default path is 自动发起 → 部门负责人审批 → HR 复核 → 自动发件. The
 * {@code autoOfferMode} risk-control switch collapses both approvals so an approved
 * final interview delivers the mail immediately.
 *
 * @author YuePeng
 */
@Slf4j
@Service
public class OfferFlowService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] WEEKDAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    /** Business defaults applied when the offer is auto-created from a final-round proposal. */
    private static final int DEFAULT_ONBOARD_LEAD_DAYS = 14;

    private static final int DEFAULT_REPLY_WINDOW_DAYS = 7;

    @Resource
    private EruptDao eruptDao;

    @Resource
    private SeqService seqService;

    @Resource
    private TraceService traceService;

    @Resource
    private NotifyService notifyService;

    @Resource
    private MailSendService mailSendService;

    @Resource
    private TalentSettingService settingService;

    @Resource
    private ResumeFlowService resumeFlowService;

    @Resource
    private TalentAuth talentAuth;

    @Resource
    private OnboardFlowService onboardFlowService;

    /**
     * Rule 5: a final-round proposal automatically opens the offer approval flow.
     */
    @Transactional
    public Offer createFromProposal(Resume resume) {
        TalentSetting setting = settingService.get();
        Offer offer = new Offer();
        offer.setCode(seqService.next(TalentConst.SEQ_OFFER));
        offer.setResume(resume);
        offer.setSalary(resume.getExpectSalary());
        offer.setProbationMonths(3);
        offer.setOfferType(OfferType.FORMAL);
        offer.setOnboardDate(LocalDate.now().plusDays(DEFAULT_ONBOARD_LEAD_DAYS));
        offer.setExpireTime(LocalDate.now().plusDays(DEFAULT_REPLY_WINDOW_DAYS));
        offer.setReportAddress(setting.getReportAddress());
        offer.setMailTo(resume.getEmail());
        offer.setStatus(Boolean.TRUE.equals(setting.getAutoOfferMode())
                ? OfferStatus.PENDING_SEND : OfferStatus.PENDING_DEPT);
        eruptDao.persistAndFlush(offer);
        traceService.log(TraceBizType.OFFER, offer.getId(), offer.getCode(), resume.getCode(),
                "自动发起 Offer 审批", null, offer.getStatus(),
                Boolean.TRUE.equals(setting.getAutoOfferMode()) ? "全自动发放模式：跳过人工审批" : "等待部门负责人审批");

        if (Boolean.TRUE.equals(setting.getAutoOfferMode())) {
            // Failure is recorded on the offer and in the trace; the record stays retryable.
            this.send(offer);
        } else {
            resumeFlowService.advanceIfActive(resume, ResumeStatus.OFFER_APPROVING,
                    "Offer 审批中", "Offer 编号：" + offer.getCode());
            notifyService.notify(TalentConst.SCENE_OFFER_APPROVAL, this.deptApproverIds(resume),
                    "Offer 待审批：" + resume.getName(),
                    String.format("候选人 %s（%s）终面拟录用，Offer %s 待您审批。",
                            resume.getName(), resume.getCode(), offer.getCode()));
        }
        return offer;
    }

    @Transactional
    public void deptApprove(Long offerId, boolean approved, String opinion) {
        Offer offer = this.require(offerId);
        if (offer.getStatus() != OfferStatus.PENDING_DEPT) {
            throw new EruptWebApiRuntimeException("当前状态不可进行部门审批：" + offer.getStatus().label());
        }
        this.assertApprover(offer, "部门负责人");
        this.requireOpinionOnReject(approved, opinion);
        offer.setDeptApprover(talentAuth.username());
        offer.setDeptOpinion(opinion);
        offer.setDeptTime(LocalDateTime.now());
        if (!approved) {
            this.reject(offer, "部门审批驳回", opinion);
            return;
        }
        this.transition(offer, OfferStatus.PENDING_HR, "部门审批通过", opinion);
        notifyService.notify(TalentConst.SCENE_OFFER_APPROVAL, resumeFlowService.hrIds(),
                "Offer 待复核：" + offer.getResume().getName(),
                String.format("Offer %s 已通过部门审批，请 HR 复核。复核通过后系统将自动发送 Offer 邮件。",
                        offer.getCode()));
    }

    /**
     * @return null when the offer went out cleanly, otherwise the delivery failure reason
     */
    @Transactional
    public String hrApprove(Long offerId, boolean approved, String opinion) {
        Offer offer = this.require(offerId);
        if (offer.getStatus() != OfferStatus.PENDING_HR) {
            throw new EruptWebApiRuntimeException("当前状态不可进行 HR 复核：" + offer.getStatus().label());
        }
        if (!talentAuth.isHr()) {
            throw new EruptWebApiRuntimeException("仅 HR 或管理员可复核 Offer");
        }
        this.requireOpinionOnReject(approved, opinion);
        offer.setHrApprover(talentAuth.username());
        offer.setHrOpinion(opinion);
        offer.setHrTime(LocalDateTime.now());
        if (!approved) {
            this.reject(offer, "HR 复核驳回", opinion);
            return null;
        }
        this.transition(offer, OfferStatus.PENDING_SEND, "HR 复核通过", opinion);
        return this.send(offer);
    }

    /**
     * Rule 6: renders the template, delivers the mail and updates both business states.
     * <p>
     * Deliberately never throws. An approval must not be rolled back because the mail server was
     * unreachable, so a failure leaves the offer in {@code PENDING_SEND} with the reason recorded
     * and is reported back to the caller for display.
     *
     * @return null on success, otherwise a user-facing failure reason
     */
    @Transactional
    public String send(Offer offer) {
        if (offer.getStatus() != OfferStatus.PENDING_SEND) {
            return "当前状态不可发送：" + offer.getStatus().label();
        }
        Resume resume = offer.getResume();
        if (StringUtils.isBlank(resume.getEmail())) {
            return this.sendFailed(offer, "候选人邮箱为空，请先补充简历邮箱");
        }
        TalentSetting setting = settingService.get();
        Map<String, String> vars = this.variables(offer, setting);
        String subject = render(StringUtils.defaultIfBlank(setting.getOfferMailSubject(),
                "${companyName} 录用通知 - ${jobPost}"), vars);
        String body = render(StringUtils.defaultIfBlank(setting.getOfferMailTemplate(),
                TalentSettingService.DEFAULT_TEMPLATE), vars);
        offer.setMailTo(resume.getEmail());
        offer.setMailContent(body);
        try {
            mailSendService.send(resume.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("Offer 邮件发送失败 offer={}: {}", offer.getCode(), e.getMessage());
            return this.sendFailed(offer, e.getMessage());
        }
        offer.setSendError(null);
        offer.setSentTime(LocalDateTime.now());
        this.transition(offer, OfferStatus.SENT, "Offer 邮件自动发送", "收件邮箱：" + resume.getEmail());
        resumeFlowService.advanceIfActive(resume, ResumeStatus.OFFER_SENT,
                "Offer 已发送", "Offer 编号：" + offer.getCode());
        notifyService.notify(TalentConst.SCENE_OFFER_SENT, resumeFlowService.hrIds(),
                "Offer 已发送：" + resume.getName(),
                String.format("Offer %s 已发送至 %s，回复截止 %s。",
                        offer.getCode(), resume.getEmail(), offer.getExpireTime()));
        return null;
    }

    private String sendFailed(Offer offer, String reason) {
        offer.setSendError(StringUtils.abbreviate(reason, 500));
        eruptDao.merge(offer);
        traceService.log(TraceBizType.OFFER, offer.getId(), offer.getCode(),
                offer.getResume().getCode(), "Offer 邮件发送失败",
                OfferStatus.PENDING_SEND, OfferStatus.PENDING_SEND, reason);
        return "Offer 邮件发送失败：" + reason + "（状态保持「待发送」，可修正配置后重新发送）";
    }

    /**
     * @return null on success, otherwise a user-facing failure reason
     */
    @Transactional
    public String resend(Long offerId) {
        return this.send(this.require(offerId));
    }

    /**
     * Rule 7: accepting an offer generates the pending-onboarding record.
     */
    @Transactional
    public void reply(Long offerId, boolean accepted, String remark) {
        Offer offer = this.require(offerId);
        if (offer.getStatus() != OfferStatus.SENT) {
            throw new EruptWebApiRuntimeException("仅「已发送」的 Offer 可登记候选人回复");
        }
        offer.setReplyRemark(remark);
        Resume resume = offer.getResume();
        if (accepted) {
            // Captured before delegating, so the reminder reaches whoever registered the acceptance.
            Long operator = talentAuth.uid();
            this.transition(offer, OfferStatus.ACCEPTED, "候选人接受 Offer", remark);
            resumeFlowService.advanceIfActive(resume, ResumeStatus.OFFER_ACCEPTED,
                    "候选人接受 Offer", "Offer 编号：" + offer.getCode());
            // Onboarding creation is delegated so the two modules stay independently testable.
            onboardFlowService.createFromOffer(offer);
            // Remind the operator to start onboarding for this candidate.
            notifyService.notify(TalentConst.SCENE_OFFER_ACCEPTED,
                    null == operator ? List.of() : List.of(operator),
                    "候选人接受 Offer：" + resume.getName(),
                    String.format("候选人 %s（%s）已接受 Offer %s，计划 %s 入职，请办理入职手续。",
                            resume.getName(), resume.getCode(), offer.getCode(), offer.getOnboardDate()));
        } else {
            this.transition(offer, OfferStatus.DECLINED, "候选人拒绝 Offer", remark);
            resume.setEliminateReason(StringUtils.defaultIfBlank(remark, "候选人拒绝 Offer"));
            resumeFlowService.advance(resume, ResumeStatus.OFFER_REJECTED, "候选人拒绝 Offer", remark);
        }
    }

    /**
     * Sweeps offers whose reply window elapsed; invoked by the scheduled job.
     *
     * @return the number of offers marked expired
     */
    @Transactional
    public int expireSweep() {
        List<Offer> stale = eruptDao.lambdaQuery(Offer.class)
                .eq(Offer::getStatus, OfferStatus.SENT)
                .lt(Offer::getExpireTime, LocalDate.now())
                .list();
        for (Offer offer : stale) {
            this.transition(offer, OfferStatus.EXPIRED, "Offer 超期未回复", "回复截止：" + offer.getExpireTime());
        }
        return stale.size();
    }

    /* ---------------- internals ---------------- */

    private void reject(Offer offer, String action, String opinion) {
        this.transition(offer, OfferStatus.APPROVAL_REJECTED, action, opinion);
        resumeFlowService.advanceIfActive(offer.getResume(), ResumeStatus.PROPOSED,
                action, "Offer 审批未通过，简历回到拟录用状态");
        notifyService.notify(TalentConst.SCENE_OFFER_APPROVAL, resumeFlowService.hrIds(),
                "Offer 被驳回：" + offer.getResume().getName(),
                String.format("Offer %s %s，意见：%s", offer.getCode(), action, StringUtils.defaultString(opinion)));
    }

    private void transition(Offer offer, OfferStatus target, String action, String detail) {
        OfferStatus from = offer.getStatus();
        offer.setStatus(target);
        eruptDao.merge(offer);
        traceService.log(TraceBizType.OFFER, offer.getId(), offer.getCode(),
                offer.getResume().getCode(), action, from, target, detail);
    }

    private void requireOpinionOnReject(boolean approved, String opinion) {
        if (!approved && StringUtils.isBlank(opinion)) {
            throw new EruptWebApiRuntimeException("驳回时必须填写审批意见");
        }
    }

    /**
     * Only the job post's designated dept leader (or an administrator) may sign the first step.
     */
    private void assertApprover(Offer offer, String role) {
        if (talentAuth.isAdmin()) {
            return;
        }
        Long uid = talentAuth.uid();
        List<Long> allowed = this.deptApproverIds(offer.getResume());
        if (null == uid || !allowed.contains(uid)) {
            throw new EruptWebApiRuntimeException("仅该岗位的" + role + "可审批此 Offer");
        }
    }

    private List<Long> deptApproverIds(Resume resume) {
        List<Long> ids = new ArrayList<>();
        if (null != resume.getJobPost() && null != resume.getJobPost().getDeptLeader()) {
            ids.add(resume.getJobPost().getDeptLeader().getId());
        }
        return ids;
    }

    private Map<String, String> variables(Offer offer, TalentSetting setting) {
        Resume resume = offer.getResume();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("candidateName", resume.getName());
        vars.put("gender", "女".equals(resume.getGender()) ? "女士" : "男".equals(resume.getGender()) ? "先生" : "");
        vars.put("jobPost", null == resume.getJobPost() ? "" : resume.getJobPost().getName());
        vars.put("salary", null == offer.getSalary() ? "" : offer.getSalary().stripTrailingZeros().toPlainString());
        vars.put("probationMonths", String.valueOf(offer.getProbationMonths()));
        vars.put("onboardDate", offer.getOnboardDate().format(ISO_DATE));
        vars.put("onboardDateCn", offer.getOnboardDate().format(DATE)
                + "（" + WEEKDAYS[offer.getOnboardDate().getDayOfWeek().getValue() - 1] + "）");
        vars.put("expireTime", offer.getExpireTime().format(DATE));
        vars.put("offerCode", offer.getCode());
        vars.put("companyName", StringUtils.defaultString(setting.getCompanyName()));
        vars.put("reportAddress", StringUtils.defaultIfBlank(offer.getReportAddress(),
                StringUtils.defaultString(setting.getReportAddress())));
        return vars;
    }

    /**
     * Replaces {@code ${var}} placeholders. Values are HTML-escaped because some of them
     * (candidate name, position) originate from parsed e-mail content.
     */
    private static String render(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", escape(entry.getValue()));
        }
        return result;
    }

    private static String escape(String value) {
        if (null == value) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private Offer require(Long id) {
        Offer offer = eruptDao.find(Offer.class, id);
        if (null == offer) {
            throw new EruptWebApiRuntimeException("Offer 不存在：" + id);
        }
        return offer;
    }

}
