package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.EruptButtonHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.talent.model.TalentSetting;
import xyz.erupt.talent.proxy.TalentSettingProxy;
import xyz.erupt.talent.service.MailSendService;

/**
 * Verifies the SMTP credentials without delivering anything to a real candidate.
 *
 * @author YuePeng
 */
@Service
public class TestSmtpButton implements EruptButtonHandler<TalentSetting> {

    @Resource
    private MailSendService mailSendService;

    @Resource
    private TalentSettingProxy settingProxy;

    @Override
    public String click(TalentSetting setting, String[] params) {
        setting.setSmtpPassword(settingProxy.resolveSecret(
                setting.getSmtpPassword(), TalentSetting::getSmtpPassword));
        try {
            mailSendService.testConnection(setting);
            return "alert(" + GsonFactory.getGson().toJson(
                    "SMTP 连接与认证成功，可以正常发送 Offer 邮件。") + ")";
        } catch (Exception e) {
            throw new EruptWebApiRuntimeException("SMTP 连接失败：" + e.getMessage());
        }
    }

}
