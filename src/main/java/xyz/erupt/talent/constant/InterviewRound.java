package xyz.erupt.talent.constant;

import lombok.Getter;
import xyz.erupt.annotation.fun.ChoiceFetchHandler;
import xyz.erupt.annotation.fun.VLModel;

import java.util.List;

/**
 * Interview round. Each round that passes automatically spawns the next one.
 *
 * @author YuePeng
 */
@Getter
public enum InterviewRound implements LabeledEnum {

    ROUND_1("一面", "#1890ff", ResumeStatus.INTERVIEW_R1),
    ROUND_2("二面", "#722ed1", ResumeStatus.INTERVIEW_R2),
    FINAL("终面", "#eb2f96", ResumeStatus.INTERVIEW_FINAL);

    private final String label;

    private final String color;

    /**
     * Resume status to apply while this round is in progress.
     */
    private final ResumeStatus resumeStatus;

    InterviewRound(String label, String color, ResumeStatus resumeStatus) {
        this.label = label;
        this.color = color;
        this.resumeStatus = resumeStatus;
    }

    /**
     * @return the round to create after this one passes, or null when this is the last round
     */
    public InterviewRound next() {
        return switch (this) {
            case ROUND_1 -> ROUND_2;
            case ROUND_2 -> FINAL;
            case FINAL -> null;
        };
    }

    public static class H implements ChoiceFetchHandler<Void> {
        @Override
        public List<VLModel> fetch(String[] params) {
            return LabeledEnum.vls(values());
        }
    }

}
