package xyz.erupt.talent.service;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of the key resume fields out of a mail's subject, body and attachment text.
 * <p>
 * Everything is a heuristic, so the raw text is always kept on the resume record for a human to
 * cross-check — the parser never blocks ingestion just because a field could not be resolved.
 *
 * @author YuePeng
 */
@Service
public class ResumeParser {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,10}");

    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** "姓名：张三" / "姓名 张三" — the label forms most Chinese job boards emit. */
    private static final Pattern LABELED_NAME = Pattern.compile("姓\\s*名[:：\\s]{1,4}([\\u4e00-\\u9fa5]{2,8}|[A-Za-z]{2,20}(?:\\s[A-Za-z]{2,20})?)");

    private static final Pattern LABELED_SCHOOL = Pattern.compile("(?:毕业院校|学校|院校)[:：\\s]{1,4}([\\u4e00-\\u9fa5A-Za-z]{2,30})");

    private static final Pattern LABELED_MAJOR = Pattern.compile("(?:专\\s*业)[:：\\s]{1,4}([\\u4e00-\\u9fa5A-Za-z]{2,30})");

    private static final Pattern WORK_YEARS = Pattern.compile("(\\d{1,2})\\s*年(?:以上)?(?:工作)?经验");

    private static final Pattern LABELED_GENDER = Pattern.compile("性\\s*别[:：\\s]{1,4}([男女])");

    /** Ordered longest-first so "研究生" never shadows a more specific match. */
    private static final String[][] EDUCATION = {
            {"博士", "博士"}, {"硕士", "硕士"}, {"研究生", "硕士"},
            {"本科", "本科"}, {"学士", "本科"}, {"大专", "大专"}, {"专科", "大专"}
    };

    /**
     * @param subject   mail subject
     * @param body      mail body text
     * @param fileNames attachment file names
     * @param fullText  body plus every attachment's extracted text
     * @param jobPosts  known job post names, used to guess which position was applied for
     */
    public Parsed parse(String subject, String body, List<String> fileNames, String fullText, List<String> jobPosts) {
        Parsed parsed = new Parsed();
        String haystack = StringUtils.defaultString(subject) + "\n"
                + String.join("\n", fileNames) + "\n"
                + StringUtils.defaultString(fullText);

        parsed.setEmail(first(EMAIL, haystack, 0));
        parsed.setPhone(first(MOBILE, haystack, 0));
        parsed.setGender(first(LABELED_GENDER, haystack, 1));
        parsed.setSchool(first(LABELED_SCHOOL, haystack, 1));
        parsed.setMajor(first(LABELED_MAJOR, haystack, 1));
        parsed.setName(this.guessName(subject, body, fileNames, haystack));
        parsed.setEducation(this.guessEducation(haystack));
        parsed.setJobPost(this.guessJobPost(haystack, jobPosts));

        String years = first(WORK_YEARS, haystack, 1);
        if (null != years) {
            parsed.setWorkYears(Integer.parseInt(years));
        }
        return parsed;
    }

    /**
     * Name resolution order: an explicit "姓名：" label, then the attachment file name
     * (job boards name files after the candidate), then the subject.
     */
    private String guessName(String subject, String body, List<String> fileNames, String haystack) {
        String labeled = first(LABELED_NAME, haystack, 1);
        if (null != labeled) {
            return labeled;
        }
        for (String fileName : fileNames) {
            String candidate = chineseName(StringUtils.substringBeforeLast(fileName, "."));
            if (null != candidate) {
                return candidate;
            }
        }
        String fromSubject = chineseName(StringUtils.defaultString(subject));
        return null != fromSubject ? fromSubject : chineseName(StringUtils.defaultString(body));
    }

    /**
     * Picks a plausible 2-4 character Chinese personal name, skipping common resume vocabulary.
     */
    private String chineseName(String text) {
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}").matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!NON_NAME_WORDS.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static final List<String> NON_NAME_WORDS = List.of(
            "简历", "应聘", "求职", "投递", "个人简历", "中文简历", "英文简历", "招聘",
            "岗位", "职位", "面试", "附件", "您好", "你好", "老师", "贵公司", "工程师",
            "经理", "主管", "专员", "助理", "总监", "实习", "校招", "社招", "转正");

    private String guessEducation(String text) {
        for (String[] pair : EDUCATION) {
            if (text.contains(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    /**
     * Longest matching job post name wins, so "高级Java工程师" beats "Java工程师".
     */
    private String guessJobPost(String text, List<String> jobPosts) {
        return jobPosts.stream()
                .filter(StringUtils::isNotBlank)
                .filter(text::contains)
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
    }

    private static String first(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    /**
     * Parsed field carrier; every field may be null when the heuristic found nothing.
     */
    @Getter
    @Setter
    public static class Parsed {

        private String name;

        private String gender;

        private String phone;

        private String email;

        private String education;

        private String school;

        private String major;

        private Integer workYears;

        private String jobPost;

    }

}
