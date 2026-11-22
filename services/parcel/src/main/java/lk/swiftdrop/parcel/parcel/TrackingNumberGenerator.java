package lk.swiftdrop.parcel.parcel;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TrackingNumberGenerator {

    private static final String PREFIX = "SD";
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray(); // Base32 excluding I/O
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger COUNTER = new AtomicInteger(RANDOM.nextInt(1000));

    public String generate() {
        int year = LocalDate.now().getYear() % 100;
        int seq = Math.abs(COUNTER.incrementAndGet() % 10000);

        char[] randomChars = new char[5];
        for (int i = 0; i < randomChars.length; i++) {
            randomChars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }

        return String.format("%s%02d%04d%s", PREFIX, year, seq, new String(randomChars));
    }
}
