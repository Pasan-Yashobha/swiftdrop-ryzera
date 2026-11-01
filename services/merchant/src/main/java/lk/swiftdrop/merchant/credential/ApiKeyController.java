package lk.swiftdrop.merchant.credential;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.dto.ApiKeyCreatedResponse;
import lk.swiftdrop.merchant.credential.dto.ApiKeyResponse;
import lk.swiftdrop.merchant.credential.dto.CreateApiKeyRequest;
import lk.swiftdrop.merchant.credential.dto.RotateApiKeyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API key management of the signed-in merchant (CONTRACT section 5.3). */
@RestController
@RequestMapping("/api/v1/merchants/me/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeys;

    public ApiKeyController(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @GetMapping
    public List<ApiKeyResponse> list() {
        return apiKeys.list().stream().map(ApiKeyResponse::from).toList();
    }

    /** The response contains the full key; this is the only time it can be read. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse create(@Valid @RequestBody CreateApiKeyRequest request) {
        return ApiKeyCreatedResponse.from(apiKeys.create(request.label(), request.environment()));
    }

    /** The body is optional: without it the old key keeps working for the default grace period. */
    @PostMapping("/{id}/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse rotate(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) RotateApiKeyRequest request) {
        return ApiKeyCreatedResponse.from(apiKeys.rotate(id, RotateApiKeyRequest.graceHoursOf(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        apiKeys.revoke(id);
    }
}
