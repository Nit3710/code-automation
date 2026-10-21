package com.platform.maintenance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Column(nullable = false, length = 1000)
    private String failureSummary;

    @Column(columnDefinition = "TEXT")
    private String relevantErrors;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String likelyRootCause;

    @Column(columnDefinition = "TEXT")
    private String confirmedFacts;

    @Column(columnDefinition = "TEXT")
    private String hypotheses;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String suggestedSolution;

    @Column(columnDefinition = "TEXT")
    private String nextStepsToTest;

    private String provider; // e.g. "gemini", "mock", "openai"

    private Integer tokensUsed;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
