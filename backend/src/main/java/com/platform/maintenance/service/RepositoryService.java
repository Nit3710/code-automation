package com.platform.maintenance.service;

import com.platform.maintenance.common.ResourceNotFoundException;
import com.platform.maintenance.dto.RepositoryDtos;
import com.platform.maintenance.model.RepositoryConfiguration;
import com.platform.maintenance.model.RepositoryEntity;
import com.platform.maintenance.model.User;
import com.platform.maintenance.model.Workspace;
import com.platform.maintenance.model.enums.ProjectType;
import com.platform.maintenance.repository.RepositoryConfigurationRepository;
import com.platform.maintenance.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryEntityRepository repositoryEntityRepository;
    private final RepositoryConfigurationRepository configurationRepository;
    private final WorkspaceService workspaceService;
    private final ProjectDetectorService projectDetectorService;
    private final ExecutionEngineService executionEngineService;

    public List<RepositoryDtos.RepositoryResponse> getRepositories(Long workspaceId, User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        List<RepositoryEntity> repositories = repositoryEntityRepository.findByWorkspaceId(workspaceId);
        return repositories.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public RepositoryDtos.RepositoryResponse getRepository(Long workspaceId, Long repositoryId, User currentUser) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        RepositoryEntity repo = repositoryEntityRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + repositoryId));
        return mapToResponse(repo);
    }

    @Transactional
    public RepositoryDtos.RepositoryResponse connectRepository(Long workspaceId, User currentUser, RepositoryDtos.ConnectRepositoryRequest request) {
        Workspace workspace = workspaceService.validateWorkspaceAccess(workspaceId, currentUser);

        if (repositoryEntityRepository.existsByWorkspaceIdAndFullName(workspaceId, request.getFullName())) {
            throw new IllegalArgumentException("Repository " + request.getFullName() + " is already connected in this workspace.");
        }

        RepositoryEntity repo = RepositoryEntity.builder()
                .workspace(workspace)
                .name(request.getName())
                .fullName(request.getFullName())
                .cloneUrl(request.getCloneUrl())
                .defaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main")
                .githubRepoId(request.getGithubRepoId())
                .active(true)
                .build();
        repo = repositoryEntityRepository.save(repo);

        // Run Automatic Project Stack Detection on Connect!
        RepositoryDtos.ProjectDetectionResult detection = autoDetectRepository(repo);

        RepositoryConfiguration config = RepositoryConfiguration.builder()
                .repository(repo)
                .projectType(detection.getDetectedType())
                .installCommand(detection.getSuggestedInstallCommand())
                .buildCommand(detection.getSuggestedBuildCommand())
                .testCommand(detection.getSuggestedTestCommand())
                .timeoutSeconds(300)
                .scheduleEnabled(false)
                .build();
        config = configurationRepository.save(config);
        repo.setConfiguration(config);

        return mapToResponse(repo);
    }

    public RepositoryDtos.ProjectDetectionResult autoDetectRepository(RepositoryEntity repo) {
        String cloneUrl = repo.getCloneUrl();
        File localDir = new File(cloneUrl);

        if (localDir.exists() && localDir.isDirectory()) {
            return projectDetectorService.detect(localDir);
        }

        // Remote URL: perform a temporary shallow clone to inspect project files
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("repo-detect-" + repo.getId());
            String branch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
            String cloneCmd = "git clone --depth 1 --branch " + branch + " " + cloneUrl + " .";

            ExecutionEngineService.StepExecutionResult result =
                    executionEngineService.executeCommand(cloneCmd, tempDir.toFile(), 60);

            if (result.isSuccess()) {
                return projectDetectorService.detect(tempDir.toFile());
            } else {
                log.warn("Shallow clone failed during auto-detection: {}", result.getOutput());
            }
        } catch (Exception e) {
            log.warn("Error running auto-detection for repo {}", repo.getFullName(), e);
        } finally {
            if (tempDir != null && Files.exists(tempDir)) {
                deleteRecursively(tempDir.toFile());
            }
        }

        // Fallback default
        return RepositoryDtos.ProjectDetectionResult.builder()
                .detectedType(ProjectType.PYTHON)
                .suggestedInstallCommand("pip install -r requirements.txt")
                .suggestedBuildCommand("")
                .suggestedTestCommand("pytest || python -m unittest discover")
                .build();
    }

    @Transactional
    public RepositoryDtos.RepositoryResponse updateConfiguration(Long workspaceId, Long repositoryId, User currentUser, RepositoryDtos.UpdateConfigurationRequest request) {
        workspaceService.validateWorkspaceAccess(workspaceId, currentUser);
        RepositoryEntity repo = repositoryEntityRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + repositoryId));

        RepositoryConfiguration config = repo.getConfiguration();
        if (config == null) {
            config = RepositoryConfiguration.builder().repository(repo).build();
        }

        if (request.getProjectType() != null) config.setProjectType(request.getProjectType());
        if (request.getInstallCommand() != null) config.setInstallCommand(request.getInstallCommand());
        if (request.getBuildCommand() != null) config.setBuildCommand(request.getBuildCommand());
        if (request.getTestCommand() != null) config.setTestCommand(request.getTestCommand());
        if (request.getLintCommand() != null) config.setLintCommand(request.getLintCommand());
        if (request.getTimeoutSeconds() != null) config.setTimeoutSeconds(request.getTimeoutSeconds());
        if (request.getScheduleEnabled() != null) config.setScheduleEnabled(request.getScheduleEnabled());
        if (request.getCronExpression() != null) config.setCronExpression(request.getCronExpression());

        configurationRepository.save(config);
        repo.setConfiguration(config);

        return mapToResponse(repo);
    }

    public RepositoryDtos.RepositoryResponse mapToResponse(RepositoryEntity repo) {
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
