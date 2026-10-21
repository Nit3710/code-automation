package com.platform.maintenance.repository;

import com.platform.maintenance.model.RepositoryConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositoryConfigurationRepository extends JpaRepository<RepositoryConfiguration, Long> {
    Optional<RepositoryConfiguration> findByRepositoryId(Long repositoryId);
}
