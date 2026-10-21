package com.platform.maintenance.repository;

import com.platform.maintenance.model.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {

    @Query("SELECT l FROM JobLog l WHERE l.job.id = :jobId ORDER BY l.stepOrder ASC")
    List<JobLog> findByJobIdOrderByStepOrderAsc(@Param("jobId") Long jobId);
}
