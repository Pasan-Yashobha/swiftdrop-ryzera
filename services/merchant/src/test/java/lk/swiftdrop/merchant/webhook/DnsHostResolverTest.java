package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DnsHostResolverTest {

    private final HostResolver resolver = new DnsHostResolver();

    @Test
    void ipLiteralsResolveToThemselvesWithoutTouchingTheNetwork() throws Exception {
        assertThat(resolver.resolve("93.184.216.34")).singleElement()
                .satisfies(address -> assertThat(address.getHostAddress()).isEqualTo("93.184.216.34"));
        assertThat(resolver.resolve("::1")).singleElement()
                .satisfies(address -> assertThat(address.isLoopbackAddress()).isTrue());
    }

    @Test
    void localhostComesFromTheHostsFileAsALoopbackAddress() throws Exception {
        assertThat(resolver.resolve("localhost")).isNotEmpty().allSatisfy(
                address -> assertThat(address.isLoopbackAddress()).isTrue());
    }
}
