package com.finflow.saga.state;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    List<SagaInstance> findByStateNotInOrderByCreatedAtDesc(List<SagaState> terminalStates);

    List<SagaInstance> findByEmailOrderByCreatedAtDesc(String email);

    Optional<SagaInstance> findByCorrelationId(String correlationId);

    List<SagaInstance> findByStateAndCreatedAtBefore(SagaState state, Instant cutoff);

    long countByState(SagaState state);

    Page<SagaInstance> findByStateOrderByCreatedAtDesc(SagaState state, Pageable pageable);
}
