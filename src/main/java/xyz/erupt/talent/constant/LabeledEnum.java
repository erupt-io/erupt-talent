package xyz.erupt.talent.constant;

import xyz.erupt.annotation.fun.VLModel;

import java.util.Arrays;
import java.util.List;

/**
 * Shared contract for every business enum in this module so that a single helper can
 * turn any of them into the {@code @Edit(type = CHOICE)} option list.
 *
 * @author YuePeng
 */
public interface LabeledEnum {

    String name();

    String getLabel();

    String getColor();

    /**
     * Terse aliases for the Lombok-generated accessors, so call sites read {@code status.label()}.
     */
    default String label() {
        return this.getLabel();
    }

    default String color() {
        return this.getColor();
    }

    static List<VLModel> vls(LabeledEnum[] values) {
        return Arrays.stream(values)
                .map(it -> new VLModel(it.name(), it.label(), null, it.color(), false))
                .toList();
    }

}
