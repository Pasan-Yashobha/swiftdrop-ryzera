package lk.swiftdrop.courier.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ProofStorageServiceTest {
    private final UUID parcel = UUID.fromString("b0000001-0000-0000-0000-000000000001");
    private final String key = "proofs/" + parcel + "/11111111-1111-1111-1111-111111111111.png";
    private S3Client client;
    private S3ProofStorageService storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new S3ProofStorageService(client, "private-proofs");
        when(client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(GetBucketAclResponse.builder()
                .owner(Owner.builder().id("owner").build())
                .grants(Grant.builder().grantee(Grantee.builder().type(Type.CANONICAL_USER).id("owner").build())
                        .permission(Permission.FULL_CONTROL).build()).build());
        when(client.getBucketPolicy(any(GetBucketPolicyRequest.class))).thenThrow(error("NoSuchBucketPolicy", 404));
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("test-etag").build());
    }

    @Test
    void uploadsValidatedBytesToConfiguredPrivateBucketAndReturnsOnlyKey() throws IOException {
        byte[] image = ProofImages.png();
        String returned = upload(image);
        assertThat(returned).matches("proofs/" + parcel + "/[0-9a-f-]{36}\\.png")
                .hasSizeLessThanOrEqualTo(255).doesNotContain("://", "private-proofs", "original-name");
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("private-proofs");
        assertThat(request.getValue().key()).isEqualTo(returned);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(image.length);
        assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
        assertThat(request.getValue().acl()).isNull();
        assertThat(request.getValue().grantRead()).isNull();
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).isEqualTo(image);
        }
        verify(client).getBucketAcl(GetBucketAclRequest.builder().bucket("private-proofs").build());
        verify(client).getBucketPolicy(GetBucketPolicyRequest.builder().bucket("private-proofs").build());
        verifyNoMoreInteractions(client); // No URLs, ACL writes, policy writes or bucket creation.
    }

    @Test
    void eachUploadGetsANewKeyRegardlessOfOriginalFilename() {
        assertThat(upload(ProofImages.png())).isNotEqualTo(upload(ProofImages.png()));
    }

    @Test
    void invalidContentAndEmptyOrOversizedFilesNeverReachS3() {
        for (byte[] bytes : new byte[][] { new byte[0], new byte[]{1, 2}, new byte[ProofImageValidator.MAX_BYTES + 1] }) {
            assertThatThrownBy(() -> upload(bytes)).isInstanceOf(BadRequestException.class);
        }
        byte[] bytes = ProofImages.png();
        assertThatThrownBy(() -> storage.storeProofPhoto(parcel, "a.gif", "image/gif",
                new ByteArrayInputStream(bytes), bytes.length)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(client);
    }

    @Test
    void putFailureDoesNotReturnAKeyOrExposeSdkDetails() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("secret/endpoint details"));
        assertThatThrownBy(() -> upload(ProofImages.png())).isInstanceOf(ApiException.class)
                .hasMessage("Failed to save delivery proof photo")
                .satisfies(e -> assertThat(((ApiException)e).code()).isEqualTo(ErrorCode.PROOF_UPLOAD_FAILED));
    }

    @Test
    void storageOutageOrDeniedPrivacyCheckIsNotTreatedAsPrivate() {
        when(client.getBucketAcl(any(GetBucketAclRequest.class))).thenThrow(error("AccessDenied", 403));
        assertThatThrownBy(() -> upload(ProofImages.png())).isInstanceOf(ApiException.class);
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void publicAclIsRejectedBeforeUpload() {
        when(client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(GetBucketAclResponse.builder()
                .owner(Owner.builder().id("owner").build()).grants(Grant.builder()
                        .grantee(Grantee.builder().type(Type.GROUP).uri("http://acs.amazonaws.com/groups/global/AllUsers").build())
                        .permission(Permission.READ).build()).build());
        assertThatThrownBy(() -> upload(ProofImages.png())).isInstanceOf(ApiException.class).hasMessageContaining("private");
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void acceptsActualMinioPrivateAclShapeButStillChecksPolicy() {
        // Matches minio/minio cmd/acl-handlers.go: empty Owner IDs, one
        // CanonicalUser FULL_CONTROL grant without an ID. This is not a live server.
        when(client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(GetBucketAclResponse.builder()
                .owner(Owner.builder().id("").build()).grants(Grant.builder()
                        .grantee(Grantee.builder().type(Type.CANONICAL_USER).build())
                        .permission(Permission.FULL_CONTROL).build()).build());
        assertThat(upload(ProofImages.png())).startsWith("proofs/" + parcel + "/");
        doReturn(GetBucketPolicyResponse.builder()
                .policy("{\"Statement\":{\"Effect\":\"Allow\",\"Principal\":\"*\"}}").build())
                .when(client).getBucketPolicy(any(GetBucketPolicyRequest.class));
        assertThatThrownBy(() -> upload(ProofImages.png())).isInstanceOf(ApiException.class);
        verify(client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void incompleteAclCannotMasqueradeAsMinioPrivatePlaceholder() {
        when(client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(GetBucketAclResponse.builder()
                .owner(Owner.builder().id("").build()).grants(Grant.builder()
                        .grantee(Grantee.builder().type(Type.CANONICAL_USER).build())
                        .permission(Permission.READ).build()).build());
        assertThatThrownBy(() -> upload(ProofImages.png())).isInstanceOf(ApiException.class);
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\"}]}",
            "{\"Statement\":{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]}}}",
            "{\"Statement\":{\"Effect\":\"Allow\",\"NotPrincipal\":{\"AWS\":\"owner\"}}}",
            "{\"Statement\":{\"Effect\":\"Allow\"}}", "not-json", "{}"
    })
    void publicOrUnverifiablePolicyIsRejected(String policy) {
        doReturn(GetBucketPolicyResponse.builder().policy(policy).build())
                .when(client).getBucketPolicy(any(GetBucketPolicyRequest.class));
        assertThatThrownBy(() -> upload(ProofImages.png())).isInstanceOf(ApiException.class);
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void specificAuthenticatedPrincipalAndDenyStatementsAreAccepted() {
        doReturn(GetBucketPolicyResponse.builder()
                .policy("{\"Statement\":[{\"Effect\":\"Deny\",\"Principal\":\"*\"},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"arn:aws:iam::123456789012:role/courier\"}}]}").build())
                .when(client).getBucketPolicy(any(GetBucketPolicyRequest.class));
        assertThat(upload(ProofImages.png())).startsWith("proofs/");
    }

    @Test
    void invalidKeysAreRejectedWithoutNetworkCalls() {
        for (String invalid : new String[] {null, "", "../secret", "https://host/photo", "proofs/other.png", key + "?x=1"}) {
            assertThat(storage.exists(invalid)).isFalse();
        }
        verifyNoInteractions(client);
    }

    @Test
    void existingProofIsDownloadedBoundedlyAndRevalidated() {
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response(ProofImages.png(), "image/png"));
        assertThat(storage.exists(key)).isTrue();
        verify(client).getObject(GetObjectRequest.builder().bucket("private-proofs").key(key).build());
    }

    @Test
    void missingObjectIsFalseButMissingBucketAndNetworkFailuresAreErrors() {
        doThrow(error("NoSuchKey", 404)).when(client).getObject(any(GetObjectRequest.class));
        assertThat(storage.exists(key)).isFalse();
        doThrow(error("NoSuchBucket", 404)).when(client).getObject(any(GetObjectRequest.class));
        assertThatThrownBy(() -> storage.exists(key)).isInstanceOf(ApiException.class);
        doThrow(SdkClientException.create("offline")).when(client).getObject(any(GetObjectRequest.class));
        assertThatThrownBy(() -> storage.exists(key)).isInstanceOf(ApiException.class);
    }

    @Test
    void existingInvalidPhotoIsFalse() {
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response(new byte[]{1, 2}, "image/png"));
        assertThat(storage.exists(key)).isFalse();
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response(ProofImages.png(), "image/jpeg"));
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void clientIsClosedWithAdapter() {
        storage.close();
        verify(client).close();
    }

    private String upload(byte[] bytes) {
        return storage.storeProofPhoto(parcel, "../../original-name.png", "image/png", new ByteArrayInputStream(bytes), bytes.length);
    }

    private static S3Exception error(String code, int status) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(status);
        builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build());
        return (S3Exception) builder.build();
    }

    private static ResponseInputStream<GetObjectResponse> response(byte[] bytes, String type) {
        return new ResponseInputStream<>(GetObjectResponse.builder().contentType(type).contentLength((long)bytes.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    }
}
