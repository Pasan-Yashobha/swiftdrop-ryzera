package lk.swiftdrop.identity.security;

import lk.swiftdrop.identity.config.AppProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    public PasswordService(AppProperties properties) {
        int cost = properties.security().bcryptCost();
        this.encoder = new BCryptPasswordEncoder(cost);
        // Fixed dummy hash to protect against timing attacks / email enumeration
        this.dummyHash = encoder.encode("dummy-password-for-timing-defense-only-1234");
    }

    public String hash(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    public boolean matches(String plainPassword, String hashedPassword) {
        if (hashedPassword == null) {
            // Run dummy comparison to preserve constant time
            encoder.matches(plainPassword, dummyHash);
            return false;
        }
        return encoder.matches(plainPassword, hashedPassword);
    }

    /**
     * Executes a dummy BCrypt comparison so requests for nonexistent accounts take
     * the exact same execution time as requests for real accounts.
     */
    public void executeDummyVerification(String plainPassword) {
        encoder.matches(plainPassword, dummyHash);
    }
}
