package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.FilterHandler;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Row-level restriction from section 4.7, extended for creator isolation:
 * <ul>
 *   <li>super admin — every interview;</li>
 *   <li>interviewer (non-HR) — only interviews they conduct, by user id;</li>
 *   <li>HR — only interviews of the resumes they created, mirroring {@link CreatorFilter}.</li>
 * </ul>
 * The HQL alias is the entity's simple name, hence the {@code Interview.} prefix. Interpolated
 * values are the session user id (numeric) or display name (single-quote-escaped), never request
 * input.
 *
 * @author YuePeng
 */
@Component
public class OwnInterviewFilter implements FilterHandler {

    private static final String ALWAYS_TRUE = "1 = 1";

    private static final String ALWAYS_FALSE = "1 = 0";

    @Resource
    private TalentAuth talentAuth;

    /**
     * The returned expression is appended after an {@code and}, so it must never be blank —
     * an unrestricted caller gets a tautology rather than an empty string.
     */
    @Override
    public String filter(String condition, String[] params) {
        if (talentAuth.isAdmin()) {
            return StringUtils.isBlank(condition) ? ALWAYS_TRUE : condition;
        }
        Long uid = talentAuth.uid();
        if (null == uid) {
            return ALWAYS_FALSE;
        }
        if (talentAuth.interviewerOnly()) {
            return "Interview.interviewer.id = " + uid;
        }
        return "Interview.resume.createBy = '" + talentAuth.username().replace("'", "''") + "'";
    }

}
