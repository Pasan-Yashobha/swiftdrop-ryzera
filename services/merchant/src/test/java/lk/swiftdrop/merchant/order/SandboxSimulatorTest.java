package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Random;
import lk.swiftdrop.merchant.order.dto.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SandboxSimulatorTest {

    @Test
    void seededRandomnessGivesPredictableTrackingNumbers() {
        SandboxSimulator first = new SandboxSimulator(new Random(42));
        SandboxSimulator second = new SandboxSimulator(new Random(42));

        String expected = "SBX" + String.format("%09d", new Random(42).nextInt(1_000_000_000));
        assertThat(first.newTrackingNo()).isEqualTo(expected);
        assertThat(second.newTrackingNo()).isEqualTo(expected);
        assertThat(first.newTrackingNo()).isEqualTo(second.newTrackingNo());
    }

    @Test
    void trackingNumbersAreSbxAndNineDigitsIncludingLeadingZeros() {
        SandboxSimulator simulator = new SandboxSimulator(new Random(1) {
            @Override
            public int nextInt(int bound) {
                return 42;
            }
        });

        assertThat(simulator.newTrackingNo()).isEqualTo("SBX000000042");
    }

    @Test
    void defaultConstructorGeneratesWellFormedNumbers() {
        SandboxSimulator simulator = new SandboxSimulator();

        for (int i = 0; i < 50; i++) {
            assertThat(simulator.newTrackingNo()).matches("SBX\\d{9}");
        }
    }

    @ParameterizedTest
    @CsvSource({"0.01,351.00", "1,450.00", "2.50,600.00", "10.00,1350.00", "50.00,5350.00", "0.33,383.00"})
    void feeIs350PlusHundredPerKilogram(String weightKg, String expectedFee) {
        Money fee = new SandboxSimulator(new Random(1)).feeFor(new BigDecimal(weightKg));

        assertThat(fee.amount()).isEqualByComparingTo(expectedFee);
        assertThat(fee.amount().scale()).isEqualTo(2);
        assertThat(fee.currency()).isEqualTo("LKR");
    }
}
