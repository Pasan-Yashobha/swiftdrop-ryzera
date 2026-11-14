package lk.swiftdrop.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import lk.swiftdrop.tracking.push.TrackingStreamService;
import lk.swiftdrop.tracking.store.TrackingStore;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingConfig;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryTrackingConfig.class)
class TrackingServiceApplicationTests {

    @Autowired
    private TrackingStore store;

    @Autowired
    private TrackingStreamService streams;

    @Test
    void contextLoadsWithTheInMemoryStore() {
        assertThat(store).isInstanceOf(InMemoryTrackingStore.class);
        assertThat(streams.isRunning()).isTrue();
    }
}
