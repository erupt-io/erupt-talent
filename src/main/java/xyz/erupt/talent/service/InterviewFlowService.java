package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.*;
import xyz.erupt.talent.model.Interview;
import xyz.erupt.talent.model.Resume;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Interview-side automation: DingTalk calendar sync on scheduling, and pipeline advancement on
 * assessment. Both are triggered from {@code InterviewProxy} after the record is persisted.
 *
 * @author YuePeng
 */
@Slf4j
@Service
public class InterviewFlowService {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TraceService traceService;

    @Resource
    private NotifyService notifyService;

    @Resource
    private DingTalkService dingTalkService;

    @Resource
    private ResumeFlowService resumeFlowService;

    @Resource
    private OfferFlowService offerFlowService;

    @Resource
    private TalentAuth talentAuth;

    /**
     * Rule 2 and rule 4: a confirmed schedule creates the DingTalk event; a submitted conclusion
     * advances the resume.
     *
     * @param previous the conclusion the record held before this save, null for a fresh record
     */
    @Transactional
    public void onSaved(Interview interview, InterviewConclusion previous) {
        this.validate(interview);
        // erupt binds a reference field from the request as a stub carrying only the id, so the
        // managed record has to be re-read before it is used or written.
        Resume resume = resumeFlowService.require(interview.getResume().getId());
        interview.setResume(resume);
        if (this.scheduled(interview)) {
            String key = this.scheduleKey(interview);
            boolean firstSync = StringUtils.isBlank(interview.getDingEventId());
            // Re-sync only when the schedule actually changed, so editing the assessment (score,
            // comment, conclusion) never needlessly re-creates the calendar event.
            if (firstSync || !key.equals(interview.getDingSyncKey())) {
                this.syncCalendar(interview, key);
                if (firstSync) {
                    resumeFlowService.advanceIfActive(resume, interview.getRound().getResumeStatus(),
                            interview.getRound().label() + "已安排", "面试时间：" + interview.getInterviewTime());
                    notifyService.notify(TalentConst.SCENE_INTERVIEW_ARRANGED,
                            interview.getInterviewer() == null ? List.of() : List.of(interview.getInterviewer().getId()),
                            "面试安排：" + resume.getName() + " " + interview.getRound().label(),
                            String.format("候选人 %s（%s）%s 定于 %s，形式：%s%s",
                                    resume.getName(), resume.getCode(), interview.getRound().label(),
                                    interview.getInterviewTime(),
                                    null == interview.getForm() ? "未指定" : interview.getForm().label(),
                                    StringUtils.isBlank(interview.getPlace()) ? "" : "，地点/链接：" + interview.getPlace()));
                } else {
                    notifyService.notify(TalentConst.SCENE_INTERVIEW_RESCHEDULED,
                            interview.getInterviewer() == null ? List.of() : List.of(interview.getInterviewer().getId()),
                            "面试改期：" + resume.getName() + " " + interview.getRound().label(),
                            String.format("候选人 %s（%s）%s 已改期至 %s，形式：%s%s",
                                    resume.getName(), resume.getCode(), interview.getRound().label(),
                                    interview.getInterviewTime(),
                                    null == interview.getForm() ? "未指定" : interview.getForm().label(),
                                    StringUtils.isBlank(interview.getPlace()) ? "" : "，地点/链接：" + interview.getPlace()));
                }
            }
        }
        if (interview.getConclusion() != previous && interview.getConclusion() != InterviewConclusion.PENDING) {
            this.onConclusion(interview, previous);
        }
    }

    private void validate(Interview interview) {
        if (interview.getRound() == InterviewRound.FINAL
                && interview.getConclusion() == InterviewConclusion.PASS) {
            throw new EruptWebApiRuntimeException("终面结论请选择「拟录用」或「淘汰」");
        }
        if (interview.getRound() != InterviewRound.FINAL
                && interview.getConclusion() == InterviewConclusion.PROPOSED) {
            throw new EruptWebApiRuntimeException("「拟录用」仅可用于终面");
        }
        if (interview.getConclusion() == InterviewConclusion.ELIMINATE
                && StringUtils.isBlank(interview.getEliminateReason())) {
            throw new EruptWebApiRuntimeException("淘汰时必须填写淘汰原因");
        }
    }

    private boolean scheduled(Interview interview) {
        return null != interview.getInterviewTime() && null != interview.getInterviewer();
    }

    /**
     * A stable fingerprint of the scheduling fields; a change here is what triggers a calendar
     * re-sync. Only the interviewer id is needed — resolving it to a DingTalk user happens later.
     */
    private String scheduleKey(Interview interview) {
        return String.join("|",
                String.valueOf(interview.getInterviewTime()),
                String.valueOf(null == interview.getInterviewer() ? null : interview.getInterviewer().getId()),
                String.valueOf(interview.getDurationMinutes()),
                StringUtils.defaultString(interview.getPlace()),
                String.valueOf(interview.getForm()));
    }

