package lk.swiftdrop.merchant.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ApiErrorFactory factory = new ApiErrorFactory(Clock.fixed(Instant.parse("2026-11-01T08:30:12Z"), ZoneOffset.UTC));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler(factory))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void apiExceptionsKeepTheirCodeStatusAndMessage() throws Exception {
        mockMvc.perform(get("/fail/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").value("2026-11-01T08:30:12Z"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("Not in the right state"))
                .andExpect(jsonPath("$.path").value("/fail/conflict"))
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void apiExceptionDetailsAreListed() throws Exception {
        mockMvc.perform(get("/fail/details"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("dropoff.city"))
                .andExpect(jsonPath("$.details[0].message").value("must not be blank"));
    }

    @Test
    void rateLimitingSetsRetryAfter() throws Exception {
        mockMvc.perform(get("/fail/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void invalidBodyReportsEveryField() throws Exception {
        mockMvc.perform(post("/fail/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"count\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void malformedJsonIsAMalformedRequest() throws Exception {
        mockMvc.perform(post("/fail/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void wrongJsonTypeNamesTheOffendingField() throws Exception {
        mockMvc.perform(post("/fail/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"count\":\"many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("count"));
    }

    @Test
    void missingParameterIsAValidationError() throws Exception {
        mockMvc.perform(get("/fail/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("page"))
                .andExpect(jsonPath("$.details[0].message").value("is required"));
    }

    @Test
    void parameterOfWrongTypeIsAValidationError() throws Exception {
        mockMvc.perform(get("/fail/param").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    void constraintOnParameterIsAValidationError() throws Exception {
        mockMvc.perform(get("/fail/param").param("page", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    void missingMultipartPartIsAValidationError() throws Exception {
        mockMvc.perform(multipartWithoutFile())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("file"));
    }

    @Test
    void uploadsAboveTheLimitAreRejected() throws Exception {
        mockMvc.perform(get("/fail/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void unsupportedContentTypeAndMethodUseSpecificCodes() throws Exception {
        mockMvc.perform(post("/fail/body").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(post("/fail/conflict"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void optimisticLockConflictAsksForReload() throws Exception {
        mockMvc.perform(get("/fail/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"));
    }

    @Test
    void dataIntegrityViolationDoesNotLeakSql() throws Exception {
        mockMvc.perform(get("/fail/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(content().string(not(containsString("insert into"))))
                .andExpect(content().string(not(containsString("owner@example.com"))));
    }

    @Test
    void bareConstraintViolationsAreValidationErrors() throws Exception {
        mockMvc.perform(get("/fail/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void securityExceptionsThatReachTheControllerLayerKeepTheirStatus() throws Exception {
        mockMvc.perform(get("/fail/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/fail/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unexpectedErrorsReturnAGenericMessageWithTheCorrelationId() throws Exception {
        mockMvc.perform(get("/fail/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("correlation id")))
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(content().string(not(containsString("jdbc:postgresql"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    private static MockMultipartHttpServletRequestBuilder multipartWithoutFile() {
        return multipart("/fail/upload").file(new MockMultipartFile("other", "x.csv", "text/csv", new byte[] {1}));
    }

    @RestController
    static class FailingController {

        record Body(@NotBlank String name, @Min(1) int count) {
        }

        @GetMapping("/fail/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Not in the right state");
        }

        @GetMapping("/fail/details")
        void details() {
            throw new BadRequestException("Invalid row",
                    List.of(new ApiError.Detail("dropoff.city", "must not be blank")));
        }

        @GetMapping("/fail/rate-limited")
        void rateLimited() {
            throw new TooManyRequestsException("Too many requests", 30);
        }

        @PostMapping("/fail/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/fail/param")
        void param(@RequestParam @Min(0) int page) {
        }

        @PostMapping("/fail/upload")
        void upload(@RequestPart("file") MultipartFile file) {
        }

        @GetMapping("/fail/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/fail/optimistic-lock")
        void optimisticLock() {
            throw new OptimisticLockingFailureException("row was updated or deleted by another transaction");
        }

        @GetMapping("/fail/integrity")
        void integrity() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [insert into merchants (contact_email) values ('owner@example.com')]");
        }

        @GetMapping("/fail/constraint")
        void constraint() {
            throw new ConstraintViolationException("invalid", Set.of());
        }

        @GetMapping("/fail/denied")
        void denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/fail/unauthenticated")
        void unauthenticated() {
            throw new BadCredentialsException("bad");
        }

        @GetMapping("/fail/unexpected")
        void unexpected() {
            throw new IllegalStateException("could not connect to jdbc:postgresql://db:5432/merchant_db");
        }
    }
}
