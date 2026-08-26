package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * @author YuePeng
 */
@Getter
public enum OfferType implements LabeledEnum {

    FORMAL("正式员工", "#52c41a"),
    INTERN("实习生", "#1890ff"),
    PART_TIME("兼职", "#faad14"),
    OUTSOURCE("外包", "#8c8c8c");

    private final String label;

    private final String color;

    OfferType(String label, String color) {
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
