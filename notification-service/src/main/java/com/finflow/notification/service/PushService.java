package com.finflow.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PushService {

    @Value("${notification.push.enabled:false}")
    private boolean pushEnabled;

    public boolean send(NotificationRequest request) {
        if (!pushEnabled) {
            log.info(
                    "Push notifications stubbed -- would send to account: {}",
                    request.accountId());
            return true;
        }
        return true;
    }
}
