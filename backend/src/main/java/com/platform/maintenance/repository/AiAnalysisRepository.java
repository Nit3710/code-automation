package com.platform.maintenance.repository;

import com.platform.maintenance.model.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    @Query("SELECT a FROM AiAnalysis a WHERE a.job.id = :jobId")
    Optional<AiAnalysis> findByJobId(@Param("jobId") Long jobId);
}
