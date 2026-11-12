package com.aresstack.drawio.render;

import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.aresstack.drawio.canvas.DrawioCanvas;

/**
 * Record Draw.io canvas commands so a scene can be painted more than once.
 *
 * <p>Run the original Draw.io JavaScript once against {@link #canvas()}, then
 * call {@link #toScene(Rectangle2D)} to obtain an immutable, thread-safe
 * {@link CanvasRecording}. The recording replays exactly the same commands
 * into every output backend (BufferedImage, PNG, SVG), so raster and vector
 * output always come from one JavaScript run.
 *
 * <p>The recording canvas is a dynamic proxy of {@link DrawioCanvas}: every
 * interface method, including ones added later, is recorded without changes
 * here. Only immutable arguments (primitives, boxed primitives, strings,
 * enums, {@code null}) and primitive arrays (copied) are accepted, so a
 * recording cannot change after the fact. {@code close()} is not recorded.
 *
 * <p>A recorder is not thread-safe; use one recorder per JavaScript run.
 */
public final class CanvasRecorder {

    /** Default maximum number of recorded commands. */
    public static final int DEFAULT_MAX_COMMANDS = 2_000_000;

    private final int maxCommands;
    private final List<CanvasRecording.Command> commands =
            new ArrayList<CanvasRecording.Command>();
    private final DrawioCanvas canvas;
    private boolean finished;

    public CanvasRecorder() {
        this(DEFAULT_MAX_COMMANDS);
    }

    public CanvasRecorder(int maxCommands) {
        if (maxCommands <= 0) {
            throw new IllegalArgumentException("maxCommands must be positive");
        }
        this.maxCommands = maxCommands;
        this.canvas = (DrawioCanvas) Proxy.newProxyInstance(
                DrawioCanvas.class.getClassLoader(),
                new Class<?>[] {DrawioCanvas.class},
                new Recorder());
    }

    /**
     * Return the canvas that records every command issued on it.
     */
    public DrawioCanvas canvas() {
        return canvas;
    }

    /**
     * Return the number of commands recorded so far.
     */
    public int size() {
        return commands.size();
    }

    /**
     * Finish recording and return the immutable scene.
     *
     * @param logicalBounds logical diagram bounds in diagram units, or
     *                      {@code null} if unknown
     */
    public CanvasRecording toScene(Rectangle2D logicalBounds) {
        return toScene(logicalBounds, null);
    }

    /**
     * Finish recording and return the immutable scene with a page background.
     *
     * @param pageBackground {@code #rrggbb} page color, or {@code null}
     */
    public CanvasRecording toScene(Rectangle2D logicalBounds, String pageBackground) {
        finished = true;
        return new CanvasRecording(commands, logicalBounds, pageBackground);
    }

    /**
     * Record a whole scene in one call.
     */
    public static CanvasRecording record(DrawioScene scene, int maxCommands) {
        if (scene instanceof CanvasRecording) {
            return (CanvasRecording) scene;
        }
        CanvasRecorder recorder = new CanvasRecorder(maxCommands);
        scene.paint(recorder.canvas());
        return recorder.toScene(scene.getLogicalBounds());
    }

    private final class Recorder implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                return null;
            }
            if (finished) {
                throw new IllegalStateException("Recording has already been finished");
            }
            if (method.getReturnType() != void.class) {
                throw new UnsupportedOperationException(
                        "Cannot record canvas method with a return value: " + method.getName());
            }
            if (commands.size() >= maxCommands) {
                throw new OutputLimitException(OutputLimitException.Limit.COMMANDS,
                        "Scene exceeds " + maxCommands + " canvas commands");
            }
            commands.add(new CanvasRecording.Command(method, immutableCopy(method, args)));
            return null;
        }

        private Object objectMethod(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return "RecordingDrawioCanvas[" + commands.size() + " commands]";
            }
        }
    }

    private static Object[] immutableCopy(Method method, Object[] args) {
        if (args == null) {
            return new Object[0];
        }
        Object[] copy = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            copy[index] = immutableArgument(method, args[index]);
        }
        return copy;
    }

    static Object immutableArgument(Method method, Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character
                || value instanceof Enum) {
            return value;
        }
        if (value instanceof double[]) {
            return ((double[]) value).clone();
        }
        if (value instanceof float[]) {
            return ((float[]) value).clone();
        }
        if (value instanceof int[]) {
            return ((int[]) value).clone();
        }
        if (value instanceof long[]) {
            return ((long[]) value).clone();
        }
        if (value instanceof boolean[]) {
            return ((boolean[]) value).clone();
        }
        if (value instanceof byte[]) {
            return ((byte[]) value).clone();
        }
        if (value instanceof String[]) {
            return ((String[]) value).clone();
        }
        throw new IllegalArgumentException("Cannot record mutable argument of type "
                + value.getClass().getName() + " for canvas method " + method.getName());
    }
}
