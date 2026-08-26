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
@Erupt(name = "初筛淘汰")
@Getter
@Setter
public class ScreenRejectForm extends BaseModel {

    @EruptField(
            edit = @Edit(title = "淘汰原因", notNull = true, type = EditType.TEXTAREA,
                    desc = "淘汰原因将写入简历并留存于流转日志")
    )
    private String eliminateReason;

}
