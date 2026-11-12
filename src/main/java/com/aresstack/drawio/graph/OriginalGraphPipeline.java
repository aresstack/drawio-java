package com.aresstack.drawio.graph;

import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.aresstack.drawio.api.DiagramFormatException;
import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.canvas.CanvasDiagnostics;
import com.aresstack.drawio.graal.DrawioCanvasBridge;
import com.aresstack.drawio.graal.DrawioHost;
import com.aresstack.drawio.graal.GraalJsRuntime;
import com.aresstack.drawio.graal.ScriptExecutionResult;
import com.aresstack.drawio.graal.ScriptResourceProvider;
import com.aresstack.drawio.graal.ScriptSource;
import com.aresstack.drawio.parse.XmlTreeJson;
import com.aresstack.drawio.render.CanvasRecorder;
import com.aresstack.drawio.render.DiagramPipeline;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.PolicyImageSources;
import com.aresstack.drawio.render.SessionCanvasDiagnostics;
import com.aresstack.drawio.render.RenderSession;

/**
 * Render a page through the original draw.io JavaScript graph stack.
 *
 * <p>Flow: strict Java XML parsing, JSON hand-off, original {@code mxCodec}
 * decoding into an original {@code mxGraphModel}, original
 * {@code mxGraph}/{@code mxGraphView}/{@code mxCellRenderer} validation
 * (styles, perimeters, edge routing, label bounds), original
 * {@code mxImageExport} painting through {@code DrawioJavaCanvas} into a
 * {@link CanvasRecorder}. The GraalJS context is closed before this method
 * returns; the returned scene is an immutable command recording.
 *
 * <p>Stateless and thread-safe: every call uses its own runtime and context.
 */
public final class OriginalGraphPipeline implements DiagramPipeline {

    /** Core bundles, always loaded first, in this order. */
    static final List<String> CORE_BUNDLES = Collections.unmodifiableList(java.util.Arrays.asList(
            "drawio-headless-env.js", "drawio-graph.js", "drawio-xml-dom.js", "drawio-headless.js"));

    /** Shape and stencil bundles loaded when present, in this order. */
    static final List<String> OPTIONAL_SHAPE_BUNDLES = Collections.unmodifiableList(java.util.Arrays.asList(
            "drawio-shape-library.js", "drawio-stencil-runtime.js", "drawio-stencils.js"));

    static final List<String> TAIL_BUNDLES = Collections.unmodifiableList(java.util.Arrays.asList(
            "drawio-canvas-adapter.js", "drawio-render.js"));

    private static final String STYLESHEET_RESOURCE = "/drawio/upstream/styles/default.xml";
    private static volatile String defaultStylesheetJson;

    private final List<String> shapeBundles;
    private final double scale;

    /** Use the shape and stencil bundles available on the classpath. */
    public OriginalGraphPipeline() {
        this(availableBundles(OPTIONAL_SHAPE_BUNDLES), 1);
    }

    /**
     * Use explicit shape bundles (resource names under {@code /drawio/}).
     *
     * @param shapeBundles bundles loaded between the headless core and the canvas adapter
     * @param scale        view scale passed to {@code mxGraphView}
     */
    public OriginalGraphPipeline(List<String> shapeBundles, double scale) {
        this.shapeBundles = Collections.unmodifiableList(new ArrayList<String>(
                Objects.requireNonNull(shapeBundles, "shapeBundles")));
        if (!(scale > 0) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("scale must be positive");
        }
        this.scale = scale;
    }

