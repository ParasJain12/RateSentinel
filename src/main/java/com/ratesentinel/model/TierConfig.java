package com.ratesentinel.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tier_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TierConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tier_name", nullable = false, unique = true)
    private String tierName;

    @Column(name = "limit_count", nullable = false)
    private Integer limitCount;

    @Column(name = "window_seconds", nullable = false)
    private Integer windowSeconds;

    @Column(name = "algorithm", nullable = false,
            columnDefinition = "VARCHAR(50)")
    @Enumerated(EnumType.STRING)
    private AlgorithmType algorithm;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}