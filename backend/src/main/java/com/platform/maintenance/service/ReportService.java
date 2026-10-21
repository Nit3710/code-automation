package com.platform.maintenance.service;

import com.platform.maintenance.dto.ReportDtos;
import com.platform.maintenance.model.AiAnalysis;
import com.platform.maintenance.model.Job;
import com.platform.maintenance.model.JobLog;
import com.platform.maintenance.model.Report;
import com.platform.maintenance.model.enums.JobStatus;
import com.platform.maintenance.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    @Transactional
    public Report generateReport(Job job, List<JobLog> logs, AiAnalysis aiAnalysis) {
        int totalSteps = logs.size();
        int passedSteps = (int) logs.stream().filter(l -> l.getExitCode() != null && l.getExitCode() == 0).count();
        int failedSteps = totalSteps - passedSteps;

        String summary = String.format("Job #%d on %s: %s (%d/%d steps passed)",
                job.getId(), job.getRepository().getFullName(), job.getStatus(), passedSteps, totalSteps);

        String markdown = buildMarkdownReport(job, logs, aiAnalysis, passedSteps, totalSteps);

        Report report = Report.builder()
                .job(job)
                .summary(summary)
                .markdownContent(markdown)
                .totalSteps(totalSteps)
                .passedSteps(passedSteps)
                .failedSteps(failedSteps)
                .build();

        return reportRepository.save(report);
    }

    public String buildMarkdownReport(Job job, List<JobLog> logs, AiAnalysis aiAnalysis, int passedSteps, int totalSteps) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String statusBadge = job.getStatus() == JobStatus.PASSED ? "✅ PASSED" : "❌ " + job.getStatus();

        StringBuilder sb = new StringBuilder();
        sb.append("# Health Check Report: ").append(job.getRepository().getFullName()).append("\n\n");
        sb.append("### Execution Status: ").append(statusBadge).append("\n\n");

        sb.append("| Metric | Value |\n");
        sb.append("| :--- | :--- |\n");
        sb.append("| **Job ID** | `").append(job.getId()).append("` |\n");
        sb.append("| **Repository** | [").append(job.getRepository().getFullName()).append("](").append(job.getRepository().getCloneUrl()).append(") |\n");
        sb.append("| **Branch** | `").append(job.getBranch()).append("` |\n");
        sb.append("| **Commit SHA** | `").append(job.getCommitSha() != null ? job.getCommitSha() : "N/A").append("` |\n");
        sb.append("| **Trigger** | `").append(job.getTriggerType()).append("` |\n");
        sb.append("| **Started At** | ").append(job.getStartedAt() != null ? job.getStartedAt().format(formatter) : "N/A").append(" |\n");
        sb.append("| **Duration** | ").append(job.getDurationMs() != null ? (job.getDurationMs() / 1000.0) + " s" : "N/A").append(" |\n");
        sb.append("| **Exit Code** | `").append(job.getExitCode() != null ? job.getExitCode() : -1).append("` |\n");
        sb.append("| **Passed Steps** | ").append(passedSteps).append(" / ").append(totalSteps).append(" |\n\n");

        sb.append("## Step Breakdown\n\n");
        sb.append("| Step | Order | Status | Duration | Exit Code |\n");
        sb.append("| :--- | :---: | :---: | :---: | :---: |\n");
        for (JobLog logItem : logs) {
            String stepStatus = (logItem.getExitCode() != null && logItem.getExitCode() == 0) ? "✅ Passed" : "❌ Failed";
            sb.append("| **").append(logItem.getStepName()).append("** | ")
                    .append(logItem.getStepOrder()).append(" | ")
                    .append(stepStatus).append(" | ")
                    .append(logItem.getDurationMs() != null ? logItem.getDurationMs() + "ms" : "N/A").append(" | ")
                    .append(logItem.getExitCode()).append(" |\n");
        }
        sb.append("\n");

        // AI Failure Analysis Section (if present)
        if (aiAnalysis != null) {
            sb.append("## 🤖 AI Failure Diagnosis (FR-06)\n\n");
            sb.append("> **Summary:** ").append(aiAnalysis.getFailureSummary()).append("\n\n");

            sb.append("### 🔍 Likely Root Cause\n");
            sb.append(aiAnalysis.getLikelyRootCause()).append("\n\n");

            if (aiAnalysis.getConfirmedFacts() != null && !aiAnalysis.getConfirmedFacts().isBlank()) {
                sb.append("### 📋 Confirmed Facts vs Hypotheses\n");
                sb.append("**Confirmed Facts:**\n").append(aiAnalysis.getConfirmedFacts()).append("\n\n");
                sb.append("**Hypotheses:**\n").append(aiAnalysis.getHypotheses()).append("\n\n");
            }

            if (aiAnalysis.getRelevantErrors() != null && !aiAnalysis.getRelevantErrors().isBlank()) {
                sb.append("### ⚠️ Key Error Logs\n```text\n");
                sb.append(aiAnalysis.getRelevantErrors()).append("\n```\n\n");
            }

            sb.append("### 💡 Recommended Remediation\n");
            sb.append(aiAnalysis.getSuggestedSolution()).append("\n\n");

            sb.append("### 🧪 What to Test Next\n");
            sb.append(aiAnalysis.getNextStepsToTest()).append("\n\n");

            sb.append("_Note: AI suggests potential remediations; changes must be verified through automated health checks prior to merging._\n\n");
        }

        sb.append("## Execution Logs\n\n");
        for (JobLog logItem : logs) {
            sb.append("### Step: ").append(logItem.getStepName()).append("\n");
            sb.append("```text\n");
            sb.append(logItem.getOutput() != null && !logItem.getOutput().isBlank() ? logItem.getOutput() : "(No output captured)");
            sb.append("\n```\n\n");
        }

        return sb.toString();
    }

    public ReportDtos.ReportResponse mapToResponse(Report report) {
        if (report == null) return null;
        return ReportDtos.ReportResponse.builder()
                .id(report.getId())
                .jobId(report.getJob().getId())
                .summary(report.getSummary())
                .markdownContent(report.getMarkdownContent())
                .totalSteps(report.getTotalSteps())
                .passedSteps(report.getPassedSteps())
                .failedSteps(report.getFailedSteps())
                .generatedAt(report.getGeneratedAt())
                .build();
    }
}
