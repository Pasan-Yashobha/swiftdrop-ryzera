package lk.swiftdrop.merchant.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces DNS in integration tests: a few fixed names, IP literals resolve to themselves, all else is unknown. */
@TestConfiguration(proxyBeanMethods = false)
class FakeDnsConfig {

    static final String PUBLIC_HOST = "hooks.example.com";
    static final String OTHER_PUBLIC_HOST = "shop.example.org";
    static final String MIXED_HOST = "rebind.example.com";
    static final String INTERNAL_HOST = "internal.example.com";

    private static final Map<String, String[]> HOSTS = Map.of(
            PUBLIC_HOST, new String[] {"93.184.216.34"},
            OTHER_PUBLIC_HOST, new String[] {"198.51.100.7"},
            MIXED_HOST, new String[] {"93.184.216.34", "10.0.0.5"},
            INTERNAL_HOST, new String[] {"192.168.1.5"});

    @Bean
    @Primary
    HostResolver fakeDns() {
        return host -> {
            String[] literals = HOSTS.get(host);
            if (literals != null) {
                InetAddress[] addresses = new InetAddress[literals.length];
                for (int i = 0; i < literals.length; i++) {
                    addresses[i] = InetAddress.getByName(literals[i]);
                }
                return addresses;
            }
            if (host.matches("[0-9.]+") || host.contains(":")) {
                return InetAddress.getAllByName(host);
            }
            throw new UnknownHostException(host);
        };
    }
}