    @Override
    public DrawioScene prepare(DiagramPage page, RenderSession session) throws RenderException {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(session, "session");
        session.checkCancelled();

        String modelJson = toJson(page);
        String request = "{\"scale\":" + scale + ",\"maxCells\":" + session.limits().getMaxCells() + "}";
        CanvasRecorder recorder = new CanvasRecorder();
        RecordingDiagnostics diagnostics = new RecordingDiagnostics(new SessionCanvasDiagnostics(session));
        DrawioHost host = new DrawioHost(resources(request, modelJson),
                new DrawioCanvasBridge(recorder.canvas(), new PolicyImageSources(session.resourcePolicy()),
                        diagnostics),
                session.textMetrics());

        ScriptExecutionResult result;
        GraalJsRuntime runtime = new GraalJsRuntime(session.remaining());
        try {
            session.onCancel(runtime::close);
            result = runtime.execute(sources(), host);
        } finally {
            runtime.close();
        }
        session.checkCancelled();
        if (diagnostics.abort != null) {
            throw diagnostics.abort.getRenderException();
        }
        if (!result.isSuccessful()) {
            throw failure(result.getErrorMessage());
        }

        GraphReport report = GraphReport.parse(result.getOutput());
        if (report.getLimitExceeded() != null) {
            throw new RenderException(RenderErrorCode.LIMIT_EXCEEDED,
                    "The diagram has more cells than the configured limit");
        }
        for (GraphReport.Diagnostic diagnostic : report.getDiagnostics()) {
            if (diagnostic.isUnsupported()) {
                session.unsupported(diagnostic.getCapability(), diagnostic.getDetail());
            } else {
                session.warn(diagnostic.getCapability(), diagnostic.getDetail());
            }
        }
        return recorder.toScene(report.getBounds());
    }

    private List<ScriptSource> sources() {
        List<ScriptSource> sources = new ArrayList<ScriptSource>();
        for (String name : CORE_BUNDLES) {
            sources.add(ScriptSource.bundled(name));
        }
        for (String name : shapeBundles) {
            sources.add(ScriptSource.bundled(name));
        }
        for (String name : TAIL_BUNDLES) {
            sources.add(ScriptSource.bundled(name));
        }
        return sources;
    }

    private static ScriptResourceProvider resources(String request, String modelJson) throws RenderException {
        final String stylesheet = defaultStylesheet();
        return resourceId -> {
            if ("request".equals(resourceId)) {
                return request;
            }
            if ("model".equals(resourceId)) {
                return modelJson;
            }
            if ("stylesheet".equals(resourceId)) {
                return stylesheet;
            }
            throw new IllegalArgumentException("Unknown graph resource");
        };
    }

    private static String toJson(DiagramPage page) throws RenderException {
        try {
            return XmlTreeJson.toJson(page.getGraphModelXml());
        } catch (DiagramFormatException exception) {
            RenderErrorCode code = exception.getReason() == DiagramFormatException.Reason.LIMIT_EXCEEDED
                    ? RenderErrorCode.LIMIT_EXCEEDED : RenderErrorCode.INVALID_XML;
            throw new RenderException(code, "The diagram page is not a readable mxGraphModel", null, exception);
        }
    }

