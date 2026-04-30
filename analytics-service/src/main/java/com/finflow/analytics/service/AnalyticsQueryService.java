package com.finflow.analytics.service;

import com.finflow.analytics.cache.AnalyticsRedisCache;
import com.finflow.analytics.config.RedisConfig;
import com.finflow.analytics.dto.AnalyticsSummaryDto;
import com.finflow.analytics.dto.DailyMetricsDto;
import com.finflow.analytics.dto.PlatformSummaryDto;
import com.finflow.analytics.model.AnalyticsReadModel;
import com.finflow.analytics.repository.AnalyticsReadModelRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AnalyticsReadModelRepository repository;
    private final AnalyticsRedisCache analyticsRedisCache;

    @Cacheable(
            value = RedisConfig.CACHE_ANALYTICS_SUMMARY,
            key = "#accountId + '-' + #fromDate + '-' + #toDate")
    public AnalyticsSummaryDto getSummary(String accountId, LocalDate fromDate, LocalDate toDate) {
        List<AnalyticsReadModel> models =
                repository.findByAccountIdAndDateBetweenOrderByDateAsc(accountId, fromDate, toDate);

        long totalTransactions = models.stream().mapToLong(AnalyticsReadModel::getTotalTransactions).sum();
        long completedTransactions = models.stream().mapToLong(AnalyticsReadModel::getCompletedTransactions).sum();
        long failedTransactions = models.stream().mapToLong(AnalyticsReadModel::getFailedTransactions).sum();
        long reversedTransactions = models.stream().mapToLong(AnalyticsReadModel::getReversedTransactions).sum();
        long fraudFlags = models.stream().mapToLong(AnalyticsReadModel::getFraudFlags).sum();

        BigDecimal totalVolume =
                models.stream()
                        .map(m -> m.getTotalVolume() != null ? m.getTotalVolume() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal completedVolume =
                models.stream()
                        .map(m -> m.getCompletedVolume() != null ? m.getCompletedVolume() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fraudRate =
                totalTransactions > 0
                        ? BigDecimal.valueOf(fraudFlags)
                                .divide(BigDecimal.valueOf(totalTransactions), 6, RoundingMode.HALF_UP)
                                .multiply(HUNDRED)
                                .setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        BigDecimal avgTransactionAmount =
                totalTransactions > 0
                        ? totalVolume.divide(
                                BigDecimal.valueOf(totalTransactions), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        String currency = models.isEmpty() ? "USD" : models.get(0).getCurrency();
        log.debug("Analytics summary computed for account: {} ({} to {})", accountId, fromDate, toDate);
        return new AnalyticsSummaryDto(
                accountId,
                totalTransactions,
                completedTransactions,
                failedTransactions,
                reversedTransactions,
                totalVolume,
                completedVolume,
                fraudFlags,
                fraudRate,
                avgTransactionAmount,
                currency,
                fromDate,
                toDate,
                Instant.now());
    }

    @Cacheable(value = RedisConfig.CACHE_DAILY_METRICS, key = "#accountId + '-' + #date")
    public DailyMetricsDto getDailyMetrics(String accountId, LocalDate date) {
        List<AnalyticsReadModel> models =
                repository.findByAccountIdAndDateBetweenOrderByDateAsc(accountId, date, date);
        if (models.isEmpty()) {
            return zeroDaily(date, "USD");
        }

        AnalyticsReadModel model = models.get(0);
        return new DailyMetricsDto(
                model.getDate(),
                model.getTotalTransactions(),
                model.getCompletedTransactions(),
                model.getFailedTransactions(),
                model.getTotalVolume() != null ? model.getTotalVolume() : BigDecimal.ZERO,
                model.getCompletedVolume() != null ? model.getCompletedVolume() : BigDecimal.ZERO,
                model.getFraudFlags(),
                model.getCurrency());
    }

    @Cacheable(value = RedisConfig.CACHE_PLATFORM_SUMMARY)
    public PlatformSummaryDto getPlatformSummary() {
        String today = LocalDate.now().toString();
        long totalTransactionsToday = analyticsRedisCache.getDailyTransactionCount(today);
        BigDecimal totalVolumeToday = BigDecimal.valueOf(analyticsRedisCache.getDailyVolume(today));
        long fraudFlagsToday = analyticsRedisCache.getDailyFraudCount(today);

        BigDecimal fraudRateToday =
                totalTransactionsToday > 0
                        ? BigDecimal.valueOf(fraudFlagsToday)
                                .divide(
                                        BigDecimal.valueOf(totalTransactionsToday),
                                        6,
                                        RoundingMode.HALF_UP)
                                .multiply(HUNDRED)
                                .setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        LocalDate date = LocalDate.now();
        long activeAccounts =
                repository.findByDateOrderByTotalVolumeDesc(date).stream()
                        .map(AnalyticsReadModel::getAccountId)
                        .distinct()
                        .count();

        long completedTransactionsToday =
                repository.findByDateOrderByTotalVolumeDesc(date).stream()
                        .mapToLong(AnalyticsReadModel::getCompletedTransactions)
                        .sum();

        return new PlatformSummaryDto(
                totalTransactionsToday,
                completedTransactionsToday,
                totalVolumeToday,
                fraudFlagsToday,
                fraudRateToday,
                activeAccounts,
                Instant.now());
    }

    public List<DailyMetricsDto> getAccountHistory(String accountId, int days) {
        int safeDays = Math.max(days, 1);
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(safeDays - 1L);

        List<AnalyticsReadModel> models =
                repository.findByAccountIdAndDateBetweenOrderByDateAsc(accountId, fromDate, toDate);
        Map<LocalDate, AnalyticsReadModel> byDate =
                models.stream()
                        .collect(Collectors.toMap(AnalyticsReadModel::getDate, Function.identity(), (a, b) -> a));

        List<DailyMetricsDto> result = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            AnalyticsReadModel model = byDate.get(date);
            if (model == null) {
                result.add(zeroDaily(date, "USD"));
            } else {
                result.add(
                        new DailyMetricsDto(
                                model.getDate(),
                                model.getTotalTransactions(),
                                model.getCompletedTransactions(),
                                model.getFailedTransactions(),
                                model.getTotalVolume() != null ? model.getTotalVolume() : BigDecimal.ZERO,
                                model.getCompletedVolume() != null
                                        ? model.getCompletedVolume()
                                        : BigDecimal.ZERO,
                                model.getFraudFlags(),
                                model.getCurrency()));
            }
        }
        return result;
    }

    private DailyMetricsDto zeroDaily(LocalDate date, String currency) {
        return new DailyMetricsDto(date, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, currency);
    }
}
