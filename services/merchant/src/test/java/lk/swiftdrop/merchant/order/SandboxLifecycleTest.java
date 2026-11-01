package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lk.swiftdrop.merchant.event.OutboxRepository;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Sandbox orders: created with a TEST credential and moved along by the merchant itself. */
class SandboxLifecycleTest extends AbstractOrderTest {

    @Autowired
    private OutboxRepository outbox;

    private IssuedKey key;
    private String trackingNo;

    @BeforeEach
    void sandboxOrder() throws Exception {
        key = testKey();
        trackingNo = body(createViaApi(key, OrderFixtures.payload("ORDER-1"), null)
                .andExpect(status().isCreated())).get("trackingNo").asText();
    }

    private ResultActions advance(String body) throws Exception {
        var request = post(SANDBOX_ORDERS + "/" + trackingNo + "/advance").header("X-API-Key", key.rawKey());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    @Test
    void orderWalksThroughTheWholeDeliveryOneStepPerCall() throws Exception {
        List<String> expected = List.of("ASSIGNED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");

        for (String status : expected) {
            advance(null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(status))
                    .andExpect(jsonPath("$.trackingNo").value(trackingNo))
                    .andExpect(jsonPath("$.sandbox").value(true))
                    .andExpect(jsonPath("$.failureReason").doesNotExist());
        }

        mockMvc.perform(get(API_ORDERS + "/" + trackingNo).header("X-API-Key", key.rawKey()))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        MerchantOrder stored = orders.findAll().get(0);
        assertThat(history.findByOrderIdOrderByOccurredAtAscIdAsc(stored.getId()))
                .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus)
                .containsExactlyInAnyOrder(
                        tuple(OrderStatus.CREATED, OrderStatus.ASSIGNED),
                        tuple(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP),
                        tuple(OrderStatus.PICKED_UP, OrderStatus.IN_TRANSIT),
                        tuple(OrderStatus.IN_TRANSIT, OrderStatus.OUT_FOR_DELIVERY),
                        tuple(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED));
    }

    @Test
    void advanceWithAnEmptyJsonBodyAlsoTakesTheNextStep() throws Exception {
        advance("{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        advance("{\"outcome\":\"NEXT\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void deliveredOrderCannotBeAdvancedAnyFurther() throws Exception {
        for (int i = 0; i < 5; i++) {
            advance(null).andExpect(status().isOk());
        }

        advance(null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        advance("{\"outcome\":\"FAILED\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertThat(orders.findAll().get(0).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void failedOutcomeEndsTheOrderWithTheGivenReason() throws Exception {
        advance(null).andExpect(status().isOk());

        advance("{\"outcome\":\"FAILED\",\"reason\":\"  Recipient not at home \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Recipient not at home"));

        advance(null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        MerchantOrder stored = orders.findAll().get(0);
        assertThat(stored.getFailureReason()).isEqualTo("Recipient not at home");
        assertThat(history.findByOrderIdOrderByOccurredAtAscIdAsc(stored.getId()))
                .extracting(OrderStatusHistory::getToStatus, OrderStatusHistory::getNote)
                .contains(tuple(OrderStatus.FAILED, "Recipient not at home"));
    }

    @Test
    void failedOutcomeWithoutAReasonGetsADefaultOne() throws Exception {
        advance("{\"outcome\":\"FAILED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Delivery failed (sandbox)"));
    }

    @Test
    void invalidAdvanceBodiesAreRejected() throws Exception {
        advance("{\"outcome\":\"EXPLODED\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        advance("{\"outcome\":\"FAILED\",\"reason\":\"" + "r".repeat(301) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("reason"));
        advance("{\"outcome\":\"FAILED\",\"reason\":\"line1\\nline2\"}").andExpect(status().isBadRequest());

        assertThat(orders.findAll().get(0).getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void unknownTrackingNumberIsNotFound() throws Exception {
        mockMvc.perform(post(SANDBOX_ORDERS + "/SBX000000000/advance").header("X-API-Key", key.rawKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void liveOrdersCannotBeAdvancedEvenWithATestCredential() throws Exception {
        orderFixtures.order(merchant).trackingNo("SD-LIVE").save();

        mockMvc.perform(post(SANDBOX_ORDERS + "/SD-LIVE/advance").header("X-API-Key", key.rawKey()))
                .andExpect(status().isNotFound());

        assertThat(orders.findAll()).extracting(MerchantOrder::getStatus).containsOnly(OrderStatus.CREATED);
    }

    @Test
    void advancingNeitherCallsTheParcelServiceNorPublishesEvents() throws Exception {
        advance(null).andExpect(status().isOk());
        advance("{\"outcome\":\"FAILED\"}").andExpect(status().isOk());

        verifyNoInteractions(parcelClient);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void portalShowsTheTimelineOfASandboxOrder() throws Exception {
        advance(null).andExpect(status().isOk());
        advance(null).andExpect(status().isOk());
        String id = orders.findAll().get(0).getId().toString();

        mockMvc.perform(get(PORTAL_ORDERS + "/" + id).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].fromStatus").value("CREATED"))
                .andExpect(jsonPath("$.history[0].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.history[1].toStatus").value("PICKED_UP"))
                .andExpect(jsonPath("$.history[1].occurredAt").isString());
    }
}
