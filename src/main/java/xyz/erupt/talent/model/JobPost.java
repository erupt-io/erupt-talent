package xyz.erupt.talent.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.config.QueryExpression;
import xyz.erupt.annotation.constant.AnnotationConst;
import xyz.erupt.annotation.sub_erupt.*;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.jpa.model.MetaModelUpdateVo;
import xyz.erupt.talent.constant.JobPostStatus;
import xyz.erupt.talent.proxy.HrOnlyPower;
import xyz.erupt.upms.model.EruptOrg;
import xyz.erupt.upms.model.EruptUser;

/**
 * Talent position. Every resume, interview and offer hangs off a job post.
 *
 * @author YuePeng
 */
@Erupt(
        name = "岗位管理",
        desc = "统一维护招聘岗位，关联全流程业务数据",
        orderBy = "JobPost.createTime desc",
        power = @Power(export = true, powerHandler = HrOnlyPower.class),
        layout = @Layout(tableLeftFixed = 1),
        drills = @Drill(
                title = "岗位简历",
                icon = "fa fa-address-book-o",
                link = @Link(column = "id", linkErupt = Resume.class, joinColumn = "jobPost.id")
        )
)
@Entity
@Table(name = "r_job_post")
@Getter
@Setter
public class JobPost extends MetaModelUpdateVo {

    @Column(nullable = false)
    @EruptField(
            views = @View(title = "岗位名称", width = "160px"),
            edit = @Edit(title = "岗位名称", notNull = true, search = @Search(operator = QueryExpression.LIKE))
    )
    private String name;

    @ManyToOne
    @EruptField(
            views = @View(title = "所属部门", column = "name"),
            edit = @Edit(title = "所属部门", notNull = true, type = EditType.REFERENCE_TREE,
                    referenceTreeType = @ReferenceTreeType(pid = "parentOrg.id"))
    )
    private EruptOrg org;

    @ManyToOne
    @EruptField(
            views = @View(title = "部门负责人", column = "name"),
            edit = @Edit(title = "部门负责人", notNull = true, type = EditType.REFERENCE_TABLE,
                    desc = "Offer 审批第一环节的审批人",
                    referenceTableType = @ReferenceTableType(label = "name"))
    )
    private EruptUser deptLeader;

    @EruptField(
            views = @View(title = "招聘HC", width = "90px"),
            edit = @Edit(title = "招聘HC", notNull = true, type = EditType.NUMBER,
                    numberType = @NumberType(min = 1, max = 9999))
    )
    private Integer hc = 1;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "岗位状态", width = "100px"),
            edit = @Edit(title = "岗位状态", notNull = true, search = @Search, type = EditType.CHOICE,
                    choiceType = @ChoiceType(fetchHandler = JobPostStatus.H.class))
    )
    private JobPostStatus status = JobPostStatus.OPEN;

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "岗位简介", show = false),
            edit = @Edit(title = "岗位简介", type = EditType.TEXTAREA)
    )
    private String intro;

}
