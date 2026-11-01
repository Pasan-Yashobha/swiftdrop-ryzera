package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.swiftdrop.merchant.credential.ControllableClock;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Dashboard statistics on a data set whose numbers are worked out by hand. The clock stands at 2026-03-01 10:00 UTC. */
@Import(ControllableClock.Config.class)
class OrderStatsTest extends AbstractOrderTest {

    private static final String STATS = PORTAL_ORDERS + "/stats";

    @Autowired
    private ControllableClock clock;

    @BeforeEach
    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void seed(OrderStatus status, String createdAt) {
        orderFixtures.order(merchant).status(status).createdAt(Instant.parse(createdAt)).save();
    }

    private void seedDataSet() {
        seed(OrderStatus.CREATED, "2026-03-01T10:00:00Z");
        seed(OrderStatus.CREATED, "2026-03-01T05:00:00Z");
        seed(OrderStatus.IN_TRANSIT, "2026-02-28T23:30:00Z");
        seed(OrderStatus.DELIVERED, "2026-02-28T00:00:00Z");
        seed(OrderStatus.DELIVERED, "2026-02-27T12:00:00Z");
        seed(OrderStatus.DELIVERED, "2026-02-27T13:00:00Z");
        seed(OrderStatus.FAILED, "2026-02-23T00:00:00Z");          // first instant of the 7 day window
        seed(OrderStatus.CANCELLED, "2026-02-22T23:59:59Z");       // just outside the window, still counted in the total
        seed(OrderStatus.RESCHEDULED, "2026-02-10T09:00:00Z");

        // never counted: sandbox order, placeholder, another merchant's order
        orderFixtures.order(merchant).sandbox().createdAt(Instant.parse("2026-03-01T09:00:00Z")).save();
        orderFixtures.order(merchant).createdAt(Instant.parse("2026-03-01T09:30:00Z")).savePlaceholder();
        Merchant other = fixtures.approvedMerchant("owner-2");
        orderFixtures.order(other).createdAt(Instant.parse("2026-03-01T08:00:00Z")).save();
    }

    @Test
    void countsLiveOrdersOfTheMerchantByStatus() throws Exception {
        seedDataSet();

        mockMvc.perform(get(STATS).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.byStatus.CREATED").value(2))
                .andExpect(jsonPath("$.byStatus.IN_TRANSIT").value(1))
                .andExpect(jsonPath("$.byStatus.DELIVERED").value(3))
                .andExpect(jsonPath("$.byStatus.FAILED").value(1))
                .andExpect(jsonPath("$.byStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.byStatus.RESCHEDULED").value(1))
                .andExpect(jsonPath("$.byStatus.PENDING").doesNotExist())
                .andExpect(jsonPath("$.byStatus.ASSIGNED").doesNotExist())
                .andExpect(jsonPath("$.delivered").value(3))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.inProgress").value(4));
    }

    @Test
    void lastSevenDaysAreUtcDatesOldestFirstWithZerosFilledIn() throws Exception {
        seedDataSet();

        JsonNode stats = body(mockMvc.perform(get(STATS).with(TestAuth.merchant(OWNER))).andExpect(status().isOk()));

        List<String> days = new ArrayList<>();
        stats.get("last7Days").forEach(day -> days.add(day.get("date").asText() + "=" + day.get("count").asLong()));
        assertThat(days).containsExactly("2026-02-23=1", "2026-02-24=0", "2026-02-25=0", "2026-02-26=0",
                "2026-02-27=2", "2026-02-28=2", "2026-03-01=2");
    }

    @Test
    void shapeMatchesTheFrontendModel() throws Exception {
        seedDataSet();

        JsonNode stats = body(mockMvc.perform(get(STATS).with(TestAuth.merchant(OWNER))));

        List<String> names = new ArrayList<>();
        stats.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("total", "byStatus", "delivered", "failed", "inProgress",
                "last7Days");
        List<String> dayFields = new ArrayList<>();
        stats.get("last7Days").get(0).fieldNames().forEachRemaining(dayFields::add);
        assertThat(dayFields).containsExactlyInAnyOrder("date", "count");
        assertThat(stats.get("last7Days")).hasSize(7);
    }

    @Test
    void merchantWithoutOrdersGetsZeros() throws Exception {
        mockMvc.perform(get(STATS).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.byStatus").isEmpty())
                .andExpect(jsonPath("$.delivered").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.inProgress").value(0))
                .andExpect(jsonPath("$.last7Days.length()").value(7))
                .andExpect(jsonPath("$.last7Days[0].count").value(0))
                .andExpect(jsonPath("$.last7Days[6].date").value("2026-03-01"));
    }

    @Test
    void windowFollowsTheClock() throws Exception {
        seed(OrderStatus.CREATED, "2026-03-01T10:00:00Z");
        clock.advance(Duration.ofDays(2));

        mockMvc.perform(get(STATS).with(TestAuth.merchant(OWNER)))
                .andExpect(jsonPath("$.last7Days[6].date").value("2026-03-03"))
                .andExpect(jsonPath("$.last7Days[4].date").value("2026-03-01"))
                .andExpect(jsonPath("$.last7Days[4].count").value(1));
    }
}
