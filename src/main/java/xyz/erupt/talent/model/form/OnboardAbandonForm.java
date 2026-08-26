package xyz.erupt.talent.model.form;

import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.jpa.model.BaseModel;

/**
 * @author YuePeng
 */
@Erupt(name = "放弃入职")
@Getter
@Setter
public class OnboardAbandonForm extends BaseModel {

    @EruptField(
            edit = @Edit(title = "放弃原因", notNull = true, type = EditType.TEXTAREA)
    )
    private String reason;

}
