package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.context.MetaContext;
import xyz.erupt.core.context.MetaUser;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.notice.channel.EruptInternalNotice;
import xyz.erupt.notice.pojo.NoticeMessage;
import xyz.erupt.notice.service.EruptNoticeService;
import xyz.erupt.talent.model.TalentSetting;
import xyz.erupt.upms.model.EruptUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Key-milestone notifications: an in-app message plus, when SMTP is configured, an e-mail.
 * <p>
 * Isolated in its own transaction so a delivery failure can never roll back the business change
 * that triggered it, and wrapped in a synthetic user context so scheduled (nobody-logged-in)
 * sends still satisfy {@code EruptNoticeService}, which reads the current user.
 *
 * @author YuePeng
 */
@Slf4j
@Service
public class NotifyService {

    @Resource
    private EruptNoticeService eruptNoticeService;

    @Resource
    private EruptInternalNotice eruptInternalNotice;

    @Resource
    private MailSendService mailSendService;

    @Resource
    private DingTalkService dingTalkService;

    @Resource
    private TalentSettingService settingService;

    @Resource
    private EruptDao eruptDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(String scene, List<Long> receivers, String title, String content) {
        List<Long> targets = receivers.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return;
        }
        boolean synthetic = null == MetaContext.getUser() || null == MetaContext.getUser().getUid();
        if (synthetic) {
            MetaContext.set(new MetaContext());
            MetaContext.register(new MetaUser(targets.get(0), "system", "系统"));
        }
        try {
            NoticeMessage message = new NoticeMessage();
            message.setTitle(title);
            message.setContent(content);
            eruptNoticeService.send(eruptInternalNotice, scene, targets, message);
        } catch (Exception e) {
            log.error("站内通知发送失败 scene={}: {}", scene, e.getMessage());
        } finally {
            if (synthetic) {
                MetaContext.remove();
            }
        }
        this.mail(targets, title, content);
        this.dingTalk(targets, title, content);
    }

    /**
     * Best-effort DingTalk work notification; skipped when DingTalk push is not configured or no
     * recipient has a phone number on file.
     */
    private void dingTalk(List<Long> receivers, String title, String content) {
        TalentSetting setting = settingService.get();
        if (!Boolean.TRUE.equals(setting.getDingEnabled()) || StringUtils.isBlank(setting.getDingAgentId())) {
            return;
        }
        List<String> mobiles = new ArrayList<>();
        for (Long uid : receivers) {
            EruptUser user = eruptDao.find(EruptUser.class, uid);
            if (null != user && StringUtils.isNotBlank(user.getPhone())) {
                mobiles.add(user.getPhone());
            }
        }
        if (!mobiles.isEmpty()) {
            dingTalkService.sendWorkNotice(mobiles, title, content);
        }
    }

    /**
     * Best-effort internal e-mail; silently skipped when SMTP is off or a user has no address.
     */
    private void mail(List<Long> receivers, String title, String content) {
        for (Long uid : receivers) {
            EruptUser user = eruptDao.find(EruptUser.class, uid);
            if (null == user || StringUtils.isBlank(user.getEmail())) {
                continue;
            }
            try {
                mailSendService.send(user.getEmail(), title, content);
            } catch (Exception e) {
                log.warn("通知邮件发送失败 uid={}: {}", uid, e.getMessage());
            }
        }
    }

}
