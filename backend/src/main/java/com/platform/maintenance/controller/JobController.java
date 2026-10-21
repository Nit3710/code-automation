package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import com.platform.maintenance.dto.JobDtos;
import com.platform.maintenance.model.User;
import com.platform.maintenance.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<JobDtos.JobResponse>>> getWorkspaceJobs(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getWorkspaceJobs(workspaceId, currentUser)));
    }

    @GetMapping("/repositories/{repositoryId}/jobs")
    public ResponseEntity<ApiResponse<List<JobDtos.JobResponse>>> getRepositoryJobs(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getRepositoryJobs(workspaceId, repositoryId, currentUser)));
    }

    @PostMapping("/repositories/{repositoryId}/jobs/run")
    public ResponseEntity<ApiResponse<JobDtos.JobResponse>> triggerHealthCheck(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId,
            @AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) JobDtos.TriggerJobRequest request) {
        JobDtos.JobResponse response = jobService.triggerHealthCheck(workspaceId, repositoryId, currentUser, request);
        return ResponseEntity.ok(ApiResponse.ok("Health check job queued", response));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<JobDtos.JobDetailResponse>> getJobDetail(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJobDetail(workspaceId, jobId, currentUser)));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<ApiResponse<JobDtos.JobResponse>> cancelJob(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        JobDtos.JobResponse response = jobService.cancelJob(workspaceId, jobId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Job cancelled successfully", response));
    }
}
