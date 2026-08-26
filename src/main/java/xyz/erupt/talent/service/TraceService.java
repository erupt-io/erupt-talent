package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.LabeledEnum;
import xyz.erupt.talent.constant.TraceBizType;
import xyz.erupt.talent.model.TalentTrace;

import java.time.LocalDateTime;

/**
 * Writes the append-only business trail. Runs in its own transaction so that logging can never
 * mark the caller's transaction rollback-only, and so a trace survives even when the caller aborts.
 *
 * @author YuePeng
 */
@Service
public class TraceService {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TalentAuth talentAuth;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(TraceBizType bizType, Long bizId, String bizCode, String resumeCode,
                    String action, LabeledEnum from, LabeledEnum to, String detail) {
        TalentTrace trace = new TalentTrace();
        trace.setBizType(bizType);
        trace.setBizId(bizId);
        trace.setBizCode(bizCode);
        trace.setResumeCode(resumeCode);
        trace.setAction(action);
        trace.setFromStatus(null == from ? null : from.label());
        trace.setToStatus(null == to ? null : to.label());
        trace.setOperator(talentAuth.username());
        trace.setOperateTime(LocalDateTime.now());
        trace.setDetail(detail);
        eruptDao.persist(trace);
    }

}
