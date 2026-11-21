package lk.swiftdrop.courier.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ErrorCode;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Private, pre-provisioned S3/MinIO bucket. Never creates buckets, policies, ACLs or URLs. */
public final class S3ProofStorageService implements ProofStorageService, AutoCloseable {
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern KEY = Pattern.compile("proofs/" + UUID_PATTERN + "/" + UUID_PATTERN + "\\.(jpg|png|webp)");
    private final S3Client client;
    private final String bucket;
    private final ObjectMapper mapper = new ObjectMapper();

    public S3ProofStorageService(S3Client client, String bucket) {
        this.client = Objects.requireNonNull(client);
        this.bucket = Objects.requireNonNull(bucket);
    }

    @Override
    public String storeProofPhoto(UUID parcelId, String originalFilename, String contentType,
                                  InputStream inputStream, long sizeBytes) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try {
            var image = ProofImageValidator.validate(contentType, inputStream, sizeBytes);
            requirePrivateBucket();
            String key = "proofs/" + parcelId + "/" + UUID.randomUUID() + image.extension();
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(mime(key)).contentLength((long) image.bytes().length)
                    .ifNoneMatch("*").build(), RequestBody.fromBytes(image.bytes()));
            // No ACL header: S3 defaults to private, including bucket-owner-enforced buckets.
            return key;
        } catch (IOException | SdkException e) {
            throw failure("Failed to save delivery proof photo");
        }
    }

    @Override
    public boolean exists(String key) {
        if (key == null || !KEY.matcher(key).matches()) return false;
        try {
            requirePrivateBucket();
            try (var response = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
                Long length = response.response().contentLength();
                if (length == null || length <= 0 || length > ProofImageValidator.MAX_BYTES) {
                    response.abort();
                    return false;
                }
                if (!mime(key).equals(response.response().contentType())) {
                    response.abort();
                    return false;
                }
                ProofImageValidator.validate(mime(key), response, length);
                return true;
            }
        } catch (S3Exception e) {
            if (e.awsErrorDetails() != null && "NoSuchKey".equals(e.awsErrorDetails().errorCode())) return false;
            throw failure("Proof storage is unavailable");
        } catch (BadRequestException e) {
            return false;
        } catch (IOException | SdkException e) {
            throw failure("Proof storage is unavailable");
        }
    }

    /** Fail closed when privacy cannot be checked, without changing infrastructure policies. */
    private void requirePrivateBucket() {
        var acl = client.getBucketAcl(GetBucketAclRequest.builder().bucket(bucket).build());
        // MinIO's acl-handlers.go returns a single private FULL_CONTROL placeholder,
        // without owner/grantee IDs. It uses bucket policies, checked below, for access.
        boolean minioPrivatePlaceholder = (acl.owner() == null || blank(acl.owner().id()))
                && acl.grants().size() == 1
                && acl.grants().get(0).grantee() != null
                && acl.grants().get(0).grantee().type() == Type.CANONICAL_USER
                && blank(acl.grants().get(0).grantee().id())
                && blank(acl.grants().get(0).grantee().uri())
                && acl.grants().get(0).permission() == Permission.FULL_CONTROL;
        if (!minioPrivatePlaceholder) {
            if (acl.owner() == null || blank(acl.owner().id()) || acl.grants().isEmpty())
                throw failure("Proof bucket privacy could not be verified");
            for (Grant grant : acl.grants()) {
                if (grant.grantee() == null || grant.grantee().type() != Type.CANONICAL_USER
                        || !acl.owner().id().equals(grant.grantee().id())) {
                    throw failure("Proof bucket must be private and owner-only");
                }
            }
        }
        String policy;
        try {
            policy = client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucket).build()).policy();
        } catch (S3Exception e) {
            if (e.statusCode() == 404 && e.awsErrorDetails() != null
                    && "NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode())) return;
            throw e;
        }
        try {
            JsonNode root = mapper.readTree(policy);
            JsonNode statements = root == null ? null : root.get("Statement");
            if (statements == null || !(statements.isArray() || statements.isObject())) throw new IOException();
            if (statements.isObject()) checkStatement(statements);
            else for (JsonNode statement : statements) checkStatement(statement);
        } catch (IOException | IllegalArgumentException e) {
            throw failure("Proof bucket privacy could not be verified");
        }
    }

    private static void checkStatement(JsonNode statement) {
        if (!statement.isObject() || !statement.path("Effect").isTextual())
            throw failure("Proof bucket privacy could not be verified");
        if (statement.path("Effect").asText().equals("Deny")) return;
        JsonNode principal = statement.get("Principal");
        if (!statement.path("Effect").asText().equals("Allow") || statement.has("NotPrincipal")
                || principal == null || principal.isNull() || principal.isEmpty() || wildcard(principal)) {
            throw failure("Proof bucket must not allow anonymous or wildcard access");
        }
    }

    private static boolean wildcard(JsonNode node) {
        if (node.isTextual()) return node.asText().isBlank() || node.asText().contains("*") || node.asText().contains("?");
        if (!node.isContainerNode() || node.isEmpty()) return true;
        for (JsonNode child : node) if (wildcard(child)) return true;
        return false;
    }

    private static String mime(String key) {
        return key.endsWith(".png") ? "image/png" : key.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static ApiException failure(String message) {
        return new ApiException(ErrorCode.PROOF_UPLOAD_FAILED, message);
    }

    @Override
    public void close() { client.close(); }
}
