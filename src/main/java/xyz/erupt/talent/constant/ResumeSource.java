package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * Where the resume came from. MAIL is written automatically by the mailbox fetch job.
 *
 * @author YuePeng
 */
@Getter
public enum ResumeSource implements LabeledEnum {

    MAIL("邮箱自动抓取", "#13c2c2"),
    MANUAL("手动录入", "#1890ff"),
    IMPORT("批量导入", "#722ed1"),
    REFERRAL("内部推荐", "#52c41a"),
    WEBSITE("官网投递", "#faad14"),
    HEADHUNTER("猎头推荐", "#eb2f96");

    private final String label;

    private final String color;

    ResumeSource(String label, String color) {
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
