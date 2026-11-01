package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Order creation from the merchant portal. */
class OrderCreationPortalTest extends AbstractOrderTest {

    @Test
    void portalOrderIsLiveByDefaultAndBooksAParcel() throws Exception {
        String id = body(createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.sandbox").value(false))
                .andExpect(jsonPath("$.source").value("PORTAL"))
                .andExpect(jsonPath("$.trackingNo").value("SD24000001"))).get("id").asText();

        assertThat(orders.findAll()).singleElement().satisfies(order -> {
            assertThat(order.getId().toString()).isEqualTo(id);
            assertThat(order.getMerchantId()).isEqualTo(merchant.getId());
            assertThat(order.getSource()).isEqualTo(OrderSource.PORTAL);
        });
        verify(parcelClient, times(1)).createParcel(any());
    }

    @Test
    void locationHeaderPointsAtThePortalOrder() throws Exception {
        MvcResult created = createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, null)
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        String location = created.getResponse().getHeader("Location");
        assertThat(location).isEqualTo(PORTAL_ORDERS + "/" + id);
        mockMvc.perform(get(location).with(TestAuth.merchant(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void sandboxSwitchCreatesASimulatedOrder() throws Exception {
        createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), true, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sandbox").value(true))
                .andExpect(jsonPath("$.trackingNo").value(matchesPattern("SBX\\d{9}")))
                .andExpect(jsonPath("$.source").value("PORTAL"));

        verifyNoInteractions(parcelClient);
    }

    @Test
    void sandboxParameterDefaultsToLive() throws Exception {
        mockMvc.perform(post(PORTAL_ORDERS).with(TestAuth.merchant(OWNER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(OrderFixtures.payload("ORDER-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sandbox").value(false));
    }

    @Test
    void sandboxParameterMustBeABoolean() throws Exception {
        mockMvc.perform(post(PORTAL_ORDERS).with(TestAuth.merchant(OWNER)).param("sandbox", "maybe")
                        .contentType(MediaType.APPLICATION_JSON).content(json(OrderFixtures.payload("ORDER-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void repeatingTheRequestReplaysTheOrder() throws Exception {
        String first = createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, "key-1")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String second = createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, "key-1")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second)).isEqualTo(objectMapper.readTree(first));
        verify(parcelClient, times(1)).createParcel(any());
    }

    @Test
    void reusedKeyWithAnotherPayloadIsUnprocessable() throws Exception {
        createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, "key-1").andExpect(status().isCreated());
        Map<String, Object> other = OrderFixtures.payload("ORDER-1");
        other.put("notes", "something else");

        createViaPortal(OWNER, other, false, "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void inFlightKeyIsInUseAndAsksForARetry() throws Exception {
        String hash = RequestFingerprint.of(OrderFixtures.request("ORDER-1"), false);
        orderFixtures.order(merchant).idempotencyKey("key-1", hash).externalRef("ORDER-1")
                .createdAt(Instant.now()).savePlaceholder();

        createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, "key-1")
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_IN_USE"))
                .andExpect(jsonPath("$.path").value(PORTAL_ORDERS));
    }

    @Test
    void duplicateExternalRefIsRejected() throws Exception {
        createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, null).andExpect(status().isCreated());

        createViaPortal(OWNER, OrderFixtures.payload("ORDER-1"), false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REF"));
    }

    @Test
    void validationErrorsNameTheNestedFields() throws Exception {
        Map<String, Object> invalid = OrderFixtures.payload("ORDER-1");
        OrderFixtures.section(invalid, "recipient").put("phone", "abc");
        OrderFixtures.section(invalid, "parcel").put("weightKg", 0);

        createViaPortal(OWNER, invalid, false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field=='recipient.phone')]").isNotEmpty())
                .andExpect(jsonPath("$.details[?(@.field=='parcel.weightKg')]").isNotEmpty());
    }

    @Test
    void unknownParcelSizeIsAMalformedRequest() throws Exception {
        Map<String, Object> invalid = OrderFixtures.payload("ORDER-1");
        OrderFixtures.section(invalid, "parcel").put("size", "HUGE");

        createViaPortal(OWNER, invalid, false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("parcel.size"));
    }
}
