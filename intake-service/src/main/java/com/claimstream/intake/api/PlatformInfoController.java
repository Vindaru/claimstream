package com.claimstream.intake.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation tier smoke endpoint: tells a caller what this service is and which
 * adjudication stages the platform will run. Real claim endpoints arrive in M4.
 */
@RestController
public class PlatformInfoController {

    static final List<String> PIPELINE =
            List.of("SUBMISSION", "ELIGIBILITY", "PRICING", "ADJUDICATION", "PAYMENT", "REMITTANCE");

    @GetMapping("/api/v1/platform")
    public PlatformInfo platform() {
        return new PlatformInfo("claimstream-intake", "M0", PIPELINE);
    }

    /** JSON response body. A record is an immutable data carrier (more on this in M1). */
    public record PlatformInfo(String service, String milestone, List<String> pipeline) {
    }
}
