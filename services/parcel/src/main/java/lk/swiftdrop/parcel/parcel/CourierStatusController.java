package lk.swiftdrop.parcel.parcel;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.swiftdrop.parcel.parcel.dto.CourierStatusUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/parcels")
public class CourierStatusController {

    private final ParcelService parcelService;

    public CourierStatusController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PatchMapping("/{parcelId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID parcelId,
            @Valid @RequestBody CourierStatusUpdateRequest request) {
        parcelService.updateCourierStatus(parcelId, request);
        return ResponseEntity.noContent().build();
    }
}
