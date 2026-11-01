import { BASE_URL } from '../shared/curl-samples';

/** One tab of a multi-language sample. */
export interface CodeSample {
  id: string;
  label: string;
  code: string;
}

export const CREATE_ORDER_BODY = `{
  "externalRef": "ORDER-1001",
  "recipient": { "name": "Nimali Perera", "phone": "+94771234567" },
  "pickup": { "addressLine": "12 Galle Rd", "city": "Colombo", "postalCode": "00300" },
  "dropoff": { "addressLine": "45 Temple St", "city": "Kandy", "postalCode": "20000" },
  "parcel": { "weightKg": 2.5, "size": "SMALL", "description": "Phone case" },
  "notes": "Call on arrival"
}`;

export const CREATE_ORDER_CURL = `curl -X POST ${BASE_URL}/merchant-api/orders \\
  -H "X-API-Key: YOUR_API_KEY" \\
  -H "Content-Type: application/json" \\
  -H "Idempotency-Key: 6f1c1d52-0c1b-4b3e-9d44-1d8e2a0f7a10" \\
  -d '${CREATE_ORDER_BODY}'`;

export const CREATE_ORDER_RESPONSE = `{
  "id": "5b7e0c1e-3f6a-4b0e-9a55-0d1d2f7d9c11",
  "trackingNo": "SD24000123",
  "externalRef": "ORDER-1001",
  "status": "CREATED",
  "sandbox": false,
  "source": "API",
  "trackingUrl": "https://swiftdrop.example/track/SD24000123",
  "recipient": { "name": "Nimali Perera", "phone": "+94771234567" },
  "pickup": { "addressLine": "12 Galle Rd", "city": "Colombo", "postalCode": "00300" },
  "dropoff": { "addressLine": "45 Temple St", "city": "Kandy", "postalCode": "20000" },
  "parcel": { "weightKg": 2.5, "size": "SMALL", "description": "Phone case" },
  "notes": "Call on arrival",
  "fee": { "amount": 450.00, "currency": "LKR" },
  "failureReason": null,
  "createdAt": "2026-11-01T08:30:12Z",
  "updatedAt": "2026-11-01T08:30:12Z"
}`;

export const GET_ORDER_CURL = `curl ${BASE_URL}/merchant-api/orders/SD24000123 \\
  -H "X-API-Key: YOUR_API_KEY"`;

export const LIST_ORDERS_CURL = `curl "${BASE_URL}/merchant-api/orders?status=IN_TRANSIT&page=0&size=20" \\
  -H "X-API-Key: YOUR_API_KEY"`;

export const ADVANCE_SANDBOX_CURL = `curl -X POST ${BASE_URL}/merchant-api/sandbox/orders/SBX123456789/advance \\
  -H "X-API-Key: YOUR_TEST_API_KEY"`;

export const FAIL_SANDBOX_CURL = `curl -X POST ${BASE_URL}/merchant-api/sandbox/orders/SBX123456789/advance \\
  -H "X-API-Key: YOUR_TEST_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{ "outcome": "FAILED", "reason": "Recipient not available" }'`;

export const TOKEN_RESPONSE = `{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "orders:write orders:read"
}`;

export const TOKEN_ERROR_RESPONSE = `{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}`;

export const ERROR_RESPONSE = `{
  "timestamp": "2026-11-01T08:30:12.123Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "The request contains invalid fields",
  "path": "/api/v1/merchant-api/orders",
  "correlationId": "b41d0c2e-5a1f-4e0b-8f7e-2c9d6d0a3f11",
  "details": [{ "field": "dropoff.city", "message": "must not be blank" }]
}`;

export const WEBHOOK_PAYLOAD = `{
  "eventId": "0b8f6a1e-6c1d-4a35-bb0a-7a0d8f5c2e90",
  "eventType": "parcel.status.in_transit",
  "version": 1,
  "occurredAt": "2026-11-01T09:15:00Z",
  "producer": "parcel-service",
  "correlationId": "b41d0c2e-5a1f-4e0b-8f7e-2c9d6d0a3f11",
  "aggregateId": "7c0b5d57-31a7-4f43-8a39-5a0c0a2f6d10",
  "data": {
    "merchantId": "d4c1a1f2-0000-4000-8000-000000000001",
    "trackingNo": "SD24000123"
  }
}`;

export const WEBHOOK_REQUEST_EXAMPLE = `POST /hooks/swiftdrop HTTP/1.1
Content-Type: application/json
X-SwiftDrop-Event-Id: 0b8f6a1e-6c1d-4a35-bb0a-7a0d8f5c2e90
X-SwiftDrop-Event-Type: parcel.status.in_transit
X-SwiftDrop-Timestamp: 1793524500
X-SwiftDrop-Signature: 9f2c...e41a`;

