package xyz.erupt.talent.job;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.job.handler.EruptJobHandler;
import xyz.erupt.talent.service.MailFetchService;

/**
 * Rule 1: periodically pulls submitted resumes out of the talent mailbox, parses them and
 * files them as resume records. Registered as an {@code EruptJob} row on first boot.
 *
 * @author YuePeng
 */
@Service
public class MailFetchJob implements EruptJobHandler {

    public static final String CODE = "talent-mail-fetch";

    @Resource
    private MailFetchService mailFetchService;

    @Override
    public String name() {
        return "招聘邮箱简历抓取";
    }

    /**
     * Every 30 minutes; adjustable in 定时任务 without a restart.
     */
    @Override
    public String cron() {
        return "0 0/30 * * * ?";
    }

    @Override
    public String exec(String code, String param) {
        MailFetchService.FetchResult result = mailFetchService.fetch();
        StringBuilder log = new StringBuilder(result.getMessage());
        result.getNotes().forEach(note -> log.append("；").append(note));
        return log.toString();
    }

}
