package interview.study.modules.resume.model;

import java.time.LocalDateTime;

/**
 * 简历列表项DTO
 */
public record ResumeListItemDTO (
        Long id,
        String filename,
        Long fileSize,
        LocalDateTime uploadAt,
        Integer accessCount,
        Integer latestScore,
        LocalDateTime lastAnalyzedAt,
        Integer interviewCount
){}