    /**
     * Removes the DingTalk event when its interview record is deleted. Non-fatal: a sync failure
     * must never block the deletion.
     */
    @Transactional
    public void onDelete(Interview interview) {
        Interview managed = eruptDao.find(Interview.class, interview.getId());
        if (null == managed || StringUtils.isBlank(managed.getDingEventId())) {
            return;
        }
        try {
            dingTalkService.deleteInterviewEvent(managed.getDingOwnerUserId(), managed.getDingEventId());
            String code = null == managed.getResume() ? null : managed.getResume().getCode();
            traceService.log(TraceBizType.INTERVIEW, managed.getId(), code, code,
                    "钉钉日程已删除", null, null, "日程ID：" + managed.getDingEventId());
        } catch (Exception e) {
            log.warn("钉钉日程删除失败 interview={}: {}", managed.getId(), e.getMessage());
        }
    }

    private void onConclusion(Interview interview, InterviewConclusion previous) {
        Resume resume = interview.getResume();
        traceService.log(TraceBizType.INTERVIEW, interview.getId(), resume.getCode(), resume.getCode(),
                interview.getRound().label() + "结论提交", previous, interview.getConclusion(),
                "打分：" + Optional.ofNullable(interview.getScore()).map(String::valueOf).orElse("未打分"));

        switch (interview.getConclusion()) {
            case ELIMINATE -> {
                resume.setEliminateReason(interview.getRound().label() + "淘汰：" + interview.getEliminateReason());
                resumeFlowService.advance(resume, ResumeStatus.ELIMINATED,
                        interview.getRound().label() + "淘汰", interview.getEliminateReason());
            }
            case PASS -> {
                InterviewRound next = interview.getRound().next();
                Interview task = new Interview();
                task.setResume(resume);
                task.setRound(next);
                task.setConclusion(InterviewConclusion.PENDING);
                eruptDao.persistAndFlush(task);
                resumeFlowService.advanceIfActive(resume, next.getResumeStatus(),
                        interview.getRound().label() + "通过", "自动创建" + next.label() + "任务");
                traceService.log(TraceBizType.INTERVIEW, task.getId(), resume.getCode(), resume.getCode(),
                        "自动创建面试任务", null, InterviewConclusion.PENDING, "轮次：" + next.label());
            }
            case PROPOSED -> {
                resumeFlowService.advanceIfActive(resume, ResumeStatus.PROPOSED,
                        "终面拟录用", "自动发起 Offer 审批");
                offerFlowService.createFromProposal(resume);
            }
            default -> {
                // PENDING never reaches here
            }
        }

        notifyService.notify(TalentConst.SCENE_INTERVIEW_RESULT, resumeFlowService.hrIds(),
                "面试结论：" + resume.getName() + " " + interview.getRound().label()
                        + " " + interview.getConclusion().label(),
                String.format("候选人 %s（%s）%s 结论为「%s」。", resume.getName(), resume.getCode(),
                        interview.getRound().label(), interview.getConclusion().label()));
    }

    /**
     * Calendar sync is deliberately non-fatal: a DingTalk outage must not block recording
     * the interview. The failure is stored on the record so HR can see and retry it.
     */
    private void syncCalendar(Interview interview, String key) {
        Resume resume = interview.getResume();
        try {
            // The first mobile owns the calendar, the rest are invited.
            List<String> mobiles = new ArrayList<>();
            if (null != interview.getInterviewer()) {
                mobiles.add(interview.getInterviewer().getPhone());
            }
            talentAuth.hrUsers().forEach(hr -> mobiles.add(hr.getPhone()));
            String summary = String.format("[%s] %s - %s",
                    interview.getRound().label(), resume.getName(),
                    null == resume.getJobPost() ? "未指定岗位" : resume.getJobPost().getName());
            String description = String.format("候选人：%s%n简历编号：%s%n应聘岗位：%s%n面试形式：%s%n地点/链接：%s",
                    resume.getName(), resume.getCode(),
                    null == resume.getJobPost() ? "未指定" : resume.getJobPost().getName(),
                    null == interview.getForm() ? "未指定" : interview.getForm().label(),
                    StringUtils.defaultIfBlank(interview.getPlace(), "未指定"));
            DingTalkService.CalendarEvent event = dingTalkService.upsertInterviewEvent(
                    interview.getDingEventId(), interview.getDingOwnerUserId(),
                    summary, description, interview.getPlace(),
                    interview.getInterviewTime().atZone(ZoneId.systemDefault()),
                    Optional.ofNullable(interview.getDurationMinutes()).orElse(60),
                    mobiles);
            interview.setDingEventId(event.eventId());
            interview.setDingOwnerUserId(event.ownerUserId());
            interview.setDingSyncKey(key);
            interview.setDingSyncError(null);
            traceService.log(TraceBizType.INTERVIEW, interview.getId(), resume.getCode(), resume.getCode(),
                    "钉钉日程同步成功", null, null, "日程ID：" + event.eventId());
        } catch (Exception e) {
            interview.setDingSyncError(StringUtils.abbreviate(e.getMessage(), 500));
            traceService.log(TraceBizType.INTERVIEW, interview.getId(), resume.getCode(), resume.getCode(),
                    "钉钉日程同步失败", null, null, e.getMessage());
            log.warn("钉钉日程同步失败 interview={}: {}", interview.getId(), e.getMessage());
        }
        eruptDao.merge(interview);
    }

}
