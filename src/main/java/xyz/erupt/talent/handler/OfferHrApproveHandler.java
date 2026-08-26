package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.model.form.OfferApproveForm;
import xyz.erupt.talent.service.OfferFlowService;

import java.util.List;

/**
 * Step 2 of the offer approval chain. Passing it triggers the automated mail; a delivery failure
 * is surfaced as a warning rather than an error, because the approval itself did succeed.
 *
 * @author YuePeng
 */
@Service
public class OfferHrApproveHandler implements OperationHandler<Offer, OfferApproveForm> {

    @Resource
    private OfferFlowService offerFlowService;

    @Override
    public String exec(List<Offer> data, OfferApproveForm form, String[] param) {
        String failure = offerFlowService.hrApprove(data.get(0).getId(),
                Boolean.TRUE.equals(form.getApproved()), form.getOpinion());
        return null == failure ? null : "alert(" + GsonFactory.getGson().toJson(failure) + ")";
    }

}
