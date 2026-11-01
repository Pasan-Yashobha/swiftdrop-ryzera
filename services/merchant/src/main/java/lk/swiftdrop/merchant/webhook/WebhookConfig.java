package lk.swiftdrop.merchant.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Where and how SwiftDrop delivers a merchant's webhook events. There is at most one per merchant, so the
 * merchant id is the primary key. The signing secret is only ever stored encrypted, and the class has no
 * {@code toString()} that would list its fields.
 */
@Entity
@Table(name = "webhook_configs")
public class WebhookConfig {

    @Id
    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false)
    private String url;

    @Column(name = "secret_ciphertext", nullable = false)
    private String secretCiphertext;

    @Column(name = "secret_hint", nullable = false)
    private String secretHint;

    @Column(nullable = false)
    private boolean active;

    /** Comma separated event types, or {@code *}. */
    @Column(nullable = false)
    private String events;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_status")
    private String lastTestStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookConfig() {
        // for JPA
    }

    public static WebhookConfig create(UUID merchantId, String url, boolean active, String events,
                                       String secretCiphertext, String secretHint, Instant now) {
        WebhookConfig config = new WebhookConfig();
        config.merchantId = merchantId;
        config.url = url;
        config.active = active;
        config.events = events;
        config.secretCiphertext = secretCiphertext;
        config.secretHint = secretHint;
        config.createdAt = now;
        config.updatedAt = now;
        return config;
    }

    public void update(String url, boolean active, String events, Instant now) {
        this.url = url;
        this.active = active;
        this.events = events;
        this.updatedAt = now;
    }

    public void replaceSecret(String secretCiphertext, String secretHint, Instant now) {
        this.secretCiphertext = secretCiphertext;
        this.secretHint = secretHint;
        this.updatedAt = now;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public boolean isActive() {
        return active;
    }

    public String getEvents() {
        return events;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public String getLastTestStatus() {
        return lastTestStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
