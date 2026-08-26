package xyz.erupt.talent.model.form;

import lombok.Getter;
import lombok.Setter;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.sub_edit.BoolType;
import xyz.erupt.jpa.model.BaseModel;

/**
 * Records the candidate's answer to a delivered offer. Accepting spawns the onboarding record.
 *
 * @author YuePeng
 */
@Erupt(name = "登记候选人回复")
@Getter
@Setter
public class OfferReplyForm extends BaseModel {

    @EruptField(
            edit = @Edit(title = "候选人答复", notNull = true, type = EditType.BOOLEAN,
                    boolType = @BoolType(trueText = "接受Offer", falseText = "拒绝Offer"))
    )
    private Boolean accepted = true;

    @EruptField(
            edit = @Edit(title = "说明", type = EditType.TEXTAREA,
                    desc = "拒绝时建议记录原因，便于后续复盘")
    )
    private String remark;

}
