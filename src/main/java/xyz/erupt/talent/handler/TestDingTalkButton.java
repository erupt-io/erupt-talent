package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.EruptButtonHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.talent.model.TalentSetting;
import xyz.erupt.talent.proxy.TalentSettingProxy;
import xyz.erupt.talent.service.DingTalkService;

/**
 * Verifies the DingTalk AppKey / AppSecret by acquiring both access tokens.
 *
 * @author YuePeng
 */
@Service
public class TestDingTalkButton implements EruptButtonHandler<TalentSetting> {

    @Resource
    private DingTalkService dingTalkService;

    @Resource
    private TalentSettingProxy settingProxy;

    @Override
    public String click(TalentSetting setting, String[] params) {
        setting.setDingAppSecret(settingProxy.resolveSecret(
                setting.getDingAppSecret(), TalentSetting::getDingAppSecret));
        try {
            dingTalkService.testCredentials(setting);
            return "alert(" + GsonFactory.getGson().toJson(
                    "钉钉凭据校验成功，可以创建面试日程。") + ")";
        } catch (Exception e) {
            throw new EruptWebApiRuntimeException("钉钉凭据校验失败：" + e.getMessage());
        }
    }

}
