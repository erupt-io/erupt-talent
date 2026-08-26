package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.expr.ExprBool;
import xyz.erupt.talent.service.TalentAuth;

/**
 * Shows a control only to super administrators — used for the status-correction escape hatch.
 *
 * @author YuePeng
 */
@Component
public class AdminOnly implements ExprBool.ExprHandler {

    @Resource
    private TalentAuth talentAuth;

    @Override
    public boolean handler(boolean expr, String[] params) {
        return talentAuth.isAdmin();
    }

}
