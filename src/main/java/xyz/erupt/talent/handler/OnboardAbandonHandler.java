package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.talent.model.Onboarding;
import xyz.erupt.talent.model.form.OnboardAbandonForm;
import xyz.erupt.talent.service.OnboardFlowService;

import java.util.List;

/**
 * @author YuePeng
 */
@Service
public class OnboardAbandonHandler implements OperationHandler<Onboarding, OnboardAbandonForm> {

    @Resource
    private OnboardFlowService onboardFlowService;

    @Override
    public String exec(List<Onboarding> data, OnboardAbandonForm form, String[] param) {
        onboardFlowService.abandon(data.get(0).getId(), form.getReason());
        return null;
    }

}
