package com.platform.maintenance.dto;

import com.platform.maintenance.model.enums.ProjectType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class RepositoryDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectRepositoryRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String fullName; // e.g. "my-org/my-repo"
        @NotBlank
        private String cloneUrl;
        private String defaultBranch;
        private String githubRepoId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateConfigurationRequest {
        private ProjectType projectType;
        private String installCommand;
        private String buildCommand;
        private String testCommand;
        private String lintCommand;
        private Integer timeoutSeconds;
        private Boolean scheduleEnabled;
        private String cronExpression;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigurationDto {
        private Long id;
        private ProjectType projectType;
        private String installCommand;
        private String buildCommand;
        private String testCommand;
        private String lintCommand;
        private Integer timeoutSeconds;
        private Boolean scheduleEnabled;
        private String cronExpression;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepositoryResponse {
        private Long id;
        private Long workspaceId;
        private String name;
        private String fullName;
        private String cloneUrl;
        private String defaultBranch;
        private Boolean active;
        private ConfigurationDto configuration;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectDetectionResult {
        private ProjectType detectedType;
        private String suggestedInstallCommand;
        private String suggestedBuildCommand;
        private String suggestedTestCommand;
        private List<String> matchedIndicators;
        private double confidence;
    }
}
