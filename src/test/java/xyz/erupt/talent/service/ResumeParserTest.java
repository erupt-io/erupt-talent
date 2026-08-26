package xyz.erupt.talent.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mailbox ingestion path relies entirely on these heuristics, and it is the one part of the
 * pipeline that cannot be exercised through the admin API, so it is covered here directly.
 *
 * @author YuePeng
 */
class ResumeParserTest {

    private final ResumeParser parser = new ResumeParser();

    private static final List<String> POSTS = List.of("采购专员", "高级采购经理", "Java工程师");

    @Test
    void parsesLabelledResumeBody() {
        String body = """
                姓名：李明
                性别：男
                手机：13812345678
                邮箱：liming@example.com
                毕业院校：华南理工大学
                专业：工业工程
                最高学历：硕士研究生
                拥有 6 年以上工作经验，应聘岗位：高级采购经理
                """;
        ResumeParser.Parsed p = parser.parse("应聘高级采购经理", body, List.of(), body, POSTS);

        assertEquals("李明", p.getName());
        assertEquals("男", p.getGender());
        assertEquals("13812345678", p.getPhone());
        assertEquals("liming@example.com", p.getEmail());
        assertEquals("华南理工大学", p.getSchool());
        assertEquals("工业工程", p.getMajor());
        assertEquals("硕士", p.getEducation());
        assertEquals(6, p.getWorkYears());
        // Longest match wins, so the specific post beats the substring "采购专员" would not match here
        assertEquals("高级采购经理", p.getJobPost());
    }

    @Test
    void fallsBackToAttachmentFileNameForTheName() {
        List<String> files = List.of("王小美-个人简历.pdf");
        ResumeParser.Parsed p = parser.parse("简历投递", "您好，请查收附件。", files,
                "您好，请查收附件。 王小美-个人简历.pdf", POSTS);

        assertEquals("王小美", p.getName(), "附件名中的候选人姓名应被识别，而不是「简历」等通用词");
    }

    @Test
    void skipsResumeVocabularyWhenGuessingTheName() {
        ResumeParser.Parsed p = parser.parse("求职简历-应聘采购专员", "", List.of(),
                "求职简历-应聘采购专员", POSTS);

        assertNotEquals("简历", p.getName());
        assertNotEquals("应聘", p.getName());
        assertEquals("采购专员", p.getJobPost());
    }

    @Test
    void prefersTheLongestMatchingJobPost() {
        ResumeParser.Parsed p = parser.parse("应聘 高级采购经理", "", List.of(),
                "应聘 高级采购经理", POSTS);
        assertEquals("高级采购经理", p.getJobPost());
    }

    @Test
    void toleratesAnEmptyMailWithoutThrowing() {
        ResumeParser.Parsed p = parser.parse(null, "", List.of(), "", POSTS);

        assertNull(p.getName());
        assertNull(p.getPhone());
        assertNull(p.getEmail());
        assertNull(p.getWorkYears());
        assertNull(p.getJobPost());
    }

    @Test
    void doesNotMistakeALongDigitRunForAMobileNumber() {
        String text = "订单号 138123456789012，联系电话 13800001111";
        ResumeParser.Parsed p = parser.parse("简历", text, List.of(), text, POSTS);

        assertEquals("13800001111", p.getPhone());
    }

}
