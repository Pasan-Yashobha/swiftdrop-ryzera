package lk.swiftdrop.courier.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ErrorCode;
import lk.swiftdrop.courier.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Local filesystem implementation of ProofStorageService.
 * Used for development, local testing, and environments where shared MinIO/S3 is not yet connected.
 * No MinIO/S3 adapter is implemented here; records store filesystem object keys only.
 */
public class FileSystemProofStorageService implements ProofStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemProofStorageService.class);

    public static final long MAX_FILE_SIZE_BYTES = ProofImageValidator.MAX_BYTES; // 5 MB

    private final Path rootStorageDir;

    public FileSystemProofStorageService(AppProperties properties) {
        String dir = (properties != null && properties.storage() != null && properties.storage().localDir() != null)
                ? properties.storage().localDir()
                : "./data/proofs";
        this.rootStorageDir = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStorageDir);
            log.info("Initialized proof photo storage directory at: {}", this.rootStorageDir);
        } catch (IOException e) {
            log.warn("Could not pre-create proof storage directory {}: {}", this.rootStorageDir, e.getMessage());
        }
    }

    @Override
    public String storeProofPhoto(UUID parcelId, String originalFilename, String contentType,
                                  InputStream inputStream, long sizeBytes) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try {
            var image = ProofImageValidator.validate(contentType, inputStream, sizeBytes);
            String key = "proofs/" + parcelId + "/" + UUID.randomUUID() + image.extension();
            Path destination = rootStorageDir.resolve(key).normalize();
            if (!safePath(destination)) { throw new BadRequestException("Invalid proof storage path"); }
            Files.createDirectories(destination.getParent());
            if (!safePath(destination)) { throw new BadRequestException("Invalid proof storage path"); }
            // A newly generated key never replaces an existing proof file.
            Files.write(destination, image.bytes(), java.nio.file.StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PROOF_UPLOAD_FAILED, "Failed to save delivery proof photo");
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\")) { return false; }
        try {
            Path relative = Paths.get(storageKey);
            if (relative.isAbsolute() || !relative.normalize().equals(relative)) { return false; }
            Path path = rootStorageDir.resolve(relative).normalize();
            if (!safePath(path) || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { return false; }
            String mime = storageKey.endsWith(".png") ? "image/png" : storageKey.endsWith(".webp")
                    ? "image/webp" : storageKey.endsWith(".jpg") ? "image/jpeg" : null;
            try (InputStream input = Files.newInputStream(path)) {
                ProofImageValidator.validate(mime, input, Files.size(path));
            }
            return true;
        } catch (IOException | java.nio.file.InvalidPathException | BadRequestException e) {
            return false;
        }
    }

    private boolean safePath(Path path) {
        if (!path.startsWith(rootStorageDir) || Files.isSymbolicLink(rootStorageDir)) { return false; }
        Path current = rootStorageDir;
        for (Path part : rootStorageDir.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) { return false; }
        }
        return true;
    }
}
