package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lk.swiftdrop.merchant.order.MerchantApiOrderController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.yaml.snakeyaml.Yaml;

/**
 * Keeps the published OpenAPI document ({@code merchant-api/openapi.yaml}) and the public controllers in step:
 * every request mapping of the merchant API controllers and the token endpoint must be documented with the same
 * HTTP method, and everything the document describes under {@code paths} must exist. No Spring context is started;
 * the mappings are read from the controller annotations by reflection.
 */
class OpenApiParityTest {

    /** The server URL of the document; the paths in the document are relative to it. */
    private static final String SERVER_PREFIX = "/api/v1";

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    /** The controllers whose mappings make up the public API (the OpenAPI document itself is documented, too). */
    private static final List<Class<?>> PUBLIC_CONTROLLERS = List.of(
            MerchantApiOrderController.class,
            MerchantApiPingController.class,
            OAuthTokenController.class,
            OpenApiController.class);

    @Test
    void everyControllerMappingIsDocumented() {
        Set<String> implemented = implementedOperations();
        Set<String> documented = documentedOperations();

        Set<String> missing = new TreeSet<>(implemented);
        missing.removeAll(documented);

        assertThat(missing)
                .as("operations implemented by the controllers but missing from openapi.yaml")
                .isEmpty();
    }

    @Test
    void everyDocumentedOperationExistsInTheControllers() {
        Set<String> implemented = implementedOperations();
        Set<String> documented = documentedOperations();

        Set<String> unknown = new TreeSet<>(documented);
        unknown.removeAll(implemented);

        assertThat(unknown)
                .as("operations described in openapi.yaml that no controller implements")
                .isEmpty();
    }

    @Test
    void theComparisonIsNotVacuous() {
        assertThat(implementedOperations())
                .contains("POST /merchant-api/orders", "GET /merchant-api/orders",
                        "GET /merchant-api/orders/{trackingNo}",
                        "POST /merchant-api/sandbox/orders/{trackingNo}/advance",
                        "GET /merchant-api/ping", "GET /merchant-api/openapi.yaml", "POST /oauth/token");
        assertThat(documentedOperations()).hasSameSizeAs(implementedOperations());
    }

    @Test
    void theDocumentIsOpenApi31WithTheServerPrefixAndWebhooks() {
        Map<String, Object> document = loadDocument();

        assertThat(String.valueOf(document.get("openapi"))).startsWith("3.1");
        assertThat(asList(document.get("servers")))
                .extracting(server -> asMap(server).get("url"))
                .containsExactly(SERVER_PREFIX);
        assertThat(asMap(document.get("webhooks"))).isNotEmpty();
        assertThat(asMap(asMap(document.get("components")).get("securitySchemes")))
                .containsKeys("ApiKeyAuth", "OAuth2ClientCredentials", "BearerAuth");
    }

    @Test
    void everyOperationHasAUniqueIdAndAtLeastOneResponse() {
        List<String> operationIds = new ArrayList<>();
        Map<String, Object> paths = asMap(loadDocument().get("paths"));
        paths.forEach((path, item) -> asMap(item).forEach((key, value) -> {
            if (HTTP_METHODS.contains(key)) {
                Map<String, Object> operation = asMap(value);
                assertThat(operation.get("operationId")).as("operationId of %s %s", key, path).isNotNull();
                assertThat(asMap(operation.get("responses"))).as("responses of %s %s", key, path).isNotEmpty();
                operationIds.add(String.valueOf(operation.get("operationId")));
            }
        }));

        assertThat(operationIds).doesNotHaveDuplicates();
    }

    /** {@code "METHOD /path"} for every request mapping of the public controllers, without the server prefix. */
    private static Set<String> implementedOperations() {
        Set<String> operations = new TreeSet<>();
        for (Class<?> controller : PUBLIC_CONTROLLERS) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String[] prefixes = classMapping == null || classMapping.path().length == 0
                    ? new String[] {""} : classMapping.path();
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] suffixes = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                RequestMethod[] methods = mapping.method();
                assertThat(methods).as("HTTP method of %s.%s", controller.getSimpleName(), method.getName())
                        .isNotEmpty();
                for (String prefix : prefixes) {
                    for (String suffix : suffixes) {
                        for (RequestMethod httpMethod : methods) {
                            operations.add(httpMethod.name() + " " + relativeToServer(prefix + suffix));
                        }
                    }
                }
            }
        }
        return operations;
    }

    /** {@code "METHOD /path"} for every operation under {@code paths} in the document. */
    private static Set<String> documentedOperations() {
        Set<String> operations = new TreeSet<>();
        asMap(loadDocument().get("paths")).forEach((path, item) -> asMap(item).forEach((key, value) -> {
            if (HTTP_METHODS.contains(key)) {
                operations.add(key.toUpperCase(Locale.ROOT) + " " + path);
            }
        }));
        return operations;
    }

    private static String relativeToServer(String fullPath) {
        assertThat(fullPath).as("controller paths start with the server prefix").startsWith(SERVER_PREFIX + "/");
        return fullPath.substring(SERVER_PREFIX.length());
    }

    private static Map<String, Object> loadDocument() {
        try (InputStream stream = OpenApiParityTest.class.getResourceAsStream("/merchant-api/openapi.yaml")) {
            assertThat(stream).as("merchant-api/openapi.yaml on the classpath").isNotNull();
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return asMap(new Yaml().load(yaml));
        } catch (IOException e) {
            throw new IllegalStateException("openapi.yaml cannot be read", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }
}
