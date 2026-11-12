/*
 * Headless draw.io graph runtime on top of the pinned original mxGraph core.
 *
 * Load order (one GraalJS context):
 *   drawio-headless-env.js, drawio-graph.js, drawio-xml-dom.js,
 *   shape/stencil bundles, drawio-canvas-adapter.js, drawio-headless.js.
 *
 * This file contains three clearly separated groups:
 *
 * 1. draw.io compatibility settings ported from the pinned draw.io
 *    js/grapheditor/Graph.js (same upstream commit). Each port names its origin.
 *    Graph.js itself is not loaded: it needs DOMPurify, the editor UI and a
 *    browser DOM (see docs/HEADLESS-GRAPH.md).
 * 2. Headless overrides: no DOM painting, no interaction handlers, label sizes
 *    from the Java text metrics port, export painting via canvas transforms.
 * 3. DrawioHeadless.render: decode with the original mxCodec, validate the
 *    original mxGraphView and paint with the original mxImageExport into the
 *    Java2D canvas bridge.
 *
 * Unsupported features are collected as diagnostics with a capability id and
 * returned to Java; nothing is silently replaced.
 */
(function (scope) {
    'use strict';

    var diagnostics = [];
    var diagnosticKeys = {};
    var MAX_DIAGNOSTICS = 200;

    function report(capability, detail) {
        var key = capability + '|' + detail;
        if (diagnosticKeys[key] || diagnostics.length >= MAX_DIAGNOSTICS) {
            return;
        }
        diagnosticKeys[key] = true;
        diagnostics.push({capability: String(capability), detail: String(detail)});
    }

    // ------------------------------------------------------------------
    // 1. draw.io compatibility (ported from js/grapheditor/Graph.js)
    // ------------------------------------------------------------------

    // Graph.js: "Overrides global constants".
    mxConstants.SHADOW_OPACITY = 0.25;
    mxConstants.SHADOWCOLOR = '#000000';

    // Graph.js: mxCodec.allowlist, reduced to the types a diagram file holds.
    // mxCodec resolves element names through window[name]; the allowlist keeps
    // untrusted XML from instantiating anything else.
    mxCodec.allowlist = ['mxStylesheet', 'Array', 'mxGraphModel',
        'mxCell', 'mxGeometry', 'mxRectangle', 'mxPoint'];
    mxObjectCodec.allowEval = false;
    mxStylesheetCodec.allowEval = false;

    // Graph.js: "Matches label positions of mxGraph 1.x".
    mxText.prototype.baseSpacingTop = 5;
    mxText.prototype.baseSpacingBottom = 1;

    // Graph.js: "Keeps edges between relative child cells inside parent".
    mxGraphModel.prototype.ignoreRelativeEdgeParent = false;

    /**
     * Headless draw.io graph. Named Graph because original draw.io shape
     * files reference Graph statics (for example Graph.handleFactory).
     */
    function Graph(model, stylesheet) {
        mxGraph.call(this, null, model, null, stylesheet);

        // Graph.js Graph constructor: "HTML entities are displayed as plain
        // text in wrapped plain text labels". The html=1 branch (DOMPurify
        // sanitizing, style scoping) is not ported: HtmlLabelParser in Java
        // interprets only a safe formatting subset and executes nothing.
        this.cellRenderer.getLabelValue = function (state) {
            var result = mxCellRenderer.prototype.getLabelValue.apply(this, arguments);
            if (result != null && state.view.graph.isHtmlLabel(state.cell)
                    && mxUtils.getValue(state.style, 'html', '0') == '0') {
                result = mxUtils.htmlEntities(result, false);
            }
            return result;
        };
    }

    mxUtils.extend(Graph, mxGraph);

    Graph.handleFactory = {};

    // Light theme defaults of draw.io (Graph.js shapeBackgroundColor and
    // shapeForegroundColor for the default theme).
    Graph.prototype.shapeBackgroundColor = '#ffffff';
    Graph.prototype.shapeForegroundColor = '#000000';

    // Graph.js Graph constructor: this.isHtmlLabel.
    Graph.prototype.isHtmlLabel = function (cell) {
        var style = this.getCurrentCellStyle(cell);
        return style != null ? (style.html == '1' || style[mxConstants.STYLE_WHITE_SPACE] == 'wrap') : false;
    };

    // Graph.js Graph.prototype.convertValueToString without placeholders,
    // translations and data sources, which are reported as unsupported.
    Graph.prototype.convertValueToString = function (cell) {
        var value = this.model.getValue(cell);
        if (value != null && typeof value === 'object') {
            if (value.getAttribute != null && value.getAttribute('placeholders') == '1') {
                report('label:placeholders', 'cell ' + cell.id + ' uses %placeholders%; label shown verbatim');
            }
            return (value.getAttribute != null ? value.getAttribute('label') : null) || '';
        }
        return mxGraph.prototype.convertValueToString.apply(this, arguments);
    };

    // Graph.js Graph.prototype.replaceDefaultColors / replaceDefaultColor,
    // light theme only. The 'invert' variant needs light-dark() support.
    var DEFAULT_COLOR_KEYS = [
        [mxConstants.STYLE_FONTCOLOR, 'fg'], [mxConstants.STYLE_FILLCOLOR, 'bg'],
        [mxConstants.STYLE_GRADIENTCOLOR, 'fg'], [mxConstants.STYLE_STROKECOLOR, 'fg'],
        [mxConstants.STYLE_IMAGE_BORDER, 'fg'], [mxConstants.STYLE_IMAGE_BACKGROUND, 'bg'],
        [mxConstants.STYLE_LABEL_BORDERCOLOR, 'fg'], [mxConstants.STYLE_SWIMLANE_FILLCOLOR, 'bg'],
        [mxConstants.STYLE_LABEL_BACKGROUNDCOLOR, 'bg']];
    var LIGHT_DARK = /^\s*light-dark\(\s*([^,()]+(?:\([^()]*\))?)\s*,/i;

    Graph.prototype.replaceDefaultColors = function (style) {
        var labelGap = style.defaultLabelBackgroundColor;
        if ((labelGap == 'page' || labelGap == 'gap')
                && style[mxConstants.STYLE_LABEL_BACKGROUNDCOLOR] == 'default') {
            style[mxConstants.STYLE_LABEL_BACKGROUNDCOLOR] = mxConstants.NONE;
        }
        for (var i = 0; i < DEFAULT_COLOR_KEYS.length; i++) {
            var key = DEFAULT_COLOR_KEYS[i][0];
            var value = style[key];
            if (value == 'default') {
                var invert = style['default' + key.charAt(0).toUpperCase() + key.substring(1)] == 'invert';
                if (invert) {
                    report('style:invert-default-color', key);
                }
                style[key] = DEFAULT_COLOR_KEYS[i][1] == 'fg'
                    ? this.shapeForegroundColor : this.shapeBackgroundColor;
            } else if (typeof value === 'string' && LIGHT_DARK.test(value)) {
                // draw.io adaptive colors: render the light-mode value.
                style[key] = LIGHT_DARK.exec(value)[1].trim();
            }
        }
        return style;
    };

    Graph.prototype.postProcessCellStyle = function (cell, style) {
        return this.replaceDefaultColors(mxGraph.prototype.postProcessCellStyle.call(this, cell, style));
    };

    // ------------------------------------------------------------------
    // 2. Headless overrides of the original core
    // ------------------------------------------------------------------

    // No interaction handlers in a renderer. The cell renderer only notifies
    // the selection handler, which is inert here.
    var inertSelectionHandler = Object.freeze({
        updateHandler: function () {
        },
        getHandler: function () {
            return null;
        },
        isHandled: function () {
            return false;
        },
        reset: function () {
        },
        refresh: function () {
        }
    });

    mxGraph.prototype.createHandlers = function () {
        this.selectionCellsHandler = inertSelectionHandler;
    };

    // DOM event listeners (mxEvent) are browser-only. Graph model events use
    // mxEventSource and keep working.
    mxEvent.addListener = function () {
    };
    mxEvent.removeListener = function () {
    };
    mxEvent.addGestureListeners = function () {
    };
    mxEvent.removeGestureListeners = function () {
    };
    mxEvent.removeAllListeners = function () {
    };
    mxEvent.release = function () {
    };

    mxCellRenderer.prototype.installListeners = function () {
        // Mouse and touch listeners are irrelevant for headless rendering.
    };

    // Shapes are painted only through mxImageExport into the Java canvas.
    // mxShape.redrawNode would update SVG or HTML DOM nodes; headless it keeps
    // only the geometry part. Bounding boxes come from the shape geometry
    // (mxShape.getShapeBoundingBox), not from SVG getBBox.
    mxShape.prototype.redrawNode = function () {
        this.updateBoundsFromPoints();
        if (!(this.visible && this.checkBounds())) {
            this.boundingBox = null;
        }
    };
    mxShape.prototype.getSvgBoundingBox = function () {
        return null;
    };

    // Reports shapes without registered implementation instead of letting
    // mxCellRenderer fall back to a rectangle or connector silently.
    var originalCreateShape = mxCellRenderer.prototype.createShape;
    mxCellRenderer.prototype.createShape = function (state) {
        var name = state.style != null ? state.style[mxConstants.STYLE_SHAPE] : null;
        if (name != null && mxCellRenderer.defaultShapes[name] == null
                && mxStencilRegistry.getStencil(name) == null) {
            report('shape:' + name, 'cell ' + state.cell.id + ' uses an unsupported shape');
        }
        return originalCreateShape.apply(this, arguments);
    };

    // Label sizes: the browser measures the label DOM. Headless, the Java text
    // metrics port measures the same value; mxText.updateBoundingBox then uses
    // the cached offset size exactly as upstream does for HTML labels.
    mxText.prototype.redraw = function () {
        this.invalidateBoundingBox();
        this.offsetWidth = null;
        this.offsetHeight = null;
        if (this.isStringSizeMeasured()) {
            var size = DrawioHeadless.measureLabel(this);
            this.offsetWidth = size.width + this.textWidthPadding;
            this.offsetHeight = size.height;
        }
    };

    // mxImageExport.doDrawShape appends SVG nodes and applies the crisp-edge
    // offset as an SVG transform attribute. Headless, the same offset is
    // applied as a canvas translation in output pixels.
    mxImageExport.prototype.doDrawShape = function (shape, canvas) {
        if (shape != null && shape.checkBounds()) {
            canvas.save();
            var offset = this.getSvgShapeOffset(shape, canvas);
            if (offset != 0) {
                var scale = canvas.state != null ? canvas.state.scale : 1;
                canvas.translate(offset / scale, offset / scale);
            }
            shape.beforePaint(canvas);
            shape.paint(canvas);
            shape.afterPaint(canvas);
            canvas.restore();
        }
    };

    // ------------------------------------------------------------------
    // 3. Rendering entry point
    // ------------------------------------------------------------------

    var FONT_BOLD = 1;
    var FONT_ITALIC = 2;

    function measureLabel(text) {
        var value = text.value;
        var html = mxUtils.isNode(value) || text.dialect == mxConstants.DIALECT_STRICTHTML;
        if (mxUtils.isNode(value)) {
            report('label:node-value', 'labels with XML node values are measured as text');
            value = mxUtils.getTextContent(value);
        }
        value = value == null ? '' : String(value);
        if (value.length == 0) {
            return {width: 0, height: 0};
        }
        var wrapWidth = text.wrap && text.bounds != null ? Math.max(1, text.bounds.width / text.scale) : -1;
        var style = text.fontStyle || 0;
        var measured = String(drawioHost.measureLabel(value, html, text.family || mxConstants.DEFAULT_FONTFAMILY,
            text.size || mxConstants.DEFAULT_FONTSIZE, (style & FONT_BOLD) == FONT_BOLD,
            (style & FONT_ITALIC) == FONT_ITALIC, wrapWidth)).split(',');
        return {width: parseFloat(measured[0]), height: parseFloat(measured[1])};
    }

    function countCells(model) {
        var count = 0;
        var stack = [model.getRoot()];
        while (stack.length > 0) {
            var cell = stack.pop();
            if (cell == null) {
                continue;
            }
            count++;
            var n = model.getChildCount(cell);
            for (var i = 0; i < n; i++) {
                stack.push(model.getChildAt(cell, i));
            }
        }
        return count;
    }

    function loadStylesheet(graph, stylesheetJson) {
        if (stylesheetJson == null) {
            return;
        }
        var doc = DrawioXml.fromJson(stylesheetJson);
        new mxCodec(doc).decode(doc.documentElement, graph.getStylesheet());
    }

    function decodeModel(graph, modelJson) {
        var doc = DrawioXml.fromJson(modelJson);
        var node = doc.documentElement;
        if (node == null || node.nodeName != 'mxGraphModel') {
            throw new Error('DrawioHeadless: expected an mxGraphModel element');
        }
        new mxCodec(doc).decode(node, graph.getModel());
        return node;
    }

    function validateReferences(model) {
        var cells = model.cells || {};
        for (var id in cells) {
            if (Object.prototype.hasOwnProperty.call(cells, id)) {
                var cell = cells[id];
                if (cell.edge) {
                    if (cell.source == null && cell.geometry != null && cell.geometry.sourcePoint == null) {
                        report('edge:dangling-source', 'edge ' + id + ' has neither source nor source point');
                    }
                    if (cell.target == null && cell.geometry != null && cell.geometry.targetPoint == null) {
                        report('edge:dangling-target', 'edge ' + id + ' has neither target nor target point');
                    }
                }
                if (cell.vertex && cell.geometry == null) {
                    report('cell:missing-geometry', 'vertex ' + id + ' has no geometry');
                }
            }
        }
    }

    function rect(r) {
        return r == null ? null : {x: r.x, y: r.y, width: r.width, height: r.height};
    }

    /**
     * Decode, lay out and paint one diagram page.
     *
     * request: {model: json, stylesheet: json|null, scale: number,
     *           maxCells: number, paint: boolean}
     * returns: JSON text {cells, bounds, diagnostics, domProbes}
     */
    function render(request) {
        var graph = new Graph();
        loadStylesheet(graph, request.stylesheet);
        var modelNode = decodeModel(graph, request.model);
        var model = graph.getModel();

        var cellCount = countCells(model);
        if (request.maxCells > 0 && cellCount > request.maxCells) {
            return JSON.stringify({limitExceeded: 'cells', cells: cellCount});
        }
        validateReferences(model);

        // Shapes are created only while a container is present. The container
        // is an inert element: no view DOM is initialised (graph.init is
        // never called) and nothing is attached to it.
        graph.container = DrawioHeadlessEnv.createInertElement('div');
        graph.view.scale = request.scale > 0 ? request.scale : 1;
        graph.view.revalidate();
        var bounds = graph.getGraphBounds();

        if (request.paint !== false) {
            var canvas = new DrawioJavaCanvas();
            new mxImageExport().drawState(graph.getView().getState(model.getRoot()), canvas);
        }

        var background = modelNode.getAttribute('background');
        return JSON.stringify({
            cells: cellCount,
            scale: graph.view.scale,
            bounds: rect(bounds),
            background: background,
            diagnostics: diagnostics,
            domProbes: DrawioHeadlessEnv.probeReport()
        });
    }

    var DrawioHeadless = {
        Graph: Graph,
        render: render,
        measureLabel: measureLabel,
        report: report,
        diagnostics: function () {
            return diagnostics.slice();
        }
    };

    scope.Graph = Graph;
    scope.DrawioHeadless = DrawioHeadless;
})(this);
