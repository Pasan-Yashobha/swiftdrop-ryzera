package lk.swiftdrop.pricing.estimate;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeeEstimationController {
    private final FeeEstimationService estimates;

    public FeeEstimationController(FeeEstimationService estimates) {
        this.estimates = estimates;
    }

    @PostMapping("/api/v1/pricing/estimate")
    public EstimateResponse estimate(@Valid @RequestBody EstimateRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return estimates.estimate(request, jwt.getSubject());
    }
}
