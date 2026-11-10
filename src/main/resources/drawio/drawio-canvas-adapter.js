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
    }

    mxUtils.extend(DrawioJavaCanvas, mxAbstractCanvas2D);

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
        this.state.fillColor = color === 'none' ? null : color;
        this.bridge.setFillColor(color);
    };

    DrawioJavaCanvas.prototype.setStrokeColor = function (color) {
        this.state.strokeColor = color === 'none' ? null : color;
        this.bridge.setStrokeColor(color);
    };

    DrawioJavaCanvas.prototype.setStrokeWidth = function (width) {
        this.state.strokeWidth = width;
        this.bridge.setStrokeWidth(width);
    };

    DrawioJavaCanvas.prototype.setDashed = function (dashed, fixDash) {
        if (fixDash) {
            throw new Error('Draw.io fixDash is not yet supported');
        }
        this.state.dashed = Boolean(dashed);
        this.bridge.setDashed(Boolean(dashed));
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

    DrawioJavaCanvas.prototype.setShadow = function (enabled) {
        this.bridge.setShadow(Boolean(enabled));
        this.state.shadow = Boolean(enabled);
    };

    DrawioJavaCanvas.prototype.setGradient = function () {
        throw new Error('Draw.io gradients require a dedicated adapter (#10)');
    };

    DrawioJavaCanvas.prototype.begin = function () {
        this.bridge.begin();
    };

    DrawioJavaCanvas.prototype.moveTo = function (x, y) {
        this.bridge.moveTo(x, y);
    };

    DrawioJavaCanvas.prototype.lineTo = function (x, y) {
        this.bridge.lineTo(x, y);
    };

    DrawioJavaCanvas.prototype.quadTo = function (cx, cy, x, y) {
        this.bridge.quadTo(cx, cy, x, y);
    };

    DrawioJavaCanvas.prototype.curveTo = function (cx1, cy1, cx2, cy2, x, y) {
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

    DrawioJavaCanvas.prototype.arcTo = function () {
        throw new Error('Draw.io arcTo requires a dedicated adapter (#10)');
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

    DrawioJavaCanvas.prototype.text = function (x, y, width, height, text) {
        this.bridge.text(x, y, width, height, String(text));
    };

    DrawioJavaCanvas.prototype.image = function (x, y, width, height, resourceId) {
        this.bridge.image(x, y, width, height, String(resourceId));
    };

    scope.DrawioJavaCanvas = DrawioJavaCanvas;
})(this);
