package com.platform.maintenance.service;

import com.platform.maintenance.dto.JobDtos;
import com.platform.maintenance.model.Job;
import com.platform.maintenance.model.RepositoryConfiguration;
import com.platform.maintenance.model.RepositoryEntity;
import com.platform.maintenance.model.enums.JobStatus;
import com.platform.maintenance.model.enums.TriggerType;
import com.platform.maintenance.repository.JobRepository;
import com.platform.maintenance.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledMonitoringService {

    private final RepositoryEntityRepository repositoryEntityRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;

    /**
     * Runs automatically in the background every day at 02:00 AM.
     * Can also be adjusted via application.yml cron settings.
     */
    @Scheduled(cron = "${app.monitoring.daily-cron:0 0 2 * * ?}")
    @Transactional
    public void executeDailyScheduledMonitoring() {
        log.info("Starting daily autonomous repository monitoring job...");
        List<RepositoryEntity> activeRepos = repositoryEntityRepository.findAll();

        for (RepositoryEntity repo : activeRepos) {
            if (!repo.getActive()) continue;

            RepositoryConfiguration config = repo.getConfiguration();
            // If scheduled monitoring is enabled (or active by default)
            boolean shouldRun = config == null || config.getScheduleEnabled() == null || config.getScheduleEnabled();

            if (shouldRun) {
                triggerAutonomousCheck(repo);
            }
        }
    }

    /**
     * Periodic safety polling every 30 minutes to catch any scheduled runs
     * and recover any hung/timed-out jobs.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupAndMonitor() {
        // Recover jobs that have been RUNNING for more than 15 minutes without progress
        List<Job> runningJobs = jobRepository.findByStatusIn(List.of(JobStatus.RUNNING));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);

        for (Job job : runningJobs) {
            if (job.getStartedAt() != null && job.getStartedAt().isBefore(cutoff)) {
                log.warn("Job #{} timed out due to worker silence. Marking TIMED_OUT", job.getId());
                job.setStatus(JobStatus.TIMED_OUT);
                job.setFinishedAt(LocalDateTime.now());
                job.setFailureReason("Execution exceeded maximum duration threshold (15 minutes).");
                jobRepository.save(job);
            }
        }
    }

    private void triggerAutonomousCheck(RepositoryEntity repo) {
        try {
            // Check if there is already an active job running to prevent duplicate overlap
            List<Job> recentJobs = jobRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            if (!recentJobs.isEmpty()) {
                Job latest = recentJobs.get(0);
                if (latest.getStatus() == JobStatus.QUEUED || latest.getStatus() == JobStatus.RUNNING) {
                    log.info("Skipping scheduled run for {} - existing job #{} is already in progress.", repo.getFullName(), latest.getId());
                    return;
                }
            }

            log.info("Triggering scheduled autonomous health check for {}", repo.getFullName());
            Job job = Job.builder()
                    .repository(repo)
                    .status(JobStatus.QUEUED)
                    .triggerType(TriggerType.SCHEDULED)
                    .branch(repo.getDefaultBranch())
                    .build();
            job = jobRepository.save(job);

            // Execute in background
            jobService.runJobAsync(job.getId());

        } catch (Exception e) {
            log.error("Failed to trigger scheduled run for repo {}", repo.getFullName(), e);
        }
    }
}
