package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.OnboardStatus;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.talent.constant.ResumeStatus;
import xyz.erupt.talent.constant.TraceBizType;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.model.Onboarding;
import xyz.erupt.talent.model.Resume;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Onboarding registration, the last leg of the pipeline.
 *
 * @author YuePeng
 */
@Service
public class OnboardFlowService {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TraceService traceService;

    @Resource
    private NotifyService notifyService;

    @Resource
    private ResumeFlowService resumeFlowService;

    /**
     * Rule 7: an accepted offer automatically produces the pending-onboarding record.
     */
    @Transactional
    public Onboarding createFromOffer(Offer offer) {
        Onboarding existing = eruptDao.lambdaQuery(Onboarding.class)
                .eq(Onboarding::getOffer, offer).one();
        if (null != existing) {
            return existing;
        }
        Resume resume = offer.getResume();
        Onboarding onboarding = new Onboarding();
        onboarding.setOffer(offer);
        onboarding.setStatus(OnboardStatus.PENDING);
        onboarding.setPlannedDate(offer.getOnboardDate());
        onboarding.setOrg(null == resume.getJobPost() ? null : resume.getJobPost().getOrg());
        eruptDao.persistAndFlush(onboarding);

        resumeFlowService.advanceIfActive(resume, ResumeStatus.PENDING_ONBOARD,
                "生成待入职数据", "计划入职日期：" + offer.getOnboardDate());
        traceService.log(TraceBizType.ONBOARDING, onboarding.getId(), offer.getCode(), resume.getCode(),
                "自动生成待入职", null, OnboardStatus.PENDING, "计划入职日期：" + offer.getOnboardDate());
        notifyService.notify(TalentConst.SCENE_ONBOARD, resumeFlowService.hrIds(),
                "待入职：" + resume.getName(),
                String.format("候选人 %s（%s）已接受 Offer %s，计划 %s 入职，请准备入职材料核对。",
                        resume.getName(), resume.getCode(), offer.getCode(), offer.getOnboardDate()));
        return onboarding;
    }

    /**
     * Rule 9: closes the loop. Reached only through the confirmation dialog, which is why the
     * explicit acknowledgement flag is validated here as well as in the form.
     */
    @Transactional
    public void confirm(Long onboardingId, LocalDate actualDate, String empNo, boolean confirmed) {
        if (!confirmed) {
            throw new EruptWebApiRuntimeException("请先勾选「确认信息无误」");
        }
        if (StringUtils.isBlank(empNo)) {
            throw new EruptWebApiRuntimeException("请填写员工工号");
        }
        Onboarding onboarding = this.require(onboardingId);
        if (onboarding.getStatus() != OnboardStatus.PENDING) {
            throw new EruptWebApiRuntimeException("当前状态不可确认入职：" + onboarding.getStatus().label());
        }
        long duplicated = eruptDao.lambdaQuery(Onboarding.class)
                .eq(Onboarding::getEmpNo, empNo)
                .ne(Onboarding::getId, onboardingId)
                .count();
        if (duplicated > 0) {
            throw new EruptWebApiRuntimeException("员工工号已被占用：" + empNo);
        }
        Resume resume = onboarding.getOffer().getResume();
        onboarding.setActualDate(null == actualDate ? onboarding.getPlannedDate() : actualDate);
        onboarding.setEmpNo(empNo);
        onboarding.setStatus(OnboardStatus.ONBOARDED);
        eruptDao.merge(onboarding);

        resumeFlowService.advance(resume, ResumeStatus.ONBOARDED, "确认入职",
                "工号：" + empNo + "，实际到岗：" + onboarding.getActualDate());
        traceService.log(TraceBizType.ONBOARDING, onboarding.getId(), onboarding.getOffer().getCode(),
                resume.getCode(), "确认入职", OnboardStatus.PENDING, OnboardStatus.ONBOARDED,
                "工号：" + empNo + "，未核对材料：" + String.join("、", this.pendingMaterials(onboarding)));
        notifyService.notify(TalentConst.SCENE_ONBOARD, resumeFlowService.hrIds(),
                "已入职：" + resume.getName(),
                String.format("候选人 %s（%s）已于 %s 正式入职，工号 %s，招聘流程闭环。",
                        resume.getName(), resume.getCode(), onboarding.getActualDate(), empNo));
    }

    @Transactional
    public void abandon(Long onboardingId, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new EruptWebApiRuntimeException("请填写放弃原因");
        }
        Onboarding onboarding = this.require(onboardingId);
        if (onboarding.getStatus() != OnboardStatus.PENDING) {
            throw new EruptWebApiRuntimeException("当前状态不可放弃入职：" + onboarding.getStatus().label());
        }
        Resume resume = onboarding.getOffer().getResume();
        onboarding.setStatus(OnboardStatus.ABANDONED);
        onboarding.setRemark(reason);
        eruptDao.merge(onboarding);

        resume.setEliminateReason(reason);
        resumeFlowService.advance(resume, ResumeStatus.ABANDONED, "放弃入职", reason);
        traceService.log(TraceBizType.ONBOARDING, onboarding.getId(), onboarding.getOffer().getCode(),
                resume.getCode(), "放弃入职", OnboardStatus.PENDING, OnboardStatus.ABANDONED, reason);
    }

    /**
     * Material items not yet ticked off — shown in the confirmation dialog.
     */
    public List<String> pendingMaterials(Onboarding onboarding) {
        return Arrays.stream(Onboarding.MATERIALS)
                .filter(it -> null == onboarding.getMaterials() || !onboarding.getMaterials().contains(it))
                .toList();
    }

    public Onboarding require(Long id) {
        Onboarding onboarding = eruptDao.find(Onboarding.class, id);
        if (null == onboarding) {
            throw new EruptWebApiRuntimeException("入职记录不存在：" + id);
        }
        return onboarding;
    }

}
