package lk.swiftdrop.identity.controller;

import java.util.Map;
import lk.swiftdrop.identity.security.RsaKeyProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final RsaKeyProvider keyProvider;

    public JwksController(RsaKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping(path = {"/.well-known/jwks.json", "/api/v1/auth/.well-known/jwks.json"}, produces = "application/json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(keyProvider.getJwksJsonObject());
    }
}
