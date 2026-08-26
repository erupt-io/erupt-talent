package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * How the interview is conducted; drives which location field the DingTalk event carries.
 *
 * @author YuePeng
 */
@Getter
public enum InterviewForm implements LabeledEnum {

    ONSITE("线下面试", "#1890ff"),
    ONLINE("线上面试", "#13c2c2"),
    PHONE("电话面试", "#faad14");

    private final String label;

    private final String color;

    InterviewForm(String label, String color) {
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
