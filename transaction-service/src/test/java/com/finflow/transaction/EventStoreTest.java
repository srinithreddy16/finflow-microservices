package com.finflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.transaction.event.EventStoreEntry;
import com.finflow.transaction.event.EventStoreImpl;
import com.finflow.transaction.event.EventStoreRepository;
import com.finflow.transaction.event.TransactionCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventStoreTest {

    @Mock private EventStoreRepository eventStoreRepository;

    @Mock private ObjectMapper objectMapper;

    @InjectMocks private EventStoreImpl eventStore;

    @Test
    void append_ShouldPersistEventToRepository() throws Exception {
        TransactionCreatedEvent event =
                new TransactionCreatedEvent(
                        "tx-001",
                        "acc-001",
                        java.math.BigDecimal.valueOf(50),
                        "USD",
                        "Note",
                        "user-1",
                        "corr-1",
                        1L);
        String json = "{\"stub\":true}";
        when(objectMapper.writeValueAsString(any(TransactionCreatedEvent.class))).thenReturn(json);

        eventStore.append(event);

        ArgumentCaptor<EventStoreEntry> captor = ArgumentCaptor.forClass(EventStoreEntry.class);
        verify(eventStoreRepository).save(captor.capture());
        EventStoreEntry saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo("tx-001");
        assertThat(saved.getEventType()).isEqualTo("TRANSACTION_CREATED");
        assertThat(saved.getEventData()).isEqualTo(json);
        assertThat(saved.getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    void getNextSequenceNumber_ShouldReturnCountPlusOne() {
        when(eventStoreRepository.countByAggregateId("tx-001")).thenReturn(3L);

        assertThat(eventStore.getNextSequenceNumber("tx-001")).isEqualTo(4L);
    }
}
