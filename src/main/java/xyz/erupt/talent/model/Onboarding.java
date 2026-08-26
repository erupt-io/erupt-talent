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
import xyz.erupt.talent.constant.OnboardStatus;
import xyz.erupt.talent.handler.OnboardAbandonHandler;
import xyz.erupt.talent.handler.OnboardConfirmHandler;
import xyz.erupt.talent.model.form.OnboardAbandonForm;
import xyz.erupt.talent.model.form.OnboardConfirmForm;
import xyz.erupt.talent.proxy.CreatorFilter;
import xyz.erupt.talent.proxy.HrOnlyPower;
import xyz.erupt.upms.model.EruptOrg;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Onboarding record, auto-created the moment a candidate accepts an offer.
 * Confirming it closes the whole talent loop.
 *
 * @author YuePeng
 */
@Erupt(
        name = "入职管理",
        desc = "承接已确认 Offer 的候选人，完成入职登记，闭环招聘流程",
        orderBy = "Onboarding.createTime desc",
        power = @Power(add = false, export = true, powerHandler = HrOnlyPower.class),
        // Isolation follows the owning resume's creator, reached through offer -> resume.
        filter = @Filter(value = "", conditionHandler = CreatorFilter.class, params = "Onboarding.offer.resume.createBy"),
        layout = @Layout(tableLeftFixed = 2),
        rowOperation = {
                @RowOperation(
                        title = "确认入职", code = "confirm", icon = "fa fa-user-plus",
                        tip = "弹出材料核对提醒，确认无误后闭环流程",
                        mode = RowOperation.Mode.SINGLE,
                        ifExpr = "item.status === '待入职'",
                        eruptClass = OnboardConfirmForm.class,
                        operationHandler = OnboardConfirmHandler.class
                ),
                @RowOperation(
                        title = "放弃入职", code = "abandon", icon = "fa fa-user-times", fold = true,
                        mode = RowOperation.Mode.SINGLE,
                        ifExpr = "item.status === '待入职'",
                        eruptClass = OnboardAbandonForm.class,
                        operationHandler = OnboardAbandonHandler.class
                )
        }
)
@Entity
@Table(name = "r_onboarding", indexes = @Index(name = "idx_onboarding_offer", columnList = "offer_id"))
@Getter
@Setter
public class Onboarding extends MetaModelUpdateVo {

    /**
     * Material checklist offered in the confirmation dialog.
     */
    public static final String[] MATERIALS = {
            "身份证", "学历学位证书", "离职证明", "体检报告",
            "银行卡", "一寸照片", "劳动合同", "背景调查授权书"
    };

    @ManyToOne(optional = false)
    @EruptField(
            views = {
                    @View(title = "Offer编号", column = "code", width = "170px"),
                    @View(title = "候选人", column = "resume.name", width = "100px"),
                    @View(title = "应聘岗位", column = "resume.jobPost.name", width = "140px")
            },
            edit = @Edit(title = "关联Offer", notNull = true, type = EditType.REFERENCE_TABLE,
                    search = @Search, readonly = @Readonly(allowChange = false),
                    referenceTableType = @ReferenceTableType(label = "code"))
    )
    private Offer offer;

    @Enumerated(EnumType.STRING)
    @Column(length = AnnotationConst.CODE_LENGTH, nullable = false)
    @EruptField(
            views = @View(title = "入职状态", width = "110px"),
            edit = @Edit(title = "入职状态", show = false, search = @Search, type = EditType.CHOICE,
                    readonly = @Readonly(allowChange = false),
                    choiceType = @ChoiceType(fetchHandler = OnboardStatus.H.class))
    )
    private OnboardStatus status = OnboardStatus.PENDING;

    @EruptField(
            views = @View(title = "计划入职日期", width = "120px", type = ViewType.DATE),
            edit = @Edit(title = "计划入职日期", search = @Search, readonly = @Readonly(allowChange = false))
    )
    private LocalDate plannedDate;

    @EruptField(
            views = @View(title = "实际到岗日期", width = "120px", type = ViewType.DATE),
            edit = @Edit(title = "实际到岗日期", search = @Search)
    )
    private LocalDate actualDate;

    @Column(length = AnnotationConst.CODE_LENGTH, unique = true)
    @EruptField(
            views = @View(title = "员工工号", width = "110px"),
            edit = @Edit(title = "员工工号", search = @Search(operator = QueryExpression.LIKE))
    )
    private String empNo;

    @ManyToOne
    @EruptField(
            views = @View(title = "所属部门", column = "name", width = "130px"),
            edit = @Edit(title = "所属部门", type = EditType.REFERENCE_TREE,
                    referenceTreeType = @ReferenceTreeType(pid = "parentOrg.id"))
    )
    private EruptOrg org;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "r_onboarding_material",
            joinColumns = @JoinColumn(name = "onboarding_id"),
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @Column(name = "material", length = AnnotationConst.CODE_LENGTH)
    @EruptField(
            views = @View(title = "已核对材料", show = false),
            edit = @Edit(title = "入职材料核对", type = EditType.MULTI_CHOICE,
                    desc = "勾选已收齐的材料，未勾选项将在确认入职时提示",
                    multiChoiceType = @MultiChoiceType(vl = {
                            @VL(value = "身份证", label = "身份证"),
                            @VL(value = "学历学位证书", label = "学历学位证书"),
                            @VL(value = "离职证明", label = "离职证明"),
                            @VL(value = "体检报告", label = "体检报告"),
                            @VL(value = "银行卡", label = "银行卡"),
                            @VL(value = "一寸照片", label = "一寸照片"),
                            @VL(value = "劳动合同", label = "劳动合同"),
                            @VL(value = "背景调查授权书", label = "背景调查授权书")
                    }))
    )
    private Set<String> materials = new LinkedHashSet<>();

    @Column(length = AnnotationConst.REMARK_LENGTH)
    @EruptField(
            views = @View(title = "备注", show = false),
            edit = @Edit(title = "备注", type = EditType.TEXTAREA)
    )
    private String remark;

}
