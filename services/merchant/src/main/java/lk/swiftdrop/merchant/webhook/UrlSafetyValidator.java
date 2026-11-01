package lk.swiftdrop.merchant.webhook;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Decides whether a merchant supplied webhook URL may be called by this service (CONTRACT section 5.5). The
 * checks keep a merchant from aiming the service at its own network: only https, no credentials in the URL, and
 * every address the host resolves to must be a public one. Numeric tricks such as {@code 2130706433} or
 * {@code 0x7f.1} are caught because the judgement is made on the resolved addresses, not on the text.
 *
 * <p>Messages never repeat the URL, which may contain tokens.
 */
@Component
public class UrlSafetyValidator {

    public static final int MAX_LENGTH = 500;

    private final boolean allowInsecureUrls;
    private final boolean allowPrivateHosts;
    private final HostResolver resolver;

    public UrlSafetyValidator(AppProperties properties, HostResolver resolver) {
        this.allowInsecureUrls = properties.webhook().allowInsecureUrls();
        this.allowPrivateHosts = properties.webhook().allowPrivateHosts();
        this.resolver = resolver;
    }

    /**
     * @return the parsed URL
     * @throws UnprocessableException {@code WEBHOOK_URL_REJECTED} when the URL is not acceptable
     */
    public URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw rejected("The webhook URL is required");
        }
        if (url.length() > MAX_LENGTH) {
            throw rejected("The webhook URL must be at most " + MAX_LENGTH + " characters long");
        }
        URI uri = parse(url);
        requireAllowedScheme(uri);
        if (uri.getRawUserInfo() != null) {
            throw rejected("The webhook URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw rejected("The webhook URL must contain a valid host name");
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw rejected("The webhook URL port must be between 1 and 65535");
        }
        requirePublicAddresses(host);
        return uri;
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw rejected("The webhook URL is not a valid URL");
        }
    }

    private void requireAllowedScheme(URI uri) {
        String scheme = uri.getScheme();
        boolean allowed = "https".equalsIgnoreCase(scheme) || (allowInsecureUrls && "http".equalsIgnoreCase(scheme));
        if (!allowed) {
            throw rejected(allowInsecureUrls
                    ? "The webhook URL must start with https:// or http://"
                    : "The webhook URL must start with https://");
        }
    }

    private void requirePublicAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException e) {
            throw rejected("The webhook URL host could not be resolved");
        }
        if (!allowPrivateHosts && Arrays.stream(addresses).anyMatch(UrlSafetyValidator::isInternal)) {
            throw rejected("The webhook URL must not point to a private or internal address");
        }
    }

    /**
     * Loopback, link-local (which includes the 169.254.169.254 cloud metadata address), site-local, multicast
     * and wildcard addresses, plus the IPv6 unique-local range, "this network" (0.0.0.0/8), shared address space
     * (100.64.0.0/10, used by some clouds for metadata) and IPv4 addresses embedded in IPv6.
     */
    private static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] octets = address.getAddress();
        if (address instanceof Inet4Address) {
            return octets[0] == 0 || (octets[0] == 100 && (octets[1] & 0xC0) == 64);
        }
        boolean uniqueLocal = (octets[0] & 0xFE) == 0xFC;
        return uniqueLocal || (embedsIpv4(octets) && isInternal(embeddedIpv4(octets)));
    }

    /** IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible (::a.b.c.d) addresses. */
    private static boolean embedsIpv4(byte[] octets) {
        for (int i = 0; i < 10; i++) {
            if (octets[i] != 0) {
                return false;
            }
        }
        return octets[10] == octets[11] && (octets[10] == 0 || octets[10] == (byte) 0xFF);
    }

    private static InetAddress embeddedIpv4(byte[] octets) {
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(octets, 12, 16));
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Four bytes are always a valid IPv4 address", e);
        }
    }

    private static UnprocessableException rejected(String message) {
        return new UnprocessableException(ErrorCode.WEBHOOK_URL_REJECTED, message);
    }
}
