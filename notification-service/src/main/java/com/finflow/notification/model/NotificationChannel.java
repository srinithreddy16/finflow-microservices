package com.finflow.notification.model;

public enum NotificationChannel {
    EMAIL("Email notification channel"),
    SMS("SMS notification channel"),
    PUSH("Push notification channel");

    private final String description;

    NotificationChannel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
