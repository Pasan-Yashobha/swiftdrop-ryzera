package lk.swiftdrop.merchant.credential;

import java.util.ArrayList;
import java.util.List;
import lk.swiftdrop.merchant.security.MerchantApiPrincipal;
import lk.swiftdrop.merchant.security.ServiceRoles;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller of the merchant API. Its principal is a {@link MerchantApiPrincipal}, which
 * controllers receive with {@code @AuthenticationPrincipal}. Authorities: {@code ROLE_MERCHANT_API} and one
 * {@code SCOPE_<scope>} per granted scope, for example {@code SCOPE_orders:write}.
 */
public class MerchantApiAuthentication extends AbstractAuthenticationToken {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    private final MerchantApiPrincipal principal;

    public MerchantApiAuthentication(MerchantApiPrincipal principal) {
        super(authoritiesOf(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    /** The authority that grants {@code scope}, for example {@code SCOPE_orders:write}. */
    public static String scopeAuthority(String scope) {
        return SCOPE_PREFIX + scope;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public MerchantApiPrincipal getPrincipal() {
        return principal;
    }

    private static List<GrantedAuthority> authoritiesOf(MerchantApiPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ServiceRoles.MERCHANT_API));
        Scopes.canonical(principal.scopes()).forEach(scope ->
                authorities.add(new SimpleGrantedAuthority(scopeAuthority(scope))));
        return authorities;
    }
}