    static String defaultStylesheet() throws RenderException {
        String cached = defaultStylesheetJson;
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = OriginalGraphPipeline.class.getResourceAsStream(STYLESHEET_RESOURCE)) {
            if (stream == null) {
                throw new RenderException(RenderErrorCode.RENDER_FAILED, "Bundled draw.io stylesheet is missing");
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            cached = XmlTreeJson.toJson(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            defaultStylesheetJson = cached;
            return cached;
        } catch (RenderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RenderException(RenderErrorCode.RENDER_FAILED,
                    "Bundled draw.io stylesheet is unreadable", null, exception);
        }
    }

    private static RenderException failure(String message) {
        String detail = message == null ? "unknown error" : message;
        if (detail.contains("timed out")) {
            return new RenderException(RenderErrorCode.TIMEOUT, "Rendering exceeded the time limit");
        }
        if (detail.contains("Unsupported draw.io stencil")) {
            return new RenderException(RenderErrorCode.UNSUPPORTED_STENCIL, detail);
        }
        return new RenderException(RenderErrorCode.RENDER_FAILED, "Graph rendering failed: " + detail);
    }

    /** Keep the first fail-fast abort; GraalJS turns host exceptions into guest errors. */
    private static final class RecordingDiagnostics implements CanvasDiagnostics {
        private final CanvasDiagnostics delegate;
        private volatile SessionCanvasDiagnostics.Abort abort;

        RecordingDiagnostics(CanvasDiagnostics delegate) {
            this.delegate = delegate;
        }

        @Override
        public void unsupported(String capabilityId, String message) {
            try {
                delegate.unsupported(capabilityId, message);
            } catch (SessionCanvasDiagnostics.Abort exception) {
                if (abort == null) {
                    abort = exception;
                }
                throw exception;
            }
        }

        @Override
        public void missingResource(String capabilityId, String message) {
            try {
                delegate.missingResource(capabilityId, message);
            } catch (SessionCanvasDiagnostics.Abort exception) {
                if (abort == null) {
                    abort = exception;
                }
                throw exception;
            }
        }
    }

    static List<String> availableBundles(List<String> names) {
        List<String> available = new ArrayList<String>();
        for (String name : names) {
            if (OriginalGraphPipeline.class.getResource("/drawio/" + name) != null) {
                available.add(name);
            }
        }
        return available;
    }

    /**
     * Result report of {@code DrawioHeadless.render}.
     */
    static final class GraphReport {
        private final Rectangle2D bounds;
        private final String limitExceeded;
        private final int cells;
        private final List<Diagnostic> diagnostics;
        private final List<String> domProbes;

        private GraphReport(Rectangle2D bounds, String limitExceeded, int cells,
                            List<Diagnostic> diagnostics, List<String> domProbes) {
            this.bounds = bounds;
            this.limitExceeded = limitExceeded;
            this.cells = cells;
            this.diagnostics = diagnostics;
            this.domProbes = domProbes;
        }

        @SuppressWarnings("unchecked")
        static GraphReport parse(String json) throws RenderException {
            try {
                Map<String, Object> map = (Map<String, Object>) SimpleJson.parse(json);
                Rectangle2D bounds = null;
                Object b = map.get("bounds");
                if (b instanceof Map) {
                    Map<String, Object> r = (Map<String, Object>) b;
                    bounds = new Rectangle2D.Double(num(r.get("x")), num(r.get("y")),
                            num(r.get("width")), num(r.get("height")));
                }
                List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
                Object d = map.get("diagnostics");
                if (d instanceof List) {
                    for (Object item : (List<Object>) d) {
                        Map<String, Object> entry = (Map<String, Object>) item;
                        diagnostics.add(new Diagnostic(String.valueOf(entry.get("capability")),
                                String.valueOf(entry.get("detail"))));
                    }
                }
                List<String> probes = new ArrayList<String>();
                Object p = map.get("domProbes");
                if (p instanceof List) {
                    for (Object item : (List<Object>) p) {
                        probes.add(String.valueOf(item));
                    }
                }
                Object limit = map.get("limitExceeded");
                Object cells = map.get("cells");
                return new GraphReport(bounds, limit == null ? null : String.valueOf(limit),
                        cells instanceof Double ? ((Double) cells).intValue() : 0,
                        Collections.unmodifiableList(diagnostics), Collections.unmodifiableList(probes));
            } catch (RuntimeException exception) {
                throw new RenderException(RenderErrorCode.RENDER_FAILED,
                        "Graph pipeline returned an unreadable report", null, exception);
            }
        }

        private static double num(Object value) {
            return value instanceof Double ? (Double) value : 0;
        }

        Rectangle2D getBounds() {
            return bounds;
        }

        String getLimitExceeded() {
            return limitExceeded;
        }

        int getCells() {
            return cells;
        }

        List<Diagnostic> getDiagnostics() {
            return diagnostics;
        }

        List<String> getDomProbes() {
            return domProbes;
        }

        static final class Diagnostic {
            private final String capability;
            private final String detail;

            Diagnostic(String capability, String detail) {
                this.capability = capability;
                this.detail = detail;
            }

            String getCapability() {
                return capability;
            }

            String getDetail() {
                return detail;
            }

            /** Missing shapes, stencils and label features change what is shown. */
            boolean isUnsupported() {
                return capability.startsWith("shape:") || capability.startsWith("stencil:")
                        || capability.startsWith("label:") || capability.startsWith("canvas:");
            }
        }
    }
}
