package xyz.erupt.talent.model.form;

import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.sub_edit.ChoiceType;
import xyz.erupt.jpa.model.BaseModel;
import xyz.erupt.talent.constant.ResumeStatus;

/**
 * Administrator-only escape hatch for correcting a resume that ended up in a wrong state.
 *
 * @author YuePeng
 */
@Erupt(name = "状态回溯修正")
@Getter
@Setter
public class StatusFixForm extends BaseModel {

    @EruptField(
            edit = @Edit(title = "修正为", notNull = true, type = EditType.CHOICE,
                    choiceType = @ChoiceType(fetchHandler = ResumeStatus.H.class))
    )
    private String targetStatus;

    @EruptField(
            edit = @Edit(title = "修正原因", notNull = true, type = EditType.TEXTAREA,
                    desc = "修正记录将永久留存于流转日志")
    )
    private String reason;

}
