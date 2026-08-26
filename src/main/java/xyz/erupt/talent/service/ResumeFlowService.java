package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.*;
import xyz.erupt.talent.model.Interview;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.upms.model.EruptUser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Screening and the resume-side status machine. Every transition goes through
 * {@link #advance} so that no state can change without a trace record.
 *
 * @author YuePeng
 */
@Service
public class ResumeFlowService {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TraceService traceService;

    @Resource
    private NotifyService notifyService;

    @Resource
    private TalentAuth talentAuth;

    /**
     * Rule 3: screening passed -> create the first-round interview task and push a to-do.
     */
    @Transactional
    public int screenPass(List<Long> resumeIds) {
        int affected = 0;
        for (Long id : resumeIds) {
            Resume resume = this.require(id);
            if (resume.getStatus() != ResumeStatus.PENDING_SCREEN) {
                continue;
            }
            resume.setScreenResult(InterviewConclusion.PASS);
            resume.setScreenTime(LocalDateTime.now());
            resume.setScreenBy(talentAuth.username());
            this.advance(resume, ResumeStatus.SCREEN_PASS, "初筛通过", "自动创建一面任务");

            Interview interview = new Interview();
            interview.setResume(resume);
            interview.setRound(InterviewRound.ROUND_1);
            interview.setConclusion(InterviewConclusion.PENDING);
            eruptDao.persistAndFlush(interview);
            traceService.log(TraceBizType.INTERVIEW, interview.getId(), resume.getCode(), resume.getCode(),
                    "自动创建面试任务", null, InterviewConclusion.PENDING, "轮次：一面");

            notifyService.notify(TalentConst.SCENE_SCREEN_PASS, this.hrIds(),
                    "待安排一面：" + resume.getName(),
                    String.format("简历 %s（%s）初筛通过，已自动创建一面任务，请安排面试官与面试时间。",
                            resume.getCode(), resume.getName()));
            affected++;
        }
        return affected;
    }

    /**
     * Screening rejected -> the reason is mandatory and the pipeline terminates here.
     */
    @Transactional
    public int screenReject(List<Long> resumeIds, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new EruptWebApiRuntimeException("请填写淘汰原因");
        }
        int affected = 0;
        for (Long id : resumeIds) {
            Resume resume = this.require(id);
            if (resume.getStatus() != ResumeStatus.PENDING_SCREEN) {
                continue;
            }
            resume.setScreenResult(InterviewConclusion.ELIMINATE);
            resume.setScreenTime(LocalDateTime.now());
            resume.setScreenBy(talentAuth.username());
            resume.setEliminateReason(reason);
            this.advance(resume, ResumeStatus.ELIMINATED, "初筛淘汰", reason);
            affected++;
        }
        return affected;
    }

    /**
     * Administrator-only correction path required by the global "one-way flow" rule.
     */
    @Transactional
    public void statusFix(Long resumeId, ResumeStatus target, String reason) {
        if (!talentAuth.isAdmin()) {
            throw new EruptWebApiRuntimeException("仅超级管理员可回溯修正数据");
        }
        if (StringUtils.isBlank(reason)) {
            throw new EruptWebApiRuntimeException("请填写修正原因");
        }
        Resume resume = this.require(resumeId);
        this.advance(resume, target, "状态回溯修正", reason);
    }

    /**
     * Applies a status transition and records it. Terminal states are never silently overwritten
     * by the automation rules — only the administrator correction path may leave them.
     */
    @Transactional
    public void advance(Resume resume, ResumeStatus target, String action, String detail) {
        ResumeStatus from = resume.getStatus();
        if (from == target) {
            return;
        }
        resume.setStatus(target);
        eruptDao.merge(resume);
        traceService.log(TraceBizType.RESUME, resume.getId(), resume.getCode(), resume.getCode(),
                action, from, target, detail);
    }

    /**
     * Same as {@link #advance} but refuses to move a resume that already reached a terminal state,
     * which is what the automated rules use.
     */
    @Transactional
    public void advanceIfActive(Resume resume, ResumeStatus target, String action, String detail) {
        if (null != resume.getStatus() && resume.getStatus().terminal()) {
            return;
        }
        this.advance(resume, target, action, detail);
    }

    public Resume require(Long id) {
        Resume resume = eruptDao.find(Resume.class, id);
        if (null == resume) {
            throw new EruptWebApiRuntimeException("简历不存在：" + id);
        }
        return resume;
    }

    public List<Long> hrIds() {
        return talentAuth.hrUsers().stream().map(EruptUser::getId).toList();
    }

}
