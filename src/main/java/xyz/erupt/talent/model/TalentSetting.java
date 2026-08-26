package xyz.erupt.talent.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.constant.AnnotationConst;
import xyz.erupt.annotation.sub_erupt.Power;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.ViewType;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModelUpdateVo;
import xyz.erupt.talent.handler.TestDingTalkButton;
import xyz.erupt.talent.handler.TestSmtpButton;
import xyz.erupt.talent.proxy.AdminOnlyPower;
import xyz.erupt.talent.proxy.TalentSettingProxy;

/**
 * All external integrations are configured visually here rather than in application.yml,
 * so an administrator can wire up the mailbox, the SMTP sender and DingTalk without a redeploy.
 * <p>
 * Rendered as a single-record form view. Secrets use {@code EditType.PASSWORD}: the framework
 * only ever sends a mask to the browser, and {@link TalentSettingProxy} keeps the stored value
 * when the mask comes back unchanged.
 *
 * @author YuePeng
 */
@Erupt(
        name = "招聘系统配置",
        desc = "邮箱抓取、Offer 发信、钉钉日程与风控开关的可视化配置",
        dataProxy = TalentSettingProxy.class,
        power = @Power(add = true, edit = true, delete = false, export = false,
                importable = false, print = false, powerHandler = AdminOnlyPower.class)
)
@Entity
@Table(name = "r_setting")
@Getter
@Setter
public class TalentSetting extends MetaModelUpdateVo {

    /* ==================== Offer mail delivery (SMTP) ==================== */

    @Transient
    @EruptField(
            edit = @Edit(title = "Offer 邮件发送", type = EditType.GROUP,
                    groupType = @GroupType(fields = {
                            "mailSendEnabled", "smtpHost", "smtpPort", "smtpSsl", "smtpAccount",
                            "smtpPassword", "smtpFromName", "testSmtp"}))
    )
    private String smtpGroup;

    @EruptField(
            views = @View(title = "启用邮件发送", type = ViewType.BOOLEAN),
            edit = @Edit(title = "启用邮件发送", type = EditType.BOOLEAN, notNull = true,
                    desc = "关闭时 Offer 审批通过后停留在「待发送」，可稍后重新发送",
                    boolType = @BoolType(trueText = "启用", falseText = "停用"))
    )
    private Boolean mailSendEnabled = false;

    @EruptField(
            edit = @Edit(title = "SMTP 服务器", placeHolder = "smtp.exmail.qq.com")
    )
    private String smtpHost;

    @EruptField(
            edit = @Edit(title = "SMTP 端口", type = EditType.NUMBER,
                    numberType = @NumberType(min = 1, max = 65535))
    )
    private Integer smtpPort = 465;

    @EruptField(
            edit = @Edit(title = "SMTP 使用 SSL", type = EditType.BOOLEAN,
                    boolType = @BoolType(trueText = "SSL", falseText = "STARTTLS"))
    )
    private Boolean smtpSsl = true;

    @EruptField(
            edit = @Edit(title = "发件邮箱账号", placeHolder = "hr@company.com")
    )
    private String smtpAccount;

    @EruptField(
            edit = @Edit(title = "发件邮箱密码", type = EditType.PASSWORD,
                    desc = "多数邮箱服务商需填写「授权码」而非登录密码")
    )
    private String smtpPassword;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            edit = @Edit(title = "发件人显示名", placeHolder = "小阔招聘")
    )
    private String smtpFromName;

    @Transient
    @EruptField(
            edit = @Edit(title = "测试发信", type = EditType.BUTTON,
                    buttonType = @ButtonType(icon = "fa fa-paper-plane", style = "primary",
                            handler = TestSmtpButton.class))
    )
    private String testSmtp;

    /* ==================== DingTalk calendar ==================== */

    @Transient
    @EruptField(
            edit = @Edit(title = "钉钉日程同步", type = EditType.GROUP,
                    groupType = @GroupType(fields = {
                            "dingEnabled", "dingAppKey", "dingAppSecret", "dingAgentId", "testDingTalk"}))
    )
    private String dingGroup;

    @EruptField(
            views = @View(title = "启用钉钉日程", type = ViewType.BOOLEAN),
            edit = @Edit(title = "启用钉钉日程", type = EditType.BOOLEAN, notNull = true,
                    desc = "需在钉钉开放平台为应用开通「日程」与「通讯录读取」权限",
                    boolType = @BoolType(trueText = "启用", falseText = "停用"))
    )
    private Boolean dingEnabled = false;

    @EruptField(
            edit = @Edit(title = "钉钉 AppKey")
    )
    private String dingAppKey;

    @EruptField(
            edit = @Edit(title = "钉钉 AppSecret", type = EditType.PASSWORD)
    )
    private String dingAppSecret;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            edit = @Edit(title = "钉钉 AgentId",
                    desc = "留空则不推送「工作通知」，仅同步日程；在钉钉开放平台应用详情中查看")
    )
    private String dingAgentId;

    @Transient
    @EruptField(
            edit = @Edit(title = "测试钉钉凭据", type = EditType.BUTTON,
                    buttonType = @ButtonType(icon = "fa fa-bolt", style = "primary",
                            handler = TestDingTalkButton.class))
    )
    private String testDingTalk;

    /* ==================== Offer template & risk control ==================== */

    @Transient
    @EruptField(
            edit = @Edit(title = "Offer 模板与风控", type = EditType.GROUP,
                    groupType = @GroupType(fields = {
                            "autoOfferMode", "companyName", "reportAddress",
                            "offerMailSubject", "offerMailTemplate"}))
    )
    private String offerGroup;

    @EruptField(
            views = @View(title = "Offer 全自动发放", type = ViewType.BOOLEAN),
            edit = @Edit(title = "Offer 全自动发放", type = EditType.BOOLEAN, notNull = true,
                    desc = "风控开关：默认人工审批。开启后终面拟录用将跳过两级审批直接发件",
                    boolType = @BoolType(trueText = "全自动发放", falseText = "人工审批"))
    )
    private Boolean autoOfferMode = false;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            edit = @Edit(title = "公司名称", placeHolder = "小阔")
    )
    private String companyName;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            edit = @Edit(title = "默认报到地点", type = EditType.TEXTAREA,
                    placeHolder = "深圳市宝安区前海人寿金融中心T1栋15楼")
    )
    private String reportAddress;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            edit = @Edit(title = "Offer 邮件主题",
                    desc = "可用变量与正文相同，例如 ${candidateName}")
    )
    private String offerMailSubject = "${companyName} 录用通知 - ${jobPost}";

    @Lob
    @EruptField(
            edit = @Edit(title = "Offer 邮件模板", type = EditType.CODE_EDITOR,
                    desc = "可用变量：${candidateName} ${gender} ${jobPost} ${salary} ${probationMonths} "
                            + "${onboardDate} ${onboardDateCn} ${expireTime} ${offerCode} ${companyName} ${reportAddress}",
                    codeEditType = @CodeEditorType(language = "html", height = 380))
    )
    private String offerMailTemplate;

}
