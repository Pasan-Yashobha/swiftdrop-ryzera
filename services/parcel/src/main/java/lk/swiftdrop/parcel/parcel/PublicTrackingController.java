package lk.swiftdrop.parcel.parcel;

import lk.swiftdrop.parcel.parcel.dto.PublicTrackingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class PublicTrackingController {

    private final ParcelService parcelService;

    public PublicTrackingController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/{trackingNo}")
    public ResponseEntity<PublicTrackingResponse> track(@PathVariable String trackingNo) {
        PublicTrackingResponse response = parcelService.getPublicTracking(trackingNo);
        return ResponseEntity.ok(response);
    }
}
