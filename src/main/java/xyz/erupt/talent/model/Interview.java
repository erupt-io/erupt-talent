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
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.Readonly;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.ViewType;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModelUpdateVo;
import xyz.erupt.talent.constant.InterviewConclusion;
import xyz.erupt.talent.constant.InterviewForm;
import xyz.erupt.talent.constant.InterviewRound;
import xyz.erupt.talent.proxy.HrOnlyReadonly;
import xyz.erupt.talent.proxy.InterviewPower;
import xyz.erupt.talent.proxy.InterviewProxy;
import xyz.erupt.talent.proxy.OwnInterviewFilter;
import xyz.erupt.upms.model.EruptUser;

import java.time.LocalDateTime;

/**
 * One interview round for one resume.
 * <p>
 * Scheduling fields are HR-only ({@link HrOnlyReadonly}); interviewers may only fill in the
 * assessment. Saving a confirmed time syncs a DingTalk calendar event, and saving a conclusion
 * advances the pipeline — both in {@link InterviewProxy}.
 *
 * @author YuePeng
 */
@Erupt(
        name = "面试管理",
        desc = "多轮面试记录、评价打分与自动流转，面试安排自动同步钉钉日程",
        orderBy = "Interview.createTime desc",
        dataProxy = InterviewProxy.class,
        filter = @Filter(value = "", conditionHandler = OwnInterviewFilter.class),
        power = @Power(export = true, powerHandler = InterviewPower.class),
        layout = @Layout(tableLeftFixed = 2)
)
@Entity
@Table(name = "r_interview", indexes = @Index(name = "idx_interview_resume", columnList = "resume_id"))
@Getter
@Setter
public class Interview extends MetaModelUpdateVo {

    @ManyToOne(optional = false)
    @EruptField(
            views = {
                    @View(title = "简历编号", column = "code", width = "150px"),
                    @View(title = "候选人", column = "name", width = "100px"),
                    @View(title = "应聘岗位", column = "jobPost.name", width = "140px")
            },
            edit = @Edit(title = "关联简历", notNull = true, type = EditType.REFERENCE_TABLE,
                    search = @Search, readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    referenceTableType = @ReferenceTableType(label = "code"))
    )
    private Resume resume;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "面试轮次", width = "90px"),
            edit = @Edit(title = "面试轮次", notNull = true, search = @Search, type = EditType.CHOICE,
                    readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    choiceType = @ChoiceType(fetchHandler = InterviewRound.H.class))
    )
    private InterviewRound round;

    @ManyToOne
    @EruptField(
            views = @View(title = "面试官", column = "name", width = "100px"),
            edit = @Edit(title = "面试官", type = EditType.REFERENCE_TABLE, search = @Search,
                    readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    referenceTableType = @ReferenceTableType(label = "name"))
    )
    private EruptUser interviewer;

    @EruptField(
            views = @View(title = "面试时间", width = "150px", type = ViewType.DATE_TIME),
            edit = @Edit(title = "面试时间", search = @Search,
                    desc = "确定面试时间并保存后，系统自动创建钉钉日程并推送提醒",
                    readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    dateType = @DateType(type = DateType.Type.DATE_TIME))
    )
    private LocalDateTime interviewTime;

    @EruptField(
            views = @View(title = "时长(分钟)", width = "90px", show = false),
            edit = @Edit(title = "时长(分钟)", type = EditType.NUMBER,
                    readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    numberType = @NumberType(min = 15, max = 480))
    )
    private Integer durationMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "面试形式", width = "100px"),
            edit = @Edit(title = "面试形式", type = EditType.CHOICE, search = @Search,
                    readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    choiceType = @ChoiceType(fetchHandler = InterviewForm.H.class))
    )
    private InterviewForm form;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "地点 / 链接", show = false),
            edit = @Edit(title = "地点 / 链接", desc = "线下填写面试地址，线上填写会议链接",
                    readonly = @Readonly(allowChange = false, exprHandler = HrOnlyReadonly.class),
                    // @Dynamic binds exactly one JS variable named `value` holding the
                    // depend-field's value — both in the browser and in server-side validation.
                    dynamic = @Dynamic(dependField = "form", condition = "value !== 'PHONE'",
                            match = Dynamic.Ctrl.SHOW, noMatch = Dynamic.Ctrl.HIDE))
    )
    private String place;

    /* ---------- Assessment (the part interviewers fill in) ---------- */

    @EruptField(
            views = @View(title = "综合打分", width = "90px"),
            edit = @Edit(title = "综合打分", type = EditType.SLIDER,
                    sliderType = @SliderType(max = 100, step = 5, markPoints = {0, 60, 80, 100}))
    )
    private Integer score;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "面试评价", show = false),
            edit = @Edit(title = "面试评价", type = EditType.TEXTAREA)
    )
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "面试结论", width = "100px"),
            edit = @Edit(title = "面试结论", notNull = true, search = @Search, type = EditType.CHOICE,
                    desc = "通过自动进入下一轮；终面选择「拟录用」自动发起 Offer 审批；淘汰则终止流程",
                    choiceType = @ChoiceType(fetchHandler = InterviewConclusion.H.class))
    )
    private InterviewConclusion conclusion = InterviewConclusion.PENDING;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "淘汰原因", show = false),
            edit = @Edit(title = "淘汰原因", type = EditType.TEXTAREA,
                    dynamic = @Dynamic(dependField = "conclusion", condition = "value === 'ELIMINATE'",
                            match = Dynamic.Ctrl.NOTNULL, noMatch = Dynamic.Ctrl.HIDE))
    )
    private String eliminateReason;

    /* ---------- DingTalk calendar sync result ---------- */

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "钉钉日程", width = "100px"),
            edit = @Edit(title = "钉钉日程ID", readonly = @Readonly(allowChange = false))
    )
    private String dingEventId;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "钉钉同步异常", show = false),
            edit = @Edit(title = "钉钉同步异常", show = false,
                    search = @Search(operator = QueryExpression.LIKE))
    )
    private String dingSyncError;

    /**
     * The DingTalk userId that owns the calendar event; needed to update or delete it, since the
     * owner may differ from the current interviewer after a reschedule. Internal, not UI-editable.
     */
    @Column(length = AnnotationConst.CODE_LENGTH)
    private String dingOwnerUserId;

    /**
     * Signature of the last-synced schedule; a save only re-syncs the calendar when it changes,
     * so editing the assessment never needlessly re-creates the event. Internal, not UI-editable.
     */
    @Column(length = AnnotationConst.REMARK_LENGTH)
    private String dingSyncKey;

}
