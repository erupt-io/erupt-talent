package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.PowerHandler;
import xyz.erupt.annotation.fun.PowerObject;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Section 4.7: interviewers may only view their own interviews and fill in the assessment —
 * they cannot create, delete or export anything. HR keeps full control.
 *
 * @author YuePeng
 */
@Component
public class InterviewPower implements PowerHandler {

    @Resource
    private TalentAuth talentAuth;

    @Override
    public void handler(PowerObject power) {
        if (talentAuth.isHr()) {
            return;
        }
        boolean interviewer = talentAuth.isInterviewer();
        power.setAdd(false);
        power.setDelete(false);
        power.setExport(false);
        power.setImportable(false);
        power.setCopy(false);
        power.setPrint(false);
        // Interviewers keep edit rights purely to record their assessment; the scheduling
        // fields are individually locked by HrOnlyReadonly.
        power.setEdit(interviewer);
        power.setQuery(interviewer);
        power.setViewDetails(interviewer);
    }

}
