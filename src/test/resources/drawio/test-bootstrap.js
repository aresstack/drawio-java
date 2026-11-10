// Expose only the minimum mxGraph globals necessary for this registration smoke test.
// Replace these test doubles with actual mxGraph core sources in the JS adapter milestone.
var mxConstants = {
    DEFAULT_FONTSIZE: 12,
    DEFAULT_FONTFAMILY: 'Arial',
    SHADOWCOLOR: '#808080',
    SHADOW_OPACITY: 0.5,
    SHADOW_OFFSET_X: 2,
    SHADOW_OFFSET_Y: 2,
    NONE: 'none'
};
function mxUrlConverter() {}
function mxPoint(x, y) { this.x = x; this.y = y; }
function mxRectangle(x, y, width, height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
}
function mxShape() {}
mxShape.prototype.customProperties = [];
function mxActor() {}
function mxSwimlane() {}
function mxHexagon() {}
var mxUtils = {
    extend: function (child, parent) {
        child.prototype = Object.create(parent.prototype);
        child.prototype.constructor = child;
    },
    getValue: function (object, key, defaultValue) {
        return object != null && Object.prototype.hasOwnProperty.call(object, key)
            ? object[key]
            : defaultValue;
    },
    clone: function (object) {
        return Object.assign({}, object);
    }
};
var Graph = {handleFactory: {}};
function mxCellRenderer() {}
mxCellRenderer.shapes = {};
mxCellRenderer.registerShape = function (name, shape) {
    mxCellRenderer.shapes[name] = shape;
};
mxCellRenderer.prototype.getShape = function (name) {
    return mxCellRenderer.shapes[name] || mxShape;
};
