package lk.swiftdrop.courier.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemProofStorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private FileSystemProofStorageService storageService;
    private final UUID parcelId = UUID.fromString("b0000001-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                null,
                null,
                new AppProperties.Storage("local", tempStorageDir.toString(), null, null, null),
                null
        );
        storageService = new FileSystemProofStorageService(properties);
    }

    @Test
    @DisplayName("Successfully stores JPEG proof photo and returns structured object key")
    void storeProofPhoto_jpeg_success() {
        byte[] fakeBytes = ProofImages.jpeg();
        InputStream is = new ByteArrayInputStream(fakeBytes);

        String key = storageService.storeProofPhoto(parcelId, "delivery.jpg", "image/jpeg", is, fakeBytes.length);

        assertThat(key).startsWith("proofs/" + parcelId + "/");
        assertThat(key).endsWith(".jpg");
        assertThat(storageService.exists(key)).isTrue();
    }

    @Test
    @DisplayName("Successfully stores PNG proof photo")
    void storeProofPhoto_png_success() {
        byte[] fakeBytes = ProofImages.png();
        InputStream is = new ByteArrayInputStream(fakeBytes);

        String key = storageService.storeProofPhoto(parcelId, "door.png", "image/png", is, fakeBytes.length);

        assertThat(key).startsWith("proofs/" + parcelId + "/");
        assertThat(key).endsWith(".png");
        assertThat(storageService.exists(key)).isTrue();
    }

    @Test
    @DisplayName("Successfully stores WebP proof photo")
    void storeProofPhoto_webp_success() {
        byte[] fakeBytes = ProofImages.webp();
        InputStream is = new ByteArrayInputStream(fakeBytes);

        String key = storageService.storeProofPhoto(parcelId, "door.webp", "image/webp", is, fakeBytes.length);

        assertThat(key).startsWith("proofs/" + parcelId + "/");
        assertThat(key).endsWith(".webp");
        assertThat(storageService.exists(key)).isTrue();
    }

    @Test
    @DisplayName("Rejects empty file with 0 bytes")
    void storeProofPhoto_rejectsEmptyFile() {
        InputStream is = new ByteArrayInputStream(new byte[0]);

        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "empty.jpg", "image/jpeg", is, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Proof photo file is empty");
    }

    @Test
    @DisplayName("Rejects photo exceeding maximum 5MB size limit")
    void storeProofPhoto_rejectsOversizedFile() {
        long oversized = (5 * 1024 * 1024) + 1; // 5MB + 1 byte
        InputStream is = new ByteArrayInputStream(new byte[10]);

        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "big.jpg", "image/jpeg", is, oversized))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Proof photo exceeds maximum allowed size of 5 MB");
    }

    @Test
    @DisplayName("Rejects unsupported MIME types (e.g. application/pdf, image/gif)")
    void storeProofPhoto_rejectsUnsupportedMimeTypes() {
        byte[] fakeBytes = "pdf-content".getBytes();
        InputStream is = new ByteArrayInputStream(fakeBytes);

        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "document.pdf", "application/pdf", is, fakeBytes.length))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only JPG, PNG, and WebP images are supported");

        InputStream isGif = new ByteArrayInputStream("gif-content".getBytes());
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "animation.gif", "image/gif", isGif, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only JPG, PNG, and WebP images are supported");
    }

    @Test
    @DisplayName("exists returns false for non-existent or null key")
    void exists_nonExistent() {
        assertThat(storageService.exists(null)).isFalse();
        assertThat(storageService.exists("")).isFalse();
        assertThat(storageService.exists("proofs/not-found.jpg")).isFalse();
    }
    @Test
    void rejectsInvalidImageBytesAndMimeMismatch() {
        byte[] junk = "not an image".getBytes();
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.jpg", "image/jpeg",
                new ByteArrayInputStream(junk), junk.length)).isInstanceOf(BadRequestException.class);
        byte[] png = ProofImages.png();
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.jpg", "image/jpeg",
                new ByteArrayInputStream(png), png.length)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.jpg", "application/pdf",
                new ByteArrayInputStream(png), png.length)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.jpg", null,
                new ByteArrayInputStream(png), png.length)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void checksActualStreamSizeAndRejectsTruncatedWebp() {
        byte[] png = ProofImages.png();
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.png", "image/png",
                new ByteArrayInputStream(png), 1)).isInstanceOf(BadRequestException.class);
        byte[] huge = new byte[ProofImageValidator.MAX_BYTES + 1];
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.png", "image/png",
                new ByteArrayInputStream(huge), 1)).isInstanceOf(BadRequestException.class);
        byte[] truncated = java.util.Arrays.copyOf(ProofImages.webp(), 25);
        assertThatThrownBy(() -> storageService.storeProofPhoto(parcelId, "fake.webp", "image/webp",
                new ByteArrayInputStream(truncated), truncated.length)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void existingKeyMustBeARealValidImageFileWithoutTraversal() throws Exception {
        String key = storageService.storeProofPhoto(parcelId, "valid.png", "image/png",
                new ByteArrayInputStream(ProofImages.png()), ProofImages.png().length);
        assertThat(storageService.exists(key)).isTrue();
        assertThat(storageService.exists("proofs/../" + key)).isFalse();
        assertThat(storageService.exists(tempStorageDir.resolve(key).toString())).isFalse();
        assertThat(storageService.exists(key.replace('/', '\\'))).isFalse();
        assertThat(storageService.exists("proofs/" + parcelId)).isFalse();
        String invalidKey = "proofs/" + parcelId + "/" + UUID.randomUUID() + ".png";
        java.nio.file.Files.write(tempStorageDir.resolve(invalidKey), "invalid image".getBytes());
        assertThat(storageService.exists(invalidKey)).isFalse();
    }

}
