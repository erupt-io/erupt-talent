package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * Interview outcome. PENDING means the round exists as a task but has not been assessed yet.
 * PROPOSED is only valid on the final round and triggers the offer approval flow.
 *
 * @author YuePeng
 */
@Getter
public enum InterviewConclusion implements LabeledEnum {

    PENDING("待面试", "#faad14"),
    PASS("通过", "#52c41a"),
    PROPOSED("拟录用", "#722ed1"),
    ELIMINATE("淘汰", "#ff4d4f");

    private final String label;

    private final String color;

    InterviewConclusion(String label, String color) {
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
