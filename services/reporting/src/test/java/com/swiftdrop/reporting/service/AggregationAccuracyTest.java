package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.common.ParcelFactNotFoundException;
import com.swiftdrop.reporting.domain.*;
import com.swiftdrop.reporting.dto.*;
import com.swiftdrop.reporting.repository.*;
import com.swiftdrop.reporting.testsupport.AbstractIntegrationTest;
import com.swiftdrop.reporting.testsupport.TestEventFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AggregationAccuracyTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private EventHandlerService eventHandlerService;

    @Autowired
    private ReportQueryService reportQueryService;

    @Autowired
    private DailyDeliveryStatsRepository dailyDeliveryStatsRepository;

    @Autowired
    private CourierDailyStatsRepository courierDailyStatsRepository;

    @Autowired
    private ZoneDailyStatsRepository zoneDailyStatsRepository;

    @Autowired
    private MerchantDailyStatsRepository merchantDailyStatsRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private static final LocalDate DAY_D = TestEventFixture.DAY_D;
    private static final LocalDate DAY_D_1 = TestEventFixture.DAY_D_1;

    @Test
    @DisplayName("T1: Accuracy test on exact fixture numbers (5 parcels, Day D and D+1, 31 events)")
    void testT1Accuracy() {
        List<EventEnvelope> events = TestEventFixture.buildAll31Events();
        assertThat(events).hasSize(31);

        // Process all 31 events
        for (EventEnvelope ev : events) {
            eventHandlerService.handle(ev);
        }

        // ── 1. Company Day D ─────────────────────────────────────────────────
        DailyDeliveryStats statsD = dailyDeliveryStatsRepository.findById(DAY_D).orElseThrow();
        assertThat(statsD.getCreatedCount()).as("Day D created").isEqualTo(5);
        assertThat(statsD.getDeliveredCount()).as("Day D delivered").isEqualTo(2);
        assertThat(statsD.getFailedCount()).as("Day D failed").isEqualTo(2);
        assertThat(statsD.getReturnedCount()).as("Day D returned").isEqualTo(1);
        assertThat(statsD.getRescheduledCount()).as("Day D rescheduled").isEqualTo(1);
        assertThat(statsD.getCancelledCount()).as("Day D cancelled").isEqualTo(1);

        // ── 2. Company Day D+1 ───────────────────────────────────────────────
        DailyDeliveryStats statsD1 = dailyDeliveryStatsRepository.findById(DAY_D_1).orElseThrow();
        assertThat(statsD1.getDeliveredCount()).as("Day D+1 delivered").isEqualTo(1);
        assertThat(statsD1.getFailedCount()).as("Day D+1 failed").isEqualTo(0);

        // ── 3. Rates ─────────────────────────────────────────────────────────
        SummaryResponse summaryD = reportQueryService.getSummary(DAY_D, DAY_D);
        assertThat(summaryD.attemptRate()).as("Attempt rate Day D: 2/(2+2)").isEqualTo(0.5); // 50.0%

        SummaryResponse summaryRange = reportQueryService.getSummary(DAY_D, DAY_D_1);
        // Final outcome rate D to D+1: delivered 3 / (delivered 3 + returned 1) = 75.0%
        assertThat(summaryRange.finalOutcomeRate()).as("Final-outcome rate D to D+1: 3/(3+1)").isEqualTo(0.75);

        // ── 4. Courier C1 ────────────────────────────────────────────────────
        CourierPerformanceResponse c1D = reportQueryService.getCourierPerformance(DAY_D, DAY_D, null).stream()
                .filter(c -> c.courierId().equals(TestEventFixture.C1)).findFirst().orElseThrow();
        assertThat(c1D.assignedCount()).as("C1 Day D assigned").isEqualTo(3);
        assertThat(c1D.deliveredCount()).as("C1 Day D delivered").isEqualTo(2);
        assertThat(c1D.failedAttempts()).as("C1 Day D failed attempts").isEqualTo(1);
        assertThat(c1D.averageDeliveryMinutes()).as("C1 Day D avg minutes: 120/2").isEqualTo(60.0);

        CourierPerformanceResponse c1D1 = reportQueryService.getCourierPerformance(DAY_D_1, DAY_D_1, null).stream()
                .filter(c -> c.courierId().equals(TestEventFixture.C1)).findFirst().orElseThrow();
        assertThat(c1D1.deliveredCount()).as("C1 Day D+1 delivered").isEqualTo(1);
        assertThat(c1D1.averageDeliveryMinutes()).as("C1 Day D+1 avg minutes").isEqualTo(30.0);

        // ── 5. Courier C2 ────────────────────────────────────────────────────
        CourierPerformanceResponse c2D = reportQueryService.getCourierPerformance(DAY_D, DAY_D, null).stream()
                .filter(c -> c.courierId().equals(TestEventFixture.C2)).findFirst().orElseThrow();
        assertThat(c2D.assignedCount()).as("C2 Day D assigned").isEqualTo(2);
        assertThat(c2D.deliveredCount()).as("C2 Day D delivered").isEqualTo(0);
        assertThat(c2D.failedAttempts()).as("C2 Day D failed attempts").isEqualTo(1);
        assertThat(c2D.averageDeliveryMinutes()).as("C2 Day D avg minutes unavailable").isNull();

        // ── 6. Zone Z2 Day D ─────────────────────────────────────────────────
        ZoneStatsResponse z2D = reportQueryService.getZoneStats(DAY_D, DAY_D).stream()
                .filter(z -> z.zoneId().equals(TestEventFixture.Z2)).findFirst().orElseThrow();
        assertThat(z2D.createdCount()).as("Z2 Day D created").isEqualTo(5);
        assertThat(z2D.deliveredCount()).as("Z2 Day D delivered").isEqualTo(2);
        assertThat(z2D.failedCount()).as("Z2 Day D failed").isEqualTo(2);

        // ── 7. Merchant M1 Day D ─────────────────────────────────────────────
        MerchantOrdersResponse m1D = reportQueryService.getMerchantOrders(DAY_D, DAY_D, TestEventFixture.M1);
        assertThat(m1D.totalCreated()).as("M1 Day D created").isEqualTo(3);
        assertThat(m1D.totalDelivered()).as("M1 Day D delivered").isEqualTo(2);
        assertThat(m1D.totalFailed()).as("M1 Day D failed").isEqualTo(1);
    }

    @Test
    @DisplayName("T2: Duplicates test (publish all events twice, totals unchanged, exactly 31 rows in processed_event)")
    void testT2Duplicates() {
        List<EventEnvelope> events = TestEventFixture.buildAll31Events();

        // First pass
        for (EventEnvelope ev : events) {
            boolean processed = eventHandlerService.handle(ev);
            assertThat(processed).isTrue();
        }

        // Second pass: duplicate events
        for (EventEnvelope ev : events) {
            boolean processed = eventHandlerService.handle(ev);
            assertThat(processed).isFalse();
        }

        // Verify totals remain identical to T1
        DailyDeliveryStats statsD = dailyDeliveryStatsRepository.findById(DAY_D).orElseThrow();
        assertThat(statsD.getCreatedCount()).isEqualTo(5);
        assertThat(statsD.getDeliveredCount()).isEqualTo(2);
        assertThat(statsD.getFailedCount()).isEqualTo(2);

        // Exactly 31 rows in processed_event
        long processedCount = processedEventRepository.count();
        assertThat(processedCount).as("Exact number of processed_event rows").isEqualTo(31L);
    }

    @Test
    @DisplayName("T3: Out of order test (P1 delivered before out_for_delivery, same counters, 40 min appears once second arrives)")
    void testT3OutOfOrder() {
        List<EventEnvelope> allEvents = TestEventFixture.buildAll31Events();

        // Extract P1 out_for_delivery and delivered events
        EventEnvelope p1Ofd = allEvents.stream()
                .filter(e -> "parcel.status.out_for_delivery".equals(e.eventType())
                        && TestEventFixture.P1.equals(e.payload().get("parcelId")))
                .findFirst().orElseThrow();

        EventEnvelope p1Delivered = allEvents.stream()
                .filter(e -> "parcel.status.delivered".equals(e.eventType())
                        && TestEventFixture.P1.equals(e.payload().get("parcelId")))
                .findFirst().orElseThrow();

        // Process all events EXCEPT p1Ofd and p1Delivered first
        for (EventEnvelope ev : allEvents) {
            if (ev.equals(p1Ofd) || ev.equals(p1Delivered)) continue;
            eventHandlerService.handle(ev);
        }

        // 1. Deliver P1 FIRST (before out_for_delivery arrives)
        eventHandlerService.handle(p1Delivered);

        // At this moment, courier C1 has P2 (80 min) but P1's duration is not known yet
        CourierPerformanceResponse c1Mid = reportQueryService.getCourierPerformance(DAY_D, DAY_D, null).stream()
                .filter(c -> c.courierId().equals(TestEventFixture.C1)).findFirst().orElseThrow();
        assertThat(c1Mid.timedCount()).isEqualTo(1); // Only P2 is timed
        assertThat(c1Mid.totalMinutes()).isEqualTo(80L);

        // 2. Out_for_delivery arrives SECOND
        eventHandlerService.handle(p1Ofd);

        // Now the 40 min appears once the second event arrives (total 120 min, avg 60.0 min)
        CourierPerformanceResponse c1Final = reportQueryService.getCourierPerformance(DAY_D, DAY_D, null).stream()
                .filter(c -> c.courierId().equals(TestEventFixture.C1)).findFirst().orElseThrow();
        assertThat(c1Final.timedCount()).isEqualTo(2);
        assertThat(c1Final.totalMinutes()).isEqualTo(120L); // 80 + 40 = 120 min
        assertThat(c1Final.averageDeliveryMinutes()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("T4: Shuffled order test (20 runs with different random seeds, retry queue handles out-of-order creation)")
    void testT4ShuffledOrder20Runs() {
        for (int seed = 1; seed <= 20; seed++) {
            databaseCleaner.clean();

            List<EventEnvelope> events = new ArrayList<>(TestEventFixture.buildAll31Events());
            Collections.shuffle(events, new Random(seed));

            Queue<EventEnvelope> queue = new ArrayDeque<>(events);
            int maxAttempts = events.size() * 5;
            int attempts = 0;

            while (!queue.isEmpty() && attempts < maxAttempts) {
                attempts++;
                EventEnvelope ev = queue.poll();
                try {
                    eventHandlerService.handle(ev);
                } catch (ParcelFactNotFoundException ex) {
                    // Retry delayed arrival (simulating queue redelivery after parcel.created arrives)
                    queue.offer(ev);
                }
            }

            assertThat(queue).as("All events processed for seed " + seed).isEmpty();

            // Verify totals match expected numbers
            DailyDeliveryStats statsD = dailyDeliveryStatsRepository.findById(DAY_D).orElseThrow();
            assertThat(statsD.getCreatedCount()).isEqualTo(5);
            assertThat(statsD.getDeliveredCount()).isEqualTo(2);
            assertThat(statsD.getFailedCount()).isEqualTo(2);

            DailyDeliveryStats statsD1 = dailyDeliveryStatsRepository.findById(DAY_D_1).orElseThrow();
            assertThat(statsD1.getDeliveredCount()).isEqualTo(1);
        }
    }
}
