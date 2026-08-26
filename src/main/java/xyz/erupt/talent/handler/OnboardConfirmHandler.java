package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.talent.model.Onboarding;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.talent.model.form.OnboardConfirmForm;
import xyz.erupt.talent.service.OnboardFlowService;

import java.util.List;

/**
 * Rule 8: the anti-misclick confirmation dialog.
 * <p>
 * {@link #eruptFormValue} is what makes the popup informative — it fills the candidate name,
 * position, planned date and the outstanding material checklist before the operator sees it,
 * so the confirmation is an informed one rather than a reflex click.
 *
 * @author YuePeng
 */
@Service
public class OnboardConfirmHandler implements OperationHandler<Onboarding, OnboardConfirmForm> {

    @Resource
    private OnboardFlowService onboardFlowService;

    @Override
    public OnboardConfirmForm eruptFormValue(List<Onboarding> data, OnboardConfirmForm form, String[] param) {
        Onboarding onboarding = onboardFlowService.require(data.get(0).getId());
        Resume resume = onboarding.getOffer().getResume();
        form.setCandidateName(resume.getName());
        form.setJobPostName(null == resume.getJobPost() ? "未指定" : resume.getJobPost().getName());
        form.setPlannedDate(onboarding.getPlannedDate());
        form.setActualDate(null == onboarding.getActualDate() ? onboarding.getPlannedDate() : onboarding.getActualDate());
        form.setEmpNo(onboarding.getEmpNo());
        List<String> pending = onboardFlowService.pendingMaterials(onboarding);
        form.setPendingMaterials(pending.isEmpty() ? "材料已全部核对齐全" : String.join("、", pending));
        form.setConfirmed(false);
        return form;
    }

    @Override
    public String exec(List<Onboarding> data, OnboardConfirmForm form, String[] param) {
        onboardFlowService.confirm(data.get(0).getId(), form.getActualDate(),
                form.getEmpNo(), Boolean.TRUE.equals(form.getConfirmed()));
        return null;
    }

}
