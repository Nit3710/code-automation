package com.platform.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.maintenance.dto.JobDtos;
import com.platform.maintenance.dto.RepositoryDtos;
import com.platform.maintenance.model.RepositoryConfiguration;
import com.platform.maintenance.model.RepositoryEntity;
import com.platform.maintenance.model.User;
import com.platform.maintenance.model.Workspace;
import com.platform.maintenance.model.enums.ProjectType;
import com.platform.maintenance.repository.RepositoryConfigurationRepository;
import com.platform.maintenance.repository.RepositoryEntityRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubIntegrationService {

    private final RepositoryEntityRepository repositoryEntityRepository;
    private final RepositoryConfigurationRepository configurationRepository;
    private final WorkspaceService workspaceService;
    private final JobService jobService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.github.token:}")
    private String githubToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitHubRepoItem {
        private String name;
        private String fullName;
        private String description;
        private String cloneUrl;
        private String defaultBranch;
        private String language;
        private boolean isPrivate;
        private int stargazersCount;
        private boolean alreadyConnected;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRepoRequest {
        private String fullName; // e.g. "org/repo"
        private String name;
        private String cloneUrl;
        private String defaultBranch;
        private String githubToken;
    }

    private String getEffectiveGitHubToken(String customToken) {
        if (customToken != null && !customToken.isBlank()) {
            return customToken.trim();
        }
        if (githubToken != null && !githubToken.isBlank() && !githubToken.contains("YOUR_") && !githubToken.equals("placeholder-token")) {
            return githubToken.trim();
        }
        return null;
    }

    private String getAuthHeader(String customToken) {
        String token = getEffectiveGitHubToken(customToken);
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.startsWith("ghp_") ? "token " + token : "Bearer " + token;
    }

    public List<GitHubRepoItem> fetchUserRepositories(String username, Long workspaceId, User currentUser, String customToken) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        List<GitHubRepoItem> repoList = new ArrayList<>();

        try {
            String url = "https://api.github.com/users/" + username + "/repos?sort=updated&per_page=50";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "AutoMaintain-Platform");
            headers.set("Accept", "application/vnd.github.v3+json");

            String authHeader = getAuthHeader(customToken);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode arrayNode = objectMapper.readTree(response.getBody());
                for (JsonNode item : arrayNode) {
                    String fullName = item.path("full_name").asText();
                    boolean exists = repositoryEntityRepository.existsByWorkspaceIdAndFullName(workspaceId, fullName);

                    repoList.add(GitHubRepoItem.builder()
                            .name(item.path("name").asText())
                            .fullName(fullName)
                            .description(item.path("description").asText(""))
                            .cloneUrl(item.path("clone_url").asText())
                            .defaultBranch(item.path("default_branch").asText("main"))
                            .language(item.path("language").asText("Unknown"))
                            .isPrivate(item.path("private").asBoolean(false))
                            .stargazersCount(item.path("stargazers_count").asInt(0))
                            .alreadyConnected(exists)
                            .build());
                }
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("GitHub API error for {}: status={}, body={}", username, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.FORBIDDEN || e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new IllegalArgumentException("GitHub rate limit reached. Please provide your GitHub Personal Access Token (PAT) with 'repo' scope to continue.");
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IllegalArgumentException("Invalid GitHub token. Please verify your Personal Access Token.");
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IllegalArgumentException("GitHub user or organization '" + username + "' was not found.");
            }
            throw new IllegalArgumentException("GitHub API error (" + e.getStatusCode() + "): " + e.getStatusText());
        } catch (Exception e) {
            log.error("Failed to fetch repositories from GitHub for user: {}", username, e);
            throw new IllegalArgumentException("Could not fetch repositories for '" + username + "': " + e.getMessage());
        }

        return repoList;
    }

    @Transactional
    public RepositoryDtos.RepositoryResponse importAndAutoStart(
            Long workspaceId, User currentUser, ImportRepoRequest request) {

        Workspace workspace = workspaceService.validateWorkspaceAccess(workspaceId, currentUser);

        if (repositoryEntityRepository.existsByWorkspaceIdAndFullName(workspaceId, request.getFullName())) {
            throw new IllegalArgumentException("Repository " + request.getFullName() + " is already connected in this workspace.");
        }

        String userToken = (request.getGithubToken() != null && !request.getGithubToken().isBlank())
                ? request.getGithubToken().trim()
                : null;

        String cloneUrl = request.getCloneUrl();
        if (userToken != null) {
            cloneUrl = "https://x-access-token:" + userToken + "@github.com/" + request.getFullName() + ".git";
        } else if (cloneUrl == null || cloneUrl.isBlank()) {
            cloneUrl = "https://github.com/" + request.getFullName() + ".git";
        }

        RepositoryEntity repo = RepositoryEntity.builder()
                .workspace(workspace)
                .name(request.getName() != null && !request.getName().isBlank() ? request.getName() : request.getFullName())
                .fullName(request.getFullName())
                .cloneUrl(cloneUrl)
                .defaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main")
                .active(true)
                .build();
        repo = repositoryEntityRepository.save(repo);

        // 1. Instant GitHub Tree Inspection for 100% Zero-Touch Stack & Command Detection
        RepositoryConfiguration config = inspectAndCreateConfigFromGitHub(repo, userToken);
        config = configurationRepository.save(config);
        repo.setConfiguration(config);

        // 2. Automatically Trigger First Health Check Immediately!
        JobDtos.JobResponse job = jobService.triggerHealthCheck(workspaceId, repo.getId(), currentUser, null);
        log.info("Repository {} imported and first health check job #{} triggered automatically!", repo.getFullName(), job.getId());

        return mapToResponse(repo);
    }

    private RepositoryConfiguration inspectAndCreateConfigFromGitHub(RepositoryEntity repo, String customToken) {
        ProjectType detectedType = ProjectType.PYTHON;
        String installCmd = "";
        String buildCmd = "";
        String testCmd = "python -m unittest discover";

        try {
            String url = "https://api.github.com/repos/" + repo.getFullName() + "/contents";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "AutoMaintain-Platform");
            headers.set("Accept", "application/vnd.github.v3+json");

            String authHeader = getAuthHeader(customToken);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode arrayNode = objectMapper.readTree(response.getBody());
                List<String> fileNames = new ArrayList<>();
                for (JsonNode item : arrayNode) {
                    fileNames.add(item.path("name").asText().toLowerCase());
                }

                if (fileNames.contains("pom.xml")) {
                    detectedType = ProjectType.JAVA_MAVEN;
                    installCmd = "mvn dependency:resolve";
                    buildCmd = "mvn clean compile";
                    testCmd = "mvn test";
                } else if (fileNames.contains("build.gradle") || fileNames.contains("build.gradle.kts")) {
                    detectedType = ProjectType.JAVA_GRADLE;
                    installCmd = "./gradlew dependencies";
                    buildCmd = "./gradlew classes";
                    testCmd = "./gradlew test";
                } else if (fileNames.contains("package.json")) {
                    if (fileNames.stream().anyMatch(f -> f.startsWith("next.config"))) {
                        detectedType = ProjectType.NEXT_JS;
                    } else {
                        detectedType = ProjectType.GENERIC_NODE;
                    }
                    installCmd = "npm install";
                    buildCmd = "npm run build";
                    testCmd = "npm test";
                } else if (fileNames.contains("requirements.txt") || fileNames.stream().anyMatch(f -> f.endsWith(".py"))) {
                    detectedType = ProjectType.PYTHON;
                    installCmd = fileNames.contains("requirements.txt") ? "pip install -r requirements.txt" : "";
                    buildCmd = "";

                    if (fileNames.contains("test_agent.py")) {
                        testCmd = "python test_agent.py";
                    } else if (fileNames.contains("mock_test.py")) {
                        testCmd = "python mock_test.py";
                    } else if (fileNames.contains("test_commands.py")) {
                        testCmd = "python test_commands.py";
                    } else {
                        testCmd = "pytest || python -m unittest discover";
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not inspect GitHub contents for {}, falling back to Python preset", repo.getFullName(), e);
        }

        return RepositoryConfiguration.builder()
                .repository(repo)
                .projectType(detectedType)
                .installCommand(installCmd)
                .buildCommand(buildCmd)
                .testCommand(testCmd)
                .timeoutSeconds(300)
                .scheduleEnabled(false)
                .build();
    }

    private RepositoryDtos.RepositoryResponse mapToResponse(RepositoryEntity repo) {
        RepositoryDtos.ConfigurationDto configDto = null;
        if (repo.getConfiguration() != null) {
            RepositoryConfiguration c = repo.getConfiguration();
            configDto = RepositoryDtos.ConfigurationDto.builder()
                    .id(c.getId())
                    .projectType(c.getProjectType())
                    .installCommand(c.getInstallCommand())
                    .buildCommand(c.getBuildCommand())
                    .testCommand(c.getTestCommand())
                    .lintCommand(c.getLintCommand())
                    .timeoutSeconds(c.getTimeoutSeconds())
                    .scheduleEnabled(c.getScheduleEnabled())
                    .cronExpression(c.getCronExpression())
                    .build();
        }

        return RepositoryDtos.RepositoryResponse.builder()
                .id(repo.getId())
                .workspaceId(repo.getWorkspace().getId())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .cloneUrl(repo.getCloneUrl())
                .defaultBranch(repo.getDefaultBranch())
                .active(repo.getActive())
                .configuration(configDto)
                .createdAt(repo.getCreatedAt())
                .build();
    }
}
