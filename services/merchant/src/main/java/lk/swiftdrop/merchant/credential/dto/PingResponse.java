package lk.swiftdrop.merchant.credential.dto;

import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/** Answer of {@code GET /merchant-api/ping}: who the credential belongs to. */
public record PingResponse(UUID merchantId, String businessName, ApiEnvironment environment, List<String> scopes) {
}
