package xyz.erupt.talent.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.constant.AnnotationConst;
import xyz.erupt.annotation.sub_erupt.Filter;
import xyz.erupt.annotation.sub_erupt.Layout;
import xyz.erupt.annotation.sub_erupt.Power;
import xyz.erupt.annotation.sub_erupt.RowOperation;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.Readonly;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.ViewType;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModelUpdateVo;
import xyz.erupt.talent.constant.OfferStatus;
import xyz.erupt.talent.constant.OfferType;
import xyz.erupt.talent.handler.*;
import xyz.erupt.talent.model.form.OfferApproveForm;
import xyz.erupt.talent.model.form.OfferReplyForm;
import xyz.erupt.talent.proxy.CreatorFilter;
import xyz.erupt.talent.proxy.HrOnlyPower;
import xyz.erupt.talent.proxy.OfferProxy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer with its two-step approval chain and the delivery record of the automated mail.
 * Every approval decision and mail body is kept for audit.
 *
 * @author YuePeng
 */
@Erupt(
        name = "Offer管理",
        desc = "线上审批 Offer，审批通过自动发送标准化邮件，全程留痕",
        orderBy = "Offer.createTime desc",
        dataProxy = OfferProxy.class,
        power = @Power(export = true, powerHandler = HrOnlyPower.class),
        // Isolation follows the owning resume's creator, so the whole pipeline stays coherent.
        filter = @Filter(value = "", conditionHandler = CreatorFilter.class, params = "Offer.resume.createBy"),
        layout = @Layout(tableLeftFixed = 2),
        rowOperation = {
                @RowOperation(
                        title = "部门审批", code = "deptApprove", icon = "fa fa-gavel",
                        mode = RowOperation.Mode.SINGLE,
                        ifExpr = "item.status === '待部门负责人审批'",
                        eruptClass = OfferApproveForm.class,
                        operationHandler = OfferDeptApproveHandler.class
                ),
                @RowOperation(
                        title = "HR复核", code = "hrApprove", icon = "fa fa-check-square-o",
                        tip = "复核通过后系统自动发送 Offer 邮件",
                        mode = RowOperation.Mode.SINGLE,
                        ifExpr = "item.status === '待HR复核'",
                        eruptClass = OfferApproveForm.class,
                        operationHandler = OfferHrApproveHandler.class
                ),
                @RowOperation(
                        title = "重新发送", code = "resend", icon = "fa fa-paper-plane",
                        callHint = "确认重新发送该 Offer 邮件？",
                        mode = RowOperation.Mode.SINGLE,
                        ifExpr = "item.status === '待发送'",
                        operationHandler = OfferResendHandler.class
                ),
                @RowOperation(
                        title = "登记候选人回复", code = "reply", icon = "fa fa-reply",
                        tip = "登记候选人接受或拒绝，接受后自动生成待入职数据",
                        mode = RowOperation.Mode.SINGLE,
                        ifExpr = "item.status === '已发送'",
                        eruptClass = OfferReplyForm.class,
                        operationHandler = OfferReplyHandler.class
                )
        }
)
@Entity
@Table(name = "r_offer", indexes = @Index(name = "idx_offer_resume", columnList = "resume_id"))
@Getter
@Setter
public class Offer extends MetaModelUpdateVo {

    @Column(length = AnnotationConst.CODE_LENGTH, unique = true, updatable = false)
    @EruptField(
            views = @View(title = "Offer编号", width = "170px"),
            edit = @Edit(title = "Offer编号", readonly = @Readonly(allowChange = false),
                    desc = "系统自动生成：OFFER + 年月日 + 4位流水号",
                    search = @Search(operator = QueryExpression.LIKE))
    )
    private String code;

