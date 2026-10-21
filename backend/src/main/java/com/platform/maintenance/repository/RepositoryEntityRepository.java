package com.platform.maintenance.repository;

import com.platform.maintenance.model.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, Long> {
    List<RepositoryEntity> findByWorkspaceId(Long workspaceId);
    Optional<RepositoryEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);
    boolean existsByWorkspaceIdAndFullName(Long workspaceId, String fullName);
}
