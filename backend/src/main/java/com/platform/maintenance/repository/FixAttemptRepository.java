package com.platform.maintenance.repository;

import com.platform.maintenance.model.FixAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FixAttemptRepository extends JpaRepository<FixAttempt, Long> {

    @Query("SELECT f FROM FixAttempt f WHERE f.job.id = :jobId ORDER BY f.createdAt DESC")
    List<FixAttempt> findByJobIdOrderByCreatedAtDesc(@Param("jobId") Long jobId);

    @Query("SELECT f FROM FixAttempt f WHERE f.job.id = :jobId ORDER BY f.createdAt DESC")
    List<FixAttempt> findFixAttempts(@Param("jobId") Long jobId);

    default Optional<FixAttempt> findFirstByJobIdOrderByCreatedAtDesc(Long jobId) {
        List<FixAttempt> list = findByJobIdOrderByCreatedAtDesc(jobId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
