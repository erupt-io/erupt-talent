package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * Which business object a trace record belongs to.
 *
 * @author YuePeng
 */
@Getter
public enum TraceBizType implements LabeledEnum {

    RESUME("简历", "#1890ff"),
    INTERVIEW("面试", "#722ed1"),
    OFFER("Offer", "#13c2c2"),
    ONBOARDING("入职", "#52c41a");

    private final String label;

    private final String color;

    TraceBizType(String label, String color) {
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
