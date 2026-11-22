package lk.swiftdrop.courier.storage;

import java.io.InputStream;
import java.util.UUID;

/**
 * Storage abstraction for delivery proof photos.
 * Keeps storage vendor decoupled so that local filesystem (dev/test)
 * or S3/MinIO (production) can be plugged in without changing domain logic.
 */
public interface ProofStorageService {

    /**
     * Stores a proof photo and returns its unique object key.
     *
     * @param parcelId parcel identifier
     * @param originalFilename original uploaded filename (if any)
     * @param contentType MIME type (image/jpeg, image/png, image/webp)
     * @param inputStream data stream
     * @param sizeBytes file size in bytes (max 5 MB)
     * @return storageKey (e.g. "proofs/b0000001-.../uuid.jpg")
     */
    String storeProofPhoto(UUID parcelId, String originalFilename, String contentType,
                           InputStream inputStream, long sizeBytes);

    /**
     * Checks if the proof photo exists for the given storage key.
     */
    boolean exists(String storageKey);
}
