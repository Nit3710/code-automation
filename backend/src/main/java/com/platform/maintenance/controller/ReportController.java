package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import com.platform.maintenance.common.ResourceNotFoundException;
import com.platform.maintenance.dto.ReportDtos;
import com.platform.maintenance.model.Job;
import com.platform.maintenance.model.Report;
import com.platform.maintenance.model.User;
import com.platform.maintenance.repository.JobRepository;
import com.platform.maintenance.service.ReportService;
import com.platform.maintenance.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspaces/{workspaceId}/jobs/{jobId}/report")
@RequiredArgsConstructor
public class ReportController {

    private final JobRepository jobRepository;
    private final WorkspaceService workspaceService;
    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<ApiResponse<ReportDtos.ReportResponse>> getReport(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        Job job = jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        Report report = job.getReport();
        if (report == null) {
            throw new ResourceNotFoundException("Report not yet generated for job id: " + jobId);
        }

        return ResponseEntity.ok(ApiResponse.ok(reportService.mapToResponse(report)));
    }

    @GetMapping(value = "/markdown", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> downloadMarkdown(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        Job job = jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        Report report = job.getReport();
        if (report == null) {
            throw new ResourceNotFoundException("Report not yet generated for job id: " + jobId);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"job-" + jobId + "-report.md\"")
                .body(report.getMarkdownContent());
    }
}
