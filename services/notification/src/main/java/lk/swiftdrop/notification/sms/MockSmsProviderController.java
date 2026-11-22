package lk.swiftdrop.notification.sms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.notification.common.ContactMasker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The mock SMS provider used in development (integration design section 6): a tiny HTTP stub that accepts a message
 * and stores it, so the demo can show the SMS without a real gateway. Guarded by the SMS API key and switched off
 * unless {@code swiftdrop.sms.mock-provider-enabled=true} (see the security configuration).
 */
@RestController
@RequestMapping(MockSmsProviderController.BASE_PATH)
public class MockSmsProviderController {

    public static final String BASE_PATH = "/mock-sms";
    public static final String API_KEY_HEADER = "X-Api-Key";

    private final MockSmsMessageRepository repository;
    private final Clock clock;

    public MockSmsProviderController(MockSmsMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> receive(@Valid @RequestBody SmsRequest request) {
        MockSmsMessage stored = repository.save(new MockSmsMessage(UUID.randomUUID(),
                ContactMasker.maskPhone(request.to()), request.message(), clock.instant()));
        return Map.of("id", stored.getId(), "status", "QUEUED");
    }

    public record SmsRequest(@NotBlank @Size(max = 32) String to, @NotBlank @Size(max = 1000) String message) {
    }
}
