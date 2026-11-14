package lk.swiftdrop.gateway.security;

import lk.swiftdrop.gateway.config.GatewayProperties;
import lk.swiftdrop.gateway.error.ApiErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Paths the gateway does NOT authenticate. Identity checks its own login/refresh calls, and
     * the Merchant service validates its own API keys and OAuth client-credentials tokens (those
     * are issued by Merchant, not Identity, so the gateway must not try to validate them).
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/.well-known/**",
            "/api/v1/oauth/token",
            "/api/v1/merchant-api/**",
            "/api/v1/public/**",
            "/fallback/**"
    };

    /** Verifies Identity's RS256 access tokens with the public keys Identity publishes (JWKS). */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(GatewayProperties props) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(props.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // default checks (expiry, not-before) plus the issuer
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         ReactiveJwtDecoder jwtDecoder,
                                                         CorsConfigurationSource cors,
                                                         ApiErrorWriter errors,
                                                         GatewayProperties props) {
        // Merchant's dev-token endpoint is only reachable (and unauthenticated) when explicitly enabled.
        String[] publicPaths = props.devRoutesEnabled()
                ? Stream.concat(Stream.of(PUBLIC_PATHS), Stream.of("/api/v1/dev/**")).toArray(String[]::new)
                : PUBLIC_PATHS;

        ServerAuthenticationEntryPoint entryPoint = (exchange, ex) -> {
            boolean hadToken = exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION);
            return errors.write(exchange, HttpStatus.UNAUTHORIZED,
                    hadToken ? "INVALID_TOKEN" : "UNAUTHENTICATED",
                    hadToken ? "The access token is invalid or has expired." : "Authentication is required.");
        };
        ServerAccessDeniedHandler deniedHandler = (exchange, ex) ->
                errors.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to do this.");

        // Do not even read an Authorization header on public paths (an expired token sent to
        // /auth/refresh must not turn into a 401).
        ServerWebExchangeMatcher publicMatcher = ServerWebExchangeMatchers.pathMatchers(publicPaths);
        ServerBearerTokenAuthenticationConverter bearer = new ServerBearerTokenAuthenticationConverter();
        ServerAuthenticationConverter converter = exchange -> publicMatcher.matches(exchange)
                .flatMap(match -> match.isMatch() ? Mono.<Authentication>empty() : bearer.convert(exchange));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(c -> c.configurationSource(cors))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .pathMatchers(publicPaths).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        // coarse role rules; each service still enforces its own fine-grained rules
                        .pathMatchers("/api/v1/admin/reports/**").hasAnyRole("ADMIN", "DISPATCHER")
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/dispatch/**").hasAnyRole("DISPATCHER", "ADMIN")
                        .pathMatchers("/api/v1/courier/**").hasAnyRole("COURIER", "ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenConverter(converter)
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(j -> j.jwtDecoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /** Identity puts the user's roles in the "roles" claim (for example ["ADMIN"]). */
    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
