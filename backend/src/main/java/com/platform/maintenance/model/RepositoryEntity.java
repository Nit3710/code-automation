package com.platform.maintenance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String fullName; // e.g. "owner/repo"

    @Column(nullable = false)
    private String cloneUrl;

    @Builder.Default
    @Column(nullable = false)
    private String defaultBranch = "main";

    private String githubRepoId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @OneToOne(mappedBy = "repository", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private RepositoryConfiguration configuration;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
