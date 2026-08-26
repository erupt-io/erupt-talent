package xyz.erupt.talent.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.constant.AnnotationConst;
import xyz.erupt.annotation.expr.ExprBool;
import xyz.erupt.annotation.sub_erupt.*;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.Readonly;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.ViewType;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModelUpdateVo;
import xyz.erupt.talent.constant.InterviewConclusion;
import xyz.erupt.talent.constant.ResumeSource;
import xyz.erupt.talent.constant.ResumeStatus;
import xyz.erupt.talent.handler.ScreenPassHandler;
import xyz.erupt.talent.handler.ScreenRejectHandler;
import xyz.erupt.talent.handler.StatusFixHandler;
import xyz.erupt.talent.model.form.ScreenRejectForm;
import xyz.erupt.talent.model.form.StatusFixForm;
import xyz.erupt.talent.proxy.AdminOnly;
import xyz.erupt.talent.proxy.CreatorFilter;
import xyz.erupt.talent.proxy.HrOnlyPower;
import xyz.erupt.talent.proxy.ResumeProxy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Candidate resume — the master record that drives the whole pipeline.
 * Fed by manual entry, Excel import and the mailbox fetch job.
 *
 * @author YuePeng
 */
@Erupt(
        name = "简历管理",
        desc = "收纳、导入、筛选候选人简历，驱动全流程业务流转",
        orderBy = "Resume.createTime desc",
        dataProxy = ResumeProxy.class,
        power = @Power(export = true, importable = true, powerHandler = HrOnlyPower.class),
        // Each HR only sees the resumes they created / imported / fetched; super admin sees all.
        filter = @Filter(value = "", conditionHandler = CreatorFilter.class, params = "Resume.createBy"),
        layout = @Layout(tableLeftFixed = 2),
        rowOperation = {
                @RowOperation(
                        title = "初筛通过", code = "screenPass", icon = "fa fa-check",
                        tip = "初筛通过后自动创建一面任务并推送待办",
                        callHint = "确认这些简历初筛通过？系统将自动创建一面任务。",
                        mode = RowOperation.Mode.MULTI,
                        ifExpr = "item.status === '待初筛'",
                        operationHandler = ScreenPassHandler.class
                ),
                @RowOperation(
                        title = "初筛淘汰", code = "screenReject", icon = "fa fa-times",
                        tip = "初筛淘汰需填写原因，流程随即终止",
                        mode = RowOperation.Mode.MULTI,
                        ifExpr = "item.status === '待初筛'",
                        eruptClass = ScreenRejectForm.class,
                        operationHandler = ScreenRejectHandler.class
                ),
                @RowOperation(
                        title = "状态回溯修正", code = "statusFix", icon = "fa fa-undo", fold = true,
                        tip = "仅超级管理员可用，用于修正异常流转数据",
                        mode = RowOperation.Mode.SINGLE,
                        show = @ExprBool(exprHandler = AdminOnly.class),
                        eruptClass = StatusFixForm.class,
                        operationHandler = StatusFixHandler.class
                )
        },
        drills = {
                @Drill(title = "面试记录", icon = "fa fa-comments-o",
                        link = @Link(column = "id", linkErupt = Interview.class, joinColumn = "resume.id")),
                @Drill(title = "Offer", icon = "fa fa-file-text-o",
                        link = @Link(column = "id", linkErupt = Offer.class, joinColumn = "resume.id"))
        }
)
@Entity
@Table(name = "r_resume", indexes = {
        @Index(name = "idx_resume_status", columnList = "status"),
        @Index(name = "idx_resume_mail_key", columnList = "mailKey")
})
@Getter
@Setter
public class Resume extends MetaModelUpdateVo {

    @Column(length = AnnotationConst.CODE_LENGTH, unique = true, updatable = false)
    @EruptField(
            views = @View(title = "简历编号", width = "150px"),
            edit = @Edit(title = "简历编号", readonly = @Readonly(allowChange = false),
                    desc = "系统自动生成：JL + 年月日 + 4位流水号",
                    search = @Search(operator = QueryExpression.LIKE))
    )
    private String code;

