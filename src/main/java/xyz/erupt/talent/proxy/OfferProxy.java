package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.talent.constant.OfferStatus;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.talent.constant.TraceBizType;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.model.Offer;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.talent.service.TalentSettingService;
import xyz.erupt.talent.service.SeqService;
import xyz.erupt.talent.service.TraceService;

/**
 * Numbering, defaults and audit for manually created offers.
 * Offers created by the automation path go through {@code OfferFlowService} instead.
 *
 * @author YuePeng
 */
@Component
public class OfferProxy implements DataProxy<Offer> {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private SeqService seqService;

    @Resource
    private TraceService traceService;

    @Resource
    private TalentSettingService settingService;

    @Override
    public void beforeAdd(Offer offer) {
        if (StringUtils.isBlank(offer.getCode())) {
            offer.setCode(seqService.next(TalentConst.SEQ_OFFER));
        }
        if (null == offer.getStatus()) {
            offer.setStatus(Boolean.TRUE.equals(settingService.get().getAutoOfferMode())
                    ? OfferStatus.PENDING_SEND : OfferStatus.PENDING_DEPT);
        }
        if (StringUtils.isBlank(offer.getReportAddress())) {
            offer.setReportAddress(settingService.get().getReportAddress());
        }
        Resume resume = this.resume(offer);
        if (null != resume) {
            offer.setMailTo(resume.getEmail());
        }
    }

    @Override
    public void afterAdd(Offer offer) {
        traceService.log(TraceBizType.OFFER, offer.getId(), offer.getCode(), this.resumeCode(offer),
                "Offer 手工创建", null, offer.getStatus(), null);
    }

    @Override
    public void afterUpdate(Offer offer) {
        traceService.log(TraceBizType.OFFER, offer.getId(), offer.getCode(), this.resumeCode(offer),
                "Offer 信息修改", null, offer.getStatus(), null);
    }

    /**
     * erupt binds a reference field from the request as a stub carrying only the id, so the
     * managed record has to be re-read before any of its own columns are used.
     */
    private Resume resume(Offer offer) {
        return null == offer.getResume() || null == offer.getResume().getId()
                ? null : eruptDao.find(Resume.class, offer.getResume().getId());
    }

    private String resumeCode(Offer offer) {
        Resume resume = this.resume(offer);
        return null == resume ? null : resume.getCode();
    }

}
