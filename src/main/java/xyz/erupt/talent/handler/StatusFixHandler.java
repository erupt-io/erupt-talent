package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.talent.constant.ResumeStatus;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.talent.model.form.StatusFixForm;
import xyz.erupt.talent.service.ResumeFlowService;

import java.util.List;

/**
 * Administrator-only status correction, the single sanctioned way out of the one-way flow.
 *
 * @author YuePeng
 */
@Service
public class StatusFixHandler implements OperationHandler<Resume, StatusFixForm> {

    @Resource
    private ResumeFlowService resumeFlowService;

    @Override
    public String exec(List<Resume> data, StatusFixForm form, String[] param) {
        ResumeStatus target;
        try {
            target = ResumeStatus.valueOf(form.getTargetStatus());
        } catch (Exception e) {
            throw new EruptWebApiRuntimeException("非法的目标状态：" + form.getTargetStatus());
        }
        resumeFlowService.statusFix(data.get(0).getId(), target, form.getReason());
        return null;
    }

}
