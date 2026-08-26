package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * @author YuePeng
 */
@Getter
public enum JobPostStatus implements LabeledEnum {

    OPEN("开放", "#52c41a"),
    PAUSED("暂停", "#faad14"),
    FILLED("招满", "#8c8c8c");

    private final String label;

    private final String color;

    JobPostStatus(String label, String color) {
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
