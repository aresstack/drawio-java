package com.aresstack.drawio.render;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.canvas.DrawioCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasRecorderTest {

    @Test
    void recordsCommandsInOrderAndIgnoresClose() {
        CanvasRecorder recorder = new CanvasRecorder();
        DrawioCanvas canvas = recorder.canvas();
        canvas.save();
        canvas.setFillColor("#ff0000");
        canvas.rect(1, 2, 3, 4);
        canvas.fill();
        canvas.restore();
        canvas.close();

        CanvasRecording recording = recorder.toScene(null);

        assertEquals(Arrays.asList("save", "setFillColor", "rect", "fill", "restore"),
                recording.commandNames());
        assertNull(recording.getLogicalBounds());
    }

    @Test
    void replaysIdenticalCommandsAndArguments() {
        CanvasRecorder first = new CanvasRecorder();
        first.canvas().setStrokeWidth(2.5);
        first.canvas().curveTo(1, 2, 3, 4, 5, 6);
        first.canvas().setDashed(true);
        CanvasRecording recording = first.toScene(new Rectangle2D.Double(0, 0, 10, 10));

        CanvasRecorder second = new CanvasRecorder();
        recording.paint(second.canvas());
        recording.paint(second.canvas());
        CanvasRecording replayed = second.toScene(null);

        assertEquals(6, replayed.size());
        assertEquals(recording.commandNames(), replayed.commandNames().subList(0, 3));
        assertEquals(recording.commandNames(), replayed.commandNames().subList(3, 6));
    }

    @Test
    void enforcesCommandLimit() {
        CanvasRecorder recorder = new CanvasRecorder(2);
        recorder.canvas().save();
        recorder.canvas().restore();

        OutputLimitException exception =
                assertThrows(OutputLimitException.class, () -> recorder.canvas().save());
        assertEquals(OutputLimitException.Limit.COMMANDS, exception.getLimit());
    }

    @Test
    void rejectsCommandsAfterFinishing() {
        CanvasRecorder recorder = new CanvasRecorder();
        recorder.toScene(null);

        assertThrows(IllegalStateException.class, () -> recorder.canvas().save());
    }

    @Test
    void objectMethodsAreNotRecorded() {
        CanvasRecorder recorder = new CanvasRecorder();
        DrawioCanvas canvas = recorder.canvas();

        assertEquals(canvas, canvas);
        assertNotEquals(canvas, new CanvasRecorder().canvas());
        canvas.hashCode();
        canvas.toString();

        assertEquals(0, recorder.size());
    }

    @Test
    void logicalBoundsAreCopiedAndValidated() {
        Rectangle2D bounds = new Rectangle2D.Double(1, 2, 3, 4);
        CanvasRecording recording = new CanvasRecorder().toScene(bounds);
        bounds.setRect(9, 9, 9, 9);

        assertEquals(new Rectangle2D.Double(1, 2, 3, 4), recording.getLogicalBounds());
        assertThrows(IllegalArgumentException.class, () -> new CanvasRecorder()
                .toScene(new Rectangle2D.Double(0, 0, Double.NaN, 1)));
        assertThrows(IllegalArgumentException.class, () -> new CanvasRecorder()
                .toScene(new Rectangle2D.Double(0, 0, -1, 1)));
    }

    @Test
    void recordingAnExistingRecordingReturnsIt() {
        CanvasRecording recording = new CanvasRecorder().toScene(null);

        assertSame(recording, CanvasRecorder.record(recording, 10));
    }

    @Test
    void replayPropagatesCanvasErrors() {
        CanvasRecorder recorder = new CanvasRecorder();
        recorder.canvas().restore();
        CanvasRecording recording = recorder.toScene(null);

        try (DrawioCanvas target = new com.aresstack.drawio.canvas.Java2DCanvas(
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                        .createGraphics())) {
            assertThrows(IllegalStateException.class, () -> recording.paint(target));
        }
    }
}
