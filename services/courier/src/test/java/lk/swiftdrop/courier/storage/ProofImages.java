package lk.swiftdrop.courier.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

public final class ProofImages {
    private ProofImages() {}
    public static byte[] jpeg() { return encode("jpg"); }
    public static byte[] png() { return encode("png"); }
    public static byte[] webp() {
        return HexFormat.of().parseHex("524946463c0000005745425056503820300000001002009d012a0200020001402625a00274ba01f801f80003c800fef0d703ff60e9832e7f8af7fe9a28c298fb68000000");
    }
    private static byte[] encode(String type) {
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), type, output);
            return output.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
