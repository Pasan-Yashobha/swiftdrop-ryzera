package lk.swiftdrop.courier.config;

import java.net.URI;
import java.time.Duration;
import lk.swiftdrop.courier.storage.FileSystemProofStorageService;
import lk.swiftdrop.courier.storage.ProofStorageService;
import lk.swiftdrop.courier.storage.S3ProofStorageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Select exactly one adapter; a typo must not silently select local storage. */
@Configuration(proxyBeanMethods = false)
public class ProofStorageConfiguration {
    @Bean
    public ProofStorageService proofStorageService(AppProperties properties) {
        var storage = properties.storage();
        String type = storage == null || storage.type() == null ? "local" : storage.type();
        return switch (type) {
            case "local" -> new FileSystemProofStorageService(properties);
            case "s3", "minio" -> {
                validateS3(storage);
                S3Client client = S3Client.builder()
                        .endpointOverride(URI.create(storage.s3Endpoint()))
                        .region(Region.of(storage.s3Region()))
                        .forcePathStyle(true)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(storage.s3AccessKey(), storage.s3SecretKey())))
                        .httpClientBuilder(UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofSeconds(3))
                                .socketTimeout(Duration.ofSeconds(3)))
                        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10))
                                .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                        .build();
                yield new S3ProofStorageService(client, storage.s3Bucket());
            }
            default -> throw new IllegalStateException("swiftdrop.storage.type must be local, s3 or minio");
        };
    }

    public static void validateS3(AppProperties.Storage storage) {
        if (storage == null) throw invalid("configuration");
        required(storage.s3Endpoint(), "s3-endpoint");
        required(storage.s3Bucket(), "s3-bucket");
        required(storage.s3Region(), "s3-region");
        required(storage.s3AccessKey(), "s3-access-key");
        required(storage.s3SecretKey(), "s3-secret-key");
        try {
            URI uri = URI.create(storage.s3Endpoint());
            if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
                throw invalid("s3-endpoint (HTTP(S) origin without credentials, query or path required)");
            }
            // Plain HTTP is limited to loopback development; remote credentials require TLS.
            if (uri.getScheme().equals("http") && !uri.getHost().equalsIgnoreCase("localhost")
                    && !uri.getHost().equals("127.0.0.1") && !uri.getHost().equals("[::1]")) {
                throw invalid("s3-endpoint (HTTPS required outside loopback development)");
            }
        } catch (IllegalArgumentException e) {
            throw invalid("s3-endpoint"); // Never include raw URI/credentials or SDK exception messages.
        }
        String bucket = storage.s3Bucket();
        if (bucket.length() < 3 || bucket.length() > 63
                || !bucket.matches("[a-z0-9][a-z0-9.-]*[a-z0-9]")
                || bucket.contains("..") || bucket.contains(".-") || bucket.contains("-.")
                || bucket.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) throw invalid("s3-bucket");
        if (!storage.s3Region().matches("[a-z0-9][a-z0-9-]*")) throw invalid("s3-region");
    }

    private static void required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field);
    }

    private static IllegalStateException invalid(String field) {
        return new IllegalStateException("Invalid or missing swiftdrop.storage." + field);
    }
}
