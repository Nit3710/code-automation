package com.platform.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.maintenance.dto.ReportDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.provider:gemini}")
    private String provider;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportDtos.AiAnalysisDto diagnoseFailure(String errorLogs, String repoName) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("mock")) {
            return null; // Fall back to rule-based engine
        }

        try {
            String prompt = """
                    You are an expert autonomous software reliability engineer.
                    Analyze this build and test failure log for repository '%s'.
                    
                    Return ONLY a valid JSON object (no markdown, no backticks) with these exact keys:
                    {
                      "failureSummary": "one-sentence summary of the failure",
                      "relevantErrors": "exact key lines from the log that caused the failure",
                      "likelyRootCause": "detailed technical explanation of why it failed",
                      "confirmedFacts": "numbered list of confirmed facts from the log",
                      "hypotheses": "numbered list of probable hypotheses",
                      "suggestedSolution": "step-by-step technical fix",
                      "nextStepsToTest": "exact test command to run to verify the fix"
                    }
                    
                    LOGS:
                    %s
                    """.formatted(repoName, errorLogs.length() > 6000 ? errorLogs.substring(0, 6000) : errorLogs);

            String geminiResponse = callGeminiApi(prompt);
            if (geminiResponse != null && !geminiResponse.isBlank()) {
                // Strip markdown backticks if present
                String cleanJson = geminiResponse.trim();
                if (cleanJson.startsWith("```json")) {
                    cleanJson = cleanJson.substring(7);
                } else if (cleanJson.startsWith("```")) {
                    cleanJson = cleanJson.substring(3);
                }
                if (cleanJson.endsWith("```")) {
                    cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                }
                cleanJson = cleanJson.trim();

                JsonNode root = objectMapper.readTree(cleanJson);
                return ReportDtos.AiAnalysisDto.builder()
                        .failureSummary(root.path("failureSummary").asText("Failure detected in build/test run."))
                        .relevantErrors(root.path("relevantErrors").asText(""))
                        .likelyRootCause(root.path("likelyRootCause").asText("Command failed with non-zero exit code."))
                        .confirmedFacts(root.path("confirmedFacts").asText(""))
                        .hypotheses(root.path("hypotheses").asText(""))
                        .suggestedSolution(root.path("suggestedSolution").asText(""))
                        .nextStepsToTest(root.path("nextStepsToTest").asText(""))
                        .provider("gemini-1.5-flash")
                        .tokensUsed(350)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Gemini diagnostic call failed, falling back to local diagnostic analyzer: {}", e.getMessage());
        }

        return null;
    }

    public String generatePatch(String filename, String currentContent, String errorLogs) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        try {
            String prompt = """
                    You are an autonomous bugfix coding agent.
                    A test failed with these logs:
                    %s
                    
                    Here is the current content of the file '%s':
                    ```
                    %s
                    ```
                    
                    Provide ONLY the complete updated, working code for '%s' that fixes the bug and satisfies tests.
                    Do not add conversational preamble. Return only the raw code.
                    """.formatted(
                            errorLogs.length() > 4000 ? errorLogs.substring(0, 4000) : errorLogs,
                            filename,
                            currentContent.length() > 8000 ? currentContent.substring(0, 8000) : currentContent,
                            filename
            );

            String response = callGeminiApi(prompt);
            if (response != null && !response.isBlank()) {
                String cleanCode = response.trim();
                if (cleanCode.startsWith("```") && cleanCode.contains("\n")) {
                    cleanCode = cleanCode.substring(cleanCode.indexOf("\n") + 1);
                }
                if (cleanCode.endsWith("```")) {
                    cleanCode = cleanCode.substring(0, cleanCode.length() - 3);
                }
                return cleanCode.trim();
            }
        } catch (Exception e) {
            log.warn("Gemini code patch generation failed, using local rule fix: {}", e.getMessage());
        }

        return null;
    }

    private String callGeminiApi(String promptText) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey); // Supports both query param and Bearer token formats

        Map<String, Object> part = new HashMap<>();
        part.put("text", promptText);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                    return textNode.asText();
                }
            }
        } catch (Exception e) {
            log.warn("Gemini API call returned error: {}", e.getMessage());
        }

        return null;
    }
}
