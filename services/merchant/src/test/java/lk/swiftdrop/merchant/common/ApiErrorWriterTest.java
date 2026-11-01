package lk.swiftdrop.merchant.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiErrorWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ApiErrorWriter writer = new ApiErrorWriter(
            new ApiErrorFactory(Clock.fixed(Instant.parse("2026-11-01T08:30:12Z"), ZoneOffset.UTC)), objectMapper);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void writesTheStandardErrorBodyWithTheStatusOfTheCode() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(new MockHttpServletRequest("GET", "/api/v1/merchant-api/orders"), response,
                ErrorCode.INVALID_API_KEY, "The API key is invalid");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("timestamp").asText()).isEqualTo("2026-11-01T08:30:12Z");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("code").asText()).isEqualTo("INVALID_API_KEY");
        assertThat(body.get("message").asText()).isEqualTo("The API key is invalid");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/merchant-api/orders");
        assertThat(body.get("correlationId").asText()).isEqualTo("corr-9");
        assertThat(body.has("details")).isFalse();
    }

    @Test
    void errorWithoutCorrelationContextOmitsTheField() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(new MockHttpServletRequest("GET", "/x"), response, ErrorCode.FORBIDDEN, "No");

        assertThat(objectMapper.readTree(response.getContentAsString()).has("correlationId")).isFalse();
    }
}
