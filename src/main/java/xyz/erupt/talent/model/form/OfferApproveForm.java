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
 * Shared by both approval steps of the offer flow.
 *
 * @author YuePeng
 */
@Erupt(name = "Offer审批")
@Getter
@Setter
public class OfferApproveForm extends BaseModel {

    @EruptField(
            edit = @Edit(title = "审批结果", notNull = true, type = EditType.BOOLEAN,
                    boolType = @BoolType(trueText = "通过", falseText = "驳回"))
    )
    private Boolean approved = true;

    @EruptField(
            edit = @Edit(title = "审批意见", type = EditType.TEXTAREA,
                    desc = "驳回时必须填写意见")
    )
    private String opinion;

}
