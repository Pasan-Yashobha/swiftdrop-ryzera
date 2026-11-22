package lk.swiftdrop.parcel.parcel;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingNumberGeneratorTest {

    private TrackingNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TrackingNumberGenerator();
    }

    @Test
    @DisplayName("Generated tracking number adheres to the standard SD prefix and length")
    void testFormatAndPrefix() {
        String trackingNumber = generator.generate();

        assertThat(trackingNumber).isNotNull();
        assertThat(trackingNumber).startsWith("SD");

        int year = LocalDate.now().getYear() % 100;
        assertThat(trackingNumber).startsWith(String.format("SD%02d", year));
        assertThat(trackingNumber).hasSize(13);
        assertThat(trackingNumber).matches("^SD[0-9]{6}[0-9A-Z]{5}$");
    }

    @Test
    @DisplayName("Generates unique tracking numbers in rapid sequential loop")
    void testSequentialUniqueness() {
        int count = 1000;
        Set<String> generated = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            String trackingNumber = generator.generate();
            boolean added = generated.add(trackingNumber);
            assertThat(added)
                    .withFailMessage("Duplicate tracking number detected: %s", trackingNumber)
                    .isTrue();
        }

        assertThat(generated).hasSize(count);
    }

    @Test
    @DisplayName("Generates unique tracking numbers under concurrent multithreaded load")
    void testConcurrentUniqueness() throws InterruptedException {
        int threadCount = 10;
        int perThread = 100;
        int totalExpected = threadCount * perThread;

        Set<String> concurrentSet = ConcurrentHashMap.newKeySet(totalExpected);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        concurrentSet.add(generator.generate());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(concurrentSet).hasSize(totalExpected);
    }
}
