package xyz.erupt.talent.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.constant.AnnotationConst;
import xyz.erupt.annotation.sub_erupt.Filter;
import xyz.erupt.annotation.sub_erupt.Power;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.ViewType;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModelUpdateVo;
import xyz.erupt.talent.handler.TestImapButton;
import xyz.erupt.talent.proxy.CreatorFilter;
import xyz.erupt.talent.proxy.TalentMailboxProxy;

/**
 * A per-HR resume-fetch mailbox. Unlike the org-wide {@link TalentSetting}, this is a normal
 * multi-row table: every HR maintains their own IMAP account, and the {@link CreatorFilter}
 * restricts the view so each HR sees only their own row(s) (super admin sees all).
 * <p>
 * Resumes fetched from a mailbox are stamped with that mailbox's creator as their {@code createBy},
 * which is exactly the key the isolation filters key on — so a fetched resume lands in, and stays
 * within, the owning HR's view.
 *
 * @author YuePeng
 */
@Erupt(
        name = "我的招聘邮箱",
        desc = "每位 HR 配置自己的简历抓取邮箱，抓取到的简历自动归属本人",
        dataProxy = TalentMailboxProxy.class,
        filter = @Filter(value = "", conditionHandler = CreatorFilter.class, params = "TalentMailbox.createBy"),
        power = @Power(export = false, importable = false, print = false)
)
@Entity
@Table(name = "r_mailbox")
@Getter
@Setter
public class TalentMailbox extends MetaModelUpdateVo {

    @EruptField(
            views = @View(title = "启用抓取", type = ViewType.BOOLEAN),
            edit = @Edit(title = "启用抓取", type = EditType.BOOLEAN, notNull = true,
                    desc = "停用时定时任务跳过本邮箱",
                    boolType = @BoolType(trueText = "启用", falseText = "停用"))
    )
    private Boolean enabled = true;

    @EruptField(
            views = @View(title = "IMAP 服务器"),
            edit = @Edit(title = "IMAP 服务器", notNull = true, placeHolder = "imap.exmail.qq.com")
    )
    private String imapHost;

    @EruptField(
            views = @View(title = "端口", width = "70px"),
            edit = @Edit(title = "IMAP 端口", notNull = true, type = EditType.NUMBER,
                    numberType = @NumberType(min = 1, max = 65535))
    )
    private Integer imapPort = 993;

    @EruptField(
            edit = @Edit(title = "IMAP 使用 SSL", type = EditType.BOOLEAN,
                    boolType = @BoolType(trueText = "SSL", falseText = "明文"))
    )
    private Boolean imapSsl = true;

    @EruptField(
            views = @View(title = "邮箱账号"),
            edit = @Edit(title = "招聘邮箱账号", notNull = true, placeHolder = "hr@company.com")
    )
    private String imapAccount;

    @EruptField(
            edit = @Edit(title = "邮箱密码", type = EditType.PASSWORD, notNull = true,
                    desc = "多数邮箱服务商需填写「授权码」而非登录密码")
    )
    private String imapPassword;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            edit = @Edit(title = "抓取文件夹", desc = "默认 INBOX")
    )
    private String imapFolder = "INBOX";

    @EruptField(
            edit = @Edit(title = "回溯天数", type = EditType.NUMBER,
                    desc = "每次任务只扫描最近 N 天的邮件",
                    numberType = @NumberType(min = 1, max = 90))
    )
    private Integer fetchLookbackDays = 7;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            edit = @Edit(title = "主题关键字过滤",
                    desc = "留空表示不过滤；多个关键字用英文逗号分隔，命中任一即视为投递邮件")
    )
    private String fetchKeyword;

    @Transient
    @EruptField(
            edit = @Edit(title = "测试邮箱连接", type = EditType.BUTTON,
                    buttonType = @ButtonType(icon = "fa fa-plug", style = "primary",
                            handler = TestImapButton.class))
    )
    private String testImap;

}
