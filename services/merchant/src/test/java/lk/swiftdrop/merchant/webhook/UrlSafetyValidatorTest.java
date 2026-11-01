package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.UnprocessableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyValidatorTest {

    private static final String PRIVATE_MESSAGE = "The webhook URL must not point to a private or internal address";

    /** A resolver that never touches DNS: IP literals resolve to themselves, other names come from this table. */
    private static final Map<String, InetAddress[]> DNS = Map.of(
            "hooks.example.com", addresses("93.184.216.34"),
            "dual.example.com", addresses("93.184.216.34", "2606:2800:220:1:248:1893:25c8:1946"),
            "rebind.example.com", addresses("93.184.216.34", "10.0.0.5"),
            "localtest.example.com", addresses("127.0.0.1"),
            "internal.example.com", addresses("192.168.10.20"),
            // What inet_aton based resolvers make of the octal spelling of 127.0.0.1.
            "0177.0.0.1", addresses("127.0.0.1"));

    private static final HostResolver RESOLVER = host -> {
        if (DNS.containsKey(host)) {
            return DNS.get(host);
        }
        if (host.matches("[0-9.]+") || host.contains(":")) {
            return InetAddress.getAllByName(host);
        }
        throw new UnknownHostException(host);
    };

    private final UrlSafetyValidator strict = validator(false, false);

    @Test
    void acceptsAnHttpsUrlThatResolvesToPublicAddresses() {
        URI uri = strict.validate("https://hooks.example.com/hooks/swiftdrop?x=1");

        assertThat(uri).hasToString("https://hooks.example.com/hooks/swiftdrop?x=1");
    }

    @Test
    void acceptsHostsWithSeveralPublicAddresses() {
        assertThat(strict.validate("https://dual.example.com:8443/h")).hasPort(8443);
    }

    @Test
    void acceptsAPublicIpLiteralAndTheSchemeInAnyCase() {
        assertThat(strict.validate("HTTPS://93.184.216.34/hook")).hasHost("93.184.216.34");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/hook",
            "https://127.255.255.254/hook",
            "https://[::1]/hook",
            "https://[0:0:0:0:0:0:0:1]:8443/hook",
            "https://10.0.0.5/hook",
            "https://172.16.0.1/hook",
            "https://172.31.255.254/hook",
            "https://192.168.1.10/hook",
            "https://169.254.169.254/latest/meta-data/",
            "https://169.254.0.1/hook",
            "https://[fe80::1]/hook",
            "https://[fd12:3456:789a::1]/hook",
            "https://[fc00::1]/hook",
            "https://224.0.0.1/hook",
            "https://239.255.255.250/hook",
            "https://[ff02::1]/hook",
            "https://0.0.0.0/hook",
            "https://[::]/hook",
            "https://0.1.2.3/hook",
            "https://100.64.0.1/hook",
            "https://100.100.100.200/latest/meta-data/",
            "https://[::ffff:127.0.0.1]/hook",
            "https://[::ffff:10.1.2.3]/hook",
            "https://[::ffff:169.254.169.254]/hook",
            "https://[::127.0.0.1]/hook"})
    void rejectsLoopbackPrivateLinkLocalMulticastAndMetadataAddresses(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .isInstanceOfSatisfying(UnprocessableException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.WEBHOOK_URL_REJECTED);
                    assertThat(e.getMessage()).isEqualTo(PRIVATE_MESSAGE);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://2130706433/hook",
            "https://127.1/hook",
            "https://017700000001/hook",
            "https://0x7f000001/hook",
            "https://0x7f.1/hook",
            "https://0x7f.0.0.1/hook"})
    void rejectsNumericSpellingsOfLoopbackAddresses(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.WEBHOOK_URL_REJECTED));
    }

    @Test
    void judgesTheResolvedAddressNotTheSpelling() {
        assertThatThrownBy(() -> strict.validate("https://0177.0.0.1/hook")).hasMessage(PRIVATE_MESSAGE);
    }

    @Test
    void rejectsIpv4EmbeddedInIpv6EvenWhenTheResolverDoesNotUnwrapIt() throws Exception {
        byte[] mappedLoopback = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 127, 0, 0, 1};
        byte[] mappedPublic = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 93, (byte) 184, (byte) 216, 34};
        UrlSafetyValidator loopbackHost = new UrlSafetyValidator(WebhookTestProperties.withUrlRules(false, false),
                host -> new InetAddress[] {Inet6Address.getByAddress(host, mappedLoopback, -1)});
        UrlSafetyValidator publicHost = new UrlSafetyValidator(WebhookTestProperties.withUrlRules(false, false),
                host -> new InetAddress[] {Inet6Address.getByAddress(host, mappedPublic, -1)});

        assertThatThrownBy(() -> loopbackHost.validate("https://hooks.example.com/h"))
                .hasMessage(PRIVATE_MESSAGE);
        assertThat(publicHost.validate("https://hooks.example.com/h")).hasHost("hooks.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localtest.example.com/hook", "https://internal.example.com/hook"})
    void rejectsNamesThatResolveToInternalAddresses(String url) {
        assertThatThrownBy(() -> strict.validate(url)).hasMessage(PRIVATE_MESSAGE);
    }

    @Test
    void rejectsAHostWhenAnyOfItsAddressesIsInternal() {
        assertThatThrownBy(() -> strict.validate("https://rebind.example.com/hook")).hasMessage(PRIVATE_MESSAGE);
    }

    @Test
    void rejectsAHostThatDoesNotResolve() {
        assertThatThrownBy(() -> strict.validate("https://nowhere.example.com/hook"))
                .isInstanceOfSatisfying(UnprocessableException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.WEBHOOK_URL_REJECTED);
                    assertThat(e.getMessage()).isEqualTo("The webhook URL host could not be resolved");
                });
    }

    @Test
    void allowPrivateHostsPermitsInternalAddressesButNotUnresolvableHosts() {
        UrlSafetyValidator lenient = validator(false, true);

        assertThat(lenient.validate("https://127.0.0.1:9000/hook")).hasPort(9000);
        assertThat(lenient.validate("https://[::1]/hook")).hasHost("[::1]");
        assertThat(lenient.validate("https://169.254.169.254/hook")).hasHost("169.254.169.254");
        assertThat(lenient.validate("https://rebind.example.com/hook")).hasHost("rebind.example.com");
        assertThatThrownBy(() -> lenient.validate("https://nowhere.example.com/hook"))
                .hasMessage("The webhook URL host could not be resolved");
    }

    @Test
    void httpIsRejectedUnlessInsecureUrlsAreAllowed() {
        assertThatThrownBy(() -> strict.validate("http://hooks.example.com/hook"))
                .isInstanceOfSatisfying(UnprocessableException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.WEBHOOK_URL_REJECTED);
                    assertThat(e.getMessage()).isEqualTo("The webhook URL must start with https://");
                });

        assertThat(validator(true, false).validate("http://hooks.example.com/hook")).hasScheme("http");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://hooks.example.com/hook",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "gopher://hooks.example.com/",
            "hooks.example.com/hook",
            "//hooks.example.com/hook",
            "/hooks/swiftdrop"})
    void rejectsOtherSchemesAndRelativeUrlsEvenWhenInsecureUrlsAreAllowed(String url) {
        UrlSafetyValidator insecure = validator(true, true);

        assertThatThrownBy(() -> insecure.validate(url))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("The webhook URL must start with https:// or http://"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://user:secretpw@hooks.example.com/hook",
            "https://user@hooks.example.com/hook",
            "https://:secretpw@hooks.example.com/hook"})
    void rejectsCredentialsInTheUrlWithoutEchoingThem(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .isInstanceOfSatisfying(UnprocessableException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("The webhook URL must not contain credentials");
                    assertThat(e.getMessage()).doesNotContain("secretpw");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"https:///hook", "https://:443/hook", "https:hooks.example.com", "https://exa_mple.com/hook"})
    void rejectsUrlsWithoutAUsableHost(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .hasMessage("The webhook URL must contain a valid host name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://hooks.example.com:0/hook", "https://hooks.example.com:65536/hook",
            "https://hooks.example.com:99999/hook"})
    void rejectsPortsOutsideTheValidRange(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .hasMessage("The webhook URL port must be between 1 and 65535");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://hooks.example.com:1/hook", "https://hooks.example.com:65535/hook"})
    void acceptsTheBoundaryPorts(String url) {
        assertThat(strict.validate(url)).hasHost("hooks.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://hooks.example.com/a b", "https://hooks.example .com/", "https://hooks.example.com/<x>",
            "https://hooks.example.com\\@127.0.0.1/", "https://hooks.example.com/\n"})
    void rejectsSyntacticallyInvalidUrls(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .hasMessage("The webhook URL is not a valid URL");
    }

    @Test
    void acceptsUrlsOfExactlyTheMaximumLengthAndRejectsLongerOnes() {
        String prefix = "https://hooks.example.com/";
        String atLimit = prefix + "a".repeat(UrlSafetyValidator.MAX_LENGTH - prefix.length());

        assertThat(strict.validate(atLimit).toString()).hasSize(500);
        assertThatThrownBy(() -> strict.validate(atLimit + "a"))
                .hasMessage("The webhook URL must be at most 500 characters long");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void rejectsMissingUrls(String url) {
        assertThatThrownBy(() -> strict.validate(url)).hasMessage("The webhook URL is required");
    }

    @Test
    void rejectionMessagesNeverEchoTheUrl() {
        String url = "http://internal.example.com/hook?token=tok_live_12345";

        assertThatThrownBy(() -> strict.validate(url))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("tok_live_12345", "internal.example.com"));
        assertThatThrownBy(() -> validator(true, false).validate(url))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("tok_live_12345", "internal.example.com"));
    }

    private static UrlSafetyValidator validator(boolean allowInsecureUrls, boolean allowPrivateHosts) {
        return new UrlSafetyValidator(WebhookTestProperties.withUrlRules(allowInsecureUrls, allowPrivateHosts), RESOLVER);
    }

    private static InetAddress[] addresses(String... literals) {
        try {
            InetAddress[] result = new InetAddress[literals.length];
            for (int i = 0; i < literals.length; i++) {
                result[i] = InetAddress.getByName(literals[i]);
            }
            return result;
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
