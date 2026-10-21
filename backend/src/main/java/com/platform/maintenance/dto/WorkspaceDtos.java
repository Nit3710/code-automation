package com.platform.maintenance.dto;

import com.platform.maintenance.model.enums.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class WorkspaceDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateWorkspaceRequest {
        @NotBlank
        private String name;
        private String slug;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceResponse {
        private Long id;
        private String name;
        private String slug;
        private Long ownerId;
        private Role userRole;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceMemberResponse {
        private Long id;
        private Long userId;
        private String name;
        private String email;
        private Role role;
        private LocalDateTime joinedAt;
    }
}
