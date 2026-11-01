package lk.swiftdrop.merchant.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.common.UnauthorizedException;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentMerchantProviderTest extends AbstractIntegrationTest {

    @Autowired
    private CurrentMerchantProvider provider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireMerchantReturnsTheMerchantOwnedByTheCaller() {
        Merchant mine = fixtures.pendingMerchant("user-a");
        fixtures.approvedMerchant("user-b");
        authenticateAs("user-a");

        assertThat(provider.requireMerchant().getId()).isEqualTo(mine.getId());
    }

    @Test
    void requireMerchantFailsWhenCallerOwnsNone() {
        fixtures.approvedMerchant("user-b");
        authenticateAs("user-a");

        assertThatThrownBy(provider::requireMerchant)
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MERCHANT_NOT_REGISTERED));
    }

    @Test
    void requireApprovedMerchantAcceptsApprovedMerchant() {
        Merchant approved = fixtures.approvedMerchant("user-a");
        authenticateAs("user-a");

        assertThat(provider.requireApprovedMerchant().getId()).isEqualTo(approved.getId());
    }

    @Test
    void requireApprovedMerchantRejectsEveryOtherStatus() {
        int index = 0;
        for (MerchantStatus status : List.of(MerchantStatus.PENDING, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED)) {
            String user = "user-" + index++;
            fixtures.merchant(user, status);
            authenticateAs(user);

            assertThatThrownBy(provider::requireApprovedMerchant)
                    .as(status.name())
                    .isInstanceOfSatisfying(ForbiddenException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MERCHANT_NOT_APPROVED));
        }
    }

    @Test
    void requireMerchantNeedsAnAuthenticatedCaller() {
        assertThatThrownBy(provider::requireMerchant).isInstanceOf(UnauthorizedException.class);
    }

    private static void authenticateAs(String userId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")), userId));
    }
}
