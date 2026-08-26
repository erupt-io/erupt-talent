package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.PowerHandler;
import xyz.erupt.annotation.fun.PowerObject;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Section 4.7: only HR specialists and super administrators may operate the pipeline.
 * Anyone else who somehow reaches the menu gets a read-only view at best.
 *
 * @author YuePeng
 */
@Component
public class HrOnlyPower implements PowerHandler {

    @Resource
    private TalentAuth talentAuth;

    @Override
    public void handler(PowerObject power) {
        if (talentAuth.isHr()) {
            return;
        }
        power.setAdd(false);
        power.setEdit(false);
        power.setDelete(false);
        power.setImportable(false);
        power.setExport(false);
        power.setCopy(false);
    }

}
