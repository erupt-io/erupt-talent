package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.model.form.OfferReplyForm;
import xyz.erupt.talent.service.OfferFlowService;

import java.util.List;

/**
 * Rule 7: records the candidate's answer; accepting spawns the pending-onboarding record.
 *
 * @author YuePeng
 */
@Service
public class OfferReplyHandler implements OperationHandler<Offer, OfferReplyForm> {

    @Resource
    private OfferFlowService offerFlowService;

    @Override
    public String exec(List<Offer> data, OfferReplyForm form, String[] param) {
        offerFlowService.reply(data.get(0).getId(),
                Boolean.TRUE.equals(form.getAccepted()), form.getRemark());
        return null;
    }

}
