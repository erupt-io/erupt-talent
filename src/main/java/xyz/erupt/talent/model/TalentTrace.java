package xyz.erupt.talent.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.constant.AnnotationConst;
import xyz.erupt.annotation.sub_erupt.Layout;
import xyz.erupt.annotation.sub_erupt.Power;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.ViewType;
import xyz.erupt.annotation.sub_field.sub_edit.ChoiceType;
import xyz.erupt.annotation.sub_field.sub_edit.DateType;
import xyz.erupt.annotation.sub_field.sub_edit.Search;
import xyz.erupt.jpa.model.BaseModel;
import xyz.erupt.talent.constant.TraceBizType;

import java.time.LocalDateTime;

/**
 * Append-only business trail. Every status transition in the pipeline writes one row here,
 * so the full history of a candidate stays queryable and is never overwritten.
 *
 * @author YuePeng
 */
@Erupt(
        name = "流转日志",
        desc = "所有状态变更的操作人与时间，永久留存，不可修改",
        orderBy = "TalentTrace.operateTime desc",
        power = @Power(add = false, edit = false, delete = false, export = true),
        layout = @Layout(pageSize = 30)
)
@Entity
@Table(name = "r_trace", indexes = {
        @Index(name = "idx_trace_biz", columnList = "bizType,bizId"),
        @Index(name = "idx_trace_resume", columnList = "resumeCode")
})
@Getter
@Setter
public class TalentTrace extends BaseModel {

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "业务类型", width = "90px"),
            edit = @Edit(title = "业务类型", search = @Search, type = EditType.CHOICE,
                    choiceType = @ChoiceType(fetchHandler = TraceBizType.H.class))
    )
    private TraceBizType bizType;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "业务编号", width = "170px"),
            edit = @Edit(title = "业务编号", search = @Search(operator = QueryExpression.LIKE))
    )
    private String bizCode;

    @EruptField(
            views = @View(title = "业务ID", width = "80px", show = false),
            edit = @Edit(title = "业务ID", search = @Search)
    )
    private Long bizId;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "简历编号", width = "150px"),
            edit = @Edit(title = "简历编号", search = @Search(operator = QueryExpression.LIKE))
    )
    private String resumeCode;

    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "操作", width = "140px"),
            edit = @Edit(title = "操作", search = @Search(operator = QueryExpression.LIKE))
    )
    private String action;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "变更前", width = "130px"),
            edit = @Edit(title = "变更前")
    )
    private String fromStatus;

    @Column(length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "变更后", width = "130px"),
            edit = @Edit(title = "变更后")
    )
    private String toStatus;

    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "操作人", width = "100px"),
            edit = @Edit(title = "操作人", search = @Search(operator = QueryExpression.LIKE))
    )
    private String operator;

    @Column(nullable = false)
    @EruptField(
            views = @View(title = "操作时间", width = "150px", type = ViewType.DATE_TIME),
            edit = @Edit(title = "操作时间", search = @Search,
                    dateType = @DateType(type = DateType.Type.DATE_TIME))
    )
    private LocalDateTime operateTime;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "详情"),
            edit = @Edit(title = "详情", type = EditType.TEXTAREA)
    )
    private String detail;

}
