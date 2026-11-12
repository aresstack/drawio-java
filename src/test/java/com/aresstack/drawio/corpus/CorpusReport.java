package com.aresstack.drawio.corpus;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Human-readable corpus report under {@code build/reports/corpus} (#13):
 * one row per fixture page or negative case, with rendered image, diff image
 * and score, so a reviewer can see visual regressions without rerunning
 * anything. CI uploads this directory as an artifact.
 */
final class CorpusReport {

    static final class Row {
        final String name;
        final String status;
        final String outcome;
        final String detail;
        final String actualImage;
        final String diffImage;
        final String referenceImage;

        Row(String name, String status, String outcome, String detail,
            String actualImage, String diffImage, String referenceImage) {
            this.name = name;
            this.status = status;
            this.outcome = outcome;
            this.detail = detail;
            this.actualImage = actualImage;
            this.diffImage = diffImage;
            this.referenceImage = referenceImage;
        }
    }

    private final String title;
    private final String fileStem;
    private final List<Row> rows = new ArrayList<Row>();

    CorpusReport(String title, String fileStem) {
        this.title = title;
        this.fileStem = fileStem;
    }

    static Path directory() {
        Path dir = Paths.get(System.getProperty("corpus.reportDir", "build/reports/corpus"));
        try {
            Files.createDirectories(dir.resolve("images"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return dir;
    }

    static String writeImage(String name, BufferedImage image) {
        if (image == null) {
            return null;
        }
        String relative = "images/" + name.replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
        try (OutputStream out = Files.newOutputStream(directory().resolve(relative))) {
            ImageIO.write(image, "png", out);
        } catch (IOException exception) {
            return null;
        }
        return relative;
    }

    static String copyReference(String corpusRelativePath) {
        try {
            String relative = "images/reference-" + corpusRelativePath.replaceAll("[^A-Za-z0-9._-]", "_");
            Files.write(directory().resolve(relative), CorpusManifest.readBytes(corpusRelativePath));
            return relative;
        } catch (IOException exception) {
            return null;
        }
    }

    synchronized void add(Row row) {
        rows.add(row);
    }

    synchronized void write() {
        Path dir = directory();
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("| Case | Status | Outcome | Detail |\n|---|---|---|---|\n");
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>").append(escape(title))
                .append("</title><style>body{font-family:sans-serif;margin:16px}table{border-collapse:collapse}"
                        + "td,th{border:1px solid #ccc;padding:4px;vertical-align:top}img{max-width:360px}"
                        + ".fail{background:#fde2e2}.ok{background:#e3f6e3}</style></head><body><h1>")
                .append(escape(title)).append("</h1><p>Diff colours: grey = matching ink, red = missing "
                        + "(in reference only), blue = extra (rendered only).</p><table><tr><th>Case</th>"
                        + "<th>Status</th><th>Outcome</th><th>Reference</th><th>Rendered</th><th>Diff</th></tr>");
        for (Row row : rows) {
            md.append("| ").append(row.name).append(" | ").append(row.status).append(" | ").append(row.outcome)
                    .append(" | ").append(row.detail.replace("|", "\\|").replace("\n", " ")).append(" |\n");
            String css = row.outcome.startsWith("PASS") ? "ok" : row.outcome.startsWith("FAIL") ? "fail" : "";
            html.append("<tr class=\"").append(css).append("\"><td>").append(escape(row.name)).append("</td><td>")
                    .append(escape(row.status)).append("</td><td>").append(escape(row.outcome)).append("<br><small>")
                    .append(escape(row.detail)).append("</small></td><td>").append(img(row.referenceImage))
                    .append("</td><td>").append(img(row.actualImage)).append("</td><td>").append(img(row.diffImage))
                    .append("</td></tr>");
        }
        html.append("</table></body></html>\n");
        try {
            Files.write(dir.resolve(fileStem + ".md"), md.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(dir.resolve(fileStem + ".html"), html.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String img(String path) {
        return path == null ? "" : "<img src=\"" + escape(path) + "\">";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
