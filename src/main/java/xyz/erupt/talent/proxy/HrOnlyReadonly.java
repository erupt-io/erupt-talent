package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.sub_field.Readonly;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Locks the interview scheduling fields for everyone except HR.
 * <p>
 * Fields using this handler declare {@code allowChange = false}, which is what makes the lock
 * actually enforced on save rather than only visual: erupt skips a field whose resolved readonly
 * flag is true and whose {@code allowChange} is false.
 * <p>
 * The handler is intentionally stateless with respect to the owning field — erupt pools annotation
 * proxies by value equality, so identical {@code @Readonly} declarations share one instance.
 *
 * @author YuePeng
 */
@Component
public class HrOnlyReadonly implements Readonly.ReadonlyHandler {

    @Resource
    private TalentAuth talentAuth;

    @Override
    public boolean add(boolean add, String[] params) {
        return !talentAuth.isHr();
    }

    @Override
    public boolean edit(boolean edit, String[] params) {
        return !talentAuth.isHr();
    }

}
