package xyz.erupt.talent.service;

import jakarta.annotation.Resource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.prop.EruptProp;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.talent.constant.ResumeSource;
import xyz.erupt.talent.constant.ResumeStatus;
import xyz.erupt.talent.constant.TraceBizType;
import xyz.erupt.talent.model.JobPost;
import xyz.erupt.talent.model.TalentMailbox;
import xyz.erupt.talent.model.Resume;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Pulls externally submitted resumes out of the company talent mailbox over IMAP, parses the
 * key fields and files them as {@link Resume} records with {@code source = MAIL}.
 * <p>
 * De-duplication is anchored on the mail {@code Message-ID}, persisted in {@code Resume.mailKey}
 * under a unique constraint, so re-scanning an overlapping window can never create duplicates.
 *
 * @author YuePeng
 */
@Slf4j
@Service
public class MailFetchService {

    /** Guard rails so one run can never exhaust memory or run unbounded. */
    private static final int MAX_MESSAGES_PER_RUN = 200;

    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    private static final int MAX_TEXT_CHARS = 200_000;

    private static final List<String> TEXT_EXTRACTABLE = List.of("pdf", "docx", "txt", "md");

    /** Extensions we are willing to write to disk. */
    private static final List<String> STORABLE = List.of("pdf", "doc", "docx", "txt", "md", "jpg", "jpeg", "png");

    private static final DateTimeFormatter DAY_DIR = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private ResumeParser resumeParser;

    @Resource
    private EruptDao eruptDao;

    @Resource
    private EruptProp eruptProp;

    @Resource
    private SeqService seqService;

    @Resource
    private TraceService traceService;

    /**
     * Verifies one mailbox's IMAP credentials and reports how many messages sit in the folder.
     */
    public int testConnection(TalentMailbox mailbox) throws Exception {
        requireConfig(mailbox);
        Store store = null;
        Folder folder = null;
        try {
            store = this.connect(mailbox);
            folder = store.getFolder(StringUtils.defaultIfBlank(mailbox.getImapFolder(), "INBOX"));
            folder.open(Folder.READ_ONLY);
            return folder.getMessageCount();
        } finally {
            close(folder, store);
        }
    }

    /**
     * Scans every enabled per-user mailbox and ingests resume submissions, attributing each fetched
     * resume to the mailbox owner so it lands within that HR's isolated view.
     */
    public FetchResult fetch() {
        FetchResult result = new FetchResult();
        List<TalentMailbox> mailboxes = eruptDao.lambdaQuery(TalentMailbox.class)
                .eq(TalentMailbox::getEnabled, true).list();
        if (mailboxes.isEmpty()) {
            result.setMessage("无启用的招聘邮箱，本次任务跳过");
            return result;
        }
        List<String> jobPosts = eruptDao.lambdaQuery(JobPost.class).listSelect(JobPost::getName);
        for (TalentMailbox mailbox : mailboxes) {
            this.fetchOne(mailbox, jobPosts, result);
        }
        result.setMessage(String.format("邮箱 %d 个，扫描 %d 封，新建简历 %d 份，跳过 %d 份，失败 %d 份",
                mailboxes.size(), result.scanned, result.created, result.skipped, result.failed));
        return result;
    }

