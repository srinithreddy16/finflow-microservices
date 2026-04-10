package com.finflow.transaction.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStoreRepository extends JpaRepository<EventStoreEntry, String> {

    List<EventStoreEntry> findByAggregateIdOrderBySequenceNumberAsc(String aggregateId);

    long countByAggregateId(String aggregateId);

    Optional<EventStoreEntry> findFirstByAggregateIdOrderBySequenceNumberDesc(String aggregateId);
}
