package lk.swiftdrop.merchant.credential;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the OpenAPI document of the merchant API; public, so integrators can read it without a key. */
@RestController
public class OpenApiController {

    private static final String DOCUMENT = "merchant-api/openapi.yaml";
    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    private final byte[] document;

    public OpenApiController() {
        try (InputStream stream = new ClassPathResource(DOCUMENT).getInputStream()) {
            this.document = stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The OpenAPI document " + DOCUMENT + " cannot be read", e);
        }
    }

    @GetMapping(path = MerchantApiPaths.OPENAPI, produces = "application/yaml")
    public ResponseEntity<byte[]> openApi() {
        return ResponseEntity.ok().contentType(YAML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(document);
    }
}
