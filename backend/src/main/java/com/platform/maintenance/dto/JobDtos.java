package com.platform.maintenance.dto;

import com.platform.maintenance.model.enums.JobStatus;
import com.platform.maintenance.model.enums.TriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class JobDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerJobRequest {
        private String branch;
        private String commitSha;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobResponse {
        private Long id;
        private Long repositoryId;
        private String repositoryName;
        private JobStatus status;
        private TriggerType triggerType;
        private String branch;
        private String commitSha;
        private String commitMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private Integer exitCode;
        private String failureReason;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobLogDto {
        private Long id;
        private String stepName;
        private Integer stepOrder;
        private Integer exitCode;
        private Long durationMs;
        private String output;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobDetailResponse {
        private JobResponse job;
        private List<JobLogDto> logs;
        private ReportDtos.ReportResponse report;
        private ReportDtos.AiAnalysisDto aiAnalysis;
    }
}
