package com.platform.maintenance;

import com.platform.maintenance.model.*;
import com.platform.maintenance.model.enums.JobStatus;
import com.platform.maintenance.model.enums.TriggerType;
import com.platform.maintenance.repository.ReportRepository;
import com.platform.maintenance.service.ReportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportServiceTest {

    private final ReportRepository reportRepository = Mockito.mock(ReportRepository.class);
    private final ReportService reportService = new ReportService(reportRepository);

    @Test
    void testBuildMarkdownReport() {
        RepositoryEntity repo = RepositoryEntity.builder()
                .id(1L)
                .name("demo-service")
                .fullName("myorg/demo-service")
                .cloneUrl("https://github.com/myorg/demo-service.git")
                .build();

        Job job = Job.builder()
                .id(101L)
                .repository(repo)
                .status(JobStatus.PASSED)
                .triggerType(TriggerType.MANUAL)
                .branch("main")
                .commitSha("a1b2c3d4e5f6")
                .startedAt(LocalDateTime.now())
                .durationMs(4500L)
                .exitCode(0)
                .build();

        JobLog log1 = JobLog.builder()
                .stepName("BUILD")
                .stepOrder(1)
                .exitCode(0)
                .durationMs(2000L)
                .output("Build SUCCESS")
                .build();

        JobLog log2 = JobLog.builder()
                .stepName("TEST")
                .stepOrder(2)
                .exitCode(0)
                .durationMs(2500L)
                .output("Tests passed: 12, Failures: 0")
                .build();

        String markdown = reportService.buildMarkdownReport(job, List.of(log1, log2), null, 2, 2);

        assertTrue(markdown.contains("Health Check Report: myorg/demo-service"));
        assertTrue(markdown.contains("✅ PASSED"));
        assertTrue(markdown.contains("a1b2c3d4e5f6"));
        assertTrue(markdown.contains("Build SUCCESS"));
        assertTrue(markdown.contains("Tests passed: 12"));
    }
}
