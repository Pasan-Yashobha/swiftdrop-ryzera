package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.client.NameLookupClient;
import com.swiftdrop.reporting.common.BadRequestException;
import com.swiftdrop.reporting.domain.CourierDailyStats;
import com.swiftdrop.reporting.domain.DailyDeliveryStats;
import com.swiftdrop.reporting.domain.ZoneDailyStats;
import com.swiftdrop.reporting.dto.CourierPerformanceResponse;
import com.swiftdrop.reporting.dto.DailyPointResponse;
import com.swiftdrop.reporting.dto.SuccessRateResponse;
import com.swiftdrop.reporting.dto.SummaryResponse;
import com.swiftdrop.reporting.dto.ZoneStatsResponse;
import com.swiftdrop.reporting.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    @Mock
    private DailyDeliveryStatsRepository dailyDeliveryStatsRepository;
    @Mock
    private CourierDailyStatsRepository courierDailyStatsRepository;
    @Mock
    private ZoneDailyStatsRepository zoneDailyStatsRepository;
    @Mock
    private MerchantDailyStatsRepository merchantDailyStatsRepository;
    @Mock
    private CourierStateRepository courierStateRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ReconciliationService reconciliationService;
    @Mock
    private NameLookupClient nameLookupClient;

    private ReportQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReportQueryService(
                dailyDeliveryStatsRepository,
                courierDailyStatsRepository,
                zoneDailyStatsRepository,
                merchantDailyStatsRepository,
                courierStateRepository,
                processedEventRepository,
                reconciliationService,
                nameLookupClient
        );
    }

    @Test
    @DisplayName("Date validation: from after to throws BadRequestException (T7)")
    void testFromAfterToThrows400() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 5);

        assertThatThrownBy(() -> service.getSummary(from, to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be after");
    }

    @Test
    @DisplayName("Date validation: range over 366 days throws BadRequestException (T7)")
    void testRangeOver366DaysThrows400() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1); // 396 days

        assertThatThrownBy(() -> service.getSummary(from, to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds the maximum allowed range");
    }

    @Test
    @DisplayName("Summary calculation: success rates and active couriers calculated correctly")
    void testSummaryCalculations() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 2);

        DailyDeliveryStats d1 = new DailyDeliveryStats();
        d1.setStatDate(from);
        d1.setCreatedCount(5);
        d1.setDeliveredCount(2);
        d1.setFailedCount(2);
        d1.setReturnedCount(1);

        DailyDeliveryStats d2 = new DailyDeliveryStats();
        d2.setStatDate(to);
        d2.setDeliveredCount(1);
        d2.setFailedCount(0);

        when(dailyDeliveryStatsRepository.findByStatDateBetweenOrderByStatDateAsc(from, to))
                .thenReturn(List.of(d1, d2));
        when(courierStateRepository.countAvailable()).thenReturn(4L);
        when(reconciliationService.isMismatchDetected()).thenReturn(false);
        OffsetDateTime now = OffsetDateTime.now();
        when(processedEventRepository.findLatestProcessedAt()).thenReturn(now);

        SummaryResponse summary = service.getSummary(from, to);

        assertThat(summary.created()).isEqualTo(5);
        assertThat(summary.delivered()).isEqualTo(3);
        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.returned()).isEqualTo(1);
        // Attempt rate: 3 / (3 + 2) = 0.6
        assertThat(summary.attemptRate()).isEqualTo(0.6);
        // Final outcome rate: 3 / (3 + 1) = 0.75
        assertThat(summary.finalOutcomeRate()).isEqualTo(0.75);
        assertThat(summary.activeCouriers()).isEqualTo(4L);
        assertThat(summary.lastUpdatedAt()).isEqualTo(now.toInstant());
    }

    @Test
    @DisplayName("Rate formulas: divisor 0 returns null")
    void testRateDivisorZeroReturnsNull() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 1);

        DailyDeliveryStats d = new DailyDeliveryStats();
        d.setStatDate(from);
        d.setCreatedCount(1);
        d.setDeliveredCount(0);
        d.setFailedCount(0);
        d.setReturnedCount(0);

        when(dailyDeliveryStatsRepository.findByStatDateBetweenOrderByStatDateAsc(from, to))
                .thenReturn(List.of(d));

        SuccessRateResponse rates = service.getSuccessRate(from, to);

        assertThat(rates.attemptRate()).isNull();
        assertThat(rates.finalOutcomeRate()).isNull();
    }

    @Test
    @DisplayName("Courier performance: calculates average delivery time and sorts correctly")
    void testCourierPerformanceCalculations() {
        LocalDate date = LocalDate.of(2026, 11, 5);
        UUID courierId = UUID.randomUUID();

        CourierDailyStats stat = new CourierDailyStats();
        stat.setStatDate(date);
        stat.setCourierId(courierId);
        stat.setAssignedCount(3);
        stat.setDeliveredCount(2);
        stat.setFailedAttempts(1);
        stat.setTotalMinutes(120);
        stat.setTimedCount(2);

        when(courierDailyStatsRepository.findByStatDateBetweenOrderByStatDateAscCourierIdAsc(date, date))
                .thenReturn(List.of(stat));
        when(nameLookupClient.getCourierName(courierId)).thenReturn("John Doe");

        List<CourierPerformanceResponse> list = service.getCourierPerformance(date, date, "delivered,desc");

        assertThat(list).hasSize(1);
        CourierPerformanceResponse courier = list.get(0);
        assertThat(courier.courierName()).isEqualTo("John Doe");
        assertThat(courier.deliveredCount()).isEqualTo(2);
        assertThat(courier.averageDeliveryMinutes()).isEqualTo(60.0);
    }
}
