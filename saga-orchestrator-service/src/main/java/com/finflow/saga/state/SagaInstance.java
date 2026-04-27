package com.finflow.saga.state;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Persistent state for one saga instance. Persisted to database so the orchestrator can recover
 * after a restart without losing saga progress.
 *
 * <p>One row per saga execution -- never reused. Completed sagas are retained for audit purposes.
 */
@Entity
@Table(name = "saga_instances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {

    @Id
    private String sagaId;

    @Column(name = "saga_type", nullable = false)
    private String sagaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaState state;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "keycloak_user_id")
    private String keycloakUserId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failure_step")
    private Integer failureStep;

    @Column(name = "compensation_reason", columnDefinition = "TEXT")
    private String compensationReason;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public void markCompleted() {
        this.state = SagaState.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason, int failedAtStep) {
        this.state = SagaState.FAILED;
        this.failureReason = reason;
        this.failureStep = failedAtStep;
    }
}
