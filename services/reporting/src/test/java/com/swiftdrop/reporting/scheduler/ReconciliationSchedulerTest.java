package com.swiftdrop.reporting.scheduler;

import com.swiftdrop.reporting.config.AppProperties;
import com.swiftdrop.reporting.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerTest {

    @Mock
    private ReconciliationService reconciliationService;

    private AppProperties appProperties;
    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setTimezone("Asia/Colombo");
        scheduler = new ReconciliationScheduler(reconciliationService, appProperties);
    }

    @Test
    @DisplayName("Scheduled reconciliation delegates to ReconciliationService for yesterday's date")
    void testSchedulerRunsForYesterday() {
        LocalDate expectedYesterday = LocalDate.now(ZoneId.of("Asia/Colombo")).minusDays(1);

        scheduler.runNightlyReconciliation();

        verify(reconciliationService).reconcileDate(eq(expectedYesterday));
    }
}
