package lk.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import lk.swiftdrop.parcel.common.ApiErrorFactory;
import lk.swiftdrop.parcel.common.GlobalExceptionHandler;
import lk.swiftdrop.parcel.parcel.dto.AddressDto;
import lk.swiftdrop.parcel.parcel.dto.CreateMerchantParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelBookingResponse;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.MoneyDto;
import lk.swiftdrop.parcel.parcel.dto.RecipientDto;
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
}
