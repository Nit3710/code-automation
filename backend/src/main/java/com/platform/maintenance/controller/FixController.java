package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import com.platform.maintenance.model.FixAttempt;
import com.platform.maintenance.model.User;
import com.platform.maintenance.service.AiFixEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspaces/{workspaceId}/jobs/{jobId}/fix")
@RequiredArgsConstructor
public class FixController {

    private final AiFixEngineService aiFixEngineService;

    @PostMapping
    public ResponseEntity<ApiResponse<FixAttempt>> attemptVerifiedFix(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        FixAttempt fix = aiFixEngineService.attemptVerifiedFix(workspaceId, jobId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("AI fix generated and tested in isolated branch", fix));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FixAttempt>> getLatestFix(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        FixAttempt fix = aiFixEngineService.getLatestFix(workspaceId, jobId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(fix));
    }

    @PostMapping("/{fixId}/pr")
    public ResponseEntity<ApiResponse<FixAttempt>> createPullRequest(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @PathVariable Long fixId,
            @AuthenticationPrincipal User currentUser) {
        FixAttempt fix = aiFixEngineService.createPullRequest(workspaceId, jobId, fixId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Pull request opened successfully", fix));
    }
}
