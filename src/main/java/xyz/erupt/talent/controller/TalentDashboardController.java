package xyz.erupt.talent.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.erupt.core.annotation.EruptRouter;
import xyz.erupt.core.constant.EruptRestPath;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.model.JobPost;
import xyz.erupt.talent.service.DashboardService;
import xyz.erupt.talent.service.TalentAuth;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backing API for the talent dashboard page: funnel metrics and their CSV export.
 * Restricted to HR and administrators — ordinary employees have no talent data rights.
 *
 * @author YuePeng
 */
@RestController
@RequestMapping(EruptRestPath.ERUPT_API + "/talent")
public class TalentDashboardController {

    @Resource
    private DashboardService dashboardService;

    @Resource
    private TalentAuth talentAuth;

    @Resource
    private EruptDao eruptDao;

    @GetMapping("/dashboard")
    @EruptRouter(verifyType = EruptRouter.VerifyType.LOGIN)
    public Map<String, Object> dashboard(@RequestParam(value = "from", required = false) String from,
                                         @RequestParam(value = "to", required = false) String to,
                                         @RequestParam(value = "jobPostId", required = false) Long jobPostId,
                                         @RequestParam(value = "source", required = false) String source) {
        this.assertHr();
        DashboardService.Dashboard data = dashboardService.build(date(from), date(to), jobPostId, source);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", data);
        result.put("jobPosts", eruptDao.lambdaQuery(JobPost.class)
                .orderBy(JobPost::getName)
                .listSelect(JobPostOption.class, JobPost::getId, JobPost::getName));
        return result;
    }

    /**
     * Funnel + channel + position breakdown as CSV, UTF-8 with a BOM so Excel opens it correctly.
     */
    @GetMapping("/dashboard/export")
    @EruptRouter(verifyType = EruptRouter.VerifyType.LOGIN, verifyMethod = EruptRouter.VerifyMethod.PARAM)
    public void export(@RequestParam(value = "from", required = false) String from,
                       @RequestParam(value = "to", required = false) String to,
                       @RequestParam(value = "jobPostId", required = false) Long jobPostId,
                       @RequestParam(value = "source", required = false) String source,
                       HttpServletResponse response) throws Exception {
        this.assertHr();
        DashboardService.Dashboard data = dashboardService.build(date(from), date(to), jobPostId, source);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=talent-funnel.csv");
        PrintWriter writer = response.getWriter();
        writer.write('﻿');
        writer.println("分组,指标,数值");
        data.getFunnel().forEach(stage -> writer.println(csv("招聘漏斗", stage.label(), stage.value())));
        writer.println(csv("核心指标", "简历总量", data.getTotalResumes()));
        writer.println(csv("核心指标", "邮箱抓取简历量", data.getMailResumes()));
        writer.println(csv("核心指标", "邮箱简历占比(%)", data.getMailRatio()));
        writer.println(csv("核心指标", "初筛通过率(%)", data.getScreenPassRate()));
        writer.println(csv("核心指标", "Offer 发放数", data.getOffersSent()));
        writer.println(csv("核心指标", "Offer 接受数", data.getOffersAccepted()));
        writer.println(csv("核心指标", "Offer 拒绝数", data.getOffersDeclined()));
        writer.println(csv("核心指标", "Offer 过期数", data.getOffersExpired()));
        writer.println(csv("核心指标", "Offer 接受率(%)", data.getOfferAcceptRate()));
        writer.println(csv("核心指标", "入职人数", data.getOnboarded()));
        writer.println(csv("核心指标", "到岗率(%)", data.getArrivalRate()));
        data.getRounds().forEach(round -> {
            writer.println(csv("面试轮次", round.label() + "已评估", round.assessed()));
            writer.println(csv("面试轮次", round.label() + "通过", round.passed()));
            writer.println(csv("面试轮次", round.label() + "通过率(%)", round.passRate()));
        });
        data.getChannels().forEach(slice -> writer.println(csv("渠道分布", slice.label(), slice.value())));
        data.getJobPosts().forEach(slice -> writer.println(csv("岗位分布", slice.label(), slice.value())));
        writer.flush();
    }

    private void assertHr() {
        if (!talentAuth.isHr()) {
            throw new EruptWebApiRuntimeException("仅 HR 与管理员可查看招聘数据看板");
        }
    }

    private static LocalDate date(String raw) {
        return StringUtils.isBlank(raw) ? null : LocalDate.parse(raw.trim());
    }

    /**
     * Minimal RFC 4180 quoting — labels can contain commas or quotes.
     */
    private static String csv(String group, String label, Object value) {
        return String.join(",", List.of(quote(group), quote(label), String.valueOf(value)));
    }

    private static String quote(String value) {
        return "\"" + StringUtils.defaultString(value).replace("\"", "\"\"") + "\"";
    }

    /**
     * Projection for the job-post filter dropdown.
     */
    public static class JobPostOption {

        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

}
