package com.aresstack.drawio.render;

import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.aresstack.drawio.canvas.DrawioCanvas;

/**
 * Hold an immutable sequence of recorded Draw.io canvas commands.
 *
 * <p>Create instances with {@link CanvasRecorder}. A recording is a
 * {@link DrawioScene}: {@link #paint(DrawioCanvas)} replays the commands in
 * their original order. Instances are immutable and may be replayed
 * concurrently into different canvases.
 */
public final class CanvasRecording implements DrawioScene {

    private final List<Command> commands;
    private final Rectangle2D logicalBounds;

    CanvasRecording(List<Command> commands, Rectangle2D logicalBounds) {
        this.commands = Collections.unmodifiableList(new ArrayList<Command>(commands));
        this.logicalBounds = logicalBounds == null ? null : validated(logicalBounds);
    }

    /**
     * Return the number of recorded commands.
     */
    public int size() {
        return commands.size();
    }

    /**
     * Return the recorded command names in order, for diagnostics and tests.
     */
    public List<String> commandNames() {
        List<String> names = new ArrayList<String>(commands.size());
        for (Command command : commands) {
            names.add(command.method.getName());
        }
        return names;
    }

    @Override
    public void paint(DrawioCanvas canvas) {
        Objects.requireNonNull(canvas, "canvas");
        for (Command command : commands) {
            command.replay(canvas);
        }
    }

    @Override
    public Rectangle2D getLogicalBounds() {
        return logicalBounds == null ? null : (Rectangle2D) logicalBounds.clone();
    }

    private static Rectangle2D validated(Rectangle2D bounds) {
        double[] values = {bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Logical bounds must be finite");
            }
        }
        if (bounds.getWidth() < 0 || bounds.getHeight() < 0) {
            throw new IllegalArgumentException("Logical bounds must not be negative");
        }
        return new Rectangle2D.Double(values[0], values[1], values[2], values[3]);
    }

    static final class Command {
        private final Method method;
        private final Object[] arguments;

        Command(Method method, Object[] arguments) {
            this.method = method;
            this.arguments = arguments;
        }

        void replay(DrawioCanvas canvas) {
            Object[] copy = new Object[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                copy[index] = CanvasRecorder.immutableArgument(method, arguments[index]);
            }
            try {
                method.invoke(canvas, copy);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Canvas method is not accessible: "
                        + method.getName(), exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException("Canvas command failed: " + method.getName(), cause);
            }
        }
    }
}
