package com.aresstack.drawio.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.ResourcePolicy;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyImageSourcesTest {

    @Test
    void rejectsEverythingUnderTheDefaultPolicy() throws IOException {
        PolicyImageSources sources = new PolicyImageSources(ResourcePolicy.none());

        assertNull(sources.resolve(dataUri(png())));
        assertNull(sources.resolve("https://example.invalid/a.png"));
    }

    @Test
    void acceptsDataUrisOnlyWhenAllowed() throws IOException {
        PolicyImageSources sources = new PolicyImageSources(ResourcePolicy.dataUrisOnly());

        assertTrue(sources.resolve(dataUri(png())).startsWith("data:image/png;base64,"));
        assertNull(sources.resolve("https://example.invalid/a.png"));
    }

    @Test
    void validatesResolverBytesAndLimits() throws IOException {
        byte[] png = png();
        PolicyImageSources sources = new PolicyImageSources(ResourcePolicy.withResolver(
                reference -> "attachment:logo".equals(reference) ? png : null));

        assertTrue(sources.resolve("attachment:logo").startsWith("data:image/png;base64,"));
        assertNull(sources.resolve("attachment:other"));

        PolicyImageSources tiny = new PolicyImageSources(ResourcePolicy.withResolver(
                reference -> png).withLimits(8, 100));
        assertThrows(IllegalArgumentException.class, () -> tiny.resolve("attachment:logo"));

        PolicyImageSources text = new PolicyImageSources(ResourcePolicy.withResolver(
                reference -> "<svg onload='x'/>".getBytes("UTF-8")));
        assertThrows(IllegalArgumentException.class, () -> text.resolve("attachment:svg"));
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, Color.RED.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static String dataUri(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
