package lk.swiftdrop.courier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiErrorFactory;
import lk.swiftdrop.courier.common.ApiErrorWriter;
import lk.swiftdrop.courier.common.CorrelationIdFilter;
import lk.swiftdrop.courier.common.GlobalExceptionHandler;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.config.AppProperties;
import lk.swiftdrop.courier.config.ClockConfig;
import lk.swiftdrop.courier.config.SecurityConfig;
import lk.swiftdrop.courier.domain.VehicleType;
import lk.swiftdrop.courier.dto.CourierProfileResponse;
import lk.swiftdrop.courier.dto.UpdateAvailabilityRequest;
import lk.swiftdrop.courier.dto.UpdateProfileRequest;
import lk.swiftdrop.courier.security.JwtRoleConverter;
import lk.swiftdrop.courier.security.RestAccessDeniedHandler;
import lk.swiftdrop.courier.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.courier.service.CourierProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CourierLocationController.class)
@org.springframework.test.context.ActiveProfiles("test")
@EnableConfigurationProperties(AppProperties.class)
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ApiErrorWriter.class,
        ApiErrorFactory.class,
        ClockConfig.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class
})
class CourierLocationControllerTest {

    private static final String COURIER_ID_STR = "c0000001-0000-0000-0000-000000000001";
    private static final UUID COURIER_ID = UUID.fromString(COURIER_ID_STR);
    private static final UUID ZONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @MockBean
    private lk.swiftdrop.courier.service.CourierLocationService locationService;

    private static final String BODY = "{\"lat\":6.9,\"lng\":79.8,\"recordedAt\":\"2026-01-01T00:00:00Z\",\"courierId\":\"frontend-must-not-control-identity\"}";

    @Test @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void reportsUsingAuthenticatedIdentityAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/courier/location").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        verify(locationService).report(eq(COURIER_ID), any(lk.swiftdrop.courier.dto.LocationReportRequest.class));
    }
    @Test void anonymousCannotReport() throws Exception {
        mockMvc.perform(post("/api/v1/courier/location").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(locationService);
    }
    @Test @WithMockUser(username = COURIER_ID_STR, roles = "MERCHANT")
    void otherRoleCannotReport() throws Exception {
        mockMvc.perform(post("/api/v1/courier/location").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(locationService);
    }
    @Test @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void invalidCoordinatesAndTimesAreRejected() throws Exception {
        for (String body : new String[]{"{}", BODY.replace("6.9", "91"), BODY.replace("79.8", "-181"),
                BODY.replace("6.9", "null"), BODY.replace("2026-01-01T00:00:00Z", "2999-01-01T00:00:00Z"),
                BODY.replace("2026-01-01T00:00:00Z", "invalid"), BODY.replace("6.9", "NaN")}) {
            mockMvc.perform(post("/api/v1/courier/location").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verifyNoInteractions(locationService);
    }
    @Test @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void brokerFailureDoesNotReturnSuccess() throws Exception {
        org.mockito.Mockito.doThrow(new lk.swiftdrop.courier.common.ApiException(lk.swiftdrop.courier.common.ErrorCode.SERVICE_UNAVAILABLE, "not confirmed"))
            .when(locationService).report(any(), any());
        mockMvc.perform(post("/api/v1/courier/location").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isServiceUnavailable());
    }
}
