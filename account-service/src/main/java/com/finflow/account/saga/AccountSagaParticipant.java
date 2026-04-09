package com.finflow.account.saga;

import com.finflow.account.config.RabbitMQConfig;
import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.dto.AccountResponseDto;
import com.finflow.account.mapper.AccountMapper;
import com.finflow.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccountSagaParticipant {

    private final AccountService accountService;
    private final SagaReplyPublisher sagaReplyPublisher;
    private final AccountMapper accountMapper;

    public record SagaCommand(
            String sagaId,
            String commandType,
            String correlationId,
            AccountRequestDto payload) {}

    @RabbitListener(queues = RabbitMQConfig.ACCOUNT_COMMANDS_QUEUE)
    public void handleSagaCommand(SagaCommand command) {
        log.info(
                "Received saga command: {} for sagaId: {}",
                command.commandType(),
                command.sagaId());

        switch (command.commandType()) {
            case "CREATE_ACCOUNT" -> handleCreateAccount(command);
            case "VERIFY_KYC" -> handleVerifyKyc(command);
            default -> handleUnknownCommand(command);
        }
    }

    private void handleCreateAccount(SagaCommand command) {
        try {
            AccountResponseDto created =
                    accountService.createAccount(command.payload(), command.correlationId());
            sagaReplyPublisher.publishSuccess(
                    command.sagaId(),
                    "CREATE_ACCOUNT",
                    created.id(),
                    command.correlationId());
        } catch (Exception e) {
            sagaReplyPublisher.publishFailure(
                    command.sagaId(),
                    "CREATE_ACCOUNT",
                    e.getMessage(),
                    command.correlationId());
        }
    }

    private void handleVerifyKyc(SagaCommand command) {
        String accountId = command.payload().accountId();
        if (!StringUtils.hasText(accountId)) {
            sagaReplyPublisher.publishFailure(
                    command.sagaId(),
                    "VERIFY_KYC",
                    "accountId is required in payload for VERIFY_KYC",
                    command.correlationId());
            return;
        }
        try {
            accountMapper.toEntity(command.payload());
            var result = accountService.performKycVerification(accountId);
            if (result.passed()) {
                AccountResponseDto account = accountService.getAccountById(accountId);
                sagaReplyPublisher.publishSuccess(
                        command.sagaId(),
                        "VERIFY_KYC",
                        account.id(),
                        command.correlationId());
            } else {
                sagaReplyPublisher.publishFailure(
                        command.sagaId(),
                        "VERIFY_KYC",
                        result.reason(),
                        command.correlationId());
            }
        } catch (Exception e) {
            sagaReplyPublisher.publishFailure(
                    command.sagaId(),
                    "VERIFY_KYC",
                    e.getMessage(),
                    command.correlationId());
        }
    }

    private void handleUnknownCommand(SagaCommand command) {
        log.warn("Unknown command type: {}", command.commandType());
        sagaReplyPublisher.publishFailure(
                command.sagaId(),
                command.commandType(),
                "Unknown command type: " + command.commandType(),
                command.correlationId());
    }
}
