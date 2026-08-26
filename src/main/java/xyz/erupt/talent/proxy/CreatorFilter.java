package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.FilterHandler;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Row-level isolation by record creator: a user only ever sees the records they themselves created,
 * super admins see everything. The HQL path to the {@code createBy} column is passed through the
 * annotation's {@code params} so the one handler can serve every entity — directly on the record
 * ({@code Resume.createBy}) or across a relation ({@code Offer.resume.createBy}).
 * <p>
 * {@code createBy} is written by the framework from the session user's display name (see
 * {@code MetaModelUpdateVo}), which is the same string {@link TalentAuth#username()} returns, so
 * the equality holds. The value is a single-quote-escaped session attribute, never request input.
 *
 * @author YuePeng
 */
@Component
public class CreatorFilter implements FilterHandler {

    private static final String ALWAYS_TRUE = "1 = 1";

    private static final String ALWAYS_FALSE = "1 = 0";

    @Resource
    private TalentAuth talentAuth;

    @Override
    public String filter(String condition, String[] params) {
        if (talentAuth.isAdmin()) {
            return StringUtils.isBlank(condition) ? ALWAYS_TRUE : condition;
        }
        // No session (e.g. a scheduled query) can never be attributed to a creator: expose nothing.
        if (null == talentAuth.uid()) {
            return ALWAYS_FALSE;
        }
        String field = (null != params && params.length > 0 && StringUtils.isNotBlank(params[0]))
                ? params[0] : "createBy";
        return field + " = '" + talentAuth.username().replace("'", "''") + "'";
    }

}
