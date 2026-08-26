package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * @author YuePeng
 */
@Getter
public enum OnboardStatus implements LabeledEnum {

    PENDING("待入职", "#faad14"),
    ONBOARDED("已正式入职", "#52c41a"),
    ABANDONED("放弃入职", "#ff4d4f");

    private final String label;

    private final String color;

    OnboardStatus(String label, String color) {
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
