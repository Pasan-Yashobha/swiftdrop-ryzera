package lk.swiftdrop.courier.config;

import lk.swiftdrop.courier.storage.FileSystemProofStorageService;
import lk.swiftdrop.courier.storage.ProofStorageService;
import lk.swiftdrop.courier.storage.S3ProofStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.*;

class ProofStorageConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class, ProofStorageConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Properties {}

    @Test
    void defaultIsLocalWithoutS3Credentials() {
        runner.withPropertyValues("swiftdrop.storage.local-dir=target/config-test-proofs")
                .run(c -> assertThat(c).hasSingleBean(ProofStorageService.class).hasSingleBean(FileSystemProofStorageService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"s3", "minio"})
    void selectsOnlyS3AndBindsCredentialsWithoutConnecting(String type) {
        configured(type).run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(ProofStorageService.class).hasSingleBean(S3ProofStorageService.class);
            assertThat(c).doesNotHaveBean(FileSystemProofStorageService.class);
            assertThat(c.getBean(AppProperties.class).storage().s3AccessKey()).isEqualTo("test-access");
            assertThat(c.getBean(AppProperties.class).storage().s3SecretKey()).isEqualTo("test-secret");
            assertThat(c.getBean(AppProperties.class).storage().toString()).doesNotContain("test-access", "test-secret");
        });
    }

    @Test
    void unknownBackendAndMissingCredentialsFailInsteadOfFallingBack() {
        runner.withPropertyValues("swiftdrop.storage.type=s33").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("swiftdrop.storage.type=s3").run(c -> assertThat(c).hasFailed());
        assertThatThrownBy(() -> ProofStorageConfiguration.validateS3(null)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "ftp://localhost", "http://user:secret@localhost:9000", "https://host/bucket", "https://host/?secret=x", "https://host/#secret", "not-a-uri", "http://remote-host:9000"})
    void invalidEndpointFailsSafely(String endpoint) {
        assertThatThrownBy(() -> ProofStorageConfiguration.validateS3(storage(endpoint, "proof-photos", "region-1", "access", "secret")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("s3-endpoint")
                .hasMessageNotContaining("user:secret");
    }

    @Test
    void requiresEveryS3Setting() {
        for (int field=0; field<4; field++) {
            String[] values={"proof-photos", "us-east-1", "access", "secret"};
            values[field]=" ";
            var settings=storage("https://minio.example",values[0],values[1],values[2],values[3]);
            assertThatThrownBy(() -> ProofStorageConfiguration.validateS3(settings)).isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"ABucket", "ab", "../bucket", "a..b", "192.168.1.1", "bucket/path", "a.-b"})
    void invalidBucketFails(String bucket) {
        assertThatThrownBy(() -> ProofStorageConfiguration.validateS3(storage("http://localhost:9000",bucket,"us-east-1","access","secret")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("s3-bucket");
    }

    private ApplicationContextRunner configured(String type) {
        return runner.withPropertyValues("swiftdrop.storage.type="+type,
                "swiftdrop.storage.s3-endpoint=http://localhost:9000", "swiftdrop.storage.s3-bucket=private-proofs",
                "swiftdrop.storage.s3-region=us-east-1", "swiftdrop.storage.s3-access-key=test-access",
                "swiftdrop.storage.s3-secret-key=test-secret");
    }

    private static AppProperties.Storage storage(String endpoint,String bucket,String region,String access,String secret) {
        return new AppProperties.Storage("s3",null,endpoint,bucket,region,access,secret);
    }
}
