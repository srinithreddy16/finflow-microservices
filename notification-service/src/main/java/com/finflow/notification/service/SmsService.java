package com.finflow.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

@Service
@Slf4j
public class SmsService {

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    private final SnsClient snsClient;

    public SmsService(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    public boolean send(NotificationRequest request) {
        String phone = request.recipientPhone();
        if (!smsEnabled || phone == null || phone.isBlank()) {
            log.info("SMS disabled or no phone -- simulating for: {}", request.accountId());
            return true;
        }

        try {
            PublishRequest publishRequest =
                    PublishRequest.builder().phoneNumber(phone).message(request.messageBody()).build();
            snsClient.publish(publishRequest);
            log.info("SMS sent to phone: {}", phone);
            return true;
        } catch (SnsException ex) {
            log.error("SMS send failed", ex);
            return false;
        }
    }
}
