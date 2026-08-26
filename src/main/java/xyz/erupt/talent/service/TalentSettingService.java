package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.model.TalentSetting;

import java.util.List;

/**
 * Single accessor for the visually configured integration settings.
 * The table intentionally holds exactly one row, seeded on first boot.
 *
 * @author YuePeng
 */
@Service
public class TalentSettingService {

    /**
     * Default offer mail body, seeded on first boot and editable in the UI afterwards.
     */
    public static final String DEFAULT_TEMPLATE = """
            <p>尊敬的 <b>${candidateName}</b> ${gender}：</p>
            <p>${companyName}非常荣幸能聘用您担任 <b>${jobPost}</b> 一职，真诚欢迎您成为${companyName}的一份子！</p>
            <ul>
              <li>请于 <b>${onboardDateCn}</b> 到我司办理入职（详情请见附件）</li>
              <li>报到地点：${reportAddress}</li>
              <li>薪资：${salary} 元/月，试用期 ${probationMonths} 个月</li>
              <li>入职材料需扫描二维码进行上传</li>
              <li>请详细阅读附件内容，并于 <b>${expireTime}</b> 前邮件回复是否接收</li>
            </ul>
            <p>如有任何疑问，请随时与我联系！</p>
            <p style="color:#999">Offer 编号：${offerCode}</p>
            """;

    @Resource
    private EruptDao eruptDao;

    /**
     * @return the stored singleton, or a transient all-disabled instance when nothing is stored yet
     */
    public TalentSetting get() {
        TalentSetting stored = stored();
        return null == stored ? defaults() : stored;
    }

    /**
     * @return the managed singleton row, or null when the table is still empty
     */
    public TalentSetting stored() {
        List<TalentSetting> list = eruptDao.lambdaQuery(TalentSetting.class)
                .orderBy(TalentSetting::getId).limit(1).list();
        return list.isEmpty() ? null : list.get(0);
    }

    @Transactional
    public void seed() {
        if (null == stored()) {
            eruptDao.persist(defaults());
        }
    }

    private TalentSetting defaults() {
        TalentSetting setting = new TalentSetting();
        setting.setOfferMailTemplate(DEFAULT_TEMPLATE);
        return setting;
    }

}
