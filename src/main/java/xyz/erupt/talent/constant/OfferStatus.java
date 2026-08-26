package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * Offer approval and delivery status.
 * Flow: PENDING_DEPT -> PENDING_HR -> SENT -> ACCEPTED / DECLINED, with EXPIRED as a timeout branch.
 * PENDING_SEND is entered when the approval passed but the SMTP delivery failed, so it can be retried.
 *
 * @author YuePeng
 */
@Getter
public enum OfferStatus implements LabeledEnum {

    PENDING_DEPT("待部门负责人审批", "#faad14"),
    PENDING_HR("待HR复核", "#faad14"),
    APPROVAL_REJECTED("审批驳回", "#ff4d4f"),
    PENDING_SEND("待发送", "#fa8c16"),
    SENT("已发送", "#13c2c2"),
    ACCEPTED("候选人接受", "#52c41a"),
    DECLINED("候选人拒绝", "#ff4d4f"),
    EXPIRED("已过期", "#8c8c8c");

    private final String label;

    private final String color;

    OfferStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public static class H implements ChoiceFetchHandler<Void> {
        @Override
        public List<VLModel> fetch(String[] params) {
            return LabeledEnum.vls(values());
        }
    }

}
