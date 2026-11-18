package lk.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.parcel.common.ApiErrorFactory;
import lk.swiftdrop.parcel.common.GlobalExceptionHandler;
import lk.swiftdrop.parcel.parcel.dto.AddressDto;
import lk.swiftdrop.parcel.parcel.dto.CreateMerchantParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelBookingResponse;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.MoneyDto;
import lk.swiftdrop.parcel.parcel.dto.RecipientDto;
import lk.swiftdrop.parcel.parcel.dto.UnassignedParcelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalParcelControllerTest {

    @Mock
    private ParcelService parcelService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        var errorFactory = new ApiErrorFactory(Clock.systemUTC());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalParcelController(parcelService))
                .setControllerAdvice(new GlobalExceptionHandler(errorFactory))
                .build();
    }

    @Test
    @DisplayName("POST /internal/parcels creates parcel and returns 201 Created")
    void testCreateParcel() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();

        var request = new CreateMerchantParcelRequest(
                merchantId,
                "ORD-12345",
                new RecipientDto("Recipient", "+94771234567", null),
                new AddressDto("Pickup Line", "Colombo", "00100"),
                new AddressDto("Dropoff Line", "Kandy", "20000"),
                new CreateMerchantParcelRequest.ParcelSpec(new BigDecimal("1.50"), ParcelSize.SMALL, "Books", null),
                "Handle carefully",
                quoteId
        );

        var response = new InternalParcelBookingResponse(
                UUID.randomUUID().toString(), "SD260009999", "CREATED",
                new MoneyDto(new BigDecimal("450.00"), "LKR")
        );

        when(parcelService.createMerchantParcel(any(), eq("key-m-1"))).thenReturn(response);

        mockMvc.perform(post("/internal/parcels")
                        .header("Idempotency-Key", "key-m-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNo").value("SD260009999"))
                .andExpect(jsonPath("$.fee.amount").value(450.00));
    }

    @Test
    @DisplayName("GET /internal/parcels/{id} returns parcel details")
    void testGetParcelDetails() throws Exception {
        UUID parcelId = UUID.randomUUID();
        var detail = new InternalParcelDetailResponse(
                parcelId, "SD260001234", "cust-user-1",
                new InternalParcelDetailResponse.PriceBreakdownDto(
                        new BigDecimal("250.00"), new BigDecimal("50.00"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00")),
                "LKR"
        );

        when(parcelService.getInternalParcelDetails(parcelId)).thenReturn(detail);

        mockMvc.perform(get("/internal/parcels/{parcelId}", parcelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNo").value("SD260001234"))
                .andExpect(jsonPath("$.customerId").value("cust-user-1"));
    }

    @Test
    @DisplayName("GET /internal/parcels defaults to CREATED status and returns list")
    void testGetUnassignedParcelsDefaultStatus() throws Exception {
        UUID parcelId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-17T12:00:00Z");
        var parcel = new UnassignedParcelResponse(
                parcelId, "SD260005555", null, "Colombo", "SMALL", 1.5, now
        );

        when(parcelService.getUnassignedParcels(ParcelStatus.CREATED)).thenReturn(List.of(parcel));

        mockMvc.perform(get("/internal/parcels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$[0].trackingNumber").value("SD260005555"))
                .andExpect(jsonPath("$[0].pickupArea").value("Colombo"))
                .andExpect(jsonPath("$[0].size").value("SMALL"))
                .andExpect(jsonPath("$[0].weight").value(1.5));
    }

    @Test
    @DisplayName("GET /internal/parcels?status=RESCHEDULED passes status filter")
    void testGetUnassignedParcelsWithStatusFilter() throws Exception {
        UUID parcelId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-17T12:00:00Z");
        var parcel = new UnassignedParcelResponse(
                parcelId, "SD260007777", null, "Kandy", "MEDIUM", 2.8, now
        );

        when(parcelService.getUnassignedParcels(ParcelStatus.RESCHEDULED)).thenReturn(List.of(parcel));

        mockMvc.perform(get("/internal/parcels").param("status", "RESCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value("SD260007777"))
                .andExpect(jsonPath("$[0].pickupArea").value("Kandy"))
                .andExpect(jsonPath("$[0].size").value("MEDIUM"))
                .andExpect(jsonPath("$[0].weight").value(2.8));
    }

    @Test
    @DisplayName("GET /internal/parcels returns empty list when no parcels match")
    void testGetUnassignedParcelsEmpty() throws Exception {
        when(parcelService.getUnassignedParcels(ParcelStatus.CREATED)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/internal/parcels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
