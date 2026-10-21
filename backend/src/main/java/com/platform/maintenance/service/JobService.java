package com.platform.maintenance.service;

import com.platform.maintenance.common.ResourceNotFoundException;
import com.platform.maintenance.dto.JobDtos;
import com.platform.maintenance.dto.ReportDtos;
import com.platform.maintenance.model.*;
import com.platform.maintenance.model.enums.JobStatus;
import com.platform.maintenance.model.enums.TriggerType;
import com.platform.maintenance.repository.JobLogRepository;
import com.platform.maintenance.repository.JobRepository;
import com.platform.maintenance.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;
    private final WorkspaceService workspaceService;
    private final ExecutionEngineService executionEngineService;
    private final AiDiagnosticService aiDiagnosticService;
    private final ReportService reportService;
    private final AiFixEngineService aiFixEngineService;

    public List<JobDtos.JobResponse> getWorkspaceJobs(Long workspaceId, User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        List<Job> jobs = jobRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return jobs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<JobDtos.JobResponse> getRepositoryJobs(Long workspaceId, Long repositoryId, User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        List<Job> jobs = jobRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);
        return jobs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public JobDtos.JobDetailResponse getJobDetail(Long workspaceId, Long jobId, User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        Job job = jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        List<JobLog> logs = jobLogRepository.findByJobIdOrderByStepOrderAsc(jobId);
        List<JobDtos.JobLogDto> logDtos = logs.stream().map(l -> JobDtos.JobLogDto.builder()
                .id(l.getId())
                .stepName(l.getStepName())
                .stepOrder(l.getStepOrder())
                .exitCode(l.getExitCode())
                .durationMs(l.getDurationMs())
                .output(l.getOutput())
                .timestamp(l.getTimestamp())
                .build()).collect(Collectors.toList());

        ReportDtos.ReportResponse reportResponse = reportService.mapToResponse(job.getReport());
        ReportDtos.AiAnalysisDto aiDto = null;
        if (job.getAiAnalysis() != null) {
            AiAnalysis a = job.getAiAnalysis();
            aiDto = ReportDtos.AiAnalysisDto.builder()
                    .id(a.getId())
                    .jobId(job.getId())
                    .failureSummary(a.getFailureSummary())
                    .relevantErrors(a.getRelevantErrors())
                    .likelyRootCause(a.getLikelyRootCause())
                    .confirmedFacts(a.getConfirmedFacts())
                    .hypotheses(a.getHypotheses())
                    .suggestedSolution(a.getSuggestedSolution())
                    .nextStepsToTest(a.getNextStepsToTest())
                    .provider(a.getProvider())
                    .tokensUsed(a.getTokensUsed())
                    .createdAt(a.getCreatedAt())
                    .build();
        }

        return JobDtos.JobDetailResponse.builder()
                .job(mapToResponse(job))
                .logs(logDtos)
                .report(reportResponse)
                .aiAnalysis(aiDto)
                .build();
    }

    @Transactional
    public JobDtos.JobResponse triggerHealthCheck(Long workspaceId, Long repositoryId, User currentUser, JobDtos.TriggerJobRequest request) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        RepositoryEntity repository = repositoryEntityRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + repositoryId));

        String targetBranch = (request != null && request.getBranch() != null && !request.getBranch().isBlank())
                ? request.getBranch() : repository.getDefaultBranch();

        Job job = Job.builder()
                .repository(repository)
                .status(JobStatus.QUEUED)
                .triggerType(TriggerType.MANUAL)
                .branch(targetBranch)
                .commitSha(request != null ? request.getCommitSha() : null)
                .build();
        job = jobRepository.save(job);

        // Dispatch asynchronous execution
        runJobAsync(job.getId());

        return mapToResponse(job);
    }

    @Transactional
    public JobDtos.JobResponse cancelJob(Long workspaceId, Long jobId, User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        Job job = jobRepository.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.CANCELLED);
            job.setFinishedAt(LocalDateTime.now());
            job.setFailureReason("Job was cancelled by user");
            job = jobRepository.save(job);
        }
        return mapToResponse(job);
    }

    @Async
    public CompletableFuture<Void> runJobAsync(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return CompletableFuture.completedFuture(null);

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            ExecutionEngineService.JobExecutionResult execResult = executionEngineService.executeJob(
                    job.getId(), job.getRepository(), job.getBranch(), null
            );

            // Persist step logs
            List<JobLog> persistedLogs = new ArrayList<>();
            for (ExecutionEngineService.StepExecutionResult step : execResult.getStepResults()) {
                JobLog logEntity = JobLog.builder()
                        .job(job)
                        .stepName(step.getStepName())
                        .stepOrder(step.getStepOrder())
                        .exitCode(step.getExitCode())
                        .durationMs(step.getDurationMs())
                        .output(step.getOutput())
                        .build();
                persistedLogs.add(jobLogRepository.save(logEntity));
            }

            job.setFinishedAt(LocalDateTime.now());
            job.setDurationMs(execResult.getTotalDurationMs());
            job.setStatus(execResult.getFinalStatus());
            job.setExitCode(execResult.getOverallExitCode());
            job.setFailureReason(execResult.getFailureReason());
            if (execResult.getCommitSha() != null) job.setCommitSha(execResult.getCommitSha());
            if (execResult.getCommitMessage() != null) job.setCommitMessage(execResult.getCommitMessage());

            // Run AI analysis if the job failed or timed out (FR-06)
            AiAnalysis aiAnalysis = null;
            if (job.getStatus() == JobStatus.FAILED || job.getStatus() == JobStatus.TIMED_OUT) {
                aiAnalysis = aiDiagnosticService.analyzeFailure(job, persistedLogs);
                job.setAiAnalysis(aiAnalysis);
            }

            // Generate Markdown report (FR-09)
            Report report = reportService.generateReport(job, persistedLogs, aiAnalysis);
            job.setReport(report);

            jobRepository.save(job);

            // Autonomous Zero-Touch Bug Remediation & PR creation if failed!
            if (job.getStatus() == JobStatus.FAILED) {
                try {
                    log.info("Triggering autonomous bugfix & PR engine for failed job {}", job.getId());
                    aiFixEngineService.attemptVerifiedFix(job.getRepository().getWorkspace().getId(), job.getId(), null);
                } catch (Exception fixEx) {
                    log.warn("Autonomous fix pipeline encountered error for job {}: {}", job.getId(), fixEx.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Job execution failed unexpectedly for jobId: {}", jobId, e);
            job.setStatus(JobStatus.INTERNAL_ERROR);
            job.setFinishedAt(LocalDateTime.now());
            job.setFailureReason("System error: " + e.getMessage());
            jobRepository.save(job);
        }

        return CompletableFuture.completedFuture(null);
    }

    public JobDtos.JobResponse mapToResponse(Job job) {
        return JobDtos.JobResponse.builder()
                .id(job.getId())
                .repositoryId(job.getRepository().getId())
                .repositoryName(job.getRepository().getFullName())
                .status(job.getStatus())
                .triggerType(job.getTriggerType())
                .branch(job.getBranch())
                .commitSha(job.getCommitSha())
                .commitMessage(job.getCommitMessage())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .durationMs(job.getDurationMs())
                .exitCode(job.getExitCode())
                .failureReason(job.getFailureReason())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
