package com.finflow.notification.model;

public enum NotificationStatus {
    PENDING("Notification queued"),
    SENT("Notification sent successfully"),
    FAILED("Notification delivery failed"),
    SKIPPED("Notification skipped (e.g. missing contact info)");

    private final String description;

    NotificationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
