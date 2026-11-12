/*
 * Adapt original mxAbstractCanvas2D command methods to the Java canvas bridge.
 * Retain the upstream JavaScript state for shapes that read canvas.state.
 * Keep this adapter independent from browser objects and Java2D types.
 */
(function (scope) {
    'use strict';

    function DrawioJavaCanvas() {
        mxAbstractCanvas2D.call(this);
        this.bridge = drawioHost.canvas();
        this.lastX = 0;
        this.lastY = 0;
    }

    mxUtils.extend(DrawioJavaCanvas, mxAbstractCanvas2D);

    // Mirror mxUtils.hasResourceReference when the full mxUtils is loaded:
    // url(...) references in colors are dropped, as in mxAbstractCanvas2D.
    function noResource(value) {
        return (value != null && typeof mxUtils.hasResourceReference === 'function' &&
            mxUtils.hasResourceReference(value)) ? null : value;
    }

    function colorOrNull(value) {
        value = noResource(value);
        return (value == null || value === 'none') ? null : String(value);
    }

    function stringOrNull(value) {
        return (value == null) ? null : String(value);
    }

    function numberOr(value, fallback) {
        var result = Number(value);
        return isFinite(result) ? result : fallback;
    }

    DrawioJavaCanvas.prototype.save = function () {
        mxAbstractCanvas2D.prototype.save.call(this);
        this.bridge.save();
    };

    DrawioJavaCanvas.prototype.restore = function () {
        mxAbstractCanvas2D.prototype.restore.call(this);
        this.bridge.restore();
    };

    DrawioJavaCanvas.prototype.scale = function (factor) {
        mxAbstractCanvas2D.prototype.scale.call(this, factor);
        this.bridge.scale(factor);
    };

    DrawioJavaCanvas.prototype.translate = function (x, y) {
        mxAbstractCanvas2D.prototype.translate.call(this, x, y);
        this.bridge.translate(x, y);
    };

    DrawioJavaCanvas.prototype.rotate = function (degrees, flipH, flipV, centerX, centerY) {
        this.bridge.rotate(degrees, Boolean(flipH), Boolean(flipV), centerX, centerY);
    };

    DrawioJavaCanvas.prototype.setAlpha = function (alpha) {
        this.state.alpha = alpha;
        this.bridge.setAlpha(alpha);
    };

    DrawioJavaCanvas.prototype.setFillAlpha = function (alpha) {
        this.state.fillAlpha = alpha;
        this.bridge.setFillAlpha(alpha);
    };

    DrawioJavaCanvas.prototype.setStrokeAlpha = function (alpha) {
        this.state.strokeAlpha = alpha;
        this.bridge.setStrokeAlpha(alpha);
    };

    DrawioJavaCanvas.prototype.setFillColor = function (color) {
        color = colorOrNull(color);
        this.state.fillColor = color;
        this.state.gradientColor = null;
        this.bridge.setFillColor(color);
    };

    DrawioJavaCanvas.prototype.setGradient = function (color1, color2, x, y, w, h,
                                                       direction, alpha1, alpha2) {
        color1 = colorOrNull(color1);
        color2 = colorOrNull(color2);

        if (color1 == null || color2 == null) {
            // A gradient without an end color is a solid fill (see mxSvgCanvas2D.updateFill)
            this.setFillColor(color1);
            return;
        }

        var s = this.state;
        s.fillColor = color1;
        s.gradientFillAlpha = (alpha1 != null) ? alpha1 : 1;
        s.gradientColor = color2;
        s.gradientAlpha = (alpha2 != null) ? alpha2 : 1;
        s.gradientDirection = direction;
        this.bridge.setGradient(color1, color2, numberOr(x, 0), numberOr(y, 0),
            numberOr(w, 0), numberOr(h, 0), stringOrNull(direction),
            numberOr(s.gradientFillAlpha, 1), numberOr(s.gradientAlpha, 1));
    };

    DrawioJavaCanvas.prototype.setFillStyle = function (value, hachureGap, fillWeight, hachureAngle) {
        mxAbstractCanvas2D.prototype.setFillStyle.apply(this, arguments);

        if (value != null && value !== 'none' && value !== 'auto' && value !== 'solid') {
            this.bridge.unsupported('canvas:fillStyle:' + value,
                'Sketch fill styles are not supported');
        }
    };

    DrawioJavaCanvas.prototype.setStrokeColor = function (color) {
        color = colorOrNull(color);
        this.state.strokeColor = color;
        this.bridge.setStrokeColor(color);
    };

    // The painted width is state.strokeWidth * state.scale, as in
    // mxSvgCanvas2D.getCurrentStrokeWidth; the Java canvas applies its scale.
    DrawioJavaCanvas.prototype.setStrokeWidth = function (width) {
        this.state.strokeWidth = width;
        this.bridge.setStrokeWidth(numberOr(width, 1));
    };

    DrawioJavaCanvas.prototype.setDashed = function (dashed, fixDash) {
        this.state.dashed = Boolean(dashed);
        this.state.fixDash = Boolean(fixDash);
        this.bridge.setDashed(Boolean(dashed));
        this.bridge.setFixDash(Boolean(fixDash));
    };

    DrawioJavaCanvas.prototype.setDashPattern = function (pattern) {
        this.state.dashPattern = pattern;
        this.bridge.setDashPattern(pattern);
    };

    DrawioJavaCanvas.prototype.setLineCap = function (cap) {
        this.state.lineCap = cap;
        this.bridge.setLineCap(cap);
    };

    DrawioJavaCanvas.prototype.setLineJoin = function (join) {
        this.state.lineJoin = join;
        this.bridge.setLineJoin(join);
    };

    DrawioJavaCanvas.prototype.setMiterLimit = function (limit) {
        this.state.miterLimit = limit;
        this.bridge.setMiterLimit(limit);
    };

    // Push the effective shadow parameters. mxShape passes its shadow style
    // object ({dx, dy, blur, color, opacity in percent}); other callers use
    // the setShadowColor/Alpha/Offset state as in mxAbstractCanvas2D.
    DrawioJavaCanvas.prototype.pushShadowStyle = function () {
        var s = this.state;
        var style = s.shadowStyle;

        if (style != null && typeof style === 'object') {
            this.bridge.setShadowStyle(colorOrNull(style.color),
                numberOr(style.opacity, 100) / 100, numberOr(style.dx, 0),
                numberOr(style.dy, 0), numberOr(style.blur, 0));
        } else {
            this.bridge.setShadowStyle(colorOrNull(s.shadowColor), numberOr(s.shadowAlpha, 1),
                numberOr(s.shadowDx, 0), numberOr(s.shadowDy, 0),
                numberOr(mxConstants.SHADOW_BLUR, 0));
        }
    };

    DrawioJavaCanvas.prototype.setShadow = function (enabled, style) {
        this.state.shadow = Boolean(enabled);
        this.state.shadowStyle = style;

        if (this.state.shadow) {
            this.pushShadowStyle();
        }

        this.bridge.setShadow(this.state.shadow);
    };

    DrawioJavaCanvas.prototype.setShadowColor = function (value) {
        mxAbstractCanvas2D.prototype.setShadowColor.call(this, value);
        this.state.shadowStyle = null;
        this.pushShadowStyle();
    };

    DrawioJavaCanvas.prototype.setShadowAlpha = function (value) {
        mxAbstractCanvas2D.prototype.setShadowAlpha.call(this, value);
        this.state.shadowStyle = null;
        this.pushShadowStyle();
    };

    DrawioJavaCanvas.prototype.setShadowOffset = function (dx, dy) {
        mxAbstractCanvas2D.prototype.setShadowOffset.call(this, dx, dy);
        this.state.shadowStyle = null;
        this.pushShadowStyle();
    };

    DrawioJavaCanvas.prototype.setFontColor = function (value) {
        value = colorOrNull(value);
        this.state.fontColor = value;
        this.bridge.setFontColor(value);
    };

    DrawioJavaCanvas.prototype.setFontBackgroundColor = function (value) {
        value = colorOrNull(value);
        this.state.fontBackgroundColor = value;
        this.bridge.setFontBackgroundColor(value);
    };

    DrawioJavaCanvas.prototype.setFontBorderColor = function (value) {
        value = colorOrNull(value);
        this.state.fontBorderColor = value;
        this.bridge.setFontBorderColor(value);
    };

    DrawioJavaCanvas.prototype.setFontBorderWidth = function (value) {
        value = parseFloat(value);
        this.state.fontBorderWidth = (isFinite(value) && value >= 0) ? value : 1;
        this.bridge.setFontBorderWidth(this.state.fontBorderWidth);
    };

    DrawioJavaCanvas.prototype.setFontSize = function (value) {
        this.state.fontSize = parseFloat(value);
        this.bridge.setFontSize(numberOr(this.state.fontSize, mxConstants.DEFAULT_FONTSIZE));
    };

    DrawioJavaCanvas.prototype.setFontFamily = function (value) {
        this.state.fontFamily = value;
        this.bridge.setFontFamily(String((value != null) ? value : mxConstants.DEFAULT_FONTFAMILY));
    };

    DrawioJavaCanvas.prototype.setFontStyle = function (value) {
        this.state.fontStyle = (value != null) ? value : 0;
        this.bridge.setFontStyle(numberOr(this.state.fontStyle, 0) | 0);
    };

    // Labels padding only affects the text background box; kept in state.
    DrawioJavaCanvas.prototype.setLabelPadding = function (value) {
        this.state.labelPadding = value;
    };

    // Links and titles have no visual effect in raster/vector exports.
    DrawioJavaCanvas.prototype.setLink = function () {
    };

    DrawioJavaCanvas.prototype.setTitle = function () {
    };

    DrawioJavaCanvas.prototype.begin = function () {
        this.bridge.begin();
    };

    DrawioJavaCanvas.prototype.moveTo = function (x, y) {
        this.lastX = x;
        this.lastY = y;
        this.bridge.moveTo(x, y);
    };

    DrawioJavaCanvas.prototype.lineTo = function (x, y) {
        this.lastX = x;
        this.lastY = y;
        this.bridge.lineTo(x, y);
    };

    DrawioJavaCanvas.prototype.quadTo = function (cx, cy, x, y) {
        this.lastX = x;
        this.lastY = y;
        this.bridge.quadTo(cx, cy, x, y);
    };

    DrawioJavaCanvas.prototype.curveTo = function (cx1, cy1, cx2, cy2, x, y) {
        this.lastX = x;
        this.lastY = y;
        this.bridge.curveTo(cx1, cy1, cx2, cy2, x, y);
    };

    DrawioJavaCanvas.prototype.close = function () {
        this.bridge.closePath();
    };

    DrawioJavaCanvas.prototype.rect = function (x, y, width, height) {
        this.bridge.rect(x, y, width, height);
    };

    DrawioJavaCanvas.prototype.roundrect = function (x, y, width, height, rx, ry) {
        this.bridge.roundrect(x, y, width, height, rx, ry);
    };

    DrawioJavaCanvas.prototype.ellipse = function (x, y, width, height) {
        this.bridge.ellipse(x, y, width, height);
    };

    // Use the original mxAbstractCanvas2D.arcTo, which converts the SVG arc
    // into cubic curves with mxUtils.arcToCurves from the last point.
    DrawioJavaCanvas.prototype.arcTo = function (rx, ry, angle, largeArcFlag, sweepFlag, x, y) {
        if (typeof mxUtils.arcToCurves !== 'function') {
            throw new Error('Draw.io arcTo requires the original mxUtils.arcToCurves');
        }
        mxAbstractCanvas2D.prototype.arcTo.call(this, rx, ry, angle, largeArcFlag, sweepFlag, x, y);
        this.lastX = x;
        this.lastY = y;
    };

    DrawioJavaCanvas.prototype.clip = function () {
        this.bridge.clip();
    };

    DrawioJavaCanvas.prototype.fill = function () {
        this.bridge.fill();
    };

    DrawioJavaCanvas.prototype.stroke = function () {
        this.bridge.stroke();
    };

    DrawioJavaCanvas.prototype.fillAndStroke = function () {
        this.bridge.fillAndStroke();
    };

    // Argument semantics of mxSvgCanvas2D.text. DOM node labels are not
    // supported because no browser DOM exists in this runtime.
    DrawioJavaCanvas.prototype.text = function (x, y, w, h, str, align, valign, wrap,
                                                format, overflow, clip, rotation, dir) {
        if (str == null) {
            return;
        }

        if (typeof str === 'object') {
            this.bridge.unsupported('label:dom-node', 'DOM node labels are not supported');
            return;
        }

        this.bridge.text(numberOr(x, 0), numberOr(y, 0), numberOr(w, 0), numberOr(h, 0),
            String(str), String((align != null) ? align : ''),
            String((valign != null) ? valign : ''), Boolean(wrap),
            String((format != null) ? format : ''), stringOrNull(overflow), Boolean(clip),
            numberOr(rotation, 0), stringOrNull(dir));
    };

    // Argument semantics of mxSvgCanvas2D.image; aspect defaults to true.
    DrawioJavaCanvas.prototype.image = function (x, y, w, h, src, aspect, flipH, flipV) {
        this.bridge.image(numberOr(x, 0), numberOr(y, 0), numberOr(w, 0), numberOr(h, 0),
            (src != null) ? String(src) : '', (aspect != null) ? Boolean(aspect) : true,
            Boolean(flipH), Boolean(flipV));
    };

    scope.DrawioJavaCanvas = DrawioJavaCanvas;
})(this);
