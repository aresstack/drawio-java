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
// Verbatim copy of the original mxUtils.arcToCurves (Apache-2.0, pinned
// upstream commit c72c9a98, mxgraph/src/util/mxUtils.js) so the canvas
// adapter's arcTo can be tested without the full mxUtils bundle.
mxUtils.arcToCurves = function(x0, y0, r1, r2, angle, largeArcFlag, sweepFlag, x, y)
	{
		x -= x0;
		y -= y0;

		if (x == 0 && y == 0)
		{
			return [];
		}
		else if (r1 == 0 || r2 == 0)
		{
			return [x0, y0, x + x0, y + y0, x + x0, y + y0];
		}
        
        var fS = sweepFlag;
        var psai = angle;
        r1 = Math.abs(r1);
        r2 = Math.abs(r2);
        var ctx = -x / 2;
        var cty = -y / 2;
        var cpsi = Math.cos(psai * Math.PI / 180);
        var spsi = Math.sin(psai * Math.PI / 180);
        var rxd = cpsi * ctx + spsi * cty;
        var ryd = -1 * spsi * ctx + cpsi * cty;
        var rxdd = rxd * rxd;
        var rydd = ryd * ryd;
        var r1x = r1 * r1;
        var r2y = r2 * r2;
        var lamda = rxdd / r1x + rydd / r2y;
        var sds;
        
        if (lamda > 1) 
        {
        	r1 = Math.sqrt(lamda) * r1;
        	r2 = Math.sqrt(lamda) * r2;
        	sds = 0;
        }  
        else
        {
        	var seif = 1;
            
        	if (largeArcFlag === fS) 
        	{
        		seif = -1;
        	}
            
        	// Clamps radicand to zero for radii that only just span the chord
        	// (eg. semicircles), where rounding errors can make it negative and
        	// the NaN would otherwise silently drop the arc (SVG spec F.6.6.2)
        	sds = seif * Math.sqrt(Math.max(0, (r1x * r2y - r1x * rydd - r2y * rxdd) / (r1x * rydd + r2y * rxdd)));
        }
        
        var txd = sds * r1 * ryd / r2;
        var tyd = -1 * sds * r2 * rxd / r1;
        var tx = cpsi * txd - spsi * tyd + x / 2;
        var ty = spsi * txd + cpsi * tyd + y / 2;
        var rad = Math.atan2((ryd - tyd) / r2, (rxd - txd) / r1) - Math.atan2(0, 1);
        var s1 = (rad >= 0) ? rad : 2 * Math.PI + rad;
        rad = Math.atan2((-ryd - tyd) / r2, (-rxd - txd) / r1) - Math.atan2((ryd - tyd) / r2, (rxd - txd) / r1);
        var dr = (rad >= 0) ? rad : 2 * Math.PI + rad;
        
        if (fS == 0 && dr > 0) 
        {
        	dr -= 2 * Math.PI;
        }
        else if (fS != 0 && dr < 0) 
        {
        	dr += 2 * Math.PI;
        }
        
        var sse = dr * 2 / Math.PI;
        var seg = Math.ceil(sse < 0 ? -1 * sse : sse);
        var segr = dr / seg;
        var t = 8/3 * Math.sin(segr / 4) * Math.sin(segr / 4) / Math.sin(segr / 2);
        var cpsir1 = cpsi * r1;
        var cpsir2 = cpsi * r2;
        var spsir1 = spsi * r1;
        var spsir2 = spsi * r2;
        var mc = Math.cos(s1);
        var ms = Math.sin(s1);
        var x2 = -t * (cpsir1 * ms + spsir2 * mc);
        var y2 = -t * (spsir1 * ms - cpsir2 * mc);
        var x3 = 0;
        var y3 = 0;

		var result = [];
        
        for (var n = 0; n < seg; ++n) 
        {
            s1 += segr;
            mc = Math.cos(s1);
            ms = Math.sin(s1);
            
            x3 = cpsir1 * mc - spsir2 * ms + tx;
            y3 = spsir1 * mc + cpsir2 * ms + ty;
            var dx = -t * (cpsir1 * ms + spsir2 * mc);
            var dy = -t * (spsir1 * ms - cpsir2 * mc);
            
            // CurveTo updates x0, y0 so need to restore it
            var index = n * 6;
            result[index] = Number(x2 + x0);
            result[index + 1] = Number(y2 + y0);
            result[index + 2] = Number(x3 - dx + x0);
            result[index + 3] = Number(y3 - dy + y0);
            result[index + 4] = Number(x3 + x0);
            result[index + 5] = Number(y3 + y0);
            
			x2 = x3 + dx;
            y2 = y3 + dy;
        }
        
        return result;
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
