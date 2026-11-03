package lk.swiftdrop.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserEntityTest {

    @Test
    void testUserLifecycleAndState() {
        Instant now = Instant.now();
        User user = User.register("Test User", "test@example.com", "+94771234567", "hash", Role.CUSTOMER, now);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getFullName()).isEqualTo("Test User");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPhone()).isEqualTo("+94771234567");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getFailedAttempts()).isEqualTo(0);
        assertThat(user.isLocked()).isFalse();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getUpdatedAt()).isEqualTo(now);

        // Lockout behavior
        assertThat(user.remainingLockout(now)).isEqualTo(Duration.ZERO);
        assertThat(user.isLockActive(now)).isFalse();

        // Failed attempts and lock activation
        boolean locked = user.recordFailedAttempt(3, 10, now);
        assertThat(locked).isFalse();
        assertThat(user.getFailedAttempts()).isEqualTo(1);

        user.recordFailedAttempt(3, 10, now);
        locked = user.recordFailedAttempt(3, 10, now);
        assertThat(locked).isTrue();
        assertThat(user.isLocked()).isTrue();
        assertThat(user.isLockActive(now.plusSeconds(30))).isTrue();
        assertThat(user.remainingLockout(now.plusSeconds(30))).isGreaterThan(Duration.ZERO);

        // Already locked check
        assertThat(user.recordFailedAttempt(3, 10, now)).isTrue();

        // Lockout expires
        Instant afterLock = now.plus(11, ChronoUnit.MINUTES);
        assertThat(user.isLockActive(afterLock)).isFalse();
        assertThat(user.isLocked()).isFalse();

        // Unlock manually
        user.recordFailedAttempt(1, 10, now);
        assertThat(user.isLocked()).isTrue();
        user.unlock(now);
        assertThat(user.isLocked()).isFalse();

        // Updates
        user.updateProfile("New Name", "+94770000000", "https://avatar.png", now);
        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getPhone()).isEqualTo("+94770000000");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatar.png");

        user.changeRole(Role.ADMIN, now);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);

        user.updatePassword("newHash", now);
        assertThat(user.getPasswordHash()).isEqualTo("newHash");

        // Soft delete
        user.softDelete(now);
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(now);
    }

    @Test
    void testGoogleRegistrationAndLinking() {
        Instant now = Instant.now();
        User user = User.registerGoogle("Google User", "google@example.com", "google-sub-123", "https://img.com/a.png", Role.CUSTOMER, now);

        assertThat(user.getGoogleId()).isEqualTo("google-sub-123");
        assertThat(user.getAvatarUrl()).isEqualTo("https://img.com/a.png");
        assertThat(user.getPasswordHash()).isNull();

        user.linkGoogle("new-sub", "https://img.com/b.png", now);
        assertThat(user.getGoogleId()).isEqualTo("new-sub");
        assertThat(user.getAvatarUrl()).isEqualTo("https://img.com/b.png");

        user.wipePasswordForPreHijackingProtection(now);
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void testRoleParsing() {
        assertThat(Role.parse("CUSTOMER")).contains(Role.CUSTOMER);
        assertThat(Role.parse("ROLE_CUSTOMER")).contains(Role.CUSTOMER);
        assertThat(Role.parse("admin")).contains(Role.ADMIN);
        assertThat(Role.parse("ROLE_ADMIN")).contains(Role.ADMIN);
        assertThat(Role.parse(null)).isEmpty();
        assertThat(Role.parse("UNKNOWN")).isEmpty();
    }

    @Test
    void testLoginAttemptAndRefreshTokenEntities() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        LoginAttempt attempt = new LoginAttempt(UUID.randomUUID(), "test@example.com", "127.0.0.1", true, now);
        assertThat(attempt.getId()).isNotNull();
        assertThat(attempt.getEmail()).isEqualTo("test@example.com");
        assertThat(attempt.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(attempt.isSuccess()).isTrue();
        assertThat(attempt.getAttemptedAt()).isEqualTo(now);

        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, familyId, "hash", now, now.plus(7, ChronoUnit.DAYS), now);
        assertThat(token.getId()).isNotNull();
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getFamilyId()).isEqualTo(familyId);
        assertThat(token.getTokenHash()).isEqualTo("hash");
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.isExpired(now.plus(8, ChronoUnit.DAYS))).isTrue();
        assertThat(token.isExpired(now.plus(1, ChronoUnit.DAYS))).isFalse();

        token.revoke(now);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(now);
    }
}
