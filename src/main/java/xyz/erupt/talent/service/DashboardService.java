package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.InterviewRound;
import xyz.erupt.talent.constant.ResumeSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talent funnel and conversion metrics for the dashboard.
 * <p>
 * Everything is computed with named-parameter SQL — the filter values never reach the statement
 * text. The date window and the job-post / channel filters all narrow the same resume population,
 * so every figure on the page describes one consistent cohort.
 *
 * @author YuePeng
 */
@Service
public class DashboardService {

    @Resource
    private EruptDao eruptDao;

    /**
     * @param from      inclusive lower bound on resume creation, nullable
     * @param to        inclusive upper bound on resume creation, nullable
     * @param jobPostId restrict to one job post, nullable
     * @param source    restrict to one channel, nullable
     */
    public Dashboard build(LocalDate from, LocalDate to, Long jobPostId, String source) {
        NamedParameterJdbcTemplate jdbc = eruptDao.getNamedParameterJdbcTemplate();
        String where = this.where();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", null == from ? null : from.atStartOfDay())
                .addValue("to", null == to ? null : to.plusDays(1).atStartOfDay())
                .addValue("jobPostId", jobPostId)
                .addValue("source", StringUtils.trimToNull(source));

        Dashboard dashboard = new Dashboard();

        Map<String, Object> resume = jdbc.queryForMap("""
                select count(*) as total,
                       sum(case when r.source = 'MAIL' then 1 else 0 end) as mail_total,
                       sum(case when r.screen_result is not null then 1 else 0 end) as screen_done,
                       sum(case when r.screen_result = 'PASS' then 1 else 0 end) as screen_pass
                from r_resume r
                """ + where, params);
        dashboard.setTotalResumes(num(resume.get("total")));
        dashboard.setMailResumes(num(resume.get("mail_total")));
        dashboard.setScreenAssessed(num(resume.get("screen_done")));
        dashboard.setScreenPassed(num(resume.get("screen_pass")));

        Map<String, Object> offer = jdbc.queryForMap("""
                select sum(case when o.status in ('SENT','ACCEPTED','DECLINED','EXPIRED') then 1 else 0 end) as sent,
                       sum(case when o.status = 'ACCEPTED' then 1 else 0 end) as accepted,
                       sum(case when o.status = 'DECLINED' then 1 else 0 end) as declined,
                       sum(case when o.status = 'EXPIRED' then 1 else 0 end) as expired
                from r_offer o join r_resume r on o.resume_id = r.id
                """ + where, params);
        dashboard.setOffersSent(num(offer.get("sent")));
        dashboard.setOffersAccepted(num(offer.get("accepted")));
        dashboard.setOffersDeclined(num(offer.get("declined")));
        dashboard.setOffersExpired(num(offer.get("expired")));

        dashboard.setOnboarded(num(jdbc.queryForMap("""
                select sum(case when b.status = 'ONBOARDED' then 1 else 0 end) as onboarded
                from r_onboarding b
                       join r_offer o on b.offer_id = o.id
                       join r_resume r on o.resume_id = r.id
                """ + where, params).get("onboarded")));

        List<Map<String, Object>> rounds = jdbc.queryForList("""
                select i.round as round,
                       sum(case when i.conclusion <> 'PENDING' then 1 else 0 end) as assessed,
                       sum(case when i.conclusion in ('PASS','PROPOSED') then 1 else 0 end) as passed
                from r_interview i join r_resume r on i.resume_id = r.id
                """ + where + " group by i.round", params);
        Map<String, long[]> byRound = new LinkedHashMap<>();
        rounds.forEach(row -> byRound.put(String.valueOf(row.get("round")),
                new long[]{num(row.get("assessed")), num(row.get("passed"))}));
        for (InterviewRound round : InterviewRound.values()) {
            long[] counts = byRound.getOrDefault(round.name(), new long[]{0, 0});
            dashboard.getRounds().add(new RoundStat(round.label(), counts[0], counts[1],
                    rate(counts[1], counts[0])));
        }

        // `value` and `label` are reserved words in several dialects (H2 among them),
        // hence the dim_ prefixes.
        dashboard.setChannels(this.distribution(jdbc, """
                select r.source as dim_label, count(*) as dim_count
                from r_resume r
                """ + where + " group by r.source order by dim_count desc", params, true));
        dashboard.setJobPosts(this.distribution(jdbc, """
                select coalesce(p.name, '未指定岗位') as dim_label, count(*) as dim_count
                from r_resume r left join r_job_post p on r.job_post_id = p.id
                """ + where + " group by p.name order by dim_count desc", params, false));

        dashboard.setScreenPassRate(rate(dashboard.getScreenPassed(), dashboard.getScreenAssessed()));
        dashboard.setMailRatio(rate(dashboard.getMailResumes(), dashboard.getTotalResumes()));
        dashboard.setOfferAcceptRate(rate(dashboard.getOffersAccepted(), dashboard.getOffersSent()));
        dashboard.setArrivalRate(rate(dashboard.getOnboarded(), dashboard.getOffersAccepted()));

        long finalProposed = dashboard.getRounds().isEmpty() ? 0
                : dashboard.getRounds().get(dashboard.getRounds().size() - 1).passed();
        dashboard.getFunnel().add(new FunnelStage("简历总量", dashboard.getTotalResumes()));
        dashboard.getFunnel().add(new FunnelStage("初筛通过", dashboard.getScreenPassed()));
        for (RoundStat round : dashboard.getRounds()) {
            dashboard.getFunnel().add(new FunnelStage(round.label() + "通过", round.passed()));
        }
        dashboard.getFunnel().add(new FunnelStage("Offer 发放", dashboard.getOffersSent()));
        dashboard.getFunnel().add(new FunnelStage("Offer 接受", dashboard.getOffersAccepted()));
        dashboard.getFunnel().add(new FunnelStage("正式入职", dashboard.getOnboarded()));
        dashboard.setFinalProposed(finalProposed);
        return dashboard;
    }

