package com.finflow.account.service;

import com.finflow.account.model.Account;
import com.finflow.account.model.AccountStatus;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class KycService {

    private static final Set<String> BLOCKLISTED_DOMAINS = Set.of("test.com", "spam.com");

    private final RestTemplate restTemplate; // Spring's tool for making HTTP calls to other services. We're injecting it here because, when the fraud-detection-service is built, this KycService will call it over HTTP to do a real KYC check.

    @Value("${service.fraud.url:http://localhost:8086}")  //this is the address of fraud service that account service will find
    private String fraudServiceUrl;

    public KycService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record KycCheckResult(boolean passed, String reason, int riskScore) {}  // The result of a KYC check. Was the check passed? Why? What was the risk score (0 = safe, 100 = definitely suspicious)?

    public KycCheckResult performKycCheck(Account account) {
        String email = account.getEmail() == null ? "" : account.getEmail().trim().toLowerCase();
        String domain = extractDomain(email);
        String phoneNumber =
                account.getPhoneNumber() == null ? "" : account.getPhoneNumber().trim();

        KycCheckResult result;
        if (BLOCKLISTED_DOMAINS.contains(domain)) {
            result = new KycCheckResult(false, "Email domain not allowed", 100);
        } else if (phoneNumber.isBlank()) {
            result = new KycCheckResult(false, "Phone number required for KYC", 80);
        } else {
            result = new KycCheckResult(true, "KYC passed", 0);
        }

        log.info(
                "KYC check for account {}: passed={}, score={}",
                account.getId(),
                result.passed(),
                result.riskScore());
        return result;
    }

    public boolean isKycRequired(Account account) {
        if (account.isKycVerified()) {
            return false;
        }
        return account.getStatus() == AccountStatus.PENDING;
    }

    private static String extractDomain(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return "";
        }
        return email.substring(at + 1);
    }
}
