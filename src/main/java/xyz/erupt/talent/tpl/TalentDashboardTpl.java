package xyz.erupt.talent.tpl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.erupt.talent.constant.LabeledEnum;
import xyz.erupt.talent.constant.ResumeSource;
import xyz.erupt.tpl.annotation.EruptTpl;
import xyz.erupt.tpl.annotation.TplAction;
import xyz.erupt.upms.service.EruptContextService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Standalone menu page for the talent dashboard.
 * The annotation value doubles as the template file name and the menu permission identifier.
 *
 * @author YuePeng
 */
@EruptTpl
@Component
public class TalentDashboardTpl {

    public static final String DASHBOARD = "talent-dashboard.ftl";

    @Resource
    private EruptContextService eruptContextService;

    @TplAction(DASHBOARD)
    public Map<String, Object> dashboard() {
        Map<String, Object> binding = new HashMap<>();
        binding.put("token", eruptContextService.getCurrentToken());
        binding.put("sources", LabeledEnum.vls(ResumeSource.values()).stream()
                .collect(Collectors.toMap(it -> it.getValue(), it -> it.getLabel(), (a, b) -> a, java.util.LinkedHashMap::new)));
        return binding;
    }

}
