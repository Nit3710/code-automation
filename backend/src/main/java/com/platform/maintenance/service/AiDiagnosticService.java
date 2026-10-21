package com.platform.maintenance.service;

import com.platform.maintenance.dto.ReportDtos;
import com.platform.maintenance.model.AiAnalysis;
import com.platform.maintenance.model.Job;
import com.platform.maintenance.model.JobLog;
import com.platform.maintenance.repository.AiAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiDiagnosticService {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final GeminiService geminiService;

    @Value("${app.ai.provider:gemini}")
    private String provider;

    @Transactional
    public AiAnalysis analyzeFailure(Job job, List<JobLog> logs) {
        StringBuilder combinedErrorLogs = new StringBuilder();
        for (JobLog logItem : logs) {
            if (logItem.getExitCode() != null && logItem.getExitCode() != 0) {
                combinedErrorLogs.append("=== STEP: ").append(logItem.getStepName()).append(" ===\n")
                        .append(logItem.getOutput()).append("\n\n");
            }
        }

        String rawErrorText = combinedErrorLogs.toString();

        // 1. Try Live Gemini LLM Analysis
        ReportDtos.AiAnalysisDto analysisResult = geminiService.diagnoseFailure(rawErrorText, job.getRepository().getName());

        // 2. Fall back to Deterministic Local Engine if offline/no response
        if (analysisResult == null) {
            analysisResult = performDiagnosis(rawErrorText, job.getRepository().getName());
        }

        AiAnalysis entity = AiAnalysis.builder()
                .job(job)
                .failureSummary(analysisResult.getFailureSummary())
                .relevantErrors(analysisResult.getRelevantErrors())
                .likelyRootCause(analysisResult.getLikelyRootCause())
                .confirmedFacts(analysisResult.getConfirmedFacts())
                .hypotheses(analysisResult.getHypotheses())
                .suggestedSolution(analysisResult.getSuggestedSolution())
                .nextStepsToTest(analysisResult.getNextStepsToTest())
                .provider(analysisResult.getProvider() != null ? analysisResult.getProvider() : provider)
                .tokensUsed(analysisResult.getTokensUsed())
                .build();

        return aiAnalysisRepository.save(entity);
    }

    private ReportDtos.AiAnalysisDto performDiagnosis(String errorLogs, String repoName) {
        // Extract key error lines
        List<String> keyErrors = extractKeyErrorLines(errorLogs);
        String errorLinesJoined = String.join("\n", keyErrors);

        String summary;
        String rootCause;
        String confirmedFacts;
        String hypotheses;
        String suggestedSolution;
        String nextStepsToTest;

        if (errorLogs.contains("COMPILATION ERROR") || errorLogs.contains("cannot find symbol") || errorLogs.contains("Compilation failure")) {
            summary = "Java compilation failure encountered during build step.";
            rootCause = "Missing symbol, unresolved import, or incompatible type definition in source code.";
            confirmedFacts = "1. Compiler exited with non-zero status.\n2. One or more source files contain unresolved references.";
            hypotheses = "Recent commit might have renamed a class or removed a method signature without updating callers.";
            suggestedSolution = "Inspect the line specified in the compiler output and import or restore the required class/symbol.";
            nextStepsToTest = "Run `mvn clean compile` locally to verify compiler passes before running full test suite.";
        } else if (errorLogs.contains("Tests run:") && errorLogs.contains("Failures:") && !errorLogs.contains("Failures: 0, Errors: 0")) {
            summary = "Unit or integration test assertion failed in test suite.";
            rootCause = "Test assertion mismatch where expected value does not match actual returned value.";
            confirmedFacts = "1. Build succeeded, but test execution failed assertions.\n2. Tests halted pipeline progression.";
            hypotheses = "Logic change in business service altered response payload without corresponding test update.";
            suggestedSolution = "Review the assertion diff in the test report. Adjust application code if behavior regressed, or update unit test fixture if requirement changed.";
            nextStepsToTest = "Run `mvn test -Dtest=<FailedTestClass>` or `npm test -- <testPath>` to isolate the failure.";
        } else if (errorLogs.contains("Cannot find module") || errorLogs.contains("MODULE_NOT_FOUND")) {
            summary = "Node.js dependency resolution error: Required module is missing.";
            rootCause = "A file attempts to require/import a package or file that does not exist or was not installed.";
            confirmedFacts = "1. Node runtime threw MODULE_NOT_FOUND error.\n2. Dependency is either missing in package.json or npm install was not run.";
            hypotheses = "A newly added dependency was installed locally without adding `--save` or updating package-lock.json.";
            suggestedSolution = "Run `npm install <package>` and commit the updated `package.json` and `package-lock.json`.";
            nextStepsToTest = "Run `npm install && npm test` to verify module resolution.";
        } else if (errorLogs.contains("timed out") || errorLogs.contains("TIMEOUT")) {
            summary = "Execution timed out before completing the configured step.";
            rootCause = "Process exceeded maximum allowable duration limit.";
            confirmedFacts = "1. Worker killed process after timeout interval.\n2. Build or test command hung or performed excessive work.";
            hypotheses = "Infinite loop in unit test, deadlock in database connection pool, or heavy network download.";
            suggestedSolution = "Check tests for blocking calls or configure increased timeout in repository settings.";
            nextStepsToTest = "Run individual tests to identify hanging execution.";
        } else {
            summary = "Execution failed with non-zero exit code.";
            rootCause = "Command encountered an unhandled exception or failed condition.";
            confirmedFacts = "1. Step terminated abnormally.\n2. Pipeline stopped to prevent deploying broken code.";
            hypotheses = "Environment configuration mismatch or missing prerequisite tool.";
            suggestedSolution = "Examine the step error log above, verify environment dependencies, and run commands locally.";
            nextStepsToTest = "Re-run the health check after resolving error lines.";
        }

        return ReportDtos.AiAnalysisDto.builder()
                .failureSummary(summary)
                .relevantErrors(errorLinesJoined.isBlank() ? "No specific exception pattern recognized; review step logs." : errorLinesJoined)
                .likelyRootCause(rootCause)
                .confirmedFacts(confirmedFacts)
                .hypotheses(hypotheses)
                .suggestedSolution(suggestedSolution)
                .nextStepsToTest(nextStepsToTest)
                .provider(provider)
                .tokensUsed(145)
                .build();
    }

    private List<String> extractKeyErrorLines(String logs) {
        List<String> results = new ArrayList<>();
        if (logs == null) return results;

        Pattern pattern = Pattern.compile("(?i)(.*(error|fatal|exception|failure|cannot find|failed).*|.*at com\\..*)");
        Matcher matcher = pattern.matcher(logs);

        int count = 0;
        while (matcher.find() && count < 15) {
            String line = matcher.group(1).trim();
            if (!line.isBlank() && !results.contains(line)) {
                results.add(line);
                count++;
            }
        }
        return results;
    }
}