    @ManyToOne(optional = false)
    @EruptField(
            views = {
                    @View(title = "简历编号", column = "code", width = "150px"),
                    @View(title = "候选人", column = "name", width = "100px"),
                    @View(title = "应聘岗位", column = "jobPost.name", width = "140px")
            },
            edit = @Edit(title = "关联简历", notNull = true, type = EditType.REFERENCE_TABLE,
                    search = @Search, referenceTableType = @ReferenceTableType(label = "code"))
    )
    private Resume resume;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "Offer状态", width = "140px"),
            edit = @Edit(title = "Offer状态", show = false, search = @Search, type = EditType.CHOICE,
                    readonly = @Readonly(allowChange = false),
                    choiceType = @ChoiceType(fetchHandler = OfferStatus.H.class))
    )
    private OfferStatus status = OfferStatus.PENDING_DEPT;

    @Column(precision = 12, scale = 2, nullable = false)
    @EruptField(
            views = @View(title = "薪资", width = "100px"),
            edit = @Edit(title = "薪资", notNull = true, type = EditType.NUMBER, desc = "单位：元/月")
    )
    private BigDecimal salary;

    @EruptField(
            views = @View(title = "试用期(月)", width = "100px"),
            edit = @Edit(title = "试用期(月)", notNull = true, type = EditType.NUMBER,
                    numberType = @NumberType(min = 0, max = 12))
    )
    private Integer probationMonths = 3;

    @EruptField(
            views = @View(title = "入职日期", width = "110px", type = ViewType.DATE),
            edit = @Edit(title = "入职日期", notNull = true, search = @Search,
                    dateType = @DateType(pickerMode = DateType.PickerMode.FUTURE))
    )
    private LocalDate onboardDate;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "Offer类型", width = "100px"),
            edit = @Edit(title = "Offer类型", notNull = true, search = @Search, type = EditType.CHOICE,
                    choiceType = @ChoiceType(fetchHandler = OfferType.H.class))
    )
    private OfferType offerType = OfferType.FORMAL;

    @EruptField(
            views = @View(title = "回复截止", width = "110px", type = ViewType.DATE),
            edit = @Edit(title = "回复截止时间", notNull = true,
                    desc = "超过该日期仍未回复，系统自动标记为已过期",
                    dateType = @DateType(pickerMode = DateType.PickerMode.FUTURE))
    )
    private LocalDate expireTime;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "报到地点", show = false),
            edit = @Edit(title = "报到地点", desc = "留空则使用「招聘系统配置」中的默认报到地点")
    )
    private String reportAddress;

    /* ---------- Approval trail ---------- */

    @Transient
    @EruptField(
            edit = @Edit(title = "审批留痕", type = EditType.GROUP,
                    groupType = @GroupType(collapsed = true,
                            fields = {"deptApprover", "deptOpinion", "deptTime", "hrApprover", "hrOpinion", "hrTime"}))
    )
    private String approvalGroup;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "部门审批人", show = false),
            edit = @Edit(title = "部门审批人", readonly = @Readonly(allowChange = false))
    )
    private String deptApprover;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "部门审批意见", show = false),
            edit = @Edit(title = "部门审批意见", type = EditType.TEXTAREA, readonly = @Readonly(allowChange = false))
    )
    private String deptOpinion;

    @EruptField(
            views = @View(title = "部门审批时间", show = false, type = ViewType.DATE_TIME),
            edit = @Edit(title = "部门审批时间", readonly = @Readonly(allowChange = false))
    )
    private LocalDateTime deptTime;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "HR复核人", show = false),
            edit = @Edit(title = "HR复核人", readonly = @Readonly(allowChange = false))
    )
    private String hrApprover;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "HR复核意见", show = false),
            edit = @Edit(title = "HR复核意见", type = EditType.TEXTAREA, readonly = @Readonly(allowChange = false))
    )
    private String hrOpinion;

    @EruptField(
            views = @View(title = "HR复核时间", show = false, type = ViewType.DATE_TIME),
            edit = @Edit(title = "HR复核时间", readonly = @Readonly(allowChange = false))
    )
    private LocalDateTime hrTime;

    /* ---------- Mail delivery record ---------- */

    @Transient
    @EruptField(
            edit = @Edit(title = "发件留痕", type = EditType.GROUP,
                    groupType = @GroupType(collapsed = true,
                            fields = {"mailTo", "sentTime", "sendError", "mailContent"}))
    )
    private String mailGroup;

    @EruptField(
            views = @View(title = "收件邮箱", show = false),
            edit = @Edit(title = "收件邮箱", readonly = @Readonly(allowChange = false))
    )
    private String mailTo;

    @EruptField(
            views = @View(title = "发送时间", width = "150px", type = ViewType.DATE_TIME),
            edit = @Edit(title = "发送时间", readonly = @Readonly(allowChange = false))
    )
    private LocalDateTime sentTime;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "发送异常", show = false),
            edit = @Edit(title = "发送异常", type = EditType.TEXTAREA, readonly = @Readonly(allowChange = false))
    )
    private String sendError;

    @Lob
    @EruptField(
            views = @View(title = "邮件正文", type = ViewType.HTML, show = false),
            edit = @Edit(title = "邮件正文快照", type = EditType.TEXTAREA,
                    desc = "变量替换后实际发出的邮件内容", readonly = @Readonly(allowChange = false))
    )
    private String mailContent;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "候选人回复说明", show = false),
            edit = @Edit(title = "候选人回复说明", type = EditType.TEXTAREA, readonly = @Readonly(allowChange = false))
    )
    private String replyRemark;

}
