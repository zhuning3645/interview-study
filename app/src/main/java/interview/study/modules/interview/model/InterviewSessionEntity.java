package interview.study.modules.interview.model;

import interview.study.modules.resume.model.ResumeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.support.SessionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试会话实体
 */
@Getter
@Setter
@Entity
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_interview_session_resume_created", columnList = "resume_id, created_at"),
        @Index(name = "idx_interview_session_resume_status_created", columnList = "resume_id, status, created_at")
})
public class InterviewSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 会话ID（UUID
     */
    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;

    /**
     * 关联简历
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeEntity resume;

    // 问题总数
    private Integer totalQuestions;

    // 当前问题索引
    private Integer currentQuestionIndex = 0;

    /**
     * 会话状态
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SessionStatus status = SessionStatus.CREATED;

    /**
     * 问题列表（JSON格式
     */
    @Column(columnDefinition = "TEXT")
    private String questionJson;

    // 总分（0-100
    private Integer overallScore;

    // 总体评价
    @Column(columnDefinition = "TEXT")
    private String overallFeedback;

    // 优势（JSON
    @Column(columnDefinition = "TEXT")
    private String strengthsJson;

    // 改进建议（JSON
    @Column(columnDefinition = "TEXT")
    private String improvementsJson;

    // 参考答案
    @Column(columnDefinition = "TEXT")
    private String referenceAnswersJson;

    //面试答案记录
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterviewAnswerEntity> answers = new ArrayList<>();

    // 创建时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 完成时间
    private LocalDateTime completedAt;

    // 评估错误信息
    @Column(length = 500)
    private String evaluateError;

    //面试状态枚举类
    public enum SessionStatus {
        CREATED,  //会话已创建
        IN_PROGRESS,  //面试进行中
        COMPLETED,  //面试已完成
        EVALUATED  //已生成评估报告
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addAnswer(InterviewAnswerEntity answer) {
        answers.add(answer);
        answer.setSession(this);
    }
}
