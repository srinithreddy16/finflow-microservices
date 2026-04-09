package com.finflow.account.kafka;

import com.finflow.account.model.Account;
import com.finflow.common.util.IdGenerator;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
/*
This class is the messenger, it publishes account-related events.
 It knows how to take an account event, package it properly,
and send it to Kafka's audit-log topic. Any service that cares about account activity
(compliance systems, analytics, reporting) can consume from that topic.
 */


@Slf4j
@Service
public class AccountEventPublisher {

    private static final String AUDIT_LOG_TOPIC = "audit-log"; // Defines the Kafka topic name as a constant.  We call it "audit-log" because this topic stores all audit records across the system.

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AccountEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    // This is the shape of the message that gets sent to Kafka. Every consumer reading from audit-log(Topic) will receive this structure serialized as JSON:
    public record AuditLogEvent(
            String eventId,
            String accountId,
            String action,
            String performedBy,
            String correlationId,
            Instant occurredOn) {}


    //Builds the Account Created event object that will be sent to Kafka.
    public void publishAccountCreated(Account account, String correlationId) {
        AuditLogEvent event =
                new AuditLogEvent(
                        IdGenerator.generate(),
                        account.getId(),
                        "ACCOUNT_CREATED",
                        account.getEmail(),
                        correlationId,
                        Instant.now());

        // This is the actual send call.
        kafkaTemplate
                .send(AUDIT_LOG_TOPIC, account.getId(), event)   // // Sends event to topic; key = account ID (keeps order), value = AuditLogEvent object
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.error(
                                        "Failed to publish ACCOUNT_CREATED audit event for account {}",
                                        account.getId(),
                                        ex);
                                return;
                            }
                            log.info("Published ACCOUNT_CREATED event for account: {}", account.getId()); // logs if everything is successful.
                        });
    }

    public void publishAccountStatusChanged(
            Account account, String newStatus, String correlationId) {
        AuditLogEvent event =
                new AuditLogEvent(
                        IdGenerator.generate(),
                        account.getId(),
                        "ACCOUNT_STATUS_CHANGED_TO_" + newStatus,
                        account.getEmail(),
                        correlationId,
                        Instant.now());
        kafkaTemplate
                .send(AUDIT_LOG_TOPIC, account.getId(), event)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.error(
                                        "Failed to publish ACCOUNT_STATUS_CHANGED audit event for account {}",
                                        account.getId(),
                                        ex);
                                return;
                            }
                            log.info(
                                    "Published ACCOUNT_STATUS_CHANGED event for account: {}",
                                    account.getId());
                        });
    }
}
