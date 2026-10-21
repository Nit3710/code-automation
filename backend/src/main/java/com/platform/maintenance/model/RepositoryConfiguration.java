package com.platform.maintenance.model;

import com.platform.maintenance.model.enums.ProjectType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "repository_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, unique = true)
    private RepositoryEntity repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectType projectType = ProjectType.UNKNOWN;

    private String installCommand;

    @Column(nullable = false)
    private String buildCommand;

    @Column(nullable = false)
    private String testCommand;

    private String lintCommand;

    @Builder.Default
    @Column(nullable = false)
    private Integer timeoutSeconds = 300;

    @Builder.Default
    @Column(nullable = false)
    private Boolean scheduleEnabled = false;

    private String cronExpression; // e.g. "0 0 * * *" (daily)

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