const NODE_VERIFY = `const crypto = require('node:crypto');
const express = require('express');

const SECRET = process.env.SWIFTDROP_WEBHOOK_SECRET; // the whsec_... value from the portal
const MAX_AGE_SECONDS = 5 * 60;
const seenEventIds = new Set(); // use a shared store with an expiry in production

function isValidSignature(rawBody, timestamp, signature) {
  const expected = crypto
    .createHmac('sha256', SECRET)
    .update(timestamp + '.')
    .update(rawBody)
    .digest('hex');
  const received = Buffer.from(signature);
  const wanted = Buffer.from(expected);
  return received.length === wanted.length && crypto.timingSafeEqual(received, wanted);
}

const app = express();

// Keep the raw bytes: the signature is computed over the body exactly as it was sent.
app.post('/hooks/swiftdrop', express.raw({ type: 'application/json' }), (req, res) => {
  const timestamp = req.get('X-SwiftDrop-Timestamp') ?? '';
  const signature = req.get('X-SwiftDrop-Signature') ?? '';
  const eventId = req.get('X-SwiftDrop-Event-Id') ?? '';

  const ageSeconds = Math.abs(Date.now() / 1000 - Number(timestamp));
  if (!Number.isFinite(ageSeconds) || ageSeconds > MAX_AGE_SECONDS) {
    return res.status(400).send('Stale or invalid timestamp');
  }
  if (!isValidSignature(req.body, timestamp, signature)) {
    return res.status(401).send('Invalid signature');
  }
  if (seenEventIds.has(eventId)) {
    return res.sendStatus(200); // already handled: acknowledge and do nothing
  }

  const event = JSON.parse(req.body.toString('utf8'));
  // Ignore the update when event.occurredAt is older than the one you stored for this parcel.
  handleEvent(event);
  seenEventIds.add(eventId);
  return res.sendStatus(200);
});`;

const PYTHON_VERIFY = `import hashlib
import hmac
import json
import os
import time

from flask import Flask, abort, request

SECRET = os.environ["SWIFTDROP_WEBHOOK_SECRET"].encode()  # the whsec_... value from the portal
MAX_AGE_SECONDS = 5 * 60
seen_event_ids = set()  # use a shared store with an expiry in production

app = Flask(__name__)


def is_valid_signature(raw_body: bytes, timestamp: str, signature: str) -> bool:
    signed_content = timestamp.encode() + b"." + raw_body
    expected = hmac.new(SECRET, signed_content, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected.encode(), signature.encode())


@app.post("/hooks/swiftdrop")
def swiftdrop_webhook():
    raw_body = request.get_data()  # the bytes exactly as sent, before any JSON parsing
    timestamp = request.headers.get("X-SwiftDrop-Timestamp", "")
    signature = request.headers.get("X-SwiftDrop-Signature", "")
    event_id = request.headers.get("X-SwiftDrop-Event-Id", "")

    try:
        age_seconds = abs(time.time() - int(timestamp))
    except ValueError:
        abort(400)
    if age_seconds > MAX_AGE_SECONDS:
        abort(400)
    if not is_valid_signature(raw_body, timestamp, signature):
        abort(401)
    if event_id in seen_event_ids:
        return "", 200  # already handled: acknowledge and do nothing

    event = json.loads(raw_body)
    # Ignore the update when event["occurredAt"] is older than the one you stored for this parcel.
    handle_event(event)
    seen_event_ids.add(event_id)
    return "", 200`;

const JAVA_VERIFY = `import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SwiftDropWebhookController {

    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    // The whsec_... value from the portal.
    private final byte[] secret =
            System.getenv("SWIFTDROP_WEBHOOK_SECRET").getBytes(StandardCharsets.UTF_8);
    // Use a shared store with an expiry in production.
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();

    @PostMapping("/hooks/swiftdrop")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody, // the bytes exactly as sent, before any JSON parsing
            @RequestHeader("X-SwiftDrop-Timestamp") String timestamp,
            @RequestHeader("X-SwiftDrop-Signature") String signature,
            @RequestHeader("X-SwiftDrop-Event-Id") String eventId) throws GeneralSecurityException {
        if (!isFresh(timestamp)) {
            return ResponseEntity.badRequest().build();
        }
        if (!isValidSignature(rawBody, timestamp, signature)) {
            return ResponseEntity.status(401).build();
        }
        if (seenEventIds.contains(eventId)) {
            return ResponseEntity.ok().build(); // already handled: acknowledge and do nothing
        }

        // Parse rawBody and ignore the update when occurredAt is older than the one you stored.
        handleEvent(rawBody);
        seenEventIds.add(eventId);
        return ResponseEntity.ok().build();
    }

    private static boolean isFresh(String timestamp) {
        try {
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            return Duration.between(signedAt, Instant.now()).abs().compareTo(MAX_AGE) <= 0;
        } catch (NumberFormatException | DateTimeException e) {
            return false;
        }
    }

    private boolean isValidSignature(byte[] rawBody, String timestamp, String signature)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        byte[] expected = HexFormat.of().formatHex(mac.doFinal(rawBody))
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }
}`;

/** Webhook signature verification, one sample per language. Each checks the signature, the timestamp window and duplicates. */
export const SIGNATURE_VERIFICATION_SAMPLES: readonly CodeSample[] = [
  { id: 'node', label: 'Node.js', code: NODE_VERIFY },
  { id: 'python', label: 'Python', code: PYTHON_VERIFY },
  { id: 'java', label: 'Java', code: JAVA_VERIFY },
];
