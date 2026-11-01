package lk.swiftdrop.merchant.credential;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.dto.CreateOAuthClientRequest;
import lk.swiftdrop.merchant.credential.dto.OAuthClientCreatedResponse;
import lk.swiftdrop.merchant.credential.dto.OAuthClientResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** OAuth client management of the signed-in merchant (CONTRACT section 5.3). */
@RestController
@RequestMapping("/api/v1/merchants/me/oauth-clients")
public class OAuthClientController {

    private final OAuthClientService clients;

    public OAuthClientController(OAuthClientService clients) {
        this.clients = clients;
    }

    @GetMapping
    public List<OAuthClientResponse> list() {
        return clients.list().stream().map(OAuthClientResponse::from).toList();
    }

    /** The response contains the client secret; this is the only time it can be read. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthClientCreatedResponse create(@Valid @RequestBody CreateOAuthClientRequest request) {
        return OAuthClientCreatedResponse.from(clients.create(request.label(), request.environment(), request.scopes()));
    }

    /** The old secret stops working at once; the new one is in the response and cannot be read again. */
    @PostMapping("/{id}/rotate-secret")
    public OAuthClientCreatedResponse rotateSecret(@PathVariable UUID id) {
        return OAuthClientCreatedResponse.from(clients.rotateSecret(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        clients.revoke(id);
    }
}
