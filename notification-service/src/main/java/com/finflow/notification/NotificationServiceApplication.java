package com.finflow.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service -- multi-channel notification delivery.
 *
 * <p>Consumes from Kafka:
 *
 * <p>- fraud-events topic -> sends fraud alerts - notify-events topic -> sends payment receipts
 * and alerts
 *
 * <p>Consumes from RabbitMQ:
 *
 * <p>- saga.commands (notify.welcome routing key) -> welcome emails - notify.commands -> direct
 * notification commands
 *
 * <p>Channels supported:
 *
 * <p>- EMAIL via AWS SES (or SMTP in dev) - SMS via AWS SNS (or stub in dev) - PUSH via Firebase
 * Cloud Messaging (stubbed)
 *
 * <p>All notifications are logged to notification_db for audit. Port: 8087
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
