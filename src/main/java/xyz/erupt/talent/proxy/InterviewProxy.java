package xyz.erupt.talent.proxy;

import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.core.context.OldEntityTL;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.talent.constant.InterviewConclusion;
import xyz.erupt.talent.model.Interview;
import xyz.erupt.talent.service.InterviewFlowService;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Bridges an interview save to the automation rules.
 * <p>
 * Detecting "the conclusion just changed" needs the pre-update value, which cannot be re-queried in
 * {@code beforeUpdate} — erupt has already mutated the managed entity by then. The pristine
 * snapshot is read from {@link OldEntityTL} instead and parked for {@code afterUpdate}.
 *
 * @author YuePeng
 */
@Slf4j
@Component
public class InterviewProxy implements DataProxy<Interview> {

    private static final ThreadLocal<InterviewConclusion> PREVIOUS = new ThreadLocal<>();

    @Resource
    private InterviewFlowService interviewFlowService;

    @Resource
    private TalentAuth talentAuth;

    @Override
    public void beforeUpdate(Interview interview) {
        if (talentAuth.interviewerOnly() && !this.isOwnRecord(interview)) {
            throw new EruptWebApiRuntimeException("面试官仅可维护自己的面试记录");
        }
        PREVIOUS.set(this.previousConclusion());
    }

    @Override
    public void afterUpdate(Interview interview) {
        try {
            interviewFlowService.onSaved(interview, PREVIOUS.get());
        } finally {
            PREVIOUS.remove();
        }
    }

    @Override
    public void afterAdd(Interview interview) {
        interviewFlowService.onSaved(interview, null);
    }

    @Override
    public void beforeDelete(Interview interview) {
        interviewFlowService.onDelete(interview);
    }

    private boolean isOwnRecord(Interview interview) {
        Long uid = talentAuth.uid();
        return null != uid && null != interview.getInterviewer()
                && uid.equals(interview.getInterviewer().getId());
    }

    /**
     * @return the conclusion held before this save, or null when it cannot be determined
     */
    private InterviewConclusion previousConclusion() {
        String snapshot = OldEntityTL.get();
        if (null == snapshot) {
            return null;
        }
        try {
            JsonObject json = GsonFactory.getGson().fromJson(snapshot, JsonObject.class);
            if (null == json || !json.has("conclusion") || json.get("conclusion").isJsonNull()) {
                return null;
            }
            return InterviewConclusion.valueOf(json.get("conclusion").getAsString());
        } catch (Exception e) {
            log.debug("面试记录旧状态解析失败: {}", e.getMessage());
            return null;
        }
    }

}
