package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Every validation rule of CONTRACT section 5.4, applied to the JSON body exactly as the API receives it. */
class CreateOrderRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Set<String> invalidFields(Consumer<Map<String, Object>> change) throws Exception {
        Map<String, Object> payload = OrderFixtures.payload("ORDER-1001");
        change.accept(payload);
        CreateOrderRequest request = MAPPER.readValue(MAPPER.writeValueAsString(payload), CreateOrderRequest.class);
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static Consumer<Map<String, Object>> set(String section, String field, Object value) {
        return payload -> OrderFixtures.section(payload, section).put(field, value);
    }

    private static Arguments invalid(String name, Consumer<Map<String, Object>> change, String expectedField) {
        return Arguments.of(name, change, expectedField);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("externalRef with a space", p -> p.put("externalRef", "ORDER 1"), "externalRef"),
                invalid("externalRef with a quote", p -> p.put("externalRef", "O'1"), "externalRef"),
                invalid("externalRef with non ASCII letters", p -> p.put("externalRef", "ÖRDER"), "externalRef"),
                invalid("externalRef longer than 100", p -> p.put("externalRef", "A".repeat(101)), "externalRef"),
                invalid("notes longer than 500", p -> p.put("notes", "n".repeat(501)), "notes"),
                invalid("notes with a line break", p -> p.put("notes", "line1\nline2"), "notes"),
                invalid("missing recipient", p -> p.remove("recipient"), "recipient"),
                invalid("blank recipient name", set("recipient", "name", "   "), "recipient.name"),
                invalid("missing recipient name", p -> OrderFixtures.section(p, "recipient").remove("name"),
                        "recipient.name"),
                invalid("recipient name longer than 120", set("recipient", "name", "N".repeat(121)),
                        "recipient.name"),
                invalid("recipient name with a control character", set("recipient", "name", "Nim\u0007ali"),
                        "recipient.name"),
                invalid("blank phone", set("recipient", "phone", ""), "recipient.phone"),
                invalid("phone with letters", set("recipient", "phone", "077-ABC-4567"), "recipient.phone"),
                invalid("phone too short", set("recipient", "phone", "+9477123"), "recipient.phone"),
                invalid("phone too long", set("recipient", "phone", "+9477123456789012"), "recipient.phone"),
                invalid("phone with a second plus", set("recipient", "phone", "+94+771234567"), "recipient.phone"),
                invalid("missing pickup", p -> p.remove("pickup"), "pickup"),
                invalid("blank pickup address", set("pickup", "addressLine", " "), "pickup.addressLine"),
                invalid("pickup address longer than 300", set("pickup", "addressLine", "A".repeat(301)),
                        "pickup.addressLine"),
                invalid("pickup address with a tab", set("pickup", "addressLine", "12\tGalle Rd"),
                        "pickup.addressLine"),
                invalid("blank pickup city", set("pickup", "city", ""), "pickup.city"),
                invalid("pickup city longer than 100", set("pickup", "city", "C".repeat(101)), "pickup.city"),
                invalid("pickup postal code longer than 16", set("pickup", "postalCode", "1".repeat(17)),
                        "pickup.postalCode"),
                invalid("missing dropoff", p -> p.remove("dropoff"), "dropoff"),
                invalid("blank dropoff address", set("dropoff", "addressLine", ""), "dropoff.addressLine"),
                invalid("dropoff address longer than 300", set("dropoff", "addressLine", "A".repeat(301)),
                        "dropoff.addressLine"),
                invalid("blank dropoff city", set("dropoff", "city", ""), "dropoff.city"),
                invalid("dropoff city longer than 100", set("dropoff", "city", "C".repeat(101)), "dropoff.city"),
                invalid("dropoff postal code longer than 16", set("dropoff", "postalCode", "1".repeat(17)),
                        "dropoff.postalCode"),
                invalid("missing parcel", p -> p.remove("parcel"), "parcel"),
                invalid("missing weight", p -> OrderFixtures.section(p, "parcel").remove("weightKg"),
                        "parcel.weightKg"),
                invalid("weight zero", set("parcel", "weightKg", 0), "parcel.weightKg"),
                invalid("weight below 0.01", set("parcel", "weightKg", new BigDecimal("0.001")), "parcel.weightKg"),
                invalid("negative weight", set("parcel", "weightKg", -1), "parcel.weightKg"),
                invalid("weight above 50", set("parcel", "weightKg", new BigDecimal("50.01")), "parcel.weightKg"),
                invalid("weight with three decimals", set("parcel", "weightKg", new BigDecimal("2.505")),
                        "parcel.weightKg"),
                invalid("absurdly large weight", set("parcel", "weightKg", new BigDecimal("1E+999999999")),
                        "parcel.weightKg"),
                invalid("description longer than 200", set("parcel", "description", "d".repeat(201)),
                        "parcel.description"),
                invalid("description with a control character", set("parcel", "description", "a\u0000b"),
                        "parcel.description"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidInput(String name, Consumer<Map<String, Object>> change, String expectedField)
            throws Exception {
        assertThat(invalidFields(change)).containsExactly(expectedField);
    }

    @Test
    void validRequestHasNoViolations() throws Exception {
        assertThat(invalidFields(payload -> { })).isEmpty();
    }

    @Test
    void optionalFieldsMayBeMissingOrBlank() throws Exception {
        assertThat(invalidFields(payload -> {
            payload.remove("externalRef");
            payload.remove("notes");
            OrderFixtures.section(payload, "pickup").remove("postalCode");
            OrderFixtures.section(payload, "dropoff").put("postalCode", "  ");
            OrderFixtures.section(payload, "parcel").remove("description");
        })).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "order-1", "ORDER_1", "a.b:c/d#e-f", "0123456789"})
    void externalRefAllowsTheDocumentedCharacters(String externalRef) throws Exception {
        assertThat(invalidFields(payload -> payload.put("externalRef", externalRef))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.1", "1", "2.5", "50", "50.00", "49.99", "10.000"})
    void weightBoundariesAreAccepted(String weightKg) throws Exception {
        assertThat(invalidFields(set("parcel", "weightKg", new BigDecimal(weightKg)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+94771234567", "0771234567", "94771234567", "+94 77 123 4567", "(077) 123-4567",
            "+94-77-123-4567", "123456789", "+123456789012345"})
    void phoneNumbersAreAcceptedAfterNormalisation(String phone) throws Exception {
        assertThat(invalidFields(set("recipient", "phone", phone))).isEmpty();
    }

    @Test
    void sizeMustBeOneOfTheThreeParcelSizes() {
        String json = """
                {"recipient":{"name":"A","phone":"+94771234567"},"pickup":{"addressLine":"a","city":"c"},
                 "dropoff":{"addressLine":"a","city":"c"},"parcel":{"weightKg":1,"size":"HUGE"}}""";

        assertThatThrownBy(() -> MAPPER.readValue(json, CreateOrderRequest.class))
                .hasMessageContaining("ParcelSize");
    }

    @Test
    void sizeIsRequired() throws Exception {
        assertThat(invalidFields(payload -> OrderFixtures.section(payload, "parcel").remove("size")))
                .containsExactly("parcel.size");
    }

    @Test
    void textIsTrimmedAndBlankOptionalFieldsBecomeNull() throws Exception {
        Map<String, Object> payload = OrderFixtures.payload("  ORDER-1001  ");
        payload.put("notes", "   ");
        OrderFixtures.section(payload, "recipient").put("name", "  Nimali Perera ");
        OrderFixtures.section(payload, "recipient").put("phone", " +94 (77) 123-4567 ");
        OrderFixtures.section(payload, "pickup").put("postalCode", "  ");
        OrderFixtures.section(payload, "parcel").put("weightKg", new BigDecimal("2.500"));
        OrderFixtures.section(payload, "parcel").put("description", "");

        CreateOrderRequest request = MAPPER.readValue(MAPPER.writeValueAsString(payload), CreateOrderRequest.class);

        assertThat(request.externalRef()).isEqualTo("ORDER-1001");
        assertThat(request.notes()).isNull();
        assertThat(request.recipient().name()).isEqualTo("Nimali Perera");
        assertThat(request.recipient().phone()).isEqualTo("+94771234567");
        assertThat(request.pickup().postalCode()).isNull();
        assertThat(request.parcel().weightKg()).isEqualTo(new BigDecimal("2.50"));
        assertThat(request.parcel().description()).isNull();
        Set<ConstraintViolation<CreateOrderRequest>> violations = VALIDATOR.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void weightIsAlwaysNormalisedToTwoDecimals() throws Exception {
        for (String weight : new String[] {"1", "1.5", "1.50", "1.500", "10", "10.0"}) {
            Map<String, Object> payload = OrderFixtures.payload();
            OrderFixtures.section(payload, "parcel").put("weightKg", new BigDecimal(weight));
            CreateOrderRequest request = MAPPER.readValue(MAPPER.writeValueAsString(payload), CreateOrderRequest.class);

            assertThat(request.parcel().weightKg().scale()).as(weight).isEqualTo(2);
            assertThat(request.parcel().weightKg()).as(weight).isEqualByComparingTo(weight);
        }
    }
}
