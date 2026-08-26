package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.EruptButtonHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.talent.model.TalentMailbox;
import xyz.erupt.talent.proxy.TalentMailboxProxy;
import xyz.erupt.talent.service.MailFetchService;

/**
 * Verifies a per-user mailbox's IMAP credentials straight from the form, before they are saved.
 * The password arrives masked when it was not re-typed, so the stored value is resolved first.
 *
 * @author YuePeng
 */
@Service
public class TestImapButton implements EruptButtonHandler<TalentMailbox> {

    @Resource
    private MailFetchService mailFetchService;

    @Resource
    private TalentMailboxProxy mailboxProxy;

    @Override
    public String click(TalentMailbox mailbox, String[] params) {
        mailbox.setImapPassword(mailboxProxy.resolvePassword(mailbox));
        try {
            int count = mailFetchService.testConnection(mailbox);
            return "alert(" + GsonFactory.getGson().toJson(
                    "邮箱连接成功，「" + mailbox.getImapFolder() + "」共有 " + count + " 封邮件。") + ")";
        } catch (Exception e) {
            throw new EruptWebApiRuntimeException("邮箱连接失败：" + e.getMessage());
        }
    }

}
