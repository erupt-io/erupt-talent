package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.core.constant.EruptConst;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.model.TalentMailbox;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Guards the per-user mailbox table: a non-admin may only ever edit or delete their own rows. The
 * list is already narrowed by {@link CreatorFilter}, but editing/deleting by id is re-checked here
 * so a hand-crafted id can never reach another HR's mailbox.
 *
 * @author YuePeng
 */
@Component
public class TalentMailboxProxy implements DataProxy<TalentMailbox> {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TalentAuth talentAuth;

    @Override
    public void beforeUpdate(TalentMailbox mailbox) {
        this.assertOwn(mailbox.getId());
    }

    @Override
    public void beforeDelete(TalentMailbox mailbox) {
        this.assertOwn(mailbox.getId());
    }

    private void assertOwn(Long id) {
        if (talentAuth.isAdmin() || null == id) {
            return;
        }
        TalentMailbox stored = eruptDao.find(TalentMailbox.class, id);
        if (null != stored && !talentAuth.username().equals(stored.getCreateBy())) {
            throw new EruptWebApiRuntimeException("仅可维护自己的招聘邮箱");
        }
    }

    /**
     * Resolves the mailbox password for the test button, whose payload carries the masked value
     * when it was not re-typed. Falls back to the stored secret of the same row.
     */
    public String resolvePassword(TalentMailbox mailbox) {
        String incoming = mailbox.getImapPassword();
        if (StringUtils.isNotBlank(incoming) && !EruptConst.PASSWORD_PLACEHOLDER.equals(incoming)) {
            return incoming;
        }
        if (null == mailbox.getId()) {
            return incoming;
        }
        TalentMailbox stored = eruptDao.find(TalentMailbox.class, mailbox.getId());
        return null == stored ? incoming : stored.getImapPassword();
    }

}
