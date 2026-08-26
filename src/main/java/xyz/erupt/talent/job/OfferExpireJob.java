package xyz.erupt.talent.job;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.job.handler.EruptJobHandler;
import xyz.erupt.talent.service.OfferFlowService;

/**
 * Marks delivered offers whose reply window elapsed as 已过期, keeping the funnel honest.
 *
 * @author YuePeng
 */
@Service
public class OfferExpireJob implements EruptJobHandler {

    public static final String CODE = "talent-offer-expire";

    @Resource
    private OfferFlowService offerFlowService;

    @Override
    public String name() {
        return "Offer 过期检查";
    }

    /**
     * Daily at 01:00.
     */
    @Override
    public String cron() {
        return "0 0 1 * * ?";
    }

    @Override
    public String exec(String code, String param) {
        return "标记过期 Offer " + offerFlowService.expireSweep() + " 条";
    }

}
