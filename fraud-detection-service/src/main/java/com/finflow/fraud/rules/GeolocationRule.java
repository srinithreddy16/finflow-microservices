package com.finflow.fraud.rules;

import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudRecord;
import com.finflow.fraud.repository.FraudRecordRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeolocationRule implements FraudRule {

    private final FraudRecordRepository fraudRecordRepository;

    @Override
    public String getRuleName() {
        return "GEOLOCATION";
    }

    @Override
    public RuleResult evaluate(FraudCheckRequest request, String accountId) {
        Optional<FraudRecord> lastRecord =
                fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc(accountId);

        if (lastRecord.isEmpty()) {
            return new RuleResult(false, 0, "Geolocation check passed");
        }

        FraudRecord previous = lastRecord.get();
        String previousCountry = previous.getCountryCode();
        String currentCountry = request.countryCode();

        if (previousCountry != null
                && !previousCountry.isBlank()
                && currentCountry != null
                && !currentCountry.isBlank()
                && !previousCountry.equalsIgnoreCase(currentCountry)) {
            return new RuleResult(
                    true,
                    70,
                    "Country mismatch: previous="
                            + previousCountry
                            + ", current="
                            + currentCountry);
        }
        return new RuleResult(false, 0, "Geolocation check passed");
    }
}
