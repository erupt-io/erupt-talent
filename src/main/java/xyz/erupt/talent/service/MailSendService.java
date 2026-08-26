package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import xyz.erupt.talent.model.TalentSetting;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Outbound mail, driven entirely by the visually configured SMTP settings.
 * A sender is built per send so that changing the configuration in the UI takes effect immediately.
 *
 * @author YuePeng
 */
@Service
public class MailSendService {

    private static final String TIMEOUT_MS = "15000";

    @Resource
    private TalentSettingService settingService;

    /**
     * @throws IllegalStateException when mail sending is switched off or incompletely configured
     */
    public void send(String to, String subject, String html) throws Exception {
        TalentSetting setting = settingService.get();
        if (!Boolean.TRUE.equals(setting.getMailSendEnabled())) {
            throw new IllegalStateException("邮件发送功能未启用，请在「招聘系统配置」中开启");
        }
        this.send(setting, to, subject, html);
    }

    /**
     * Sends with an explicit configuration — used by the "test mail" button, which must be able to
     * verify credentials the administrator has typed but not yet saved.
     */
    public void send(TalentSetting setting, String to, String subject, String html) throws Exception {
        JavaMailSenderImpl sender = buildSender(setting);
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        String fromName = StringUtils.defaultIfBlank(setting.getSmtpFromName(), setting.getSmtpAccount());
        helper.setFrom(new InternetAddress(setting.getSmtpAccount(), fromName, StandardCharsets.UTF_8.name()).toString());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        sender.send(message);
    }

    /**
     * Verifies the SMTP credentials without delivering anything.
     */
    public void testConnection(TalentSetting setting) throws Exception {
        buildSender(setting).testConnection();
    }

    private JavaMailSenderImpl buildSender(TalentSetting setting) {
        if (StringUtils.isAnyBlank(setting.getSmtpHost(), setting.getSmtpAccount(), setting.getSmtpPassword())
                || null == setting.getSmtpPort()) {
            throw new IllegalStateException("SMTP 配置不完整：服务器、端口、账号、密码均为必填");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(setting.getSmtpHost());
        sender.setPort(setting.getSmtpPort());
        sender.setUsername(setting.getSmtpAccount());
        sender.setPassword(setting.getSmtpPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (Boolean.TRUE.equals(setting.getSmtpSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return sender;
    }

}
