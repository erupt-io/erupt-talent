package xyz.erupt.talent;

import org.springframework.context.annotation.Configuration;
import xyz.erupt.core.constant.MenuTypeEnum;
import xyz.erupt.core.module.EruptModule;
import xyz.erupt.core.module.EruptModuleInvoke;
import xyz.erupt.core.module.MetaMenu;
import xyz.erupt.core.module.ModuleInfo;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.talent.model.*;
import xyz.erupt.talent.tpl.TalentDashboardTpl;
import xyz.erupt.tpl.service.EruptTplService;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the talent menu tree on first boot. Menu codes are stable, so a re-run only
 * fills in what is missing — erupt persists them with "insert if absent" semantics and derives
 * the per-model button permissions automatically.
 *
 * @author YuePeng
 */
@Configuration
public class EruptTalentAutoConfiguration implements EruptModule {

    static {
        EruptModuleInvoke.addEruptModule(EruptTalentAutoConfiguration.class);
    }

    /**
     * Menu code of the dashboard page; also the role-binding key.
     */
    public static final String MENU_DASHBOARD = "talent-dashboard";

    public static final String MENU_ROOT = "$talent";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.builder().name(TalentConst.MODULE).build();
    }

    @Override
    public List<MetaMenu> initMenus() {
        List<MetaMenu> menus = new ArrayList<>();
        MetaMenu root = MetaMenu.createRootMenu(MENU_ROOT, "招聘管理", "fa fa-users", 10);
        menus.add(root);
        menus.add(MetaMenu.createSimpleMenu(MENU_DASHBOARD, "招聘数据看板",
                TalentDashboardTpl.DASHBOARD, root, 10, EruptTplService.TPL));
        menus.add(MetaMenu.createEruptClassMenu(JobPost.class, root, 20));
        menus.add(MetaMenu.createEruptClassMenu(Resume.class, root, 30));
        menus.add(MetaMenu.createEruptClassMenu(Interview.class, root, 40));
        menus.add(MetaMenu.createEruptClassMenu(Offer.class, root, 50));
        menus.add(MetaMenu.createEruptClassMenu(Onboarding.class, root, 60));
        menus.add(MetaMenu.createEruptClassMenu(TalentMailbox.class, root, 65));
        menus.add(MetaMenu.createEruptClassMenu(TalentTrace.class, root, 70));
        menus.add(MetaMenu.createEruptClassMenu(TalentSetting.class, root, 80, MenuTypeEnum.FORM));
        return menus;
    }

}
