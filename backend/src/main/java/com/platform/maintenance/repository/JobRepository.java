package com.platform.maintenance.repository;

import com.platform.maintenance.model.Job;
import com.platform.maintenance.model.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    @Query("SELECT j FROM Job j JOIN j.repository r WHERE r.workspace.id = :workspaceId ORDER BY j.createdAt DESC")
    List<Job> findByWorkspaceIdOrderByCreatedAtDesc(@Param("workspaceId") Long workspaceId);

    List<Job> findByStatusIn(List<JobStatus> statuses);

    @Query("SELECT j FROM Job j JOIN j.repository r WHERE j.id = :jobId AND r.workspace.id = :workspaceId")
    Optional<Job> findByIdAndWorkspaceId(@Param("jobId") Long jobId, @Param("workspaceId") Long workspaceId);
}