    /**
     * Shared resume-population filter. Every clause is null-guarded in SQL so one statement text
     * serves every filter combination and stays fully parameterised.
     */
    private String where() {
        return """
                where (:from is null or r.create_time >= :from)
                  and (:to is null or r.create_time < :to)
                  and (:jobPostId is null or r.job_post_id = :jobPostId)
                  and (:source is null or r.source = :source)
                """;
    }

    private List<Slice> distribution(NamedParameterJdbcTemplate jdbc, String sql,
                                     MapSqlParameterSource params, boolean translateSource) {
        List<Slice> slices = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, params)) {
            String label = String.valueOf(row.get("dim_label"));
            if (translateSource) {
                label = sourceLabel(label);
            }
            slices.add(new Slice(label, num(row.get("dim_count"))));
        }
        return slices;
    }

    private static String sourceLabel(String name) {
        try {
            return ResumeSource.valueOf(name).label();
        } catch (Exception e) {
            return name;
        }
    }

    private static long num(Object value) {
        return null == value ? 0L : ((Number) value).longValue();
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0d : Math.round(numerator * 10000d / denominator) / 100d;
    }

    /* ---------------- carriers ---------------- */

    public record FunnelStage(String label, long value) {
    }

    public record RoundStat(String label, long assessed, long passed, double passRate) {
    }

    public record Slice(String label, long value) {
    }

    @Getter
    @Setter
    public static class Dashboard {

        private long totalResumes;

        private long mailResumes;

        private double mailRatio;

        private long screenAssessed;

        private long screenPassed;

        private double screenPassRate;

        private long finalProposed;

        private long offersSent;

        private long offersAccepted;

        private long offersDeclined;

        private long offersExpired;

        private double offerAcceptRate;

        private long onboarded;

        private double arrivalRate;

        private final List<RoundStat> rounds = new ArrayList<>();

        private final List<FunnelStage> funnel = new ArrayList<>();

        private List<Slice> channels = new ArrayList<>();

        private List<Slice> jobPosts = new ArrayList<>();

    }

}
