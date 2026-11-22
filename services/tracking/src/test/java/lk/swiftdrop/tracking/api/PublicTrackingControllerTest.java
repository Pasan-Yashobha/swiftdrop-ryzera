package lk.swiftdrop.tracking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.tracking.event.TrackingEventConsumer;
import lk.swiftdrop.tracking.push.TrackingStreamService;
import lk.swiftdrop.tracking.testsupport.Events;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingConfig;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryTrackingConfig.class)
class PublicTrackingControllerTest {

    private static final Instant T0 = Instant.now().minus(Duration.ofHours(2));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TrackingEventConsumer consumer;

    @Autowired
    private InMemoryTrackingStore store;

    @Autowired
    private TrackingStreamService streams;

    @BeforeEach
    void reset() {
        store.clear();
    }

    private void created(String trackingNo) {
        consumer.onMessage(Events.created(trackingNo, UUID.randomUUID().toString(), 1, T0).message());
    }

    @Test
    void returnsThePublicViewOfAKnownParcel() throws Exception {
        created("SD-1000-0001");
        consumer.onMessage(Events.status("picked_up", "SD-1000-0001", 2, T0.plusSeconds(600)).message());

        mvc.perform(get("/api/v1/public/tracking/sd-1000-0001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.trackingNo").value("SD-1000-0001"))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.stop").value(2))
                .andExpect(jsonPath("$.stops.length()").value(5))
                .andExpect(jsonPath("$.origin.city").value("Colombo"))
                .andExpect(jsonPath("$.destination.city").value("Kandy"))
                .andExpect(jsonPath("$.history[0].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.eta.basis").value("SCHEDULE"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void anUnknownParcelIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/tracking/SD-9999-9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TRACKING_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/public/tracking/SD-9999-9999"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void anInvalidTrackingNumberIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/public/tracking/ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRACKING_NUMBER"));
    }

    @Test
    void theStreamSendsTheCurrentStateThenEveryChange() throws Exception {
        created("SD-1000-0002");

        MvcResult result = mvc.perform(get("/api/v1/public/tracking/SD-1000-0002/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String initial = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(initial).contains("event:tracking").contains("\"status\":\"CREATED\"").contains("retry:");
        assertThat(streams.openStreams("SD-1000-0002")).isEqualTo(1);

        consumer.onMessage(Events.status("out_for_delivery", "SD-1000-0002", 5, T0.plusSeconds(900)).message());

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"status\":\"OUT_FOR_DELIVERY\"");
    }

    @Test
    void aStreamForAnUnknownParcelIsAJsonNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/tracking/SD-9999-9998/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TRACKING_NOT_FOUND"));
    }

    @Test
    void theSameEndpointsAnswerUnderTheGatewaysSignedInPrefix() throws Exception {
        created("SD-1000-0003");

        mvc.perform(get("/api/v1/tracking/SD-1000-0003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNo").value("SD-1000-0003"));
        mvc.perform(get("/api/v1/tracking/SD-1000-0003/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());

        assertThat(streams.openStreams("SD-1000-0003")).isEqualTo(1);
    }

    @Test
    void writesAreNotAllowed() throws Exception {
        mvc.perform(post("/api/v1/public/tracking/SD-1000-0001").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void theDevSimulatorIsClosedOutsideTheDevProfile() throws Exception {
        mvc.perform(post("/api/v1/dev/tracking/simulate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anythingElseIsDenied() throws Exception {
        mvc.perform(get("/api/v1/parcels"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownPathUnderTrackingIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/tracking/SD-1000-0001/receipt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void healthIsOpenForProbes() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void theCorrelationIdIsEchoedAndSecurityHeadersAreSet() throws Exception {
        mvc.perform(get("/api/v1/public/tracking/SD-9999-9999").header("X-Correlation-Id", "corr-123"))
                .andExpect(header().string("X-Correlation-Id", "corr-123"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.correlationId").value("corr-123"));
    }

    @Test
    void anUnsafeCorrelationIdIsReplaced() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/public/tracking/SD-9999-9999")
                        .header("X-Correlation-Id", "<script>"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotEqualTo("<script>").hasSize(36);
    }

    @Test
    void corsAllowsTheFrontend() throws Exception {
        mvc.perform(get("/api/v1/public/tracking/SD-9999-9999").header("Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
