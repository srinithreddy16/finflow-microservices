package com.finflow.saga.controller;

import com.finflow.common.util.IdGenerator;
import com.finflow.saga.orchestrator.OnboardingSagaOrchestrator;
import com.finflow.saga.orchestrator.OnboardingSagaOrchestrator.OnboardingRequest;
import com.finflow.saga.state.SagaInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the Saga Orchestrator.
 *
 * <p>Primary endpoint: POST /api/saga/onboarding This starts the account onboarding saga which
 * runs asynchronously. The response is HTTP 202 ACCEPTED (not 201 CREATED) because the saga is not
 * complete when the response is returned -- it runs in the background via RabbitMQ message passing.
 *
 * <p>Clients should poll GET /api/saga/{sagaId} to check completion.
 */
@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Saga Orchestrator")
public class SagaController {

    private final OnboardingSagaOrchestrator sagaOrchestrator;

    public record SagaStatusDto(
            String sagaId,
            String sagaType,
            String state,
            int currentStep,
            String email,
            String accountId,
            String keycloakUserId,
            String failureReason,
            String correlationId,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {}

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start account onboarding saga")
    public SagaStatusDto startOnboardingSaga(
            @RequestBody @Valid OnboardingRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String corrId) {
        String correlationId = corrId != null ? corrId : IdGenerator.correlationId();
        OnboardingRequest orchestrationRequest =
                new OnboardingRequest(
                        request.email(),
                        request.firstName(),
                        request.lastName(),
                        request.phoneNumber(),
                        request.tenantId(),
                        correlationId);

        SagaInstance saga = sagaOrchestrator.startSaga(orchestrationRequest);
        log.info("Onboarding saga started via REST for email: {}", request.email());
        return toDto(saga);
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga status by ID")
    public SagaStatusDto getSagaStatus(@PathVariable String sagaId) {
        return toDto(sagaOrchestrator.getSagaStatus(sagaId));
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Get saga history for an email")
    public List<SagaStatusDto> getSagaHistoryByEmail(@PathVariable String email) {
        return sagaOrchestrator.getSagaHistoryByEmail(email).stream().map(this::toDto).toList();
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active (non-terminal) sagas")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public List<SagaStatusDto> getActiveSagas() {
        // Admin only endpoint -- secured by ROLE_BUSINESS_ADMIN
        return sagaOrchestrator.getActiveSagas().stream().map(this::toDto).toList();
    }

    @GetMapping("/health")
    @Operation(summary = "Saga orchestrator health check")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "activeSagas", sagaOrchestrator.getActiveSagas().size(),
                "timestamp", Instant.now());
    }

    private SagaStatusDto toDto(SagaInstance saga) {
        return new SagaStatusDto(
                saga.getSagaId(),
                saga.getSagaType(),
                saga.getState() != null ? saga.getState().name() : null,
                saga.getCurrentStep(),
                saga.getEmail(),
                saga.getAccountId(),
                saga.getKeycloakUserId(),
                saga.getFailureReason(),
                saga.getCorrelationId(),
                saga.getCreatedAt(),
                saga.getUpdatedAt(),
                saga.getCompletedAt());
    }
}
