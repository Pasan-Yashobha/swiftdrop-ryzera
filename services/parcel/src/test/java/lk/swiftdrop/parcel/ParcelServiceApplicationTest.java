package lk.swiftdrop.parcel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelServiceApplicationTest {

    @Test
    void applicationClassLoads() {
        assertThat(ParcelServiceApplication.class).isNotNull();
    }
}
