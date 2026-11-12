package com.aresstack.drawio.visual;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.RenderWarning;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;
import com.aresstack.drawio.graph.OriginalGraphPipeline;

/**
 * Developer tool: render draw.io files to PNG and SVG next to an output folder
 * for visual inspection. Usage: RenderToFiles outDir file.drawio...
 */
public final class RenderToFiles {

    private RenderToFiles() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        DrawioRenderer renderer = DrawioRenderer.builder()
                .pipeline(new OriginalGraphPipeline(
                        Collections.singletonList("test-shape-library.js"), 1))
                .build();
        RenderOptions options = RenderOptions.builder()
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN).build();
        for (int i = 1; i < args.length; i++) {
            File input = new File(args[i]);
            String xml = new String(Files.readAllBytes(input.toPath()), StandardCharsets.UTF_8);
            String base = input.getName().replaceAll("\\.[^.]+$", "");
            long start = System.nanoTime();
            RenderResult<byte[]> png = renderer.renderPng(xml, options);
            Files.write(new File(out, base + ".png").toPath(), png.getOutput());
            RenderResult<String> svg = renderer.renderSvg(xml, options);
            Files.write(new File(out, base + ".svg").toPath(), svg.getOutput().getBytes(StandardCharsets.UTF_8));
            System.out.println(base + ": " + png.getWidth() + "x" + png.getHeight() + " in "
                    + (System.nanoTime() - start) / 1000000 + " ms");
            for (RenderWarning warning : png.getWarnings()) {
                System.out.println("  warning " + warning.getCapabilityId() + ": " + warning.getMessage());
            }
        }
        renderer.close();
    }
}
