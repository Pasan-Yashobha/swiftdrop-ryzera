package lk.swiftdrop.merchant.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Random;
import lk.swiftdrop.merchant.order.dto.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Stands in for the parcel service in sandbox mode: no network call, tracking numbers start with {@code SBX}. */
@Component
class SandboxSimulator {

    static final String TRACKING_PREFIX = "SBX";
    static final String CURRENCY = "LKR";

    private static final int TRACKING_DIGITS = 9;
    private static final int TRACKING_BOUND = 1_000_000_000;
    private static final BigDecimal BASE_FEE = new BigDecimal("350.00");
    private static final BigDecimal FEE_PER_KG = new BigDecimal("100.00");

    private final Random random;

    @Autowired
    SandboxSimulator() {
        this(new SecureRandom());
    }

    /** For tests: a seeded {@link Random} makes the tracking numbers predictable. */
    SandboxSimulator(Random random) {
        this.random = random;
    }

    /** "SBX" followed by nine random digits. */
    String newTrackingNo() {
        return TRACKING_PREFIX + String.format("%0" + TRACKING_DIGITS + "d", random.nextInt(TRACKING_BOUND));
    }

    /** 350.00 plus 100.00 per kilogram. */
    Money feeFor(BigDecimal weightKg) {
        BigDecimal amount = BASE_FEE.add(FEE_PER_KG.multiply(weightKg)).setScale(2, RoundingMode.HALF_UP);
        return new Money(amount, CURRENCY);
    }
}
