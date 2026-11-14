package lk.swiftdrop.audit.web.dto;

import java.time.Instant;

/** Result of re-computing the whole hash chain. {@code firstBrokenSeq} is the first record that does not match. */
public record VerifyResponse(boolean valid, long checked, Long firstBrokenSeq, String reason, Instant verifiedAt) {
}
