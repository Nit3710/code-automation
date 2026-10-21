package com.platform.maintenance.repository;

import com.platform.maintenance.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @Query("SELECT w FROM Workspace w JOIN WorkspaceMember wm ON w.id = wm.workspace.id WHERE wm.user.id = :userId")
    List<Workspace> findWorkspacesByUserId(@Param("userId") Long userId);
}
