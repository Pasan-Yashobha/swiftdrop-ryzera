package lk.swiftdrop.identity.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.identity.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);
    private static final String DEFAULT_KEY_ID = "swiftdrop-identity-key-1";

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;
    private final JWKSet jwkSet;

    public RsaKeyProvider(AppProperties properties) {
        String privatePem = properties.security().jwtPrivateKeyPem();
        String publicPem = properties.security().jwtPublicKeyPem();

        if (StringUtils.hasText(privatePem) && StringUtils.hasText(publicPem)) {
            try {
                this.publicKey = parsePublicKey(publicPem);
                this.privateKey = parsePrivateKey(privatePem);
                this.keyId = DEFAULT_KEY_ID;
                log.info("Initialized RSA keys from configured PEM properties");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse configured RSA keys", e);
            }
        } else {
            log.info("No RSA keys configured; generating ephemeral 2048-bit RSA key pair for Identity Service");
            KeyPair keyPair = generateEphemeralKeyPair();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.keyId = DEFAULT_KEY_ID;
        }

        RSAKey rsaKey = new RSAKey.Builder(this.publicKey)
                .privateKey(this.privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(this.keyId)
                .build();
        this.jwkSet = new JWKSet(rsaKey.toPublicJWK());
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public Map<String, Object> getJwksJsonObject() {
        return jwkSet.toJSONObject();
    }

    private static KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generator not available", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
