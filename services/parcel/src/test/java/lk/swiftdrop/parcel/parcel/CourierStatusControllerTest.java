package lk.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import lk.swiftdrop.parcel.common.ApiErrorFactory;
import lk.swiftdrop.parcel.common.GlobalExceptionHandler;
import lk.swiftdrop.parcel.parcel.dto.CourierStatusUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourierStatusControllerTest {

    @Mock
    private ParcelService parcelService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        var errorFactory = new ApiErrorFactory(Clock.systemUTC());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CourierStatusController(parcelService))
                .setControllerAdvice(new GlobalExceptionHandler(errorFactory))
                .build();
    }

    @Test
    @DisplayName("PATCH /internal/parcels/{id}/status with Idempotency-Key returns 204 No Content")
    void testUpdateStatusWithIdempotencyKey() throws Exception {
        UUID parcelId = UUID.randomUUID();
        String idempotencyKey = "courier-idem-123";

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
                "CR-999", ParcelStatus.PICKED_UP, null, null, null, null, "Picked up from merchant"
        );

        mockMvc.perform(patch("/internal/parcels/{parcelId}/status", parcelId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(parcelService).updateCourierStatus(parcelId, request, idempotencyKey);
    }

    @Test
    @DisplayName("PATCH /internal/parcels/{id}/status without Idempotency-Key returns 204 No Content")
    void testUpdateStatusWithoutIdempotencyKey() throws Exception {
        UUID parcelId = UUID.randomUUID();

        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
                "CR-999", ParcelStatus.DELIVERED, "John Doe", "proofs/img.jpg", null, null, "Delivered"
        );

        mockMvc.perform(patch("/internal/parcels/{parcelId}/status", parcelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(parcelService).updateCourierStatus(parcelId, request, null);
    }

    @Test
    @DisplayName("PATCH /internal/parcels/{id}/status with invalid body returns 400 Bad Request")
    void testUpdateStatusInvalidBody() throws Exception {
        UUID parcelId = UUID.randomUUID();

        // courierId is blank -> fails @NotBlank
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
                "  ", ParcelStatus.DELIVERED, null, null, null, null, null
        );

        mockMvc.perform(patch("/internal/parcels/{parcelId}/status", parcelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
