package com.finflow.account.saga;

import com.finflow.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccountCompensationHandler {

    private final AccountService accountService;

    public record CompensationCommand(
            String sagaId, String accountId, String reason, String correlationId) {}

    @RabbitListener(queues = "saga.account.compensation")
    public void handleCompensation(CompensationCommand command) {
        log.warn(
                "Compensating account creation for sagaId: {}, accountId: {}",
                command.sagaId(),
                command.accountId());
        try {
            accountService.deleteAccount(command.accountId());
            log.info("Account compensation completed for sagaId: {}", command.sagaId());
        } catch (Exception e) {
            log.error("Account compensation failed for sagaId: {}", command.sagaId(), e);
        }
    }
}
