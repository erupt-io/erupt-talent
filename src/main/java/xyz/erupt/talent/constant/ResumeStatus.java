package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * Resume lifecycle status. Advances in one direction only; see {@code ResumeFlowService}.
 *
 * @author YuePeng
 */
@Getter
public enum ResumeStatus implements LabeledEnum {

    PENDING_SCREEN("待初筛", "#faad14"),
    SCREEN_PASS("初筛通过", "#1890ff"),
    INTERVIEW_R1("一面中", "#1890ff"),
    INTERVIEW_R2("二面中", "#1890ff"),
    INTERVIEW_FINAL("终面中", "#1890ff"),
    PROPOSED("拟录用", "#722ed1"),
    OFFER_APPROVING("Offer审批中", "#722ed1"),
    OFFER_SENT("Offer已发送", "#13c2c2"),
    OFFER_ACCEPTED("已接受Offer", "#52c41a"),
    OFFER_REJECTED("已拒绝Offer", "#ff4d4f"),
    PENDING_ONBOARD("待入职", "#52c41a"),
    ONBOARDED("已入职", "#237804"),
    ABANDONED("放弃入职", "#ff4d4f"),
    ELIMINATED("已淘汰", "#8c8c8c");

    private final String label;

    private final String color;

    ResumeStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    /**
     * Terminal states can no longer be advanced by the automation rules.
     */
    public boolean terminal() {
        return this == ONBOARDED || this == ELIMINATED || this == ABANDONED || this == OFFER_REJECTED;
    }

    public static class H implements ChoiceFetchHandler<Void> {
        @Override
        public List<VLModel> fetch(String[] params) {
            return LabeledEnum.vls(values());
        }
    }

}
