package com.finflow.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

@Service
@Slf4j
public class EmailService {

    @Value("${aws.ses.from-email:noreply@finflow.com}")
    private String fromEmail;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;

    private final SesClient sesClient;

    public EmailService(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    public boolean send(NotificationRequest request) {
        if (!emailEnabled) {
            log.info(
                    "Email disabled -- simulating send to: {}, subject: {}",
                    request.recipientEmail(),
                    request.subject());
            return true;
        }

        try {
            SendEmailRequest sendEmailRequest =
                    SendEmailRequest.builder()
                            .source(fromEmail)
                            .destination(
                                    Destination.builder()
                                            .toAddresses(request.recipientEmail())
                                            .build())
                            .message(
                                    Message.builder()
                                            .subject(Content.builder().data(request.subject()).build())
                                            .body(
                                                    Body.builder()
                                                            .text(
                                                                    Content.builder()
                                                                            .data(request.messageBody())
                                                                            .build())
                                                            .build())
                                            .build())
                            .build();

            sesClient.sendEmail(sendEmailRequest);
            log.info("Email sent to: {} subject: {}", request.recipientEmail(), request.subject());
            return true;
        } catch (SesException ex) {
            log.error("Email send failed to: {}", request.recipientEmail(), ex);
            return false;
        }
    }
}
