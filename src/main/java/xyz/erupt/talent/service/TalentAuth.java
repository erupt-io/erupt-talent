package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.core.module.MetaUserinfo;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.upms.model.EruptRole;
import xyz.erupt.upms.model.EruptUser;
import xyz.erupt.upms.service.EruptUserService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Role checks behind the permission model of section 4.7:
 * super admin &gt; HR specialist &gt; interviewer &gt; ordinary employee (no access at all).
 *
 * @author YuePeng
 */
@Service
public class TalentAuth {

    @Resource
    private EruptUserService eruptUserService;

    @Resource
    private EruptDao eruptDao;

    /**
     * @return the cached user info, or null when there is no logged-in session (e.g. a scheduled job)
     */
    public MetaUserinfo userinfo() {
        return eruptUserService.getSimpleUserInfo();
    }

    public Long uid() {
        MetaUserinfo info = userinfo();
        return null == info ? null : info.getId();
    }

    public String username() {
        MetaUserinfo info = userinfo();
        return null == info ? "system" : info.getUsername();
    }

    public boolean isAdmin() {
        MetaUserinfo info = userinfo();
        return null != info && info.isSuperAdmin();
    }

    /**
     * HR specialists own the whole pipeline; super admins inherit that.
     */
    public boolean isHr() {
        MetaUserinfo info = userinfo();
        if (null == info) {
            return false;
        }
        return info.isSuperAdmin() || roles(info).contains(TalentConst.ROLE_HR);
    }

    public boolean isInterviewer() {
        MetaUserinfo info = userinfo();
        return null != info && roles(info).contains(TalentConst.ROLE_INTERVIEWER);
    }

    /**
     * @return true when the caller may only ever see their own interview rows
     */
    public boolean interviewerOnly() {
        return !isHr() && isInterviewer();
    }

    /**
     * All users holding the HR role, used as the default notification audience.
     */
    public List<EruptUser> hrUsers() {
        return eruptDao.lambdaQuery(EruptUser.class)
                .eq(EruptUser::getStatus, true)
                .list()
                .stream()
                .filter(it -> null != it.getRoles() && it.getRoles().stream()
                        .map(EruptRole::getCode).anyMatch(TalentConst.ROLE_HR::equals))
                .toList();
    }

    private List<String> roles(MetaUserinfo info) {
        return Optional.ofNullable(info.getRoles()).orElse(Collections.emptyList());
    }

}
