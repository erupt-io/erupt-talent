package xyz.erupt.talent.model.form;

import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.Readonly;
import xyz.erupt.annotation.sub_field.sub_edit.BoolType;
import xyz.erupt.annotation.sub_field.sub_edit.DateType;
import xyz.erupt.annotation.sub_field.sub_edit.InputType;
import xyz.erupt.jpa.model.BaseModel;

import java.time.LocalDate;

/**
 * The anti-misclick dialog required by the spec: opening it shows the candidate, the position,
 * the onboarding date and the outstanding material checklist, all read-only, and the operator has
 * to tick an explicit confirmation before the loop is closed.
 * Pre-filled by {@code OnboardConfirmHandler.eruptFormValue}.
 *
 * @author YuePeng
 */
@Erupt(name = "确认入职")
@Getter
@Setter
public class OnboardConfirmForm extends BaseModel {

    @EruptField(
            edit = @Edit(title = "候选人姓名", readonly = @Readonly,
                    inputType = @InputType(fullSpan = true))
    )
    private String candidateName;

    @EruptField(
            edit = @Edit(title = "应聘岗位", readonly = @Readonly,
                    inputType = @InputType(fullSpan = true))
    )
    private String jobPostName;

    @EruptField(
            edit = @Edit(title = "计划入职日期", readonly = @Readonly)
    )
    private LocalDate plannedDate;

    @EruptField(
            edit = @Edit(title = "实际入职日期", notNull = true,
                    desc = "默认取计划入职日期，如有出入请修正",
                    dateType = @DateType(type = DateType.Type.DATE))
    )
    private LocalDate actualDate;

    @EruptField(
            edit = @Edit(title = "待核对材料清单", type = EditType.TEXTAREA, readonly = @Readonly,
                    desc = "未勾选的材料将在此列出，请与候选人当面核对")
    )
    private String pendingMaterials;

    @EruptField(
            edit = @Edit(title = "员工工号", notNull = true,
                    desc = "工号需全局唯一")
    )
    private String empNo;

    @EruptField(
            edit = @Edit(title = "确认信息无误", notNull = true, type = EditType.BOOLEAN,
                    desc = "请核对以上信息后勾选，勾选后方可完成入职闭环",
                    boolType = @BoolType(trueText = "已核对无误", falseText = "未核对"))
    )
    private Boolean confirmed = false;

}
