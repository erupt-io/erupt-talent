package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.service.OfferFlowService;

import java.util.List;

/**
 * Retries delivery for an offer parked in 待发送.
 *
 * @author YuePeng
 */
@Service
public class OfferResendHandler implements OperationHandler<Offer, Void> {

    @Resource
    private OfferFlowService offerFlowService;

    @Override
    public String exec(List<Offer> data, Void form, String[] param) {
        String failure = offerFlowService.resend(data.get(0).getId());
        return null == failure ? null : "alert(" + GsonFactory.getGson().toJson(failure) + ")";
    }

}
