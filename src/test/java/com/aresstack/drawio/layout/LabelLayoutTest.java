package com.aresstack.drawio.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelLayoutTest {

    private static final RunStyle BASE = new RunStyle("Helvetica", 12, false, false);
    /** Every character is 6 px wide, so expectations are exact. */
    private static final TextMetrics FIXED = (text, font) ->
            new TextBounds((int) Math.round(text.length() * font.getSizePixels() / 2), 9, 3, 12);

    private static String text(List<TextRun> line) {
        StringBuilder out = new StringBuilder();
        for (TextRun run : line) {
            out.append(run.getText());
        }
        return out.toString();
    }

    @Test
    void parsesFormattingTagsLineBreaksAndEntities() {
        List<List<TextRun>> lines = HtmlLabelParser.parse(
                "Review <b>request</b><br>Gr&ouml;&szlig;e&nbsp;&amp;&#x41;<div>next</div>", true, BASE);

        assertEquals(3, lines.size());
        assertEquals("Review request", text(lines.get(0)));
        assertTrue(lines.get(0).get(1).getStyle().isBold());
        assertEquals("Größe &A", text(lines.get(1)));
        assertEquals("next", text(lines.get(2)));
    }

    @Test
    void dropsScriptsAndKeepsUnknownMarkupAsTextOnly() {
        List<List<TextRun>> lines = HtmlLabelParser.parse(
                "<img src=x onerror=alert(1)><script>alert(2)</script><a href=\"javascript:x\">Link</a>"
                        + "<span style=\"font-weight:bold;color:#ff0000;font-size:20px\">Red</span>", true, BASE);

        assertEquals(1, lines.size());
        assertEquals("LinkRed", text(lines.get(0)));
        RunStyle red = lines.get(0).get(1).getStyle();
        assertTrue(red.isBold());
        assertEquals("#ff0000", red.getColor());
        assertEquals(20, red.getSize(), 0);
    }

    @Test
    void collapsesWhitespaceAndTreatsLinefeedsAsBreaksInHtml() {
        List<List<TextRun>> lines = HtmlLabelParser.parse("  a   b \n c", true, BASE);
        assertEquals(2, lines.size());
        assertEquals("a b", text(lines.get(0)));
        assertEquals("c", text(lines.get(1)));
    }

    @Test
    void plainLabelsKeepMarkupCharacters() {
        List<List<TextRun>> lines = HtmlLabelParser.parse("a <b> c\nd", false, BASE);
        assertEquals("a <b> c", text(lines.get(0)));
        assertEquals("d", text(lines.get(1)));
    }

    @Test
    void wrapsGreedilyAtSpacesAndMeasuresLineHeights() {
        LabelLayout layout = LabelLayout.layout("aaaa bbbb cccc", true, BASE, 60, FIXED);

        assertEquals(2, layout.getLines().size());
        assertEquals("aaaa bbbb", text(layout.getLines().get(0).getRuns()));
        assertEquals(54, layout.getLines().get(0).getWidth(), 0);
        assertEquals(2 * 12 * LabelLayout.LINE_HEIGHT, layout.getHeight(), 1e-9);
        assertEquals(54, layout.getWidth(), 0);
    }

    @Test
    void unwrappedLabelIsAsWideAsItsLongestLine() {
        LabelLayout layout = LabelLayout.layout("ab<br>abcdef", true, BASE, -1, FIXED);
        assertEquals(36, layout.getWidth(), 0);
        assertFalse(layout.getLines().isEmpty());
    }

    @Test
    void neverBreaksAWordAtAStyleChange() {
        // "hello" is 30 px, wider than the 25 px wrap width: it overflows as one word.
        LabelLayout layout = LabelLayout.layout("<b>hel</b>lo world", true, BASE, 25, FIXED);

        assertEquals(2, layout.getLines().size());
        assertEquals("hello", text(layout.getLines().get(0).getRuns()));
        assertEquals("world", text(layout.getLines().get(1).getRuns()));
        assertTrue(layout.getLines().get(0).getRuns().get(0).getStyle().isBold());
    }
}
