package com.platform.maintenance.repository;

import com.platform.maintenance.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("SELECT r FROM Report r WHERE r.job.id = :jobId")
    Optional<Report> findByJobId(@Param("jobId") Long jobId);
}
