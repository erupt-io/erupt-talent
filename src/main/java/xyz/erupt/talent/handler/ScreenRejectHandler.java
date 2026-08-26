package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.jpa.model.BaseModel;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.talent.model.form.ScreenRejectForm;
import xyz.erupt.talent.service.ResumeFlowService;

import java.util.List;

/**
 * Screening rejected: the reason is mandatory and the pipeline terminates.
 *
 * @author YuePeng
 */
@Service
public class ScreenRejectHandler implements OperationHandler<Resume, ScreenRejectForm> {

    @Resource
    private ResumeFlowService resumeFlowService;

    @Override
    public String exec(List<Resume> data, ScreenRejectForm form, String[] param) {
        int affected = resumeFlowService.screenReject(
                data.stream().map(BaseModel::getId).toList(), form.getEliminateReason());
        return "alert(" + GsonFactory.getGson().toJson(affected + " 份简历已初筛淘汰，流程终止。") + ")";
    }

}
