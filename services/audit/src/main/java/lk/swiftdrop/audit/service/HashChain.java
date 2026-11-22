package lk.swiftdrop.audit.service;

import lk.swiftdrop.audit.domain.AuditEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tamper evidence: every record's hash covers the previous record's hash, so changing or removing any
 * row breaks every hash after it. Fields are length-prefixed so two different rows can never produce the
 * same text to hash.
 */
public final class HashChain {

    /** "Previous hash" of the very first record. */
    public static final String GENESIS = "0".repeat(64);

    private HashChain() {
    }

    public static String hash(String prevHash, AuditEntry entry) {
        return hash(prevHash, entry.eventId(), entry.occurredAt(), entry.actorId(), entry.actorRole(),
                entry.action(), entry.entityType(), entry.entityId(), entry.correlationId(), entry.details());
    }

    public static String hash(String prevHash, UUID eventId, Instant occurredAt, String actorId, String actorRole,
                              String action, String entityType, String entityId, String correlationId,
                              Map<String, String> details) {
        StringBuilder text = new StringBuilder();
        field(text, prevHash);
        field(text, eventId.toString());
        field(text, occurredAt.toString());
        field(text, actorId);
        field(text, actorRole);
        field(text, action);
        field(text, entityType);
        field(text, entityId);
        field(text, correlationId);
        TreeMap<String, String> sorted = new TreeMap<>(details == null ? Map.of() : details);
        sorted.forEach((key, value) -> {
            field(text, key);
            field(text, value);
        });
        return sha256(text.toString());
    }

    private static void field(StringBuilder text, String value) {
        String v = value == null ? "" : value;
        text.append(v.length()).append(':').append(v).append(';');
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
