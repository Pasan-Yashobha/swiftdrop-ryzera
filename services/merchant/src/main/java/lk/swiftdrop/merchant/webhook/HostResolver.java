package lk.swiftdrop.merchant.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Turns a host name into the addresses a connection would use; replaceable so that tests need no DNS. */
@FunctionalInterface
public interface HostResolver {

    /** @return every address of {@code host}; IP literals resolve to themselves */
    InetAddress[] resolve(String host) throws UnknownHostException;
}
