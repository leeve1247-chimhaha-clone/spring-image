package com.multirkh.chimhahaimage.image.resize;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnailator;
import org.springframework.stereotype.Service;

@Service
public class ImageResizerService {
    static final int WIDTH = 350;
    static final int HEIGHT = 275;

    public InputStream createResizedImage(InputStream rawImage) throws IOException {
        byte[] bytes = rawImage.readAllBytes();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            Thumbnailator.createThumbnail(new ByteArrayInputStream(bytes), outputStream, WIDTH, HEIGHT);
        } catch (IOException e) {
            // Standard reader fails on PNGs with minimal/missing ancillary chunks (e.g. 1x1 fixtures).
            // Fallback: decode with ignoreMetadata=true and resize manually via AWT.
            BufferedImage src = readIgnoringMetadata(bytes);
            if (src == null) {
                throw e;
            }
            BufferedImage scaled = scale(src);
            outputStream.reset();
            ImageIO.write(scaled, "png", outputStream);
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private static BufferedImage readIgnoringMetadata(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage src) {
        int srcW = Math.max(1, src.getWidth());
        int srcH = Math.max(1, src.getHeight());
        double ratio = Math.min((double) WIDTH / srcW, (double) HEIGHT / srcH);
        int newW = Math.max(1, (int) Math.round(srcW * ratio));
        int newH = Math.max(1, (int) Math.round(srcH * ratio));

        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, newW, newH, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
