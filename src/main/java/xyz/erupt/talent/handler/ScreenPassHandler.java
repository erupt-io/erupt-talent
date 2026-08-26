package xyz.erupt.talent.handler;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.jpa.model.BaseModel;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.talent.service.ResumeFlowService;

import java.util.List;

/**
 * Rule 3: screening passed -> first-round interview task is created automatically.
 *
 * @author YuePeng
 */
@Service
public class ScreenPassHandler implements OperationHandler<Resume, Void> {

    @Resource
    private ResumeFlowService resumeFlowService;

    @Override
    public String exec(List<Resume> data, Void form, String[] param) {
        int affected = resumeFlowService.screenPass(data.stream().map(BaseModel::getId).toList());
        return "alert(" + GsonFactory.getGson().toJson(
                affected + " 份简历初筛通过，已自动创建一面任务并推送待办。") + ")";
    }

}
