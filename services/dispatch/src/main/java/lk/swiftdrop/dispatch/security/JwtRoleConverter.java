package lk.swiftdrop.dispatch.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Converts JWT claims (role / roles) into Spring Security GrantedAuthority instances.
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String ROLE_CLAIM = "role";
    static final String ROLES_CLAIM = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = roleValues(jwt).stream()
                .flatMap(value -> Role.parse(value).stream())
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static Set<String> roleValues(Jwt jwt) {
        Set<String> values = new LinkedHashSet<>();
        if (jwt.getClaim(ROLE_CLAIM) instanceof String single) {
            values.add(single);
        }
        if (jwt.getClaim(ROLES_CLAIM) instanceof Collection<?> many) {
            many.stream().filter(String.class::isInstance).map(String.class::cast).forEach(values::add);
        }
        return values;
    }
}

