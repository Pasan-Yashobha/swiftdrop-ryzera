package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

class OpenApiEndpointTest extends AbstractCredentialTest {

    private static final String PATH = "/api/v1/merchant-api/openapi.yaml";

    @Test
    void theDocumentIsPublicAndServedAsYaml() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/yaml")))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andExpect(content().string(startsWith("openapi: 3.1")));
    }

    @Test
    void theServedDocumentIsTheClasspathFile() throws Exception {
        byte[] expected;
        try (InputStream stream = new ClassPathResource("merchant-api/openapi.yaml").getInputStream()) {
            expected = stream.readAllBytes();
        }

        MvcResult result = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(expected);
    }

    @Test
    void theDocumentIsValidOpenApiThatDescribesTheMerchantApi() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();

        Map<String, Object> document = new Yaml().load(new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8));

        assertThat(document.get("openapi").toString()).startsWith("3.1");
        assertThat(document).containsKeys("info", "paths", "components");
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        assertThat(paths).containsKeys("/merchant-api/ping", "/oauth/token", "/merchant-api/openapi.yaml");
        assertThat(paths.keySet()).anyMatch(path -> path.startsWith("/merchant-api/orders"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ping = (Map<String, Object>) paths.get("/merchant-api/ping");
        assertThat(ping).containsKey("get");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = (Map<String, Object>) paths.get("/oauth/token");
        assertThat(token).containsKey("post");
    }

    @Test
    void aBadCredentialDoesNotBlockThePublicDocument() throws Exception {
        mockMvc.perform(get(PATH).header("X-API-Key", "sd_live_zzzzzzzz_" + "A".repeat(43)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void theDocumentCannotBeChanged() throws Exception {
        mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
    }
}
