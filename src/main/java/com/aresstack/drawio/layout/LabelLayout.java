package com.aresstack.drawio.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lay out a draw.io label into measured lines.
 *
 * <p>Shared by label measurement (graph bounds in the headless view) and label
 * painting (Java2D canvas), so both agree on line breaks and sizes. Lines are
 * {@link #LINE_HEIGHT} times their largest font size high, matching draw.io's
 * {@code mxConstants.LINE_HEIGHT}. Wrapping is greedy at spaces when a wrap
 * width is given, like CSS {@code white-space: normal} with
 * {@code word-wrap: normal}: a single word longer than the width overflows.
 */
public final class LabelLayout {

    /** draw.io {@code mxConstants.LINE_HEIGHT}. */
    public static final double LINE_HEIGHT = 1.2;
    private static final int MAX_LINES = 2000;

    private final List<Line> lines;
    private final double width;
    private final double height;

    private LabelLayout(List<Line> lines) {
        this.lines = Collections.unmodifiableList(lines);
        double w = 0;
        double h = 0;
        for (Line line : lines) {
            w = Math.max(w, line.getWidth());
            h += line.getHeight();
        }
        this.width = w;
        this.height = h;
    }

    /**
     * Lay out a label.
     *
     * @param value     label value
     * @param html      whether the value is HTML
     * @param base      font of the label
     * @param wrapWidth wrap width in pixels, or a value {@code <= 0} for no wrapping
     * @param metrics   text measurement port
     */
    public static LabelLayout layout(String value, boolean html, RunStyle base,
                                     double wrapWidth, TextMetrics metrics) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(metrics, "metrics");
        List<List<TextRun>> paragraphs = HtmlLabelParser.parse(value, html, base);
        List<Line> lines = new ArrayList<Line>();
        for (List<TextRun> paragraph : paragraphs) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            if (wrapWidth > 0 && Double.isFinite(wrapWidth)) {
                wrap(paragraph, base, wrapWidth, metrics, lines);
            } else {
                lines.add(Line.of(paragraph, base, metrics));
            }
        }
        return new LabelLayout(lines);
    }

    private static void wrap(List<TextRun> paragraph, RunStyle base, double wrapWidth,
                             TextMetrics metrics, List<Line> out) {
        List<TextRun> line = new ArrayList<TextRun>();
        double lineWidth = 0;
        for (TextRun run : paragraph) {
            String text = run.getText();
            int start = 0;
            while (start < text.length()) {
                int space = text.indexOf(' ', start);
                int end = space < 0 ? text.length() : space + 1;
                String word = text.substring(start, end);
                double wordWidth = measure(word.trim(), run.getStyle(), metrics);
                double advance = measure(word, run.getStyle(), metrics);
                if (!line.isEmpty() && lineWidth + wordWidth > wrapWidth && out.size() < MAX_LINES) {
                    out.add(Line.of(trimEnd(line), base, metrics));
                    line = new ArrayList<TextRun>();
                    lineWidth = 0;
                }
                append(line, word, run.getStyle());
                lineWidth += advance;
                start = end;
            }
        }
        out.add(Line.of(trimEnd(line), base, metrics));
    }

    private static void append(List<TextRun> line, String text, RunStyle style) {
        if (!line.isEmpty() && line.get(line.size() - 1).getStyle() == style) {
            TextRun last = line.remove(line.size() - 1);
            line.add(new TextRun(last.getText() + text, style));
        } else {
            line.add(new TextRun(text, style));
        }
    }

    private static List<TextRun> trimEnd(List<TextRun> runs) {
        if (!runs.isEmpty()) {
            TextRun last = runs.get(runs.size() - 1);
            String trimmed = last.getText().replaceAll(" +$", "");
            runs.set(runs.size() - 1, new TextRun(trimmed, last.getStyle()));
        }
        return runs;
    }

    static double measure(String text, RunStyle style, TextMetrics metrics) {
        if (text.isEmpty()) {
            return 0;
        }
        return metrics.measure(text, style.toFont()).getWidth();
    }

    public List<Line> getLines() {
        return lines;
    }

    /** Width of the widest line in pixels. */
    public double getWidth() {
        return width;
    }

    /** Sum of the line heights in pixels. */
    public double getHeight() {
        return height;
    }

    /**
     * One laid-out line.
     */
    public static final class Line {
        private final List<TextRun> runs;
        private final double[] runWidths;
        private final double width;
        private final double height;
        private final double maxFontSize;

        private Line(List<TextRun> runs, double[] runWidths, double width, double maxFontSize) {
            this.runs = Collections.unmodifiableList(new ArrayList<TextRun>(runs));
            this.runWidths = runWidths;
            this.width = width;
            this.maxFontSize = maxFontSize;
            this.height = maxFontSize * LINE_HEIGHT;
        }

        static Line of(List<TextRun> runs, RunStyle base, TextMetrics metrics) {
            double[] widths = new double[runs.size()];
            double total = 0;
            double maxSize = runs.isEmpty() ? base.getSize() : 0;
            for (int i = 0; i < runs.size(); i++) {
                TextRun run = runs.get(i);
                widths[i] = measure(run.getText(), run.getStyle(), metrics);
                total += widths[i];
                maxSize = Math.max(maxSize, run.getStyle().getSize());
            }
            return new Line(runs, widths, total, maxSize);
        }

        public List<TextRun> getRuns() {
            return runs;
        }

        /** Measured width of the run at {@code index}. */
        public double getRunWidth(int index) {
            return runWidths[index];
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        /** Largest font size on the line; the baseline is derived from it. */
        public double getMaxFontSize() {
            return maxFontSize;
        }
    }
}
