package interview.study.modules.resume.respository;

import interview.study.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 根据文件哈希查找简历（用于去重
     * @param fileHash
     * @return
     */
    Optional<ResumeEntity> findByFileHash(String fileHash);

    /**
     * 检查文件哈希是否存在
     * @param fileHash
     * @return
     */
    boolean existsByFileHash(String fileHash);
}
