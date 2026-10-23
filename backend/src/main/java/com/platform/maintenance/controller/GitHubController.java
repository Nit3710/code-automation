package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import com.platform.maintenance.dto.RepositoryDtos;
import com.platform.maintenance.model.User;
import com.platform.maintenance.service.GitHubIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubIntegrationService gitHubIntegrationService;

    @GetMapping("/repos")
    public ResponseEntity<ApiResponse<List<GitHubIntegrationService.GitHubRepoItem>>> getGitHubRepositories(
            @PathVariable Long workspaceId,
            @RequestParam String username,
            @RequestHeader(value = "X-GitHub-Token", required = false) String headerToken,
            @RequestParam(required = false) String token,
            @AuthenticationPrincipal User currentUser) {
        String effectiveToken = (headerToken != null && !headerToken.isBlank()) ? headerToken : token;
        List<GitHubIntegrationService.GitHubRepoItem> repos =
                gitHubIntegrationService.fetchUserRepositories(username, workspaceId, currentUser, effectiveToken);
        return ResponseEntity.ok(ApiResponse.ok("Repositories fetched successfully", repos));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<RepositoryDtos.RepositoryResponse>> importRepository(
            @PathVariable Long workspaceId,
            @RequestBody GitHubIntegrationService.ImportRepoRequest request,
            @AuthenticationPrincipal User currentUser) {
        RepositoryDtos.RepositoryResponse response =
                gitHubIntegrationService.importAndAutoStart(workspaceId, currentUser, request);
        return ResponseEntity.ok(ApiResponse.ok("Repository imported and health check started automatically", response));
    }
}
