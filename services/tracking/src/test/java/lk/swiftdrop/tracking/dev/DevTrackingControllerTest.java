package lk.swiftdrop.tracking.dev;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.tracking.testsupport.InMemoryTrackingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The dev simulator exists in the dev profile and publishes to the tracking queue only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@Import(InMemoryTrackingConfig.class)
class DevTrackingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void simulateStartsAJourneyOnTheTrackingQueue() throws Exception {
        mvc.perform(post("/api/v1/dev/tracking/simulate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"Galle\",\"destination\":\"Jaffna\",\"stepSeconds\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trackingNo").value(matchesPattern("SD-\\d{4}-\\d{4}")))
                .andExpect(jsonPath("$.events").value(greaterThan(10)))
                .andExpect(jsonPath("$.stepSeconds").value(2));

        verify(rabbitTemplate, timeout(3000).atLeastOnce()).send(eq(""), eq("tracking.q"), any(Message.class));
    }

    @Test
    void simulateWorksWithoutABody() throws Exception {
        mvc.perform(post("/api/v1/dev/tracking/simulate"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.stepSeconds").value(6));
    }

    @Test
    void aRawEventIsPublished() throws Exception {
        mvc.perform(post("/api/v1/dev/tracking/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"parcel.status.delivered\",\"data\":{\"trackingNo\":\"SD-1000-0001\"}}"))
                .andExpect(status().isAccepted());

        verify(rabbitTemplate, atLeastOnce()).send(eq(""), eq("tracking.q"), any(Message.class));
    }

    @Test
    void aRawEventNeedsAnEventType() throws Exception {
        mvc.perform(post("/api/v1/dev/tracking/events").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
