package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.model.TalentSeq;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Global numbering rule: {@code PREFIX + yyyyMMdd + 4-digit sequence}, unique and never reused.
 * <p>
 * The counter row is locked pessimistically and bumped in its own transaction, so a number is
 * consumed even if the surrounding business transaction later rolls back — gaps are acceptable,
 * duplicates are not. The very first caller of a new day can lose the insert race, which is why
 * {@link #next(String)} retries: on the second pass the row exists and the row lock serialises.
 *
 * @author YuePeng
 */
@Service
public class SeqService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final int MAX_ATTEMPTS = 5;

    @Resource
    private EruptDao eruptDao;

    /**
     * Self-reference so the inner call still goes through the transaction proxy.
     */
    @Lazy
    @Resource
    private SeqService self;

    public String next(String prefix) {
        String key = prefix + LocalDate.now().format(DAY);
        RuntimeException last = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return key + String.format("%04d", self.bump(key));
            } catch (RuntimeException e) {
                // Lost the insert race for a brand-new counter row; the retry takes the update path.
                last = e;
            }
        }
        throw last;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int bump(String key) {
        TalentSeq seq = eruptDao.getEntityManager().find(TalentSeq.class, key, LockModeType.PESSIMISTIC_WRITE);
        if (null == seq) {
            seq = new TalentSeq();
            seq.setId(key);
            seq.setValue(1);
            eruptDao.persistAndFlush(seq);
        } else {
            seq.setValue(seq.getValue() + 1);
            eruptDao.mergeAndFlush(seq);
        }
        return seq.getValue();
    }

}
