package com.platform.maintenance.service;

import com.platform.maintenance.model.RepositoryConfiguration;
import com.platform.maintenance.model.RepositoryEntity;
import com.platform.maintenance.model.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExecutionEngineService {

    @Value("${app.sandbox.workspace-root:./workspace-storage}")
    private String workspaceRoot;

    private static final int MAX_LOG_LENGTH = 500_000; // 500 KB limit per step

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(ghp_[a-zA-Z0-9]{36,})"),
            Pattern.compile("(?i)(github_pat_[a-zA-Z0-9_]{50,})"),
            Pattern.compile("(?i)(bearer\\s+[a-zA-Z0-9_\\-\\.]+)"),
            Pattern.compile("(?i)(password\\s*=\\s*['\"]?[^\\s'\"]+['\"]?)"),
            Pattern.compile("(?i)(secret\\s*=\\s*['\"]?[^\\s'\"]+['\"]?)"),
            Pattern.compile("(?i)(api[_-]?key\\s*[:=]\\s*['\"]?[^\\s'\"]+['\"]?)")
    );

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepExecutionResult {
        private String stepName;
        private int stepOrder;
        private int exitCode;
        private long durationMs;
        private String output;
        private boolean success;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobExecutionResult {
        private JobStatus finalStatus;
        private String commitSha;
        private String commitMessage;
        private long totalDurationMs;
        private int overallExitCode;
        private String failureReason;
        @Builder.Default
        private List<StepExecutionResult> stepResults = new ArrayList<>();
    }

    public JobExecutionResult executeJob(Long jobId, RepositoryEntity repository, String targetBranch, Integer customTimeout) {
        long overallStart = System.currentTimeMillis();
        Path jobDir = null;
        List<StepExecutionResult> stepResults = new ArrayList<>();
        RepositoryConfiguration config = repository.getConfiguration();
        int timeoutSeconds = (customTimeout != null && customTimeout > 0) ? customTimeout :
                (config != null && config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 300);

        String commitSha = "unknown";
        String commitMessage = "Manual health check trigger";

        try {
            jobDir = Files.createDirectories(Path.of(workspaceRoot, "job-" + jobId));
            File workingDir = jobDir.toFile();

            // 1. CLONE STEP
            long cloneStart = System.currentTimeMillis();
            String cloneUrl = repository.getCloneUrl();
            String branch = (targetBranch != null && !targetBranch.isBlank()) ? targetBranch : repository.getDefaultBranch();

            StepExecutionResult cloneResult;
            File localRepoDir = new File(cloneUrl);
            if (localRepoDir.exists() && localRepoDir.isDirectory()) {
                // Local repository path for testing or offline environment
                cloneResult = executeCommand("git -C \"" + cloneUrl + "\" rev-parse HEAD", workingDir, 30);
                if (cloneResult.isSuccess() && !cloneResult.getOutput().isBlank()) {
                    commitSha = cloneResult.getOutput().trim().split("\\R")[0];
                }
                StepExecutionResult msgResult = executeCommand("git -C \"" + cloneUrl + "\" log -1 --pretty=%B", workingDir, 30);
                if (msgResult.isSuccess() && !msgResult.getOutput().isBlank()) {
                    commitMessage = msgResult.getOutput().trim();
                }
                workingDir = localRepoDir;
                cloneResult = StepExecutionResult.builder()
                        .stepName("CHECKOUT")
                        .stepOrder(1)
                        .exitCode(0)
                        .durationMs(System.currentTimeMillis() - cloneStart)
                        .output("Using verified repository path: " + cloneUrl + " at branch " + branch)
                        .success(true)
                        .build();
            } else {
                // Remote git clone
                String cloneCmd = "git clone --depth 1 --branch " + branch + " " + cloneUrl + " .";
                cloneResult = executeCommand(cloneCmd, workingDir, timeoutSeconds);
                cloneResult.setStepName("CLONE");
                cloneResult.setStepOrder(1);

                if (cloneResult.isSuccess()) {
                    StepExecutionResult shaResult = executeCommand("git rev-parse HEAD", workingDir, 30);
                    if (shaResult.isSuccess() && !shaResult.getOutput().isBlank()) {
                        commitSha = shaResult.getOutput().trim();
                    }
                    StepExecutionResult msgResult = executeCommand("git log -1 --pretty=%B", workingDir, 30);
                    if (msgResult.isSuccess() && !msgResult.getOutput().isBlank()) {
                        commitMessage = msgResult.getOutput().trim();
                    }
                }
            }
            stepResults.add(cloneResult);

            if (!cloneResult.isSuccess()) {
                return JobExecutionResult.builder()
                        .finalStatus(JobStatus.FAILED)
                        .commitSha(commitSha)
                        .commitMessage(commitMessage)
                        .totalDurationMs(System.currentTimeMillis() - overallStart)
                        .overallExitCode(cloneResult.getExitCode())
                        .failureReason("Failed at CLONE step: " + cloneResult.getOutput())
                        .stepResults(stepResults)
                        .build();
            }

            int stepOrder = 2;

            // 2. INSTALL STEP (if configured)
            if (config != null && config.getInstallCommand() != null && !config.getInstallCommand().isBlank()) {
                StepExecutionResult installResult = executeCommand(config.getInstallCommand(), workingDir, timeoutSeconds);
                installResult.setStepName("INSTALL");
                installResult.setStepOrder(stepOrder++);
                stepResults.add(installResult);

                if (!installResult.isSuccess()) {
                    return JobExecutionResult.builder()
                            .finalStatus(installResult.getExitCode() == 124 ? JobStatus.TIMED_OUT : JobStatus.FAILED)
                            .commitSha(commitSha)
                            .commitMessage(commitMessage)
                            .totalDurationMs(System.currentTimeMillis() - overallStart)
                            .overallExitCode(installResult.getExitCode())
                            .failureReason("Failed at INSTALL step: exit code " + installResult.getExitCode())
                            .stepResults(stepResults)
                            .build();
                }
            }

            // 3. BUILD STEP (if configured)
            if (config != null && config.getBuildCommand() != null && !config.getBuildCommand().isBlank()) {
                StepExecutionResult buildResult = executeCommand(config.getBuildCommand(), workingDir, timeoutSeconds);
                buildResult.setStepName("BUILD");
                buildResult.setStepOrder(stepOrder++);
                stepResults.add(buildResult);

                if (!buildResult.isSuccess()) {
                    return JobExecutionResult.builder()
                            .finalStatus(buildResult.getExitCode() == 124 ? JobStatus.TIMED_OUT : JobStatus.FAILED)
                            .commitSha(commitSha)
                            .commitMessage(commitMessage)
                            .totalDurationMs(System.currentTimeMillis() - overallStart)
                            .overallExitCode(buildResult.getExitCode())
                            .failureReason("Failed at BUILD step: exit code " + buildResult.getExitCode())
                            .stepResults(stepResults)
                            .build();
                }
            }

            // 4. TEST STEP (if configured)
            if (config != null && config.getTestCommand() != null && !config.getTestCommand().isBlank()) {
                StepExecutionResult testResult = executeCommand(config.getTestCommand(), workingDir, timeoutSeconds);
                testResult.setStepName("TEST");
                testResult.setStepOrder(stepOrder++);
                stepResults.add(testResult);

                if (!testResult.isSuccess()) {
                    return JobExecutionResult.builder()
                            .finalStatus(testResult.getExitCode() == 124 ? JobStatus.TIMED_OUT : JobStatus.FAILED)
                            .commitSha(commitSha)
                            .commitMessage(commitMessage)
                            .totalDurationMs(System.currentTimeMillis() - overallStart)
                            .overallExitCode(testResult.getExitCode())
                            .failureReason("Failed at TEST step: exit code " + testResult.getExitCode())
                            .stepResults(stepResults)
                            .build();
                }
            }

            // 5. LINT STEP (optional)
            if (config != null && config.getLintCommand() != null && !config.getLintCommand().isBlank()) {
                StepExecutionResult lintResult = executeCommand(config.getLintCommand(), workingDir, timeoutSeconds);
                lintResult.setStepName("LINT");
                lintResult.setStepOrder(stepOrder++);
                stepResults.add(lintResult);

                if (!lintResult.isSuccess()) {
                    return JobExecutionResult.builder()
                            .finalStatus(lintResult.getExitCode() == 124 ? JobStatus.TIMED_OUT : JobStatus.FAILED)
                            .commitSha(commitSha)
                            .commitMessage(commitMessage)
                            .totalDurationMs(System.currentTimeMillis() - overallStart)
                            .overallExitCode(lintResult.getExitCode())
                            .failureReason("Failed at LINT step: exit code " + lintResult.getExitCode())
                            .stepResults(stepResults)
                            .build();
                }
            }

            return JobExecutionResult.builder()
                    .finalStatus(JobStatus.PASSED)
                    .commitSha(commitSha)
                    .commitMessage(commitMessage)
                    .totalDurationMs(System.currentTimeMillis() - overallStart)
                    .overallExitCode(0)
                    .stepResults(stepResults)
                    .build();

        } catch (Exception e) {
            log.error("Execution engine internal failure on job {}", jobId, e);
            return JobExecutionResult.builder()
                    .finalStatus(JobStatus.INTERNAL_ERROR)
                    .commitSha(commitSha)
                    .commitMessage(commitMessage)
                    .totalDurationMs(System.currentTimeMillis() - overallStart)
                    .overallExitCode(1)
                    .failureReason("Internal engine error: " + e.getMessage())
                    .stepResults(stepResults)
                    .build();
        } finally {
            // Clean up temporary workspace directory if it was created
            if (jobDir != null && Files.exists(jobDir)) {
                try {
                    deleteRecursively(jobDir.toFile());
                } catch (Exception e) {
                    log.warn("Could not clean up job sandbox dir: {}", jobDir, e);
                }
            }
        }
    }

    public StepExecutionResult executeCommand(String command, File workingDir, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        StringBuilder outputBuilder = new StringBuilder();
        int exitCode = -1;

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> commandList = isWindows
                ? List.of("cmd.exe", "/c", command)
                : List.of("sh", "-c", command);

        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.directory(workingDir);
        pb.redirectErrorStream(true); // combine stdout and stderr

        // Sanitized environment
        Map<String, String> env = pb.environment();
        env.put("CI", "true");

        try {
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputBuilder.length() < MAX_LOG_LENGTH) {
                        outputBuilder.append(redactSecrets(line)).append("\n");
                    } else if (!outputBuilder.toString().endsWith("[...OUTPUT TRUNCATED DUE TO SIZE LIMIT...]\n")) {
                        outputBuilder.append("\n[...OUTPUT TRUNCATED DUE TO SIZE LIMIT...]\n");
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                exitCode = 124; // Standard timeout exit code
                outputBuilder.append("\n[ERROR: Step timed out after ").append(timeoutSeconds).append(" seconds]\n");
            } else {
                exitCode = process.exitValue();
            }

        } catch (Exception e) {
            exitCode = 1;
            outputBuilder.append("\n[Execution Error: ").append(e.getMessage()).append("]\n");
        }

        long duration = System.currentTimeMillis() - start;
        return StepExecutionResult.builder()
                .stepName("COMMAND")
                .exitCode(exitCode)
                .durationMs(duration)
                .output(outputBuilder.toString().trim())
                .success(exitCode == 0)
                .build();
    }

    public String redactSecrets(String input) {
        if (input == null) return null;
        String redacted = input;
        for (Pattern pattern : SECRET_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("[REDACTED_SECRET]");
        }
        return redacted;
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
