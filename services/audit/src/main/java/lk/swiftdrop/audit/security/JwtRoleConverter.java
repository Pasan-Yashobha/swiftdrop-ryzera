package lk.swiftdrop.audit.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Identity puts the user's role in "role" (text) and "roles" (list), for example ADMIN. They become ROLE_ADMIN. */
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Set<String> KNOWN_ROLES = Set.of("ADMIN", "DISPATCHER", "CUSTOMER", "MERCHANT", "COURIER");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> values = new LinkedHashSet<>();
        if (jwt.getClaim("role") instanceof String single) {
            values.add(single);
        }
        if (jwt.getClaim("roles") instanceof Collection<?> many) {
            many.stream().filter(String.class::isInstance).map(String.class::cast).forEach(values::add);
        }
        Set<GrantedAuthority> authorities = values.stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(KNOWN_ROLES::contains)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