    /**
     * Scans a single mailbox. A failure on one mailbox is recorded as a note and never aborts the
     * rest of the run.
     */
    private void fetchOne(TalentMailbox mailbox, List<String> jobPosts, FetchResult result) {
        try {
            requireConfig(mailbox);
        } catch (Exception e) {
            result.getNotes().add(mailbox.getImapAccount() + "：配置不完整，已跳过");
            return;
        }
        List<String> keywords = Arrays.stream(StringUtils.defaultString(mailbox.getFetchKeyword()).split(","))
                .map(String::trim).filter(StringUtils::isNotBlank).toList();
        Store store = null;
        Folder folder = null;
        try {
            store = this.connect(mailbox);
            folder = store.getFolder(StringUtils.defaultIfBlank(mailbox.getImapFolder(), "INBOX"));
            folder.open(Folder.READ_ONLY);
            Date since = Date.from(LocalDate.now()
                    .minusDays(Optional.ofNullable(mailbox.getFetchLookbackDays()).orElse(7))
                    .atStartOfDay(ZoneId.systemDefault()).toInstant());
            Message[] messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, since));
            int from = Math.max(0, messages.length - MAX_MESSAGES_PER_RUN);
            for (int i = from; i < messages.length; i++) {
                result.scanned++;
                try {
                    this.ingest(messages[i], keywords, jobPosts, result, mailbox.getCreateBy());
                } catch (Exception e) {
                    result.failed++;
                    log.warn("简历邮件解析失败: {}", e.getMessage());
                }
            }
            if (from > 0) {
                result.getNotes().add(mailbox.getImapAccount() + "：窗口内邮件超过 " + MAX_MESSAGES_PER_RUN
                        + " 封，本次仅处理最新 " + MAX_MESSAGES_PER_RUN + " 封，其余将在下次任务处理");
            }
        } catch (Exception e) {
            result.getNotes().add(mailbox.getImapAccount() + "：抓取失败 - " + e.getMessage());
            log.error("邮箱抓取失败 account={}", mailbox.getImapAccount(), e);
        } finally {
            close(folder, store);
        }
    }

    private void ingest(Message message, List<String> keywords, List<String> jobPosts,
                        FetchResult result, String owner) throws Exception {
        String subject = StringUtils.defaultString(decode(message.getSubject()));
        if (!keywords.isEmpty() && keywords.stream().noneMatch(subject::contains)) {
            result.skipped++;
            return;
        }
        String mailKey = this.messageId(message);
        if (eruptDao.lambdaQuery(Resume.class).eq(Resume::getMailKey, mailKey).count() > 0) {
            result.skipped++;
            return;
        }

        Parts parts = new Parts();
        this.walk(message, parts);

        String fullText = StringUtils.abbreviate(
                parts.body + "\n" + String.join("\n", parts.attachmentTexts), MAX_TEXT_CHARS);
        ResumeParser.Parsed parsed = resumeParser.parse(subject, parts.body, parts.fileNames, fullText, jobPosts);

        Resume resume = new Resume();
        resume.setCode(seqService.next(TalentConst.SEQ_RESUME));
        resume.setSource(ResumeSource.MAIL);
        resume.setStatus(ResumeStatus.PENDING_SCREEN);
        // Attribute the resume to the mailbox owner so it stays inside their isolation view. The
        // scheduled job runs without a session, so @PrePersist will not overwrite this value.
        resume.setCreateBy(owner);
        resume.setMailKey(mailKey);
        resume.setMailSubject(StringUtils.abbreviate(subject, 500));
        resume.setMailFrom(this.sender(message));
        resume.setMailText(fullText);
        resume.setDeliverTime(null == message.getReceivedDate() ? LocalDateTime.now()
                : LocalDateTime.ofInstant(message.getReceivedDate().toInstant(), ZoneId.systemDefault()));
        resume.setName(StringUtils.defaultIfBlank(parsed.getName(), "待确认"));
        resume.setGender(parsed.getGender());
        resume.setPhone(parsed.getPhone());
        resume.setEmail(StringUtils.defaultIfBlank(parsed.getEmail(), resume.getMailFrom()));
        resume.setEducation(parsed.getEducation());
        resume.setSchool(parsed.getSchool());
        resume.setMajor(parsed.getMajor());
        resume.setWorkYears(parsed.getWorkYears());
        resume.setAttachment(this.store(parts));
        if (null != parsed.getJobPost()) {
            resume.setJobPost(eruptDao.lambdaQuery(JobPost.class).eq(JobPost::getName, parsed.getJobPost()).one());
        }
        if (null == resume.getJobPost()) {
            resume.setRemark("未能自动识别应聘岗位，请人工补充");
        }
        this.save(resume);
        result.created++;
        traceService.log(TraceBizType.RESUME, resume.getId(), resume.getCode(), resume.getCode(),
                "邮箱自动抓取", null, ResumeStatus.PENDING_SCREEN,
                "来源邮件：" + StringUtils.abbreviate(subject, 200));
    }

    /**
     * One resume per transaction so a single bad mail cannot roll back a whole batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Resume resume) {
        eruptDao.persistAndFlush(resume);
    }

    /* ---------------- MIME walking ---------------- */

    private void walk(Part part, Parts acc) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                this.walk(multipart.getBodyPart(i), acc);
            }
            return;
        }
        String disposition = part.getDisposition();
        String fileName = decode(part.getFileName());
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (StringUtils.isNotBlank(fileName) && !part.isMimeType("text/*"));
        if (isAttachment) {
            this.readAttachment(part, fileName, acc);
            return;
        }
        if (part.isMimeType("text/plain")) {
            acc.body = StringUtils.abbreviate(acc.body + "\n" + asString(part), MAX_TEXT_CHARS);
        } else if (part.isMimeType("text/html")) {
            acc.body = StringUtils.abbreviate(acc.body + "\n" + stripHtml(asString(part)), MAX_TEXT_CHARS);
        }
    }

    private void readAttachment(Part part, String fileName, Parts acc) throws Exception {
        if (StringUtils.isBlank(fileName)) {
            return;
        }
        acc.fileNames.add(fileName);
        if (part.getSize() > MAX_ATTACHMENT_BYTES) {
            acc.attachmentTexts.add("[附件过大，未解析：" + fileName + "]");
            return;
        }
        byte[] bytes;
        try (InputStream in = part.getInputStream()) {
            bytes = IOUtils.toByteArray(in);
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            acc.attachmentTexts.add("[附件过大，未解析：" + fileName + "]");
            return;
        }
        String ext = extension(fileName);
        if (STORABLE.contains(ext)) {
            acc.files.add(new AttachmentFile(ext, bytes));
        }
        if (TEXT_EXTRACTABLE.contains(ext)) {
            acc.attachmentTexts.add(this.extractText(ext, bytes, fileName));
        }
    }

    private String extractText(String ext, byte[] bytes, String fileName) {
        try {
            switch (ext) {
                case "pdf" -> {
                    try (PDDocument document = Loader.loadPDF(bytes)) {
                        return new PDFTextStripper().getText(document);
                    }
                }
                case "docx" -> {
                    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                         XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                        return extractor.getText();
                    }
                }
                default -> {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("附件文本提取失败 {}: {}", fileName, e.getMessage());
            return "[附件解析失败：" + fileName + "]";
        }
    }

    /**
     * Writes the collected attachments into erupt's upload directory using the same
     * {@code /yyyy-MM-dd/<random>.<ext>} layout the manual uploader produces, so the
     * attachment column renders and downloads them as usual.
     * <p>
     * File names are generated locally and the extension is whitelisted — nothing from the mail
     * ever reaches the path, which rules out traversal.
     *
     * @return the joined relative paths, or null when there was nothing to store
     */
    private String store(Parts parts) {
        if (parts.files.isEmpty()) {
            return null;
        }
        List<String> paths = new ArrayList<>();
        String dir = LocalDate.now().format(DAY_DIR);
        for (AttachmentFile file : parts.files) {
            try {
                String relative = "/" + dir + "/" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                        + "." + file.ext();
                Path target = Paths.get(eruptProp.getUploadPath(), relative).normalize();
                Path root = Paths.get(eruptProp.getUploadPath()).normalize();
                if (!target.startsWith(root)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, file.bytes());
                paths.add(relative);
            } catch (Exception e) {
                log.warn("简历附件落盘失败: {}", e.getMessage());
            }
        }
        return paths.isEmpty() ? null : String.join("|", paths);
    }

    /* ---------------- helpers ---------------- */

    private Store connect(TalentMailbox mailbox) throws Exception {
        boolean ssl = Boolean.TRUE.equals(mailbox.getImapSsl());
        String protocol = ssl ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", mailbox.getImapHost());
        props.put("mail." + protocol + ".port", String.valueOf(mailbox.getImapPort()));
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "30000");
        if (ssl) {
            props.put("mail." + protocol + ".ssl.enable", "true");
        }
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(mailbox.getImapHost(), mailbox.getImapPort(), mailbox.getImapAccount(), mailbox.getImapPassword());
        return store;
    }

    private void requireConfig(TalentMailbox mailbox) {
        if (StringUtils.isAnyBlank(mailbox.getImapHost(), mailbox.getImapAccount(), mailbox.getImapPassword())
                || null == mailbox.getImapPort()) {
            throw new IllegalStateException("IMAP 配置不完整：服务器、端口、账号、密码均为必填");
        }
    }

    /**
     * Falls back to a stable synthetic key when the server omits Message-ID, so the unique
     * constraint still de-duplicates repeat scans of the same mail.
     */
    private String messageId(Message message) throws MessagingException {
        String[] header = message.getHeader("Message-ID");
        if (null != header && header.length > 0 && StringUtils.isNotBlank(header[0])) {
            return StringUtils.abbreviate(header[0].trim(), 500);
        }
        return StringUtils.abbreviate("synthetic:" + this.sender(message) + ":"
                + decode(message.getSubject()) + ":"
                + (null == message.getReceivedDate() ? "" : message.getReceivedDate().getTime()), 500);
    }

    private String sender(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        if (null == from || from.length == 0) {
            return null;
        }
        return from[0] instanceof InternetAddress ia ? ia.getAddress() : from[0].toString();
    }

    private static String asString(Part part) throws Exception {
        Object content = part.getContent();
        return content instanceof String s ? s
                : new String(IOUtils.toByteArray(part.getInputStream()), StandardCharsets.UTF_8);
    }

    private static String stripHtml(String html) {
        return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]{0,2000}>", " ")
                .replaceAll("&nbsp;?", " ")
                .replaceAll("[ \\t]{2,}", " ");
    }

    private static String decode(String raw) {
        if (StringUtils.isBlank(raw)) {
            return raw;
        }
        try {
            return MimeUtility.decodeText(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String extension(String fileName) {
        return StringUtils.lowerCase(StringUtils.substringAfterLast(fileName, "."));
    }

    private static void close(Folder folder, Store store) {
        try {
            if (null != folder && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
            // closing a mailbox folder is best-effort
        }
        try {
            if (null != store && store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
            // closing a mailbox connection is best-effort
        }
    }

    /* ---------------- carriers ---------------- */

    private static class Parts {

        private String body = "";

        private final List<String> fileNames = new ArrayList<>();

        private final List<String> attachmentTexts = new ArrayList<>();

        private final List<AttachmentFile> files = new ArrayList<>();

    }

    private record AttachmentFile(String ext, byte[] bytes) {
    }

    @Getter
    @Setter
    public static class FetchResult {

        private int scanned;

        private int created;

        private int skipped;

        private int failed;

        private String message;

        private final List<String> notes = new ArrayList<>();

    }

}
