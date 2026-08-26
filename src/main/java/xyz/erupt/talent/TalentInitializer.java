package xyz.erupt.talent;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.job.model.EruptJob;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.notice.model.NoticeScene;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.talent.job.MailFetchJob;
import xyz.erupt.talent.job.OfferExpireJob;
import xyz.erupt.talent.model.Interview;
import xyz.erupt.talent.service.TalentSettingService;
import xyz.erupt.upms.enums.EruptFunPermissions;
import xyz.erupt.upms.model.EruptMenu;
import xyz.erupt.upms.model.EruptRole;
import xyz.erupt.upms.util.UPMSUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Idempotent seeding of everything the module needs to be usable straight after the first boot:
 * notification scenes, the configuration row, the two scheduled jobs and the two talent roles.
 * <p>
 * Runs on {@code ApplicationReadyEvent} rather than as a {@code CommandLineRunner} because the
 * menu tree is created by upms's own runner — waiting until the context is fully ready is what
 * makes the role-to-menu binding below able to find those menus.
 *
 * @author YuePeng
 */
@Slf4j
@Component
public class TalentInitializer {

    /** Interviewers get read + assess rights on interviews only. */
    private static final List<EruptFunPermissions> INTERVIEWER_PERMISSIONS =
            List.of(EruptFunPermissions.EDIT, EruptFunPermissions.VIEW_DETAIL);

    private static final Map<String, String> SCENES = Map.of(
            TalentConst.SCENE_SCREEN_PASS, "简历初筛通过",
            TalentConst.SCENE_INTERVIEW_ARRANGED, "面试已安排",
            TalentConst.SCENE_INTERVIEW_RESCHEDULED, "面试改期",
            TalentConst.SCENE_INTERVIEW_RESULT, "面试结论提交",
            TalentConst.SCENE_OFFER_APPROVAL, "Offer 审批流转",
            TalentConst.SCENE_OFFER_SENT, "Offer 已发送",
            TalentConst.SCENE_OFFER_ACCEPTED, "候选人接受 Offer",
            TalentConst.SCENE_ONBOARD, "入职提醒"
    );

    @Resource
    private EruptDao eruptDao;

    @Resource
    private TalentSettingService settingService;

    /**
     * Self-reference so each step below runs in its own transaction.
     */
    @Lazy
    @Resource
    private TalentInitializer self;

    /**
     * Each step is isolated: a failure is logged and the remaining steps still run, and a broken
     * seed can never take the whole application down on boot.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        this.step("通知场景", () -> self.seedScenes());
        this.step("系统配置", () -> settingService.seed());
        this.step("定时任务", () -> self.seedJobs());
        this.step("招聘角色", () -> self.seedRoles());
    }

    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("招聘模块初始化失败（{}）：{}", name, e.getMessage());
        }
    }

    @Transactional
    public void seedScenes() {
        SCENES.forEach((code, name) -> {
            NoticeScene scene = new NoticeScene();
            scene.setCode(code);
            scene.setName(name);
            eruptDao.persistIfNotExist(NoticeScene.class, scene, "code", code);
        });
    }

    /**
     * Registers the two job rows so the schedules exist out of the box; the cron and the on/off
     * switch remain editable in 定时任务.
     */
    @Transactional
    public void seedJobs() {
        this.seedJob(MailFetchJob.CODE, MailFetchJob.class.getName(), "招聘邮箱简历抓取",
                "0 0/30 * * * ?", "定时抓取招聘邮箱投递简历，自动解析并录入简历数据源");
        this.seedJob(OfferExpireJob.CODE, OfferExpireJob.class.getName(), "Offer 过期检查",
                "0 0 1 * * ?", "将超过回复截止日期仍未回复的 Offer 标记为已过期");
    }

    private void seedJob(String code, String handler, String name, String cron, String remark) {
        EruptJob job = new EruptJob();
        job.setCode(code);
        job.setHandler(handler);
        job.setName(name);
        job.setCron(cron);
        job.setStatus(true);
        job.setRecordLog(true);
        job.setRemark(remark);
        eruptDao.persistIfNotExist(EruptJob.class, job, "code", code);
    }

    /**
     * Creates the HR and interviewer roles described in section 4.7 and binds their menus.
     * Existing roles are left untouched apart from filling in menus they are missing, so an
     * administrator's later customisation is never overwritten.
     */
    @Transactional
    public void seedRoles() {
        EruptMenu root = this.menu(EruptTalentAutoConfiguration.MENU_ROOT);
        if (null == root) {
            log.warn("招聘菜单尚未初始化，跳过角色初始化");
            return;
        }
        List<EruptMenu> all = new ArrayList<>();
        all.add(root);
        all.addAll(this.children(root));

        this.upsertRole(TalentConst.ROLE_HR, "HR专员", all);

        Set<EruptMenu> interviewerMenus = new LinkedHashSet<>();
        interviewerMenus.add(root);
        EruptMenu interviewMenu = this.menu(Interview.class.getSimpleName());
        if (null != interviewMenu) {
            interviewerMenus.add(interviewMenu);
            INTERVIEWER_PERMISSIONS.forEach(permission -> Optional.ofNullable(
                            this.menu(UPMSUtil.getEruptFunPermissionsCode(Interview.class.getSimpleName(), permission)))
                    .ifPresent(interviewerMenus::add));
        }
        this.upsertRole(TalentConst.ROLE_INTERVIEWER, "面试官", new ArrayList<>(interviewerMenus));
    }

    private void upsertRole(String code, String name, List<EruptMenu> menus) {
        EruptRole role = eruptDao.lambdaQuery(EruptRole.class).eq(EruptRole::getCode, code).one();
        if (null == role) {
            role = new EruptRole();
            role.setCode(code);
            role.setName(name);
            role.setStatus(true);
            role.setMenus(new LinkedHashSet<>(menus));
            eruptDao.persist(role);
            return;
        }
        Set<EruptMenu> current = null == role.getMenus() ? new LinkedHashSet<>() : new LinkedHashSet<>(role.getMenus());
        if (current.addAll(menus)) {
            role.setMenus(current);
            eruptDao.merge(role);
        }
    }

    private EruptMenu menu(String code) {
        return eruptDao.lambdaQuery(EruptMenu.class).eq(EruptMenu::getCode, code).one();
    }

    /**
     * Direct children plus their own children — enough for the two-level talent tree
     * (model menus and the button permissions erupt derives from them).
     */
    private List<EruptMenu> children(EruptMenu parent) {
        List<EruptMenu> direct = eruptDao.lambdaQuery(EruptMenu.class)
                .eq(EruptMenu::getParentMenu, parent).list();
        List<EruptMenu> result = new ArrayList<>(direct);
        direct.forEach(it -> result.addAll(eruptDao.lambdaQuery(EruptMenu.class)
                .eq(EruptMenu::getParentMenu, it).list()));
        return result;
    }

}
