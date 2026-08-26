package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.model.form.OfferApproveForm;
import xyz.erupt.talent.service.OfferFlowService;

import java.util.List;

/**
 * Step 1 of the offer approval chain: the job post's dept leader.
 *
 * @author YuePeng
 */
@Service
public class OfferDeptApproveHandler implements OperationHandler<Offer, OfferApproveForm> {

    @Resource
    private OfferFlowService offerFlowService;

    @Override
    public String exec(List<Offer> data, OfferApproveForm form, String[] param) {
        offerFlowService.deptApprove(data.get(0).getId(),
                Boolean.TRUE.equals(form.getApproved()), form.getOpinion());
        return null;
    }

}