    @Column(nullable = false)
    @EruptField(
            views = @View(title = "候选人姓名", width = "100px"),
            edit = @Edit(title = "候选人姓名", notNull = true, search = @Search(operator = QueryExpression.LIKE))
    )
    private String name;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "性别", width = "60px"),
            edit = @Edit(title = "性别", type = EditType.CHOICE,
                    choiceType = @ChoiceType(type = ChoiceType.Type.RADIO, vl = {
                            @VL(value = "男", label = "男"),
                            @VL(value = "女", label = "女")
                    }))
    )
    private String gender;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "手机号", width = "120px"),
            edit = @Edit(title = "手机号", search = @Search(operator = QueryExpression.LIKE))
    )
    private String phone;

    @EruptField(
            views = @View(title = "邮箱", width = "180px"),
            edit = @Edit(title = "邮箱", desc = "Offer 邮件将发送至该地址",
                    search = @Search(operator = QueryExpression.LIKE))
    )
    private String email;

    @ManyToOne
    @EruptField(
            views = @View(title = "应聘岗位", column = "name", width = "140px"),
            edit = @Edit(title = "应聘岗位", notNull = true, type = EditType.REFERENCE_TABLE,
                    search = @Search, referenceTableType = @ReferenceTableType(label = "name"))
    )
    private JobPost jobPost;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "简历状态", width = "120px"),
            edit = @Edit(title = "简历状态", show = false, search = @Search,
                    readonly = @Readonly(allowChange = false),
                    type = EditType.CHOICE, choiceType = @ChoiceType(fetchHandler = ResumeStatus.H.class))
    )
    private ResumeStatus status = ResumeStatus.PENDING_SCREEN;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "来源渠道", width = "120px"),
            edit = @Edit(title = "来源渠道", notNull = true, search = @Search, type = EditType.CHOICE,
                    choiceType = @ChoiceType(fetchHandler = ResumeSource.H.class))
    )
    private ResumeSource source = ResumeSource.MANUAL;

    @EruptField(
            views = @View(title = "投递时间", width = "150px", type = ViewType.DATE_TIME),
            edit = @Edit(title = "投递时间", search = @Search, dateType = @DateType(type = DateType.Type.DATE_TIME))
    )
    private LocalDateTime deliverTime;

    /* ---------- Profile ---------- */

    @Transient
    @EruptField(
            edit = @Edit(title = "背景资料", type = EditType.GROUP,
                    groupType = @GroupType(fields = {"education", "school", "major", "workYears", "expectSalary"}))
    )
    private String profileGroup;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "学历", width = "80px", show = false),
            edit = @Edit(title = "学历", type = EditType.CHOICE, search = @Search,
                    choiceType = @ChoiceType(vl = {
                            @VL(value = "大专", label = "大专"),
                            @VL(value = "本科", label = "本科"),
                            @VL(value = "硕士", label = "硕士"),
                            @VL(value = "博士", label = "博士"),
                            @VL(value = "其他", label = "其他")
                    }))
    )
    private String education;

    @EruptField(
            views = @View(title = "毕业院校", show = false),
            edit = @Edit(title = "毕业院校")
    )
    private String school;

    @EruptField(
            views = @View(title = "专业", show = false),
            edit = @Edit(title = "专业")
    )
    private String major;

    @EruptField(
            views = @View(title = "工作年限", width = "90px", show = false),
            edit = @Edit(title = "工作年限", type = EditType.NUMBER, numberType = @NumberType(min = 0, max = 60))
    )
    private Integer workYears;

    @Column(precision = 12, scale = 2)
    @EruptField(
            views = @View(title = "期望薪资", show = false),
            edit = @Edit(title = "期望薪资", type = EditType.NUMBER, desc = "单位：元/月")
    )
    private BigDecimal expectSalary;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "简历附件", type = ViewType.ATTACHMENT_DIALOG, show = false),
            edit = @Edit(title = "简历附件", type = EditType.ATTACHMENT,
                    attachmentType = @AttachmentType(maxLimit = 5, fileTypes = {"pdf", "doc", "docx", "jpg", "png"}))
    )
    private String attachment;

    /* ---------- Screening ---------- */

    @Transient
    @EruptField(
            edit = @Edit(title = "初筛与流转", type = EditType.GROUP,
                    groupType = @GroupType(collapsed = true,
                            fields = {"screenResult", "screenTime", "screenBy", "eliminateReason", "remark"}))
    )
    private String screenGroup;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "初筛结果", width = "90px"),
            edit = @Edit(title = "初筛结果", show = false, search = @Search, type = EditType.CHOICE,
                    readonly = @Readonly(allowChange = false),
                    choiceType = @ChoiceType(fetchHandler = InterviewConclusion.H.class))
    )
    private InterviewConclusion screenResult;

    @EruptField(
            views = @View(title = "初筛时间", show = false, type = ViewType.DATE_TIME),
            edit = @Edit(title = "初筛时间", show = false, readonly = @Readonly(allowChange = false))
    )
    private LocalDateTime screenTime;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "初筛人", show = false),
            edit = @Edit(title = "初筛人", show = false, readonly = @Readonly(allowChange = false))
    )
    private String screenBy;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "淘汰原因", show = false),
            edit = @Edit(title = "淘汰原因", show = false, type = EditType.TEXTAREA,
                    readonly = @Readonly(allowChange = false))
    )
    private String eliminateReason;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "备注", show = false),
            edit = @Edit(title = "备注", type = EditType.TEXTAREA)
    )
    private String remark;

    /* ---------- Mailbox ingestion trace ---------- */

    @Transient
    @EruptField(
            edit = @Edit(title = "邮件溯源", type = EditType.GROUP,
                    groupType = @GroupType(collapsed = true, fields = {"mailSubject", "mailFrom", "mailText"}))
    )
    private String mailGroup;

    /**
     * Message-ID of the source mail; unique so a re-scan can never ingest the same mail twice.
     */
    @Column(length = AnnotationConst.REMARK_LENGTH, unique = true, updatable = false)
    private String mailKey;

    @EruptField(
            views = @View(title = "邮件主题", show = false),
            edit = @Edit(title = "邮件主题", readonly = @Readonly(allowChange = false))
    )
    private String mailSubject;

    @EruptField(
            views = @View(title = "投递邮箱", show = false),
            edit = @Edit(title = "投递邮箱", readonly = @Readonly(allowChange = false))
    )
    private String mailFrom;

    @Lob
    @EruptField(
            edit = @Edit(title = "简历原文", type = EditType.TEXTAREA,
                    desc = "邮件正文与附件解析出的原始文本，供人工复核解析结果",
                    readonly = @Readonly(allowChange = false))
    )
    private String mailText;

}
