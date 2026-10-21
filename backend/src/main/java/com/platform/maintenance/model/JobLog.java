package com.platform.maintenance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable = false)
    private String stepName; // CLONE, DETECT, BUILD, TEST, LINT

    @Column(nullable = false)
    private Integer stepOrder;

    private Integer exitCode;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String output;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
