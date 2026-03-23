package interview.study.modules.interview.repository;

import interview.study.modules.interview.model.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 面试会话Repository
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionRepository, Long> {

    /**
     * 根据会话ID查找
     * @param SessionId
     * @return
     */
    Optional<InterviewSessionRepository> findBySessionId(String SessionId);

    /**
     * 根据会话ID查找（同时加载相关简历）
     * @param SessionId
     * @return
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String SessionId);

}
