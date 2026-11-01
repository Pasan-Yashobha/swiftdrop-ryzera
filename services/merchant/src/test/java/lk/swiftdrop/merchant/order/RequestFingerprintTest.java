package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.OrderAddress;
import lk.swiftdrop.merchant.order.dto.OrderParcel;
import lk.swiftdrop.merchant.order.dto.OrderRecipient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RequestFingerprintTest {

    private static CreateOrderRequest base() {
        return OrderFixtures.request("ORDER-1");
    }

    @Test
    void isLowerCaseHexSha256() {
        assertThat(RequestFingerprint.of(base(), false)).matches("[0-9a-f]{64}");
    }

    @Test
    void sameRequestSameFingerprint() {
        assertThat(RequestFingerprint.of(base(), false)).isEqualTo(RequestFingerprint.of(base(), false));
    }

    @Test
    void sandboxFlagIsPartOfTheFingerprint() {
        assertThat(RequestFingerprint.of(base(), true)).isNotEqualTo(RequestFingerprint.of(base(), false));
    }

    @Test
    void weightNotationDoesNotMatter() {
        CreateOrderRequest other = new CreateOrderRequest("ORDER-1", base().recipient(), base().pickup(),
                base().dropoff(), new OrderParcel(new BigDecimal("2.50"), ParcelSize.SMALL, "Phone case"),
                "Call on arrival");

        assertThat(RequestFingerprint.of(other, false)).isEqualTo(RequestFingerprint.of(base(), false));
    }

    @Test
    void absentAndBlankOptionalFieldsAreTheSame() {
        CreateOrderRequest absent = new CreateOrderRequest(null, base().recipient(),
                new OrderAddress("12 Galle Rd", "Colombo", null), base().dropoff(),
                new OrderParcel(new BigDecimal("2.50"), ParcelSize.SMALL, null), null);
        CreateOrderRequest blank = new CreateOrderRequest("  ", base().recipient(),
                new OrderAddress("12 Galle Rd", "Colombo", "   "), base().dropoff(),
                new OrderParcel(new BigDecimal("2.50"), ParcelSize.SMALL, " "), "");

        assertThat(RequestFingerprint.of(blank, false)).isEqualTo(RequestFingerprint.of(absent, false));
    }

    @Test
    void valuesCannotRunIntoEachOther() {
        CreateOrderRequest a = new CreateOrderRequest(null, new OrderRecipient("Ann", "+94771234567"),
                new OrderAddress("1 A", "BC", null), base().dropoff(), base().parcel(), null);
        CreateOrderRequest b = new CreateOrderRequest(null, new OrderRecipient("Ann", "+94771234567"),
                new OrderAddress("1 AB", "C", null), base().dropoff(), base().parcel(), null);

        assertThat(RequestFingerprint.of(a, false)).isNotEqualTo(RequestFingerprint.of(b, false));
    }

    static Stream<CreateOrderRequest> changedRequests() {
        CreateOrderRequest base = base();
        return Stream.of(
                OrderFixtures.request("ORDER-2"),
                new CreateOrderRequest("ORDER-1", new OrderRecipient("Other", "+94771234567"), base.pickup(),
                        base.dropoff(), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", new OrderRecipient("Nimali Perera", "+94770000000"), base.pickup(),
                        base.dropoff(), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), new OrderAddress("13 Galle Rd", "Colombo", "00300"),
                        base.dropoff(), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), new OrderAddress("12 Galle Rd", "Galle", "00300"),
                        base.dropoff(), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), new OrderAddress("12 Galle Rd", "Colombo", "00301"),
                        base.dropoff(), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(),
                        new OrderAddress("46 Temple St", "Kandy", "20000"), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(),
                        new OrderAddress("45 Temple St", "Matale", "20000"), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(),
                        new OrderAddress("45 Temple St", "Kandy", "20001"), base.parcel(), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(), base.dropoff(),
                        new OrderParcel(new BigDecimal("2.51"), ParcelSize.SMALL, "Phone case"), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(), base.dropoff(),
                        new OrderParcel(new BigDecimal("2.5"), ParcelSize.LARGE, "Phone case"), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(), base.dropoff(),
                        new OrderParcel(new BigDecimal("2.5"), ParcelSize.SMALL, "Tablet"), base.notes()),
                new CreateOrderRequest("ORDER-1", base.recipient(), base.pickup(), base.dropoff(), base.parcel(),
                        "Leave at the gate"));
    }

    @ParameterizedTest
    @MethodSource("changedRequests")
    void everyFieldChangesTheFingerprint(CreateOrderRequest changed) {
        assertThat(RequestFingerprint.of(changed, false)).isNotEqualTo(RequestFingerprint.of(base(), false));
    }
}
