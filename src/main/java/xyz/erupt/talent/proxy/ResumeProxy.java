package xyz.erupt.talent.proxy;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.talent.constant.TalentConst;
import xyz.erupt.talent.constant.ResumeSource;
import xyz.erupt.talent.constant.ResumeStatus;
import xyz.erupt.talent.constant.TraceBizType;
import xyz.erupt.talent.model.Resume;
import xyz.erupt.talent.service.SeqService;
import xyz.erupt.talent.service.TraceService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Applies the global numbering rule and the "every change is logged" rule to resumes.
 *
 * @author YuePeng
 */
@Component
public class ResumeProxy implements DataProxy<Resume> {

    @Resource
    private SeqService seqService;

    @Resource
    private TraceService traceService;

    @Override
    public void beforeAdd(Resume resume) {
        this.initialise(resume, ResumeSource.MANUAL);
    }

    @Override
    public void afterAdd(Resume resume) {
        traceService.log(TraceBizType.RESUME, resume.getId(), resume.getCode(), resume.getCode(),
                "简历录入", null, resume.getStatus(),
                "来源：" + resume.getSource().label());
    }

    @Override
    public void afterUpdate(Resume resume) {
        traceService.log(TraceBizType.RESUME, resume.getId(), resume.getCode(), resume.getCode(),
                "简历信息修改", null, resume.getStatus(), null);
    }

    @Override
    public void afterDelete(Resume resume) {
        traceService.log(TraceBizType.RESUME, resume.getId(), resume.getCode(), resume.getCode(),
                "简历删除", resume.getStatus(), null, null);
    }

    /**
     * Excel import: every row still gets a generated code and starts at the screening stage.
     */
    @Override
    public void excelImportProcess(List<Resume> list) {
        list.forEach(it -> this.initialise(it, ResumeSource.IMPORT));
    }

    private void initialise(Resume resume, ResumeSource fallbackSource) {
        if (StringUtils.isBlank(resume.getCode())) {
            resume.setCode(seqService.next(TalentConst.SEQ_RESUME));
        }
        if (null == resume.getSource()) {
            resume.setSource(fallbackSource);
        }
        if (null == resume.getStatus()) {
            resume.setStatus(ResumeStatus.PENDING_SCREEN);
        }
        if (null == resume.getDeliverTime()) {
            resume.setDeliverTime(LocalDateTime.now());
        }
    }

}
