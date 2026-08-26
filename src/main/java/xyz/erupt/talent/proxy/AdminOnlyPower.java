package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.PowerHandler;
import xyz.erupt.annotation.fun.PowerObject;
import xyz.erupt.talent.service.TalentAuth;

/**
 * System configuration holds integration credentials, so it is restricted to super
 * administrators — HR specialists explicitly have no system-configuration rights.
 *
 * @author YuePeng
 */
@Component
public class AdminOnlyPower implements PowerHandler {

    @Resource
    private TalentAuth talentAuth;

    @Override
    public void handler(PowerObject power) {
        boolean admin = talentAuth.isAdmin();
        power.setAdd(admin);
        power.setEdit(admin);
        power.setViewDetails(admin);
        power.setQuery(admin);
        power.setDelete(false);
        power.setExport(false);
        power.setImportable(false);
        power.setCopy(false);
        power.setPrint(false);
    }

}
