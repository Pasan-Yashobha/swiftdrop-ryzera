package lk.swiftdrop.courier.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lk.swiftdrop.courier.common.BadRequestException;

/** Bounded raw-byte validation; no Base64, BLOB, or cloud-storage behavior. */
public final class ProofImageValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private ProofImageValidator() {}

    public static ValidatedImage validate(String contentType, InputStream input, long advertisedSize) throws IOException {
        if (advertisedSize <= 0) { throw invalid("Proof photo file is empty"); }
        if (advertisedSize > MAX_BYTES) { throw invalid("Proof photo exceeds maximum allowed size of 5 MB"); }
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        String extension = switch (mime) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw invalid("Only JPG, PNG, and WebP images are supported for delivery proof");
        };
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) { throw invalid("Proof photo exceeds maximum allowed size of 5 MB"); }
        if (bytes.length == 0 || bytes.length != advertisedSize) { throw invalid("Proof photo size does not match its content"); }
        if (extension.equals(".webp")) {
            validateWebpContainer(bytes);
        } else {
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) { throw invalid("Invalid image content"); }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    String format = reader.getFormatName();
                    boolean matching = extension.equals(".png") ? format.equalsIgnoreCase("png")
                            : format.equalsIgnoreCase("JPEG");
                    if (!matching || reader.read(0) == null) { throw invalid("Image content does not match its media type"); }
                } finally { reader.dispose(); }
            } catch (IOException | IllegalArgumentException e) {
                throw invalid("Invalid image content");
            }
        }
        return new ValidatedImage(bytes, extension);
    }

    // The JDK has no WebP pixel decoder. Check its RIFF container and static image frame headers instead.
    private static void validateWebpContainer(byte[] bytes) {
        if (bytes.length < 20 || !tag(bytes, 0).equals("RIFF") || !tag(bytes, 8).equals("WEBP")
                || unsignedInt(bytes, 4) + 8 != bytes.length) { throw invalid("Invalid WebP container"); }
        boolean image = false;
        int offset = 12;
        while (offset < bytes.length) {
            if (offset + 8 > bytes.length) { throw invalid("Invalid WebP chunk"); }
            long size = unsignedInt(bytes, offset + 4);
            long end = offset + 8L + size;
            long padded = end + (size & 1);
            if (end > bytes.length || padded > bytes.length) { throw invalid("Truncated WebP content"); }
            String type = tag(bytes, offset);
            int data = offset + 8;
            if (type.equals("VP8 ")) {
                if (size < 10 || (bytes[data] & 1) != 0 || (bytes[data + 3] & 255) != 157
                        || bytes[data + 4] != 1 || bytes[data + 5] != 42) { throw invalid("Invalid WebP image frame"); }
                int width = ((bytes[data + 6] & 255) | ((bytes[data + 7] & 255) << 8)) & 16383;
                int height = ((bytes[data + 8] & 255) | ((bytes[data + 9] & 255) << 8)) & 16383;
                if (width == 0 || height == 0) { throw invalid("Invalid WebP dimensions"); }
                image = true;
            } else if (type.equals("VP8L")) {
                if (size < 5 || (bytes[data] & 255) != 47) { throw invalid("Invalid WebP lossless frame"); }
                image = true;
            }
            offset = (int) padded;
        }
        if (!image) { throw invalid("Unsupported or missing static WebP image frame"); }
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
    private static long unsignedInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }
    private static BadRequestException invalid(String message) { return new BadRequestException(message); }
    public record ValidatedImage(byte[] bytes, String extension) {}
}
