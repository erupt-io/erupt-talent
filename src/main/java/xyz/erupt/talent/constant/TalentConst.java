package xyz.erupt.talent.constant;

/**
 * Module-wide identifiers: notice scene codes, seeded role codes and numbering prefixes.
 *
 * @author YuePeng
 */
public interface TalentConst {

    String MODULE = "erupt-hr";

    /* ---------- Seeded role codes (see EruptTalentAutoConfiguration) ---------- */

    String ROLE_HR = "TALENT_HR";

    String ROLE_INTERVIEWER = "TALENT_INTERVIEWER";

    /* ---------- Notice scene codes ---------- */

    String SCENE_SCREEN_PASS = "talent.screen_pass";

    String SCENE_INTERVIEW_ARRANGED = "talent.interview_arranged";

    String SCENE_INTERVIEW_RESCHEDULED = "talent.interview_rescheduled";

    String SCENE_INTERVIEW_RESULT = "talent.interview_result";

    String SCENE_OFFER_APPROVAL = "talent.offer_approval";

    String SCENE_OFFER_SENT = "talent.offer_sent";

    String SCENE_OFFER_ACCEPTED = "talent.offer_accepted";

    String SCENE_ONBOARD = "talent.onboard";

    /* ---------- Numbering prefixes (JL + yyyyMMdd + 4 digits / OFFER + yyyyMMdd + 4 digits) ---------- */

    String SEQ_RESUME = "JL";

    String SEQ_OFFER = "OFFER";

}
