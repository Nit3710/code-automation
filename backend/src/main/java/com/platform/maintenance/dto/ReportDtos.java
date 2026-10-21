package com.platform.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ReportDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResponse {
        private Long id;
        private Long jobId;
        private String summary;
        private String markdownContent;
        private Integer totalSteps;
        private Integer passedSteps;
        private Integer failedSteps;
        private LocalDateTime generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiAnalysisDto {
        private Long id;
        private Long jobId;
        private String failureSummary;
        private String relevantErrors;
        private String likelyRootCause;
        private String confirmedFacts;
        private String hypotheses;
        private String suggestedSolution;
        private String nextStepsToTest;
        private String provider;
        private Integer tokensUsed;
        private LocalDateTime createdAt;
    }
}
