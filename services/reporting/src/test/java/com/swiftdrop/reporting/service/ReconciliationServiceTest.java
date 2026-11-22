package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.client.ParcelServiceClient;
import com.swiftdrop.reporting.domain.DailyDeliveryStats;
import com.swiftdrop.reporting.repository.DailyDeliveryStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private DailyDeliveryStatsRepository dailyDeliveryStatsRepository;
    @Mock
    private ParcelServiceClient parcelServiceClient;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(dailyDeliveryStatsRepository, parcelServiceClient);
    }

    @Test
    @DisplayName("T9: Reconciliation flags mismatch when numbers differ")
    void testReconciliationMismatchFlagged() {
        LocalDate yesterday = LocalDate.of(2026, 11, 5);

        DailyDeliveryStats reportingStats = new DailyDeliveryStats();
        reportingStats.setStatDate(yesterday);
        reportingStats.setDeliveredCount(2);
        reportingStats.setFailedCount(1); // reporting has 1 failed

        when(dailyDeliveryStatsRepository.findById(yesterday)).thenReturn(Optional.of(reportingStats));
        // Parcel service authoritative counts show 2 failed!
        when(parcelServiceClient.getDailyCounts(yesterday))
                .thenReturn(new ParcelServiceClient.ParcelDailyCounts(2, 2));

        boolean matches = reconciliationService.reconcileDate(yesterday);

        assertThat(matches).isFalse();
        assertThat(reconciliationService.isMismatchDetected()).isTrue();
    }

    @Test
    @DisplayName("Reconciliation passes when numbers match")
    void testReconciliationMatches() {
        LocalDate yesterday = LocalDate.of(2026, 11, 5);

        DailyDeliveryStats reportingStats = new DailyDeliveryStats();
        reportingStats.setStatDate(yesterday);
        reportingStats.setDeliveredCount(2);
        reportingStats.setFailedCount(2);

        when(dailyDeliveryStatsRepository.findById(yesterday)).thenReturn(Optional.of(reportingStats));
        when(parcelServiceClient.getDailyCounts(yesterday))
                .thenReturn(new ParcelServiceClient.ParcelDailyCounts(2, 2));

        boolean matches = reconciliationService.reconcileDate(yesterday);

        assertThat(matches).isTrue();
        assertThat(reconciliationService.isMismatchDetected()).isFalse();
    }
}
