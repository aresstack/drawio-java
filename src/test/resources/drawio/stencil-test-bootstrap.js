/*
 * TEST SCAFFOLDING ONLY - never packaged in the production JAR.
 *
 * Extends the test-only stubs of test-bootstrap.js with exactly the members
 * that the original mxStencil.js (test-upstream/) needs to parse and paint
 * stencils. In production these come from the pinned core bundle
 * (drawio-headless-env.js + drawio-graph.js, issue #9), which is loaded
 * before drawio-stencil-runtime.js.
 *
 * Function bodies marked "verbatim" are copied from jgraph/drawio
 * c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3 (Apache-2.0).
 */

// Values copied from mxgraph/src/util/mxConstants.js.
mxConstants.NODETYPE_ELEMENT = 1;
mxConstants.RECTANGLE_ROUNDING_FACTOR = 0.15;
mxConstants.STYLE_POINTER_EVENTS = 'pointerEvents';
mxConstants.STYLE_STROKEWIDTH = 'strokeWidth';
mxConstants.STYLE_FLIPH = 'flipH';
mxConstants.STYLE_FLIPV = 'flipV';
mxConstants.STYLE_DIRECTION = 'direction';
mxConstants.STYLE_FONTCOLOR = 'fontColor';
mxConstants.STYLE_BACKGROUND_OUTLINE = 'backgroundOutline';
mxConstants.DIRECTION_NORTH = 'north';
mxConstants.DIRECTION_SOUTH = 'south';

// Verbatim: mxUtils.getChildNodes (mxUtils.js line 782).
mxUtils.getChildNodes = function(node, nodeType)
{
	nodeType = nodeType || mxConstants.NODETYPE_ELEMENT;

	var children = [];
	var tmp = node.firstChild;

	while (tmp != null)
	{
		if (tmp.nodeType == nodeType)
		{
			children.push(tmp);
		}

		tmp = tmp.nextSibling;
	}

	return children;
};

// Verbatim: mxUtils.getTextContent (mxUtils.js line 1512).
mxUtils.getTextContent = function(node)
{
	return (node != null) ? node[(node.textContent === undefined) ? 'text' : 'textContent'] : '';
};

// Verbatim: mxUtils.getValue (mxUtils.js line 2118).
mxUtils.getValue = function(array, key, defaultValue)
{
	var value = (array != null) ? array[key] : null;

	if (value == null)
	{
		value = defaultValue;
	}

	return value;
};

// Verbatim: mxUtils.getNumber (mxUtils.js line 2169).
mxUtils.getNumber = function(array, key, defaultValue)
{
	var value = (array != null) ? array[key] : null;

	if (value == null)
	{
		value = defaultValue || 0;
	}

	return Number(value);
};

// Test stub, NOT upstream: the real isValidColor needs named-color tables.
// It accepts hex colors and 'none' only, which is enough for these tests.
mxUtils.isValidColor = function(color)
{
	return color != null && (color == 'none' || /^#[0-9a-fA-F]{3,8}$/.test(String(color)));
};

// Verbatim: mxUtils.mod (line 4030) and mxUtils.indexOf (line 545).
mxUtils.mod = function(n, m)
{
	return ((n % m) + m) % m;
};

mxUtils.indexOf = function(array, obj)
{
	if (array != null && obj != null)
	{
		for (var i = 0; i < array.length; i++)
		{
			if (array[i] == obj)
			{
				return i;
			}
		}
	}

	return -1;
};

// Verbatim logic with shortened comments: mxShape.prototype.addPoints
// (mxShape.js line 1483), used by
// mxStencil for paths with rounded="1".
mxShape.prototype.addPoints = function(c, pts, rounded, arcSize, close, exclude, initialMove)
{
	if (pts != null && pts.length > 0)
	{
		initialMove = (initialMove != null) ? initialMove : true;
		var pe = pts[pts.length - 1];

		// Adds virtual waypoint in the center between start and end point
		if (close && rounded)
		{
			pts = pts.slice();
			var p0 = pts[0];
			var wp = new mxPoint(pe.x + (p0.x - pe.x) / 2, pe.y + (p0.y - pe.y) / 2);
			pts.splice(0, 0, wp);
		}

		var pt = pts[0];
		var i = 1;

		// Draws the line segments
		if (initialMove)
		{
			c.moveTo(pt.x, pt.y);
		}
		else
		{
			c.lineTo(pt.x, pt.y);
		}

		while (i < ((close) ? pts.length : pts.length - 1))
		{
			var tmp = pts[mxUtils.mod(i, pts.length)];
			var dx = pt.x - tmp.x;
			var dy = pt.y - tmp.y;

			if (rounded && (dx != 0 || dy != 0) && (exclude == null || mxUtils.indexOf(exclude, i - 1) < 0))
			{
				var dist = Math.sqrt(dx * dx + dy * dy);
				var nx1 = dx * Math.min(arcSize, dist / 2) / dist;
				var ny1 = dy * Math.min(arcSize, dist / 2) / dist;

				var x1 = tmp.x + nx1;
				var y1 = tmp.y + ny1;
				c.lineTo(x1, y1);

				var next = pts[mxUtils.mod(i + 1, pts.length)];

				// Uses next non-overlapping point
				while (i < pts.length - 2 && Math.round(next.x - tmp.x) == 0 && Math.round(next.y - tmp.y) == 0)
				{
					next = pts[mxUtils.mod(i + 2, pts.length)];
					i++;
				}

				dx = next.x - tmp.x;
				dy = next.y - tmp.y;

				dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
				var nx2 = dx * Math.min(arcSize, dist / 2) / dist;
				var ny2 = dy * Math.min(arcSize, dist / 2) / dist;

				var x2 = tmp.x + nx2;
				var y2 = tmp.y + ny2;

				c.quadTo(tmp.x, tmp.y, x2, y2);
				tmp = new mxPoint(x2, y2);
			}
			else
			{
				c.lineTo(tmp.x, tmp.y);
			}

			pt = tmp;
			i++;
		}

		if (close)
		{
			c.close();
		}
		else
		{
			c.lineTo(pe.x, pe.y);
		}
	}
};

// Mirrors mxCellRenderer.defaultShapes of the real renderer (empty here).
mxCellRenderer.defaultShapes = Object.create(null);
