package lk.swiftdrop.merchant.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.event.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests verifying merchant registration, approval lifecycle, and
 * conflict detection across the merchant onboarding state machine.
 */
@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    private static final MerchantProfile PROFILE = new MerchantProfile("Lanka Crafts", null, "owner@crafts.lk",
            "+94771234567", "12 Galle Road", "Colombo", null);

    @Mock
    private MerchantRepository repository;

    @Mock
    private OutboxService outbox;

    private MerchantService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T08:30:00Z"), ZoneOffset.UTC);
        service = new MerchantService(repository, outbox, clock);
    }

    @Test
    void concurrentRegistrationOfTheSameOwnerIsReportedAsAlreadyRegistered() {
        when(repository.existsByOwnerUserId("user-1")).thenReturn(false);
        when(repository.saveAndFlush(any(Merchant.class))).thenThrow(new DataIntegrityViolationException("uq_merchants_owner"));

        assertThatThrownBy(() -> service.register("user-1", PROFILE))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MERCHANT_ALREADY_REGISTERED));
        verifyNoInteractions(outbox);
    }

    @Test
    void getByOwnerFailsWithMerchantNotRegistered() {
        when(repository.findByOwnerUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByOwner("user-1"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MERCHANT_NOT_REGISTERED));
    }

    @Test
    void getByIdFailsForUnknownMerchant() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
