package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.annotation.exception.EruptException;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.core.constant.EruptConst;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.model.TalentSetting;
import xyz.erupt.talent.service.TalentAuth;
import xyz.erupt.talent.service.TalentSettingService;

import java.util.function.Function;

/**
 * Load / save logic for the single-record configuration form view. The framework renders the form
 * schema only, so both directions are implemented here.
 * <p>
 * Secrets are never round-tripped through the browser: erupt sends a mask for every
 * {@code EditType.PASSWORD} field, and — unlike the table editor — the form view does not restore
 * the stored value automatically, so the mask is translated back here. A blank submission keeps
 * the stored secret as well, so clearing the input can never silently erase credentials.
 *
 * @author YuePeng
 */
@Component
public class TalentSettingProxy implements DataProxy<TalentSetting> {

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TalentSettingService settingService;

    @Resource
    private TalentAuth talentAuth;

    @Override
    public void formViewBehavior(TalentSetting model) {
        TalentSetting stored = settingService.stored();
        if (null == stored) {
            model.setOfferMailTemplate(TalentSettingService.DEFAULT_TEMPLATE);
            return;
        }
        BeanUtils.copyProperties(stored, model);
    }

    /**
     * The form-view save endpoint is not transactional itself, so the write boundary is opened here.
     */
    @Transactional
    @Override
    public void formSave(TalentSetting model) throws EruptException {
        if (!talentAuth.isAdmin()) {
            throw new EruptException("仅超级管理员可修改招聘系统配置");
        }
        TalentSetting stored = settingService.stored();
        if (null == stored) {
            this.blankMaskedSecrets(model);
            eruptDao.persist(model);
            return;
        }
        this.restoreSecrets(model, stored);
        BeanUtils.copyProperties(model, stored, "id", "createBy", "createTime");
        eruptDao.merge(stored);
    }

    /**
     * A brand-new configuration has nothing to fall back on, so a mask means "not provided".
     */
    private void blankMaskedSecrets(TalentSetting model) {
        if (masked(model.getSmtpPassword())) {
            model.setSmtpPassword(null);
        }
        if (masked(model.getDingAppSecret())) {
            model.setDingAppSecret(null);
        }
    }

    private void restoreSecrets(TalentSetting model, TalentSetting stored) {
        keep(model.getSmtpPassword(), stored::getSmtpPassword, model::setSmtpPassword);
        keep(model.getDingAppSecret(), stored::getDingAppSecret, model::setDingAppSecret);
    }

    private void keep(String incoming, java.util.function.Supplier<String> current,
                      java.util.function.Consumer<String> setter) {
        if (masked(incoming) || StringUtils.isBlank(incoming)) {
            setter.accept(current.get());
        }
    }

    private static boolean masked(String value) {
        return EruptConst.PASSWORD_PLACEHOLDER.equals(value);
    }

    /**
     * Resolves the stored secret for a value that may be a mask — used by the test buttons, which
     * receive the raw form object straight from the browser.
     */
    public String resolveSecret(String incoming, Function<TalentSetting, String> reader) {
        if (StringUtils.isNotBlank(incoming) && !masked(incoming)) {
            return incoming;
        }
        TalentSetting stored = settingService.stored();
        return null == stored ? null : reader.apply(stored);
    }

}
