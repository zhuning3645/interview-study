package interview.study.modules.resume.respository;

import interview.study.modules.resume.model.ResumeAnalysisEntity;
import interview.study.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 简历评测Repository
 */
@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {

    /**
     * 根据简历查找所有测评记录
     * @param resume
     * @return
     */
    List<ResumeAnalysisEntity> findByResumeOrderByAnalyzedAtDesc(ResumeEntity resume);

    /**
     * 根据简历ID查找最新的评测记录
     * @param resumeId
     * @return
     */
    ResumeAnalysisEntity findFirstByResumeOrderByAnalyzedAtDesc(Long resumeId);

    /**
     * 根据简历ID查找所有评测记录
     * @param resumeId
     * @return
     */
    List<ResumeAnalysisEntity> findAllByResumeOrderByAnalyzedAtDesc(Long resumeId);
}
