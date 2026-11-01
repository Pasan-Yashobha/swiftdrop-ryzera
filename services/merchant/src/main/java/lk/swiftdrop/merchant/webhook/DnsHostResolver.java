package lk.swiftdrop.merchant.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/** Resolves host names with the system resolver. */
@Component
class DnsHostResolver implements HostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
