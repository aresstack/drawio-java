/*
 * Bundle original draw.io scripts from pinned upstream sources.
 * Upstream commit: c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3
 * Apache-2.0; see upstream/LICENSE and THIRD-PARTY-NOTICES.md.
 */
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxPoint
 *
 * Implements a 2-dimensional vector with double precision coordinates.
 * 
 * Constructor: mxPoint
 *
 * Constructs a new point for the optional x and y coordinates. If no
 * coordinates are given, then the default values for <x> and <y> are used.
 */
function mxPoint(x, y)
{
	this.x = (x != null) ? x : 0;
	this.y = (y != null) ? y : 0;
};

/**
 * Variable: x
 *
 * Holds the x-coordinate of the point. Default is 0.
 */
mxPoint.prototype.x = null;

/**
 * Variable: y
 *
 * Holds the y-coordinate of the point. Default is 0.
 */
mxPoint.prototype.y = null;

/**
 * Function: equals
 * 
 * Returns true if the given object equals this point.
 */
mxPoint.prototype.equals = function(obj)
{
	return obj != null && obj.x == this.x && obj.y == this.y;
};

/**
 * Function: clone
 *
 * Returns a clone of this <mxPoint>.
 */
mxPoint.prototype.clone = function()
{
	// Handles subclasses as well
	return mxUtils.clone(this);
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxRectangle
 *
 * Extends <mxPoint> to implement a 2-dimensional rectangle with double
 * precision coordinates.
 * 
 * Constructor: mxRectangle
 *
 * Constructs a new rectangle for the optional parameters. If no parameters
 * are given then the respective default values are used.
 */
function mxRectangle(x, y, width, height)
{
	mxPoint.call(this, x, y);

	this.width = (width != null) ? width : 0;
	this.height = (height != null) ? height : 0;
};

/**
 * Extends mxPoint.
 */
mxRectangle.prototype = new mxPoint();
mxRectangle.prototype.constructor = mxRectangle;

/**
 * Variable: width
 *
 * Holds the width of the rectangle. Default is 0.
 */
mxRectangle.prototype.width = null;

/**
 * Variable: height
 *
 * Holds the height of the rectangle. Default is 0.
 */
mxRectangle.prototype.height = null;

/**
 * Function: setRect
 * 
 * Sets this rectangle to the specified values
 */
mxRectangle.prototype.setRect = function(x, y, w, h)
{
    this.x = x;
    this.y = y;
    this.width = w;
    this.height = h;
};

/**
 * Function: getCenterX
 * 
 * Returns the x-coordinate of the center point.
 */
mxRectangle.prototype.getCenterX = function ()
{
	return this.x + this.width/2;
};

/**
 * Function: getCenterY
 * 
 * Returns the y-coordinate of the center point.
 */
mxRectangle.prototype.getCenterY = function ()
{
	return this.y + this.height/2;
};

/**
 * Function: add
 *
 * Adds the given rectangle to this rectangle.
 */
mxRectangle.prototype.add = function(rect)
{
	if (rect != null)
	{
		var minX = Math.min(this.x, rect.x);
		var minY = Math.min(this.y, rect.y);
		var maxX = Math.max(this.x + this.width, rect.x + rect.width);
		var maxY = Math.max(this.y + this.height, rect.y + rect.height);
		
		this.x = minX;
		this.y = minY;
		this.width = maxX - minX;
		this.height = maxY - minY;
	}
};

/**
 * Function: intersect
 * 
 * Changes this rectangle to where it overlaps with the given rectangle.
 */
mxRectangle.prototype.intersect = function(rect)
{
	if (rect != null)
	{
		var r1 = this.x + this.width;
		var r2 = rect.x + rect.width;
		
		var b1 = this.y + this.height;
		var b2 = rect.y + rect.height;
		
		this.x = Math.max(this.x, rect.x);
		this.y = Math.max(this.y, rect.y);
		this.width = Math.min(r1, r2) - this.x;
		this.height = Math.min(b1, b2) - this.y;
	}
};

/**
 * Function: intersectsPoint
 * 
 * Returns true if the given point is inside this rectangle.
 */
mxRectangle.prototype.intersectsPoint = function(x, y)
{
	return x >= this.x && x <= this.x + this.width &&
	       y >= this.y && y <= this.y + this.height;
};

/**
 * Function: grow
 *
 * Grows the rectangle by the given amount, that is, this method subtracts
 * the given amount from the x- and y-coordinates and adds twice the amount
 * to the width and height.
 */
mxRectangle.prototype.grow = function(amount)
{
	this.x -= amount;
	this.y -= amount;
	this.width += 2 * amount;
	this.height += 2 * amount;
	
	return this;
};

/**
 * Function: getPoint
 * 
 * Returns the top, left corner as a new <mxPoint>.
 */
mxRectangle.prototype.getPoint = function()
{
	return new mxPoint(this.x, this.y);
};

/**
 * Function: rotate90
 * 
 * Rotates this rectangle by 90 degree around its center point.
 */
mxRectangle.prototype.rotate90 = function()
{
	var t = (this.width - this.height) / 2;
	this.x += t;
	this.y -= t;
	var tmp = this.width;
	this.width = this.height;
	this.height = tmp;
};

/**
 * Function: equals
 * 
 * Returns true if the given object equals this rectangle.
 */
mxRectangle.prototype.equals = function(obj)
{
	return obj != null && obj.x == this.x && obj.y == this.y &&
		obj.width == this.width && obj.height == this.height;
};

/**
 * Function: fromPoint
 * 
 * Returns a new <mxRectangle> from the given <mxPoint>.
 */
mxRectangle.fromPoint = function(pt)
{
	return new mxRectangle(pt.x, pt.y, 0, 0);
};

/**
 * Function: fromRectangle
 * 
 * Returns a new <mxRectangle> which is a copy of the given rectangle.
 */
mxRectangle.fromRectangle = function(rect)
{
	return new mxRectangle(rect.x, rect.y, rect.width, rect.height);
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxGeometry
 * 
 * Extends <mxRectangle> to represent the geometry of a cell.
 * 
 * For vertices, the geometry consists of the x- and y-location, and the width
 * and height. For edges, the geometry consists of the optional terminal- and
 * control points. The terminal points are only required if an edge is
 * unconnected, and are stored in the <sourcePoint> and <targetPoint>
 * variables, respectively.
 * 
 * Example:
 * 
 * If an edge is unconnected, that is, it has no source or target terminal,
 * then a geometry with terminal points for a new edge can be defined as
 * follows.
 * 
 * (code)
 * geometry.setTerminalPoint(new mxPoint(x1, y1), true);
 * geometry.points = [new mxPoint(x2, y2)];
 * geometry.setTerminalPoint(new mxPoint(x3, y3), false);
 * (end)
 * 
 * Control points are used regardless of the connected state of an edge and may
 * be ignored or interpreted differently depending on the edge's <mxEdgeStyle>.
 * 
 * To disable automatic reset of control points after a cell has been moved or
 * resized, the the <mxGraph.resizeEdgesOnMove> and
 * <mxGraph.resetEdgesOnResize> may be used.
 *
 * Edge Labels:
 * 
 * Using the x- and y-coordinates of a cell's geometry, it is possible to
 * position the label on edges on a specific location on the actual edge shape
 * as it appears on the screen. The x-coordinate of an edge's geometry is used
 * to describe the distance from the center of the edge from -1 to 1 with 0
 * being the center of the edge and the default value. The y-coordinate of an
 * edge's geometry is used to describe the absolute, orthogonal distance in
 * pixels from that point. In addition, the <mxGeometry.offset> is used as an
 * absolute offset vector from the resulting point.
 * 
 * This coordinate system is applied if <relative> is true, otherwise the
 * offset defines the absolute vector from the edge's center point to the
 * label and the values for <x> and <y> are ignored.
 * 
 * The width and height parameter for edge geometries can be used to set the
 * label width and height (eg. for word wrapping).
 * 
 * Ports:
 * 
 * The term "port" refers to a relatively positioned, connectable child cell,
 * which is used to specify the connection between the parent and another cell
 * in the graph. Ports are typically modeled as vertices with relative
 * geometries.
 * 
 * Offsets:
 * 
 * The <offset> field is interpreted in 3 different ways, depending on the cell
 * and the geometry. For edges, the offset defines the absolute offset for the
 * edge label. For relative geometries, the offset defines the absolute offset
 * for the origin (top, left corner) of the vertex, otherwise the offset
 * defines the absolute offset for the label inside the vertex or group.
 * 
 * Constructor: mxGeometry
 *
 * Constructs a new object to describe the size and location of a vertex or
 * the control points of an edge.
 */
function mxGeometry(x, y, width, height)
{
	mxRectangle.call(this, x, y, width, height);
};

/**
 * Extends mxRectangle.
 */
mxGeometry.prototype = new mxRectangle();
mxGeometry.prototype.constructor = mxGeometry;

/**
 * Variable: TRANSLATE_CONTROL_POINTS
 * 
 * Global switch to translate the points in translate. Default is true.
 */
mxGeometry.prototype.TRANSLATE_CONTROL_POINTS = true;

/**
 * Variable: alternateBounds
 *
 * Stores alternate values for x, y, width and height in a rectangle. See
 * <swap> to exchange the values. Default is null.
 */
mxGeometry.prototype.alternateBounds = null;

/**
 * Variable: sourcePoint
 *
 * Defines the source <mxPoint> of the edge. This is used if the
 * corresponding edge does not have a source vertex. Otherwise it is
 * ignored. Default is  null.
 */
mxGeometry.prototype.sourcePoint = null;

/**
 * Variable: targetPoint
 *
 * Defines the target <mxPoint> of the edge. This is used if the
 * corresponding edge does not have a target vertex. Otherwise it is
 * ignored. Default is null.
 */
mxGeometry.prototype.targetPoint = null;

/**
 * Variable: points
 *
 * Array of <mxPoints> which specifies the control points along the edge.
 * These points are the intermediate points on the edge, for the endpoints
 * use <targetPoint> and <sourcePoint> or set the terminals of the edge to
 * a non-null value. Default is null.
 */
mxGeometry.prototype.points = null;

/**
 * Variable: offset
 *
 * For edges, this holds the offset (in pixels) from the position defined
 * by <x> and <y> on the edge. For relative geometries (for vertices), this
 * defines the absolute offset from the point defined by the relative
 * coordinates. For absolute geometries (for vertices), this defines the
 * offset for the label. Default is null.
 */
mxGeometry.prototype.offset = null;

/**
 * Variable: relative
 *
 * Specifies if the coordinates in the geometry are to be interpreted as
 * relative coordinates. For edges, this is used to define the location of
 * the edge label relative to the edge as rendered on the display. For
 * vertices, this specifies the relative location inside the bounds of the
 * parent cell.
 * 
 * If this is false, then the coordinates are relative to the origin of the
 * parent cell or, for edges, the edge label position is relative to the
 * center of the edge as rendered on screen.
 * 
 * Default is false.
 */
mxGeometry.prototype.relative = false;

/**
 * Function: swap
 * 
 * Swaps the x, y, width and height with the values stored in
 * <alternateBounds> and puts the previous values into <alternateBounds> as
 * a rectangle. This operation is carried-out in-place, that is, using the
 * existing geometry instance. If this operation is called during a graph
 * model transactional change, then the geometry should be cloned before
 * calling this method and setting the geometry of the cell using
 * <mxGraphModel.setGeometry>.
 */
mxGeometry.prototype.swap = function()
{
	if (this.alternateBounds != null)
	{
		var old = new mxRectangle(
			this.x, this.y, this.width, this.height);

		this.x = this.alternateBounds.x;
		this.y = this.alternateBounds.y;
		this.width = this.alternateBounds.width;
		this.height = this.alternateBounds.height;

		this.alternateBounds = old;
	}
};

/**
 * Function: getTerminalPoint
 * 
 * Returns the <mxPoint> representing the source or target point of this
 * edge. This is only used if the edge has no source or target vertex.
 * 
 * Parameters:
 * 
 * isSource - Boolean that specifies if the source or target point
 * should be returned.
 */
mxGeometry.prototype.getTerminalPoint = function(isSource)
{
	return (isSource) ? this.sourcePoint : this.targetPoint;
};

/**
 * Function: setTerminalPoint
 * 
 * Sets the <sourcePoint> or <targetPoint> to the given <mxPoint> and
 * returns the new point.
 * 
 * Parameters:
 * 
 * point - Point to be used as the new source or target point.
 * isSource - Boolean that specifies if the source or target point
 * should be set.
 */
mxGeometry.prototype.setTerminalPoint = function(point, isSource)
{
	if (isSource)
	{
		this.sourcePoint = point;
	}
	else
	{
		this.targetPoint = point;
	}
	
	return point;
};

/**
 * Function: rotate
 * 
 * Rotates the geometry by the given angle around the given center. That is,
 * <x> and <y> of the geometry, the <sourcePoint>, <targetPoint> and all
 * <points> are translated by the given amount. <x> and <y> are only
 * translated if <relative> is false.
 * 
 * Parameters:
 * 
 * angle - Number that specifies the rotation angle in degrees.
 * cx - <mxPoint> that specifies the center of the rotation.
 */
mxGeometry.prototype.rotate = function(angle, cx)
{
	var rad = mxUtils.toRadians(angle);
	var cos = Math.cos(rad);
	var sin = Math.sin(rad);
	
	// Rotates the geometry
	if (!this.relative)
	{
		var ct = new mxPoint(this.getCenterX(), this.getCenterY());
		var pt = mxUtils.getRotatedPoint(ct, cos, sin, cx);
		
		this.x = Math.round(pt.x - this.width / 2);
		this.y = Math.round(pt.y - this.height / 2);
	}

	// Rotates the source point
	if (this.sourcePoint != null)
	{
		var pt = mxUtils.getRotatedPoint(this.sourcePoint, cos, sin, cx);
		this.sourcePoint.x = Math.round(pt.x);
		this.sourcePoint.y = Math.round(pt.y);
	}
	
	// Translates the target point
	if (this.targetPoint != null)
	{
		var pt = mxUtils.getRotatedPoint(this.targetPoint, cos, sin, cx);
		this.targetPoint.x = Math.round(pt.x);
		this.targetPoint.y = Math.round(pt.y);	
	}
	
	// Translate the control points
	if (this.points != null)
	{
		for (var i = 0; i < this.points.length; i++)
		{
			if (this.points[i] != null)
			{
				var pt = mxUtils.getRotatedPoint(this.points[i], cos, sin, cx);
				this.points[i].x = Math.round(pt.x);
				this.points[i].y = Math.round(pt.y);
			}
		}
	}
};

/**
 * Function: translate
 * 
 * Translates the geometry by the specified amount. That is, <x> and <y> of the
 * geometry, the <sourcePoint>, <targetPoint> and all <points> are translated
 * by the given amount. <x> and <y> are only translated if <relative> is false.
 * If <TRANSLATE_CONTROL_POINTS> is false, then <points> are not modified by
 * this function.
 * 
 * Parameters:
 * 
 * dx - Number that specifies the x-coordinate of the translation.
 * dy - Number that specifies the y-coordinate of the translation.
 * ignorePosition - Specifies if the position should be ignored.
 */
mxGeometry.prototype.translate = function(dx, dy, ignorePosition)
{
	dx = parseFloat(dx);
	dy = parseFloat(dy);
	
	// Translates the geometry
	if (!this.relative && !ignorePosition)
	{
		this.x = parseFloat(this.x) + dx;
		this.y = parseFloat(this.y) + dy;
	}

	// Translates the source point
	if (this.sourcePoint != null)
	{
		this.sourcePoint.x = parseFloat(this.sourcePoint.x) + dx;
		this.sourcePoint.y = parseFloat(this.sourcePoint.y) + dy;
	}
	
	// Translates the target point
	if (this.targetPoint != null)
	{
		this.targetPoint.x = parseFloat(this.targetPoint.x) + dx;
		this.targetPoint.y = parseFloat(this.targetPoint.y) + dy;		
	}

	// Translate the control points
	if (this.TRANSLATE_CONTROL_POINTS && this.points != null)
	{
		for (var i = 0; i < this.points.length; i++)
		{
			if (this.points[i] != null)
			{
				this.points[i].x = parseFloat(this.points[i].x) + dx;
				this.points[i].y = parseFloat(this.points[i].y) + dy;
			}
		}
	}
};

/**
 * Function: scale
 * 
 * Scales the geometry by the given amount. That is, <x> and <y> of the
 * geometry, the <sourcePoint>, <targetPoint> and all <points> are scaled
 * by the given amount. <x>, <y>, <width> and <height> are only scaled if
 * <relative> is false. If <fixedAspect> is true, then the smaller value
 * is used to scale the width and the height.
 * 
 * Parameters:
 * 
 * sx - Number that specifies the horizontal scale factor.
 * sy - Number that specifies the vertical scale factor.
 * fixedAspect - Optional boolean to keep the aspect ratio fixed.
 */
mxGeometry.prototype.scale = function(sx, sy, fixedAspect)
{
	sx = parseFloat(sx);
	sy = parseFloat(sy);

	// Translates the source point
	if (this.sourcePoint != null)
	{
		this.sourcePoint.x = parseFloat(this.sourcePoint.x) * sx;
		this.sourcePoint.y = parseFloat(this.sourcePoint.y) * sy;
	}
	
	// Translates the target point
	if (this.targetPoint != null)
	{
		this.targetPoint.x = parseFloat(this.targetPoint.x) * sx;
		this.targetPoint.y = parseFloat(this.targetPoint.y) * sy;		
	}

	// Translate the control points
	if (this.points != null)
	{
		for (var i = 0; i < this.points.length; i++)
		{
			if (this.points[i] != null)
			{
				this.points[i].x = parseFloat(this.points[i].x) * sx;
				this.points[i].y = parseFloat(this.points[i].y) * sy;
			}
		}
	}
	
	// Translates the geometry
	if (!this.relative)
	{
		this.x = parseFloat(this.x) * sx;
		this.y = parseFloat(this.y) * sy;

		if (fixedAspect)
		{
			sy = sx = Math.min(sx, sy);
		}
		
		this.width = parseFloat(this.width) * sx;
		this.height = parseFloat(this.height) * sy;
	}
};

/**
 * Function: equals
 * 
 * Returns true if the given object equals this geometry.
 */
mxGeometry.prototype.equals = function(obj)
{
	return mxRectangle.prototype.equals.apply(this, arguments) &&
		this.relative == obj.relative &&
		((this.sourcePoint == null && obj.sourcePoint == null) || (this.sourcePoint != null && this.sourcePoint.equals(obj.sourcePoint))) &&
		((this.targetPoint == null && obj.targetPoint == null) || (this.targetPoint != null && this.targetPoint.equals(obj.targetPoint))) &&
		((this.points == null && obj.points == null) || (this.points != null && mxUtils.equalPoints(this.points, obj.points))) &&
		((this.alternateBounds == null && obj.alternateBounds == null) || (this.alternateBounds != null && this.alternateBounds.equals(obj.alternateBounds))) &&
		((this.offset == null && obj.offset == null) || (this.offset != null && obj.offset != null &&
			(typeof this.offset.equals === 'function' ? this.offset.equals(obj.offset) :
			this.offset.x == obj.offset.x && this.offset.y == obj.offset.y)));
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxEventObject
 * 
 * The mxEventObject is a wrapper for all properties of a single event.
 * Additionally, it also offers functions to consume the event and check if it
 * was consumed as follows:
 * 
 * (code)
 * evt.consume();
 * INV: evt.isConsumed() == true
 * (end)
 * 
 * Constructor: mxEventObject
 *
 * Constructs a new event object with the specified name. An optional
 * sequence of key, value pairs can be appended to define properties.
 * 
 * Example:
 *
 * (code)
 * new mxEventObject("eventName", key1, val1, .., keyN, valN)
 * (end)
 */
function mxEventObject(name)
{
	this.name = name;
	this.properties = [];
	
	for (var i = 1; i < arguments.length; i += 2)
	{
		if (arguments[i + 1] != null)
		{
			this.properties[arguments[i]] = arguments[i + 1];
		}
	}
};

/**
 * Variable: name
 *
 * Holds the name.
 */
mxEventObject.prototype.name = null;

/**
 * Variable: properties
 *
 * Holds the properties as an associative array.
 */
mxEventObject.prototype.properties = null;

/**
 * Variable: consumed
 *
 * Holds the consumed state. Default is false.
 */
mxEventObject.prototype.consumed = false;

/**
 * Function: getName
 * 
 * Returns <name>.
 */
mxEventObject.prototype.getName = function()
{
	return this.name;
};

/**
 * Function: getProperties
 * 
 * Returns <properties>.
 */
mxEventObject.prototype.getProperties = function()
{
	return this.properties;
};

/**
 * Function: getProperty
 * 
 * Returns the property for the given key.
 */
mxEventObject.prototype.getProperty = function(key)
{
	return this.properties[key];
};

/**
 * Function: isConsumed
 *
 * Returns true if the event has been consumed.
 */
mxEventObject.prototype.isConsumed = function()
{
	return this.consumed;
};

/**
 * Function: consume
 *
 * Consumes the event.
 */
mxEventObject.prototype.consume = function()
{
	this.consumed = true;
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxEventSource
 *
 * Base class for objects that dispatch named events. To create a subclass that
 * inherits from mxEventSource, the following code is used.
 *
 * (code)
 * function MyClass() { };
 *
 * MyClass.prototype = new mxEventSource();
 * MyClass.prototype.constructor = MyClass;
 * (end)
 *
 * Known Subclasses:
 *
 * <mxGraphModel>, <mxGraph>, <mxGraphView>, <mxEditor>, <mxCellOverlay>,
 * <mxToolbar>, <mxWindow>
 * 
 * Constructor: mxEventSource
 *
 * Constructs a new event source.
 */
function mxEventSource(eventSource)
{
	this.setEventSource(eventSource);
};

/**
 * Variable: eventListeners
 *
 * Holds the event names and associated listeners in an array. The array
 * contains the event name followed by the respective listener for each
 * registered listener.
 */
mxEventSource.prototype.eventListeners = null;

/**
 * Variable: removedListeners
 *
 * <mxDictionary> of the listeners that <removeListener> collected to remove
 * them in one pass, or null. See <beginRemoveListeners>.
 */
mxEventSource.prototype.removedListeners = null;

/**
 * Variable: removedListenerCount
 *
 * Number of calls of <removeListener> collected in <removedListeners>.
 */
mxEventSource.prototype.removedListenerCount = 0;

/**
 * Variable: eventsEnabled
 *
 * Specifies if events can be fired. Default is true.
 */
mxEventSource.prototype.eventsEnabled = true;

/**
 * Variable: eventSource
 *
 * Optional source for events. Default is null.
 */
mxEventSource.prototype.eventSource = null;

/**
 * Function: isEventsEnabled
 * 
 * Returns <eventsEnabled>.
 */
mxEventSource.prototype.isEventsEnabled = function()
{
	return this.eventsEnabled;
};

/**
 * Function: setEventsEnabled
 * 
 * Sets <eventsEnabled>.
 */
mxEventSource.prototype.setEventsEnabled = function(value)
{
	this.eventsEnabled = value;
};

/**
 * Function: getEventSource
 * 
 * Returns <eventSource>.
 */
mxEventSource.prototype.getEventSource = function()
{
	return this.eventSource;
};

/**
 * Function: setEventSource
 * 
 * Sets <eventSource>.
 */
mxEventSource.prototype.setEventSource = function(value)
{
	this.eventSource = value;
};

/**
 * Function: addListener
 *
 * Binds the specified function to the given event name. If no event name
 * is given, then the listener is registered for all events.
 * 
 * The parameters of the listener are the sender and an <mxEventObject>.
 */
mxEventSource.prototype.addListener = function(name, funct)
{
	// Removes the listener before it is added again
	if (this.removedListeners != null && this.removedListeners.get(funct))
	{
		this.removeCollectedListeners();
	}

	if (this.eventListeners == null)
	{
		this.eventListeners = [];
	}
	
	this.eventListeners.push(name);
	this.eventListeners.push(funct);
};

/**
 * Function: removeListener
 *
 * Removes all occurrences of the given listener from <eventListeners>.
 */
mxEventSource.prototype.removeListener = function(funct)
{
	if (this.removedListeners != null)
	{
		this.removedListeners.put(funct, true);
		this.removedListenerCount++;
	}
	else if (this.eventListeners != null)
	{
		var i = 0;
		
		while (i < this.eventListeners.length)
		{
			if (this.eventListeners[i+1] == funct)
			{
				this.eventListeners.splice(i, 2);
			}
			else
			{
				i += 2;
			}
		}
	}
};

/**
 * Function: beginRemoveListeners
 *
 * Collects the listeners that <removeListener> removes until
 * <endRemoveListeners> removes all occurrences of them in one pass, which
 * takes linear time for many listeners. Collected listeners are removed
 * before <fireEvent> and before they are added again, and listeners removed
 * while an event is fired are removed at once, so the same listeners are
 * called as without collecting. Only <eventListeners> keeps the collected
 * listeners until then.
 */
mxEventSource.prototype.beginRemoveListeners = function()
{
	if (this.removedListeners == null)
	{
		this.removedListeners = new mxDictionary();
		this.removedListenerCount = 0;
	}
};

/**
 * Function: endRemoveListeners
 *
 * Removes the listeners collected since <beginRemoveListeners>.
 */
mxEventSource.prototype.endRemoveListeners = function()
{
	this.removeCollectedListeners();
	this.removedListeners = null;
};

/**
 * Function: removeCollectedListeners
 *
 * Removes all occurrences of the listeners in <removedListeners> from
 * <eventListeners> in one pass.
 */
mxEventSource.prototype.removeCollectedListeners = function()
{
	if (this.removedListenerCount > 0)
	{
		var remove = this.removedListeners;
		this.removedListeners = new mxDictionary();
		this.removedListenerCount = 0;

		if (this.eventListeners != null)
		{
			var j = 0;

			for (var i = 0; i < this.eventListeners.length; i += 2)
			{
				if (!remove.get(this.eventListeners[i + 1]))
				{
					this.eventListeners[j++] = this.eventListeners[i];
					this.eventListeners[j++] = this.eventListeners[i + 1];
				}
			}

			this.eventListeners.length = j;
		}
	}
};

/**
 * Function: fireEvent
 *
 * Dispatches the given event to the listeners which are registered for
 * the event. The sender argument is optional. The current execution scope
 * ("this") is used for the listener invocation (see <mxUtils.bind>).
 *
 * Example:
 *
 * (code)
 * fireEvent(new mxEventObject("eventName", key1, val1, .., keyN, valN))
 * (end)
 * 
 * Parameters:
 *
 * evt - <mxEventObject> that represents the event.
 * sender - Optional sender to be passed to the listener. Default value is
 * the return value of <getEventSource>.
 */
mxEventSource.prototype.fireEvent = function(evt, sender)
{
	// Listeners removed by listeners are removed at once (see beginRemoveListeners)
	this.removeCollectedListeners();
	var removed = this.removedListeners;
	this.removedListeners = null;

	if (this.eventListeners != null && this.isEventsEnabled())
	{
		if (evt == null)
		{
			evt = new mxEventObject();
		}
		
		if (sender == null)
		{
			sender = this.getEventSource();
		}

		if (sender == null)
		{
			sender = this;
		}

		for (var i = 0; i < this.eventListeners.length; i += 2)
		{
			var listen = this.eventListeners[i];
			
			if (listen == null || listen == evt.getName())
			{
				// Workaround for closure compiler bug where args are
				// null in listeners for FF if an array var is used
				this.eventListeners[i+1].apply(this, [sender, evt]);
			}
		}
	}

	this.removedListeners = removed;
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
var mxEvent =
{

	/**
	 * Class: mxEvent
	 * 
	 * Cross-browser DOM event support. For internal event handling,
	 * <mxEventSource> and the graph event dispatch loop in <mxGraph> are used.
	 * 
	 * Memory Leaks:
	 * 
	 * Use this class for adding and removing listeners to/from DOM nodes. The
	 * <removeAllListeners> function is provided to remove all listeners that
	 * have been added using <addListener>. The function should be invoked when
	 * the last reference is removed in the JavaScript code, typically when the
	 * referenced DOM node is removed from the DOM.
	 *
	 * Function: addListener
	 * 
	 * Binds the function to the specified event on the given element. Use
	 * <mxUtils.bind> in order to bind the "this" keyword inside the function
	 * to a given execution scope.
	 */
	addListener: function()
	{
		var updateListenerList = function(element, eventName, funct)
		{
			if (element.mxListenerList == null)
			{
				element.mxListenerList = [];
			}
			
			var entry = {name: eventName, f: funct};
			element.mxListenerList.push(entry);
		};
		
		if (window.addEventListener)
		{
			// Checks if passive event listeners are supported
			// see https://github.com/Modernizr/Modernizr/issues/1894
			var supportsPassive = false;
			
			try
			{
				document.addEventListener('test', function() {}, Object.defineProperty &&
					Object.defineProperty({}, 'passive', {get: function()
					{supportsPassive = true;}}));
			}
			catch (e)
			{
				// ignore
			}
			
			return function(element, eventName, funct)
			{
				element.addEventListener(eventName, funct,
					(supportsPassive) ?
					{passive: false} : false);
				updateListenerList(element, eventName, funct);
			};
		}
		else
		{
			return function(element, eventName, funct)
			{
				element.attachEvent('on' + eventName, funct);
				updateListenerList(element, eventName, funct);				
			};
		}
	}(),

	/**
	 * Function: removeListener
	 *
	 * Removes the specified listener from the given element.
	 */
	removeListener: function()
	{
		var updateListener = function(element, eventName, funct)
		{
			if (element.mxListenerList != null)
			{
				var listenerCount = element.mxListenerList.length;
				
				for (var i = 0; i < listenerCount; i++)
				{
					var entry = element.mxListenerList[i];
					
					if (entry.f == funct)
					{
						element.mxListenerList.splice(i, 1);
						break;
					}
				}
				
				if (element.mxListenerList.length == 0)
				{
					element.mxListenerList = null;
				}
			}
		};
		
		if (window.removeEventListener)
		{
			return function(element, eventName, funct)
			{
				element.removeEventListener(eventName, funct, false);
				updateListener(element, eventName, funct);
			};
		}
		else
		{
			return function(element, eventName, funct)
			{
				element.detachEvent('on' + eventName, funct);
				updateListener(element, eventName, funct);
			};
		}
	}(),

	/**
	 * Function: removeAllListeners
	 * 
	 * Removes all listeners from the given element.
	 */
	removeAllListeners: function(element)
	{
		var list = element.mxListenerList;

		if (list != null)
		{
			while (list.length > 0)
			{
				var entry = list[0];
				mxEvent.removeListener(element, entry.name, entry.f);
			}
		}
	},
	
	/**
	 * Function: addGestureListeners
	 * 
	 * Adds the given listeners for touch, mouse and/or pointer events. If
	 * <mxClient.IS_POINTER> is true then pointer events will be registered,
	 * else the respective mouse events will be registered. If <mxClient.IS_POINTER>
	 * is false and <mxClient.IS_TOUCH> is true then the respective touch events
	 * will be registered as well as the mouse events. The optional cancel
	 * listener is registered for pointercancel or touchcancel, which end a
	 * gesture without an end event if the browser or the system takes over
	 * the pointer (eg. for a system gesture or an alert).
	 */
	addGestureListeners: function(node, startListener, moveListener, endListener, cancelListener)
	{
		if (startListener != null)
		{
			mxEvent.addListener(node, (mxClient.IS_POINTER) ? 'pointerdown' : 'mousedown', startListener);
		}
		
		if (moveListener != null)
		{
			mxEvent.addListener(node, (mxClient.IS_POINTER) ? 'pointermove' : 'mousemove', moveListener);
		}
		
		if (endListener != null)
		{
			mxEvent.addListener(node, (mxClient.IS_POINTER) ? 'pointerup' : 'mouseup', endListener);
		}
		
		if (!mxClient.IS_POINTER && mxClient.IS_TOUCH)
		{
			if (startListener != null)
			{
				mxEvent.addListener(node, 'touchstart', startListener);
			}
			
			if (moveListener != null)
			{
				mxEvent.addListener(node, 'touchmove', moveListener);
			}
			
			if (endListener != null)
			{
				mxEvent.addListener(node, 'touchend', endListener);
			}
		}

		if (cancelListener != null)
		{
			if (mxClient.IS_POINTER)
			{
				mxEvent.addListener(node, 'pointercancel', cancelListener);
			}
			else if (mxClient.IS_TOUCH)
			{
				mxEvent.addListener(node, 'touchcancel', cancelListener);
			}
		}
	},
	
	/**
	 * Function: removeGestureListeners
	 * 
	 * Removes the given listeners from mousedown, mousemove, mouseup and the
	 * respective touch events if <mxClient.IS_TOUCH> is true, and the optional
	 * cancel listener (see <addGestureListeners>).
	 */
	removeGestureListeners: function(node, startListener, moveListener, endListener, cancelListener)
	{
		if (startListener != null)
		{
			mxEvent.removeListener(node, (mxClient.IS_POINTER) ? 'pointerdown' : 'mousedown', startListener);
		}
		
		if (moveListener != null)
		{
			mxEvent.removeListener(node, (mxClient.IS_POINTER) ? 'pointermove' : 'mousemove', moveListener);
		}
		
		if (endListener != null)
		{
			mxEvent.removeListener(node, (mxClient.IS_POINTER) ? 'pointerup' : 'mouseup', endListener);
		}
		
		if (!mxClient.IS_POINTER && mxClient.IS_TOUCH)
		{
			if (startListener != null)
			{
				mxEvent.removeListener(node, 'touchstart', startListener);
			}
			
			if (moveListener != null)
			{
				mxEvent.removeListener(node, 'touchmove', moveListener);
			}
			
			if (endListener != null)
			{
				mxEvent.removeListener(node, 'touchend', endListener);
			}
		}

		if (cancelListener != null)
		{
			if (mxClient.IS_POINTER)
			{
				mxEvent.removeListener(node, 'pointercancel', cancelListener);
			}
			else if (mxClient.IS_TOUCH)
			{
				mxEvent.removeListener(node, 'touchcancel', cancelListener);
			}
		}
	},
	
	/**
	 * Function: redirectMouseEvents
	 *
	 * Redirects the mouse events from the given DOM node to the graph dispatch
	 * loop using the event and given state as event arguments. State can
	 * either be an instance of <mxCellState> or a function that returns an
	 * <mxCellState>. The down, move, up and dblClick arguments are optional
	 * functions that take the trigger event as arguments and replace the
	 * default behaviour.
	 */
	redirectMouseEvents: function(node, graph, state, down, move, up, dblClick)
	{
		var getState = function(evt)
		{
			return (typeof(state) == 'function') ? state(evt) : state;
		};
		
		mxEvent.addGestureListeners(node, function (evt)
		{
			if (down != null)
			{
				down(evt);
			}
			else if (!mxEvent.isConsumed(evt))
			{
				graph.fireMouseEvent(mxEvent.MOUSE_DOWN, new mxMouseEvent(evt, getState(evt)));
			}
		},
		function (evt)
		{
			if (move != null)
			{
				move(evt);
			}
			else if (!mxEvent.isConsumed(evt))
			{
				graph.fireMouseEvent(mxEvent.MOUSE_MOVE, new mxMouseEvent(evt, getState(evt)));
			}
		},
		function (evt)
		{
			if (up != null)
			{
				up(evt);
			}
			else if (!mxEvent.isConsumed(evt))
			{
				graph.fireMouseEvent(mxEvent.MOUSE_UP, new mxMouseEvent(evt, getState(evt)));
			}
		});

		mxEvent.addListener(node, 'dblclick', function (evt)
		{
			if (dblClick != null)
			{
				dblClick(evt);
			}
			else if (!mxEvent.isConsumed(evt))
			{
				var tmp = getState(evt);
				graph.dblClick(evt, (tmp != null) ? tmp.cell : null);
			}
		});
	},

	/**
	 * Function: release
	 * 
	 * Removes the known listeners from the given DOM node and its descendants.
	 * 
	 * Parameters:
	 * 
	 * element - DOM node to remove the listeners from.
	 */
	release: function(element)
	{
		try
		{
			if (element != null)
			{
				mxEvent.removeAllListeners(element);
				
				var children = element.childNodes;
				
				if (children != null)
				{
			        var childCount = children.length;
			        
			        for (var i = 0; i < childCount; i += 1)
			        {
			        	mxEvent.release(children[i]);
			        }
			    }
			}
		}
		catch (e)
		{
			// ignores errors as this is typically called in cleanup code
		}
	},

	/**
	 * Function: addMouseWheelListener
	 * 
	 * Installs the given function as a handler for mouse wheel events. The
	 * function has two arguments: the mouse event and a boolean that specifies
	 * if the wheel was moved up or down.
	 * 
	 * This has been tested with IE 6 and 7, Firefox (all versions), Opera and
	 * Safari. It does currently not work on Safari for Mac.
	 * 
	 * Example:
	 * 
	 * (code)
	 * mxEvent.addMouseWheelListener(function (evt, up, pinch)
	 * {
	 *   mxLog.show();
	 *   mxLog.debug('mouseWheel: up='+up);
	 * });
	 *(end)
	 * 
	 * Parameters:
	 * 
	 * funct - Handler function that takes the event argument, a boolean argument
	 * for the mousewheel direction and a boolean to specify if the underlying
	 * event was a pinch gesture on a touch device. For a pinch gesture with
	 * pointer events, the center of the pinch and the ratio of the distance
	 * between the pointers to the distance at the previous call follow.
	 * target - Target for installing the listener in Google Chrome. See 
	 * https://www.chromestatus.com/features/6662647093133312.
	 */
	addMouseWheelListener: function(funct, target)
	{
		if (funct != null)
		{
			var wheelHandler = function(evt)
			{
				// IE does not give an event object but the
				// global event object is the mousewheel event
				// at this point in time.
				if (evt == null)
				{
					evt = window.event;
				}
			
				//To prevent window zoom on trackpad pinch
				if (evt.ctrlKey) 
				{
					evt.preventDefault();
				}

				// Reads deltaMode before the deltas as Firefox converts the line
				// deltas of mouse wheels to pixels otherwise, which would make
				// them look like the pixel deltas of trackpads
				var min = (evt.deltaMode == 0) ? 0.5 : 0;

				// Handles the event using the given function, including small
				// deltas of pinch gestures on trackpads (ctrl key), which
				// zoom in proportion to the delta
				if (Math.abs(evt.deltaX) > min || Math.abs(evt.deltaY) > min ||
					(evt.ctrlKey && evt.deltaY != null && evt.deltaY != 0))
				{
					funct(evt, (evt.deltaY == 0) ?  -evt.deltaX > 0 : -evt.deltaY > 0);
				}
			};
	
			target = target != null ? target : window;
					
			if (mxClient.IS_SF && !mxClient.IS_TOUCH)
			{
				var scale = 1;
				
				mxEvent.addListener(target, 'gesturestart', function(evt)
				{
					mxEvent.consume(evt);
					scale = 1;
				});
				
				mxEvent.addListener(target, 'gesturechange', function(evt)
				{
					mxEvent.consume(evt);
					var diff = scale - evt.scale;
					
					if (Math.abs(diff) > 0.2)
					{
						funct(evt, diff < 0, true);
						scale = evt.scale;
					}
				});

				mxEvent.addListener(target, 'gestureend', function(evt)
				{
					mxEvent.consume(evt);
				});
			}
			else
			{
				var evtCache = [];
				var dist0 = 0;

				var getDistance = function()
				{
					return Math.sqrt(Math.pow(evtCache[0].clientX - evtCache[1].clientX, 2) +
						Math.pow(evtCache[0].clientY - evtCache[1].clientY, 2));
				};

				// Ends a pinch if a finger is lifted or cancelled, as cancelled
				// pointers would otherwise stay in the cache
				var resetPinch = function()
				{
					evtCache = [];
					dist0 = 0;
				};
				
				// Adds basic listeners for graph event dispatching
				mxEvent.addGestureListeners(target, mxUtils.bind(this, function(evt)
				{
					if (!mxEvent.isMouseEvent(evt) && evt.pointerId != null)
					{
						evtCache.push(evt);

						// Starts at the distance of the fingers when the pinch begins
						if (evtCache.length == 2)
						{
							dist0 = getDistance();
						}
					}
				}),
				mxUtils.bind(this, function(evt)
				{
					if (!mxEvent.isMouseEvent(evt) && evtCache.length == 2)
					{
						// Find this event in the cache and update its record with this event
						for (var i = 0; i < evtCache.length; i++)
						{
							if (evt.pointerId == evtCache[i].pointerId)
							{
								evtCache[i] = evt;
								break;
							}
						}
						
					   	// Calculate the distance between the two pointers
						var dist = getDistance();
					
						if (dist0 > 0 && dist > 0 && Math.abs(dist - dist0) > mxEvent.PINCH_THRESHOLD)
						{
							var cx = evtCache[0].clientX + (evtCache[1].clientX - evtCache[0].clientX) / 2;
							var cy = evtCache[0].clientY + (evtCache[1].clientY - evtCache[0].clientY) / 2;
							
							funct(evtCache[0], dist > dist0, true, cx, cy, dist / dist0);
						
						   	// Cache the distance for the next move event 
							dist0 = dist;
						}
					}
				}),
				resetPinch, resetPinch);
			}
			
			mxEvent.addListener(target, 'wheel', wheelHandler);
		}
	},
	
	/**
	 * Function: disableContextMenu
	 *
	 * Disables the context menu for the given element.
	 */
	disableContextMenu: function(element)
	{
		mxEvent.addListener(element, 'contextmenu', function(evt)
		{
			if (evt.preventDefault)
			{
				evt.preventDefault();
			}
			
			return false;
		});
	},
	
	/**
	 * Function: getSource
	 * 
	 * Returns the event's target or srcElement depending on the browser.
	 */
	getSource: function(evt)
	{
		return (evt.srcElement != null) ? evt.srcElement : evt.target;
	},

	/**
	 * Function: isConsumed
	 * 
	 * Returns true if the event has been consumed using <consume>.
	 */
	isConsumed: function(evt)
	{
		return evt.isConsumed != null && evt.isConsumed;
	},

	/**
	 * Function: isTouchEvent
	 * 
	 * Returns true if the event was generated using a touch device (not a pen or mouse).
	 */
	isTouchEvent: function(evt)
	{
		return (evt.pointerType != null) ? (evt.pointerType == 'touch' || evt.pointerType ===
			evt.MSPOINTER_TYPE_TOUCH) : (evt.type != null && evt.type.indexOf('touch') == 0);
	},

	/**
	 * Function: isPenEvent
	 * 
	 * Returns true if the event was generated using a pen (not a touch device or mouse).
	 */
	isPenEvent: function(evt)
	{
		return (evt.pointerType != null) ? (evt.pointerType == 'pen' || evt.pointerType ===
			evt.MSPOINTER_TYPE_PEN) : (evt.type != null && evt.type.indexOf('pen') == 0);
	},

	/**
	 * Function: isMultiTouchEvent
	 * 
	 * Returns true if the event was generated using a touch device (not a pen or mouse).
	 */
	isMultiTouchEvent: function(evt)
	{
		return (evt.type != null && evt.type.indexOf('touch') == 0 && evt.touches != null &&
			evt.touches.length > 1);
	},

	/**
	 * Function: isMouseEvent
	 * 
	 * Returns true if the event was generated using a mouse (not a pen or touch device).
	 */
	isMouseEvent: function(evt)
	{
		// Workaround for mixed event types during one gesture in Chrome on Linux
		// but not on touch-capable devices (eg Android tablets in desktop UA mode)
		if (!mxClient.IS_ANDROID && mxClient.IS_LINUX && mxClient.IS_GC &&
			navigator.maxTouchPoints <= 1)
		{
			return true;
		}
		else
		{
			return (evt.pointerType != null) ? (evt.pointerType == 'mouse' || evt.pointerType ===
				evt.MSPOINTER_TYPE_MOUSE) : (evt.type != null && evt.type.indexOf('mouse') == 0);
		}
	},
	
	/**
	 * Function: isLeftMouseButton
	 * 
	 * Returns true if the left mouse button is pressed for the given event.
	 * To check if a button is pressed during a mouseMove you should use the
	 * <mxGraph.isMouseDown> property. Note that this returns true in Firefox
	 * for control+left-click on the Mac.
	 */
	isLeftMouseButton: function(evt)
	{
		// Special case for mousemove and mousedown we check the buttons
		// if it exists because which is 0 even if no button is pressed
		if ('buttons' in evt && (evt.type == 'mousedown' || evt.type == 'mousemove'))
		{
			return evt.buttons == 1;
		}
		else if ('which' in evt)
		{
	        return evt.which === 1;
	    }
		else
		{
	        return evt.button === 1;
	    }
	},
	
	/**
	 * Function: isMiddleMouseButton
	 * 
	 * Returns true if the middle mouse button is pressed for the given event.
	 * To check if a button is pressed during a mouseMove you should use the
	 * <mxGraph.isMouseDown> property.
	 */
	isMiddleMouseButton: function(evt)
	{
		if ('which' in evt)
		{
	        return evt.which === 2;
	    }
		else
		{
	        return evt.button === 4;
	    }
	},
	
	/**
	 * Function: isRightMouseButton
	 * 
	 * Returns true if the right mouse button was pressed. Note that this
	 * button might not be available on some systems. For handling a popup
	 * trigger <isPopupTrigger> should be used.
	 */
	isRightMouseButton: function(evt)
	{
		if ('which' in evt)
		{
	        return evt.which === 3;
	    }
		else
		{
	        return evt.button === 2;
	    }
	},

	/**
	 * Function: isPopupTrigger
	 * 
	 * Returns true if the event is a popup trigger. This implementation
	 * returns true if the right button or the left button and control was
	 * pressed on a Mac.
	 */
	isPopupTrigger: function(evt)
	{
		return mxEvent.isRightMouseButton(evt) || (mxClient.IS_MAC && mxEvent.isControlDown(evt) &&
			!mxEvent.isShiftDown(evt) && !mxEvent.isMetaDown(evt) && !mxEvent.isAltDown(evt));
	},

	/**
	 * Function: isShiftDown
	 * 
	 * Returns true if the shift key is pressed for the given event.
	 */
	isShiftDown: function(evt)
	{
		return (evt != null) ? evt.shiftKey : false;
	},

	/**
	 * Function: isAltDown
	 * 
	 * Returns true if the alt key is pressed for the given event.
	 */
	isAltDown: function(evt)
	{
		return (evt != null) ? evt.altKey : false;
	},

	/**
	 * Function: isControlDown
	 * 
	 * Returns true if the control key is pressed for the given event.
	 */
	isControlDown: function(evt)
	{
		return (evt != null) ? evt.ctrlKey : false;
	},

	/**
	 * Function: isMetaDown
	 * 
	 * Returns true if the meta key is pressed for the given event.
	 */
	isMetaDown: function(evt)
	{
		return (evt != null) ? evt.metaKey : false;
	},

	/**
	 * Function: getMainEvent
	 * 
	 * Returns the touch or mouse event that contains the mouse coordinates.
	 */
	getMainEvent: function(e)
	{
		if ((e.type == 'touchstart' || e.type == 'touchmove') && e.touches != null && e.touches[0] != null)
		{
			e = e.touches[0];
		}
		else if (e.type == 'touchend' && e.changedTouches != null && e.changedTouches[0] != null)
		{
			e = e.changedTouches[0];
		}
		
		return e;
	},
	
	/**
	 * Function: getClientX
	 * 
	 * Returns true if the meta key is pressed for the given event.
	 */
	getClientX: function(e)
	{
		return mxEvent.getMainEvent(e).clientX;
	},

	/**
	 * Function: getClientY
	 * 
	 * Returns true if the meta key is pressed for the given event.
	 */
	getClientY: function(e)
	{
		return mxEvent.getMainEvent(e).clientY;
	},

	/**
	 * Function: consume
	 * 
	 * Consumes the given event.
	 * 
	 * Parameters:
	 * 
	 * evt - Native event to be consumed.
	 * preventDefault - Optional boolean to prevent the default for the event.
	 * Default is true.
	 * stopPropagation - Option boolean to stop event propagation. Default is
	 * true.
	 */
	consume: function(evt, preventDefault, stopPropagation)
	{
		preventDefault = (preventDefault != null) ? preventDefault : true;
		stopPropagation = (stopPropagation != null) ? stopPropagation : true;
		
		if (preventDefault)
		{
			if (evt.preventDefault)
			{
				if (stopPropagation)
				{
					evt.stopPropagation();
				}
				
				evt.preventDefault();
			}
			else if (stopPropagation)
			{
				evt.cancelBubble = true;
			}
		}

		// Opera
		evt.isConsumed = true;

		// Other browsers
		if (!evt.preventDefault)
		{
			evt.returnValue = false;
		}
	},

	/**
	 * Function: preventDefault
	 * 
	 * Prevents default actions on mouse or pointer down events for the
	 * given element. Typically used to prevent focus on the element.
	 * 
	 * Parameters:
	 * 
	 * elt - DOM element in which default actions should be prevented.
	 */
	preventDefault: function(elt)
	{
		mxEvent.addListener(elt, (mxClient.IS_POINTER) ? 'pointerdown' : 'mousedown',
			mxUtils.bind(this, function(evt)
		{
			evt.preventDefault();
		}));
	},
	
	//
	// Special handles in mouse events
	//
	
	/**
	 * Variable: LABEL_HANDLE
	 * 
	 * Index for the label handle in an mxMouseEvent. This should be a negative
	 * value that does not interfere with any possible handle indices. Default
	 * is -1.
	 */
	LABEL_HANDLE: -1,
	
	/**
	 * Variable: ROTATION_HANDLE
	 * 
	 * Index for the rotation handle in an mxMouseEvent. This should be a
	 * negative value that does not interfere with any possible handle indices.
	 * Default is -2.
	 */
	ROTATION_HANDLE: -2,

	/**
	 * Variable: CONNECT_HANDLE
	 *
	 * Index for the connect handle in an mxMouseEvent. Default is -3.
	 */
	CONNECT_HANDLE: -3,

	/**
	 * Variable: CUSTOM_HANDLE
	 * 
	 * Start index for the custom handles in an mxMouseEvent. This should be a
	 * negative value and is the start index which is decremented for each
	 * custom handle. Default is -100.
	 */
	CUSTOM_HANDLE: -100,
	
	/**
	 * Variable: VIRTUAL_HANDLE
	 * 
	 * Start index for the virtual handles in an mxMouseEvent. This should be a
	 * negative value and is the start index which is decremented for each
	 * virtual handle. Default is -100000. This assumes that there are no more
	 * than VIRTUAL_HANDLE - CUSTOM_HANDLE custom handles.
	 * 
	 */
	VIRTUAL_HANDLE: -100000,
	
	//
	// Event names
	//
	
	/**
	 * Variable: MOUSE_DOWN
	 *
	 * Specifies the event name for mouseDown.
	 */
	MOUSE_DOWN: 'mouseDown',
	
	/**
	 * Variable: MOUSE_MOVE
	 *
	 * Specifies the event name for mouseMove. 
	 */
	MOUSE_MOVE: 'mouseMove',
	
	/**
	 * Variable: MOUSE_UP
	 *
	 * Specifies the event name for mouseUp. 
	 */
	MOUSE_UP: 'mouseUp',

	/**
	 * Variable: ACTIVATE
	 *
	 * Specifies the event name for activate.
	 */
	ACTIVATE: 'activate',

	/**
	 * Variable: RESIZE_START
	 *
	 * Specifies the event name for resizeStart.
	 */
	RESIZE_START: 'resizeStart',

	/**
	 * Variable: RESIZE
	 *
	 * Specifies the event name for resize.
	 */
	RESIZE: 'resize',

	/**
	 * Variable: RESIZE_END
	 *
	 * Specifies the event name for resizeEnd.
	 */
	RESIZE_END: 'resizeEnd',

	/**
	 * Variable: MOVE_START
	 *
	 * Specifies the event name for moveStart.
	 */
	MOVE_START: 'moveStart',

	/**
	 * Variable: MOVE
	 *
	 * Specifies the event name for move.
	 */
	MOVE: 'move',

	/**
	 * Variable: MOVE_END
	 *
	 * Specifies the event name for moveEnd.
	 */
	MOVE_END: 'moveEnd',

	/**
	 * Variable: DOCK
	 *
	 * Specifies the event name for dock.
	 */
	DOCK: 'dock',

	/**
	 * Variable: UNDOCK
	 *
	 * Specifies the event name for undock.
	 */
	UNDOCK: 'undock',

	/**
	 * Variable: PAN_START
	 *
	 * Specifies the event name for panStart.
	 */
	PAN_START: 'panStart',

	/**
	 * Variable: PAN
	 *
	 * Specifies the event name for pan.
	 */
	PAN: 'pan',

	/**
	 * Variable: PAN_END
	 *
	 * Specifies the event name for panEnd.
	 */
	PAN_END: 'panEnd',

	/**
	 * Variable: MINIMIZE
	 *
	 * Specifies the event name for minimize.
	 */
	MINIMIZE: 'minimize',

	/**
	 * Variable: NORMALIZE
	 *
	 * Specifies the event name for normalize.
	 */
	NORMALIZE: 'normalize',

	/**
	 * Variable: MAXIMIZE
	 *
	 * Specifies the event name for maximize.
	 */
	MAXIMIZE: 'maximize',

	/**
	 * Variable: HIDE
	 *
	 * Specifies the event name for hide.
	 */
	HIDE: 'hide',

	/**
	 * Variable: SHOW
	 *
	 * Specifies the event name for show.
	 */
	SHOW: 'show',

	/**
	 * Variable: CLOSE
	 *
	 * Specifies the event name for close.
	 */
	CLOSE: 'close',

	/**
	 * Variable: DESTROY
	 *
	 * Specifies the event name for destroy.
	 */
	DESTROY: 'destroy',

	/**
	 * Variable: REFRESH
	 *
	 * Specifies the event name for refresh.
	 */
	REFRESH: 'refresh',

	/**
	 * Variable: SIZE
	 *
	 * Specifies the event name for size.
	 */
	SIZE: 'size',
	
	/**
	 * Variable: SELECT
	 *
	 * Specifies the event name for select.
	 */
	SELECT: 'select',

	/**
	 * Variable: FIRED
	 *
	 * Specifies the event name for fired.
	 */
	FIRED: 'fired',

	/**
	 * Variable: FIRE_MOUSE_EVENT
	 *
	 * Specifies the event name for fireMouseEvent.
	 */
	FIRE_MOUSE_EVENT: 'fireMouseEvent',

	/**
	 * Variable: CONSUME_MOUSE_EVENT
	 *
	 * Specifies the event name for consumeMouseEvent.
	 */
	CONSUME_MOUSE_EVENT: 'consumeMouseEvent',

	/**
	 * Variable: GESTURE
	 *
	 * Specifies the event name for gesture.
	 */
	GESTURE: 'gesture',

	/**
	 * Variable: CANCEL_MOUSE_GESTURE
	 *
	 * Specifies the event name for cancelMouseGesture.
	 */
	CANCEL_MOUSE_GESTURE: 'cancelMouseGesture',

	/**
	 * Variable: TAP_AND_HOLD
	 *
	 * Specifies the event name for tapAndHold.
	 */
	TAP_AND_HOLD: 'tapAndHold',

	/**
	 * Variable: GET
	 *
	 * Specifies the event name for get.
	 */
	GET: 'get',

	/**
	 * Variable: RECEIVE
	 *
	 * Specifies the event name for receive.
	 */
	RECEIVE: 'receive',

	/**
	 * Variable: CONNECT
	 *
	 * Specifies the event name for connect.
	 */
	CONNECT: 'connect',

	/**
	 * Variable: DISCONNECT
	 *
	 * Specifies the event name for disconnect.
	 */
	DISCONNECT: 'disconnect',

	/**
	 * Variable: SUSPEND
	 *
	 * Specifies the event name for suspend.
	 */
	SUSPEND: 'suspend',

	/**
	 * Variable: RESUME
	 *
	 * Specifies the event name for suspend.
	 */
	RESUME: 'resume',

	/**
	 * Variable: MARK
	 *
	 * Specifies the event name for mark.
	 */
	MARK: 'mark',

	/**
	 * Variable: ROOT
	 *
	 * Specifies the event name for root.
	 */
	ROOT: 'root',

	/**
	 * Variable: POST
	 *
	 * Specifies the event name for post.
	 */
	POST: 'post',

	/**
	 * Variable: OPEN
	 *
	 * Specifies the event name for open.
	 */
	OPEN: 'open',

	/**
	 * Variable: SAVE
	 *
	 * Specifies the event name for open.
	 */
	SAVE: 'save',

	/**
	 * Variable: BEFORE_ADD_VERTEX
	 *
	 * Specifies the event name for beforeAddVertex.
	 */
	BEFORE_ADD_VERTEX: 'beforeAddVertex',

	/**
	 * Variable: ADD_VERTEX
	 *
	 * Specifies the event name for addVertex.
	 */
	ADD_VERTEX: 'addVertex',

	/**
	 * Variable: AFTER_ADD_VERTEX
	 *
	 * Specifies the event name for afterAddVertex.
	 */
	AFTER_ADD_VERTEX: 'afterAddVertex',

	/**
	 * Variable: DONE
	 *
	 * Specifies the event name for done.
	 */
	DONE: 'done',

	/**
	 * Variable: EXECUTE
	 *
	 * Specifies the event name for execute.
	 */
	EXECUTE: 'execute',

	/**
	 * Variable: EXECUTED
	 *
	 * Specifies the event name for executed.
	 */
	EXECUTED: 'executed',

	/**
	 * Variable: BEGIN_UPDATE
	 *
	 * Specifies the event name for beginUpdate.
	 */
	BEGIN_UPDATE: 'beginUpdate',

	/**
	 * Variable: START_EDIT
	 *
	 * Specifies the event name for startEdit.
	 */
	START_EDIT: 'startEdit',

	/**
	 * Variable: END_UPDATE
	 *
	 * Specifies the event name for endUpdate.
	 */
	END_UPDATE: 'endUpdate',

	/**
	 * Variable: END_EDIT
	 *
	 * Specifies the event name for endEdit.
	 */
	END_EDIT: 'endEdit',

	/**
	 * Variable: BEFORE_UNDO
	 *
	 * Specifies the event name for beforeUndo.
	 */
	BEFORE_UNDO: 'beforeUndo',

	/**
	 * Variable: UNDO
	 *
	 * Specifies the event name for undo.
	 */
	UNDO: 'undo',

	/**
	 * Variable: REDO
	 *
	 * Specifies the event name for redo.
	 */
	REDO: 'redo',

	/**
	 * Variable: CHANGE
	 *
	 * Specifies the event name for change.
	 */
	CHANGE: 'change',

	/**
	 * Variable: NOTIFY
	 *
	 * Specifies the event name for notify.
	 */
	NOTIFY: 'notify',

	/**
	 * Variable: LAYOUT_CELLS
	 *
	 * Specifies the event name for layoutCells.
	 */
	LAYOUT_CELLS: 'layoutCells',

	/**
	 * Variable: CLICK
	 *
	 * Specifies the event name for click.
	 */
	CLICK: 'click',

	/**
	 * Variable: SCALE
	 *
	 * Specifies the event name for scale.
	 */
	SCALE: 'scale',

	/**
	 * Variable: TRANSLATE
	 *
	 * Specifies the event name for translate.
	 */
	TRANSLATE: 'translate',

	/**
	 * Variable: SCALE_AND_TRANSLATE
	 *
	 * Specifies the event name for scaleAndTranslate.
	 */
	SCALE_AND_TRANSLATE: 'scaleAndTranslate',

	/**
	 * Variable: UP
	 *
	 * Specifies the event name for up.
	 */
	UP: 'up',

	/**
	 * Variable: DOWN
	 *
	 * Specifies the event name for down.
	 */
	DOWN: 'down',

	/**
	 * Variable: ADD
	 *
	 * Specifies the event name for add.
	 */
	ADD: 'add',

	/**
	 * Variable: REMOVE
	 *
	 * Specifies the event name for remove.
	 */
	REMOVE: 'remove',
	
	/**
	 * Variable: CLEAR
	 *
	 * Specifies the event name for clear.
	 */
	CLEAR: 'clear',

	/**
	 * Variable: ADD_CELLS
	 *
	 * Specifies the event name for addCells.
	 */
	ADD_CELLS: 'addCells',

	/**
	 * Variable: CELLS_ADDED
	 *
	 * Specifies the event name for cellsAdded.
	 */
	CELLS_ADDED: 'cellsAdded',

	/**
	 * Variable: MOVE_CELLS
	 *
	 * Specifies the event name for moveCells.
	 */
	MOVE_CELLS: 'moveCells',

	/**
	 * Variable: CELLS_MOVED
	 *
	 * Specifies the event name for cellsMoved.
	 */
	CELLS_MOVED: 'cellsMoved',

	/**
	 * Variable: RESIZE_CELLS
	 *
	 * Specifies the event name for resizeCells.
	 */
	RESIZE_CELLS: 'resizeCells',

	/**
	 * Variable: CELLS_RESIZED
	 *
	 * Specifies the event name for cellsResized.
	 */
	CELLS_RESIZED: 'cellsResized',

	/**
	 * Variable: TOGGLE_CELLS
	 *
	 * Specifies the event name for toggleCells.
	 */
	TOGGLE_CELLS: 'toggleCells',

	/**
	 * Variable: CELLS_TOGGLED
	 *
	 * Specifies the event name for cellsToggled.
	 */
	CELLS_TOGGLED: 'cellsToggled',

	/**
	 * Variable: ORDER_CELLS
	 *
	 * Specifies the event name for orderCells.
	 */
	ORDER_CELLS: 'orderCells',

	/**
	 * Variable: CELLS_ORDERED
	 *
	 * Specifies the event name for cellsOrdered.
	 */
	CELLS_ORDERED: 'cellsOrdered',

	/**
	 * Variable: REMOVE_CELLS
	 *
	 * Specifies the event name for removeCells.
	 */
	REMOVE_CELLS: 'removeCells',

	/**
	 * Variable: CELLS_REMOVED
	 *
	 * Specifies the event name for cellsRemoved.
	 */
	CELLS_REMOVED: 'cellsRemoved',

	/**
	 * Variable: GROUP_CELLS
	 *
	 * Specifies the event name for groupCells.
	 */
	GROUP_CELLS: 'groupCells',

	/**
	 * Variable: UNGROUP_CELLS
	 *
	 * Specifies the event name for ungroupCells.
	 */
	UNGROUP_CELLS: 'ungroupCells',

	/**
	 * Variable: REMOVE_CELLS_FROM_PARENT
	 *
	 * Specifies the event name for removeCellsFromParent.
	 */
	REMOVE_CELLS_FROM_PARENT: 'removeCellsFromParent',

	/**
	 * Variable: FOLD_CELLS
	 *
	 * Specifies the event name for foldCells.
	 */
	FOLD_CELLS: 'foldCells',

	/**
	 * Variable: CELLS_FOLDED
	 *
	 * Specifies the event name for cellsFolded.
	 */
	CELLS_FOLDED: 'cellsFolded',

	/**
	 * Variable: ALIGN_CELLS
	 *
	 * Specifies the event name for alignCells.
	 */
	ALIGN_CELLS: 'alignCells',

	/**
	 * Variable: LABEL_CHANGED
	 *
	 * Specifies the event name for labelChanged.
	 */
	LABEL_CHANGED: 'labelChanged',

	/**
	 * Variable: CONNECT_CELL
	 *
	 * Specifies the event name for connectCell.
	 */
	CONNECT_CELL: 'connectCell',

	/**
	 * Variable: CELL_CONNECTED
	 *
	 * Specifies the event name for cellConnected.
	 */
	CELL_CONNECTED: 'cellConnected',

	/**
	 * Variable: SPLIT_EDGE
	 *
	 * Specifies the event name for splitEdge.
	 */
	SPLIT_EDGE: 'splitEdge',

	/**
	 * Variable: FLIP_EDGE
	 *
	 * Specifies the event name for flipEdge.
	 */
	FLIP_EDGE: 'flipEdge',

	/**
	 * Variable: START_EDITING
	 *
	 * Specifies the event name for startEditing.
	 */
	START_EDITING: 'startEditing',

	/**
	 * Variable: EDITING_STARTED
	 *
	 * Specifies the event name for editingStarted.
	 */
	EDITING_STARTED: 'editingStarted',

	/**
	 * Variable: EDITING_STOPPED
	 *
	 * Specifies the event name for editingStopped.
	 */
	EDITING_STOPPED: 'editingStopped',

	/**
	 * Variable: ADD_OVERLAY
	 *
	 * Specifies the event name for addOverlay.
	 */
	ADD_OVERLAY: 'addOverlay',

	/**
	 * Variable: REMOVE_OVERLAY
	 *
	 * Specifies the event name for removeOverlay.
	 */
	REMOVE_OVERLAY: 'removeOverlay',

	/**
	 * Variable: UPDATE_CELL_SIZE
	 *
	 * Specifies the event name for updateCellSize.
	 */
	UPDATE_CELL_SIZE: 'updateCellSize',

	/**
	 * Variable: ESCAPE
	 *
	 * Specifies the event name for escape.
	 */
	ESCAPE: 'escape',

	/**
	 * Variable: DOUBLE_CLICK
	 *
	 * Specifies the event name for doubleClick.
	 */
	DOUBLE_CLICK: 'doubleClick',

	/**
	 * Variable: START
	 *
	 * Specifies the event name for start.
	 */
	START: 'start',

	/**
	 * Variable: UPDATE
	 *
	 * Specifies the event name for update.
	 */
	UPDATE: 'update',

	/**
	 * Variable: RESET
	 *
	 * Specifies the event name for reset.
	 */
	RESET: 'reset',

	/**
	 * Variable: PINCH_THRESHOLD
	 *
	 * Change of the distance between the fingers in pixels for pinch gestures
	 * to fire a mouse wheel event. Default value is 4.
	 */
	PINCH_THRESHOLD: 4

};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxUndoableEdit
 * 
 * Implements a composite undoable edit. Here is an example for a custom change
 * which gets executed via the model:
 * 
 * (code)
 * function CustomChange(model, name)
 * {
 *   this.model = model;
 *   this.name = name;
 *   this.previous = name;
 * };
 * 
 * CustomChange.prototype.execute = function()
 * {
 *   var tmp = this.model.name;
 *   this.model.name = this.previous;
 *   this.previous = tmp;
 * };
 * 
 * var name = prompt('Enter name');
 * graph.model.execute(new CustomChange(graph.model, name));
 * (end)
 * 
 * Event: mxEvent.EXECUTED
 * 
 * Fires between START_EDIT and END_EDIT after an atomic change was executed.
 * The <code>change</code> property contains the change that was executed.
 * 
 * Event: mxEvent.START_EDIT
 * 
 * Fires before a set of changes will be executed in <undo> or <redo>.
 * This event contains no properties.
 * 
 * Event: mxEvent.END_EDIT
 *
 * Fires after a set of changeswas executed in <undo> or <redo>.
 * This event contains no properties.
 * 
 * Constructor: mxUndoableEdit
 * 
 * Constructs a new undoable edit for the given source.
 */
function mxUndoableEdit(source, significant)
{
	this.source = source;
	this.changes = [];
	this.significant = (significant != null) ? significant : true;
};

/**
 * Variable: source
 * 
 * Specifies the source of the edit.
 */
mxUndoableEdit.prototype.source = null;

/**
 * Variable: changes
 * 
 * Array that contains the changes that make up this edit. The changes are
 * expected to either have an undo and redo function, or an execute
 * function. Default is an empty array.
 */
mxUndoableEdit.prototype.changes = null;

/**
 * Variable: significant
 * 
 * Specifies if the undoable change is significant.
 * Default is true.
 */
mxUndoableEdit.prototype.significant = null;

/**
 * Variable: undone
 * 
 * Specifies if this edit has been undone. Default is false.
 */
mxUndoableEdit.prototype.undone = false;

/**
 * Variable: redone
 * 
 * Specifies if this edit has been redone. Default is false.
 */
mxUndoableEdit.prototype.redone = false;

/**
 * Function: isEmpty
 * 
 * Returns true if the this edit contains no changes.
 */
mxUndoableEdit.prototype.isEmpty = function()
{
	return this.changes.length == 0;
};

/**
 * Function: isSignificant
 * 
 * Returns <significant>.
 */
mxUndoableEdit.prototype.isSignificant = function()
{
	return this.significant;
};

/**
 * Function: add
 * 
 * Adds the specified change to this edit. The change is an object that is
 * expected to either have an undo and redo, or an execute function.
 */
mxUndoableEdit.prototype.add = function(change)
{
	this.changes.push(change);
};

/**
 * Function: notify
 * 
 * Hook to notify any listeners of the changes after an <undo> or <redo>
 * has been carried out. This implementation is empty.
 */
mxUndoableEdit.prototype.notify = function() { };

/**
 * Function: die
 * 
 * Hook to free resources after the edit has been removed from the command
 * history. This implementation is empty.
 */
mxUndoableEdit.prototype.die = function() { };

/**
 * Function: undo
 * 
 * Undoes all changes in this edit.
 */
mxUndoableEdit.prototype.undo = function()
{
	if (!this.undone)
	{
		this.source.fireEvent(new mxEventObject(mxEvent.START_EDIT));
		var count = this.changes.length;
		
		for (var i = count - 1; i >= 0; i--)
		{
			var change = this.changes[i];
			
			if (change.execute != null)
			{
				change.execute();
			}
			else if (change.undo != null)
			{
				change.undo();
			}
			
			// New global executed event
			this.source.fireEvent(new mxEventObject(mxEvent.EXECUTED, 'change', change));
		}
		
		this.undone = true;
		this.redone = false;
		this.source.fireEvent(new mxEventObject(mxEvent.END_EDIT));
	}
	
	this.notify();
};

/**
 * Function: redo
 * 
 * Redoes all changes in this edit.
 */
mxUndoableEdit.prototype.redo = function()
{
	if (!this.redone)
	{
		this.source.fireEvent(new mxEventObject(mxEvent.START_EDIT));
		var count = this.changes.length;
		
		for (var i = 0; i < count; i++)
		{
			var change = this.changes[i];
			
			if (change.execute != null)
			{
				change.execute();
			}
			else if (change.redo != null)
			{
				change.redo();
			}
			
			// New global executed event
			this.source.fireEvent(new mxEventObject(mxEvent.EXECUTED, 'change', change));
		}
		
		this.undone = false;
		this.redone = true;
		this.source.fireEvent(new mxEventObject(mxEvent.END_EDIT));
	}
	
	this.notify();
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
var mxObjectIdentity =
{
	/**
	 * Class: mxObjectIdentity
	 * 
	 * Identity for JavaScript objects and functions. This is implemented using
	 * a simple incrementing counter which is stored in each object under
	 * <FIELD_NAME>.
	 * 
	 * The identity for an object does not change during its lifecycle.
	 * 
	 * Variable: FIELD_NAME
	 * 
	 * Name of the field to be used to store the object ID. Default is
	 * <code>mxObjectId</code>.
	 */
	FIELD_NAME: 'mxObjectId',

	/**
	 * Variable: counter
	 * 
	 * Current counter.
	 */
	counter: 0,

	/**
	 * Function: get
	 * 
	 * Returns the ID for the given object or function or null if no object
	 * is specified.
	 */
	get: function(obj)
	{
		if (obj != null)
		{
			if (obj[mxObjectIdentity.FIELD_NAME] == null)
			{
				if (typeof obj === 'object')
				{
					var ctor = mxUtils.getFunctionName(obj.constructor);
					obj[mxObjectIdentity.FIELD_NAME] = ctor + '#' + mxObjectIdentity.counter++;
				}
				else if (typeof obj === 'function')
				{
					obj[mxObjectIdentity.FIELD_NAME] = 'Function#' + mxObjectIdentity.counter++;
				}
			}
			
			return obj[mxObjectIdentity.FIELD_NAME];
		}
		
		return null;
	},

	/**
	 * Function: clear
	 * 
	 * Deletes the ID from the given object or function.
	 */
	clear: function(obj)
	{
		if (typeof(obj) === 'object' || typeof obj === 'function')
		{
			delete obj[mxObjectIdentity.FIELD_NAME];
		}
	}

};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxDictionary
 *
 * A wrapper class for an associative array with object keys. Note: This
 * implementation uses <mxObjectIdentitiy> to turn object keys into strings.
 * 
 * Constructor: mxEventSource
 *
 * Constructs a new dictionary which allows object to be used as keys.
 */
function mxDictionary()
{
	this.clear();
};

/**
 * Function: map
 *
 * Stores the (key, value) pairs in this dictionary.
 */
mxDictionary.prototype.map = null;

/**
 * Function: clear
 *
 * Clears the dictionary.
 */
mxDictionary.prototype.clear = function()
{
	this.map = {};
};

/**
 * Function: get
 *
 * Returns the value for the given key.
 */
mxDictionary.prototype.get = function(key)
{
	var id = mxObjectIdentity.get(key);
	
	return this.map[id];
};

/**
 * Function: put
 *
 * Stores the value under the given key and returns the previous
 * value for that key.
 */
mxDictionary.prototype.put = function(key, value)
{
	var id = mxObjectIdentity.get(key);
	var previous = this.map[id];
	this.map[id] = value;
	
	return previous;
};

/**
 * Function: remove
 *
 * Removes the value for the given key and returns the value that
 * has been removed.
 */
mxDictionary.prototype.remove = function(key)
{
	var id = mxObjectIdentity.get(key);
	var previous = this.map[id];
	delete this.map[id];
	
	return previous;
};

/**
 * Function: getCount
 *
 * Returns the number of objects in this dictionary.
 */
mxDictionary.prototype.getCount = function()
{
	var count = 0;

	for (var key in this.map)
	{
		count++;
	}

	return count;
};

/**
 * Function: getKeys
 *
 * Returns all keys as an array.
 */
mxDictionary.prototype.getKeys = function()
{
	var result = [];
	
	for (var key in this.map)
	{
		result.push(key);
	}
	
	return result;
};

/**
 * Function: getValues
 *
 * Returns all values as an array.
 */
mxDictionary.prototype.getValues = function()
{
	var result = [];
	
	for (var key in this.map)
	{
		result.push(this.map[key]);
	}
	
	return result;
};

/**
 * Function: visit
 *
 * Visits all entries in the dictionary using the given function with the
 * following signature: function(key, value) where key is a string and
 * value is an object.
 * 
 * Parameters:
 * 
 * visitor - A function that takes the key and value as arguments.
 */
mxDictionary.prototype.visit = function(visitor)
{
	for (var key in this.map)
	{
		visitor(key, this.map[key]);
	}
};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
var mxCellPath =
{

	/**
	 * Class: mxCellPath
	 * 
	 * Implements a mechanism for temporary cell Ids.
	 * 
	 * Variable: PATH_SEPARATOR
	 * 
	 * Defines the separator between the path components. Default is ".".
	 */
	PATH_SEPARATOR: '.',
	
	/**
	 * Function: create
	 * 
	 * Creates the cell path for the given cell. The cell path is a
	 * concatenation of the indices of all ancestors on the (finite) path to
	 * the root, eg. "0.0.0.1".
	 * 
	 * Parameters:
	 * 
	 * cell - Cell whose path should be returned.
	 */
	create: function(cell)
	{
		var result = '';
		
		if (cell != null)
		{
			var parent = cell.getParent();
			
			while (parent != null)
			{
				var index = parent.getIndex(cell);
				result = index + mxCellPath.PATH_SEPARATOR + result;
				
				cell = parent;
				parent = cell.getParent();
			}
		}
		
		// Removes trailing separator
		var n = result.length;
		
		if (n > 1)
		{
			result = result.substring(0, n - 1);
		}
		
		return result;
	},
	
	/**
	 * Function: getParentPath
	 * 
	 * Returns the path for the parent of the cell represented by the given
	 * path. Returns null if the given path has no parent.
	 * 
	 * Parameters:
	 * 
	 * path - Path whose parent path should be returned.
	 */
	getParentPath: function(path)
	{
		if (path != null)
		{
			var index = path.lastIndexOf(mxCellPath.PATH_SEPARATOR);

			if (index >= 0)
			{
				return path.substring(0, index);
			}
			else if (path.length > 0)
			{
				return '';
			}
		}

		return null;
	},

	/**
	 * Function: resolve
	 * 
	 * Returns the cell for the specified cell path using the given root as the
	 * root of the path.
	 * 
	 * Parameters:
	 * 
	 * root - Root cell of the path to be resolved.
	 * path - String that defines the path.
	 */
	resolve: function(root, path)
	{
		var parent = root;
		
		if (path != null)
		{
			var tokens = path.split(mxCellPath.PATH_SEPARATOR);
			
			for (var i=0; i<tokens.length; i++)
			{
				parent = parent.getChildAt(parseInt(tokens[i]));
			}
		}
		
		return parent;
	},
	
	/**
	 * Function: compare
	 * 
	 * Compares the given cell paths and returns -1 if p1 is smaller, 0 if
	 * p1 is equal and 1 if p1 is greater than p2.
	 */
	compare: function(p1, p2)
	{
		var min = Math.min(p1.length, p2.length);
		var comp = 0;
		
		for (var i = 0; i < min; i++)
		{
			if (p1[i] != p2[i])
			{
				if (p1[i].length == 0 ||
					p2[i].length == 0)
				{
					comp = (p1[i] == p2[i]) ? 0 : ((p1[i] > p2[i]) ? 1 : -1);
				}
				else
				{
					var t1 = parseInt(p1[i]);
					var t2 = parseInt(p2[i]);
					
					comp = (t1 == t2) ? 0 : ((t1 > t2) ? 1 : -1);
				}
				
				break;
			}
		}
		
		// Compares path length if both paths are equal to this point
		if (comp == 0)
		{
			var t1 = p1.length;
			var t2 = p2.length;
			
			if (t1 != t2)
			{
				comp = (t1 > t2) ? 1 : -1;
			}
		}
		
		return comp;
	}

};
;
/**
 * Copyright (c) 2006-2015, JGraph Holdings Ltd
 * Copyright (c) 2006-2015, draw.io AG
 */
/**
 * Class: mxCell
 *
 * Cells are the elements of the graph model. They represent the state
 * of the groups, vertices and edges in a graph.
 * 
 * Custom attributes:
 * 
 * For custom attributes we recommend using an XML node as the value of a cell.
 * The following code can be used to create a cell with an XML node as the
 * value:
 * 
 * (code)
 * var doc = mxUtils.createXmlDocument();
 * var node = doc.createElement('MyNode')
 * node.setAttribute('label', 'MyLabel');
 * node.setAttribute('attribute1', 'value1');
 * graph.insertVertex(graph.getDefaultParent(), null, node, 40, 40, 80, 30);
 * (end)
 * 
 * For the label to work, <mxGraph.convertValueToString> and
 * <mxGraph.cellLabelChanged> should be overridden as follows:
 * 
 * (code)
 * graph.convertValueToString = function(cell)
 * {
 *   if (mxUtils.isNode(cell.value))
 *   {
 *     return cell.getAttribute('label', '')
 *   }
 * };
 * 
 * var cellLabelChanged = graph.cellLabelChanged;
 * graph.cellLabelChanged = function(cell, newValue, autoSize)
 * {
 *   if (mxUtils.isNode(cell.value))
 *   {
 *     // Clones the value for correct undo/redo
 *     var elt = cell.value.cloneNode(true);
 *     elt.setAttribute('label', newValue);
 *     newValue = elt;
 *   }
 *   
 *   cellLabelChanged.apply(this, arguments);
 * };
 * (end)
 * 
 * Callback: onInit
 *
 * Called from within the constructor.
 * 
 * Constructor: mxCell
 *
 * Constructs a new cell to be used in a graph model.
 * This method invokes <onInit> upon completion.
 * 
 * Parameters:
 * 
 * value - Optional object that represents the cell value.
 * geometry - Optional <mxGeometry> that specifies the geometry.
 * style - Optional formatted string that defines the style.
 */
function mxCell(value, geometry, style)
{
	this.value = value;
	this.setGeometry(geometry);
	this.setStyle(style);
	
	if (this.onInit != null)
	{
		this.onInit();
	}
};

/**
 * Variable: id
 *
 * Holds the Id. Default is null.
 */
mxCell.prototype.id = null;

/**
 * Variable: value
 *
 * Holds the user object. Default is null.
 */
mxCell.prototype.value = null;

/**
 * Variable: geometry
 *
 * Holds the <mxGeometry>. Default is null.
 */
mxCell.prototype.geometry = null;

/**
 * Variable: style
 *
 * Holds the style as a string of the form [(stylename|key=value);]. Default is
 * null.
 */
mxCell.prototype.style = null;

/**
 * Variable: vertex
 *
 * Specifies whether the cell is a vertex. Default is false.
 */
mxCell.prototype.vertex = false;

/**
 * Variable: edge
 *
 * Specifies whether the cell is an edge. Default is false.
 */
mxCell.prototype.edge = false;

/**
 * Variable: connectable
 *
 * Specifies whether the cell is connectable. Default is true.
 */
mxCell.prototype.connectable = true;

/**
 * Variable: visible
 *
 * Specifies whether the cell is visible. Default is true.
 */
mxCell.prototype.visible = true;

/**
 * Variable: collapsed
 *
 * Specifies whether the cell is collapsed. Default is false.
 */
mxCell.prototype.collapsed = false;

/**
 * Variable: parent
 *
 * Reference to the parent cell.
 */
mxCell.prototype.parent = null;

/**
 * Variable: source
 *
 * Reference to the source terminal.
 */
mxCell.prototype.source = null;

/**
 * Variable: target
 *
 * Reference to the target terminal.
 */
mxCell.prototype.target = null;

/**
 * Variable: children
 *
 * Holds the child cells.
 */
mxCell.prototype.children = null;

/**
 * Variable: edges
 *
 * Holds the edges.
 */
mxCell.prototype.edges = null;

/**
 * Variable: mxTransient
 *
 * List of members that should not be cloned inside <clone>. This field is
 * passed to <mxUtils.clone> and is not made persistent in <mxCellCodec>.
 * This is not a convention for all classes, it is only used in this class
 * to mark transient fields since transient modifiers are not supported by
 * the language. The overlays belong to the cell in the graph, their
 * listeners refer to that cell (see <mxGraph.addCellOverlay>).
 */
mxCell.prototype.mxTransient = ['id', 'value', 'parent', 'source',
                                'target', 'children', 'edges', 'overlays'];

/**
 * Function: getId
 *
 * Returns the Id of the cell as a string.
 */
mxCell.prototype.getId = function()
{
	return this.id;
};
		
/**
 * Function: setId
 *
 * Sets the Id of the cell to the given string.
 */
mxCell.prototype.setId = function(id)
{
	this.id = id;
};

/**
 * Function: getValue
 *
 * Returns the user object of the cell. The user
 * object is stored in <value>.
 */
mxCell.prototype.getValue = function()
{
	return this.value;
};
		
/**
 * Function: setValue
 *
 * Sets the user object of the cell. The user object
 * is stored in <value>.
 */
mxCell.prototype.setValue = function(value)
{
	this.value = value;
};

/**
 * Function: valueChanged
 *
 * Changes the user object after an in-place edit
 * and returns the previous value. This implementation
 * replaces the user object with the given value and
 * returns the old user object.
 */
mxCell.prototype.valueChanged = function(newValue)
{
	var previous = this.getValue();
	this.setValue(newValue);
	
	return previous;
};

/**
 * Function: getGeometry
 *
 * Returns the <mxGeometry> that describes the <geometry>.
 */
mxCell.prototype.getGeometry = function()
{
	return this.geometry;
};

/**
 * Function: setGeometry
 *
 * Sets the <mxGeometry> to be used as the <geometry>.
 */
mxCell.prototype.setGeometry = function(geometry)
{
	this.geometry = geometry;
};

/**
 * Function: getStyle
 *
 * Returns a string that describes the <style>.
 */
mxCell.prototype.getStyle = function()
{
	return this.style;
};

/**
 * Function: setStyle
 *
 * Sets the string to be used as the <style>.
 */
mxCell.prototype.setStyle = function(style)
{
	this.style = style;
};

/**
 * Function: isVertex
 *
 * Returns true if the cell is a vertex.
 */
mxCell.prototype.isVertex = function()
{
	return this.vertex != 0;
};

/**
 * Function: setVertex
 *
 * Specifies if the cell is a vertex. This should only be assigned at
 * construction of the cell and not be changed during its lifecycle.
 * 
 * Parameters:
 * 
 * vertex - Boolean that specifies if the cell is a vertex.
 */
mxCell.prototype.setVertex = function(vertex)
{
	this.vertex = vertex;
};

/**
 * Function: isEdge
 *
 * Returns true if the cell is an edge.
 */
mxCell.prototype.isEdge = function()
{
	return this.edge != 0;
};
	
/**
 * Function: setEdge
 * 
 * Specifies if the cell is an edge. This should only be assigned at
 * construction of the cell and not be changed during its lifecycle.
 * 
 * Parameters:
 * 
 * edge - Boolean that specifies if the cell is an edge.
 */
mxCell.prototype.setEdge = function(edge)
{
	this.edge = edge;
};

/**
 * Function: isConnectable
 *
 * Returns true if the cell is connectable.
 */
mxCell.prototype.isConnectable = function()
{
	return this.connectable != 0;
};

/**
 * Function: setConnectable
 *
 * Sets the connectable state.
 * 
 * Parameters:
 * 
 * connectable - Boolean that specifies the new connectable state.
 */
mxCell.prototype.setConnectable = function(connectable)
{
	this.connectable = connectable;
};

/**
 * Function: isVisible
 *
 * Returns true if the cell is visibile.
 */
mxCell.prototype.isVisible = function()
{
	return this.visible != 0;
};

/**
 * Function: setVisible
 *
 * Specifies if the cell is visible.
 * 
 * Parameters:
 * 
 * visible - Boolean that specifies the new visible state.
 */
mxCell.prototype.setVisible = function(visible)
{
	this.visible = visible;
};

/**
 * Function: isCollapsed
 *
 * Returns true if the cell is collapsed.
 */
mxCell.prototype.isCollapsed = function()
{
	return this.collapsed != 0;
};

/**
 * Function: setCollapsed
 *
 * Sets the collapsed state.
 * 
 * Parameters:
 * 
 * collapsed - Boolean that specifies the new collapsed state.
 */
mxCell.prototype.setCollapsed = function(collapsed)
{
	this.collapsed = collapsed;
};

/**
 * Function: getParent
 *
 * Returns the cell's parent.
 */
mxCell.prototype.getParent = function()
{
	return this.parent;
};

/**
 * Function: setParent
 *
 * Sets the parent cell.
 * 
 * Parameters:
 * 
 * parent - <mxCell> that represents the new parent.
 */
mxCell.prototype.setParent = function(parent)
{
	this.parent = parent;
};

/**
 * Function: getTerminal
 *
 * Returns the source or target terminal.
 * 
 * Parameters:
 * 
 * source - Boolean that specifies if the source terminal should be
 * returned.
 */
mxCell.prototype.getTerminal = function(source)
{
	return (source) ? this.source : this.target;
};

/**
 * Function: setTerminal
 *
 * Sets the source or target terminal and returns the new terminal.
 * 
 * Parameters:
 * 
 * terminal - <mxCell> that represents the new source or target terminal.
 * isSource - Boolean that specifies if the source or target terminal
 * should be set.
 */
mxCell.prototype.setTerminal = function(terminal, isSource)
{
	if (isSource)
	{
		this.source = terminal;
	}
	else
	{
		this.target = terminal;
	}
	
	return terminal;
};

/**
 * Function: getChildCount
 *
 * Returns the number of child cells.
 */
mxCell.prototype.getChildCount = function()
{
	return (this.children == null) ? 0 : this.children.length;
};

/**
 * Function: getIndex
 *
 * Returns the index of the specified child in the child array.
 * 
 * Parameters:
 * 
 * child - Child whose index should be returned.
 */
mxCell.prototype.getIndex = function(child)
{
	return mxUtils.indexOf(this.children, child);
};

/**
 * Function: getChildAt
 *
 * Returns the child at the specified index.
 * 
 * Parameters:
 * 
 * index - Integer that specifies the child to be returned.
 */
mxCell.prototype.getChildAt = function(index)
{
	return (this.children == null) ? null : this.children[index];
};

/**
 * Function: insert
 *
 * Inserts the specified child into the child array at the specified index
 * and updates the parent reference of the child. If not childIndex is
 * specified then the child is appended to the child array. Returns the
 * inserted child.
 * 
 * Parameters:
 * 
 * child - <mxCell> to be inserted or appended to the child array.
 * index - Optional integer that specifies the index at which the child
 * should be inserted into the child array.
 */
mxCell.prototype.insert = function(child, index)
{
	if (child != null)
	{
		if (index == null)
		{
			index = this.getChildCount();
			
			if (child.getParent() == this)
			{
				index--;
			}
		}

		child.removeFromParent();
		child.setParent(this);
		
		if (this.children == null)
		{
			this.children = [];
			this.children.push(child);
		}
		else
		{
			this.children.splice(index, 0, child);
		}
	}
	
	return child;
};

/**
 * Function: remove
 *
 * Removes the child at the specified index from the child array and
 * returns the child that was removed. Will remove the parent reference of
 * the child.
 * 
 * Parameters:
 * 
 * index - Integer that specifies the index of the child to be
 * removed.
 */
mxCell.prototype.remove = function(index)
{
	var child = null;
	
	if (this.children != null && index >= 0)
	{
		child = this.getChildAt(index);
		
		if (child != null)
		{
			this.children.splice(index, 1);
			child.setParent(null);
		}
	}
	
	return child;
};

/**
 * Function: removeFromParent
 *
 * Removes the cell from its parent.
 */
mxCell.prototype.removeFromParent = function()
{
	if (this.parent != null)
	{
		var index = this.parent.getIndex(this);
		this.parent.remove(index);
	}
};

/**
 * Function: getEdgeCount
 *
 * Returns the number of edges in the edge array.
 */
mxCell.prototype.getEdgeCount = function()
{
	return (this.edges == null) ? 0 : this.edges.length;
};

/**
 * Function: getEdgeIndex
 *
 * Returns the index of the specified edge in <edges>.
 * 
 * Parameters:
 * 
 * edge - <mxCell> whose index in <edges> should be returned.
 */
mxCell.prototype.getEdgeIndex = function(edge)
{
	return mxUtils.indexOf(this.edges, edge);
};

/**
 * Function: getEdgeAt
 *
 * Returns the edge at the specified index in <edges>.
 * 
 * Parameters:
 * 
 * index - Integer that specifies the index of the edge to be returned.
 */
mxCell.prototype.getEdgeAt = function(index)
{
	return (this.edges == null) ? null : this.edges[index];
};

/**
 * Function: insertEdge
 *
 * Inserts the specified edge into the edge array and returns the edge.
 * Will update the respective terminal reference of the edge.
 * 
 * Parameters:
 * 
 * edge - <mxCell> to be inserted into the edge array.
 * isOutgoing - Boolean that specifies if the edge is outgoing.
 */
mxCell.prototype.insertEdge = function(edge, isOutgoing)
{
	if (edge != null)
	{
		edge.removeFromTerminal(isOutgoing);
		edge.setTerminal(this, isOutgoing);
		
		// Checks the last edge first, as both ends of a loop are inserted
		// one after the other (looking up each loop in all edges of its
		// terminal made 40,000 loops take 5 s to open)
		if (this.edges == null ||
			edge.getTerminal(!isOutgoing) != this ||
			(this.edges[this.edges.length - 1] != edge &&
			mxUtils.indexOf(this.edges, edge) < 0))
		{
			if (this.edges == null)
			{
				this.edges = [];
			}
			
			this.edges.push(edge);
		}
	}
	
	return edge;
};

/**
 * Function: removeEdge
 *
 * Removes the specified edge from the edge array and returns the edge.
 * Will remove the respective terminal reference from the edge.
 * 
 * Parameters:
 * 
 * edge - <mxCell> to be removed from the edge array.
 * isOutgoing - Boolean that specifies if the edge is outgoing.
 */
mxCell.prototype.removeEdge = function(edge, isOutgoing)
{
	if (edge != null)
	{
		if (edge.getTerminal(!isOutgoing) != this &&
			this.edges != null)
		{
			var index = this.getEdgeIndex(edge);
			
			if (index >= 0)
			{
				this.edges.splice(index, 1);
			}
		}
		
		edge.setTerminal(null, isOutgoing);
	}
	
	return edge;
};

/**
 * Function: removeFromTerminal
 *
 * Removes the edge from its source or target terminal.
 * 
 * Parameters:
 * 
 * isSource - Boolean that specifies if the edge should be removed from its
 * source or target terminal.
 */
mxCell.prototype.removeFromTerminal = function(isSource)
{
	var terminal = this.getTerminal(isSource);
	
	if (terminal != null)
	{
		terminal.removeEdge(this, isSource);
	}
};

/**
 * Function: hasAttribute
 * 
 * Returns true if the user object is an XML node that contains the given
 * attribute.
 * 
 * Parameters:
 * 
 * name - Name of the attribute.
 */
mxCell.prototype.hasAttribute = function(name)
{
	var userObject = this.getValue();
	
	return (userObject != null &&
		userObject.nodeType == mxConstants.NODETYPE_ELEMENT && userObject.hasAttribute) ?
		userObject.hasAttribute(name) : userObject.getAttribute(name) != null;
};

/**
 * Function: getAttribute
 *
 * Returns the specified attribute from the user object if it is an XML
 * node.
 * 
 * Parameters:
 * 
 * name - Name of the attribute whose value should be returned.
 * defaultValue - Optional default value to use if the attribute has no
 * value.
 */
mxCell.prototype.getAttribute = function(name, defaultValue)
{
	var userObject = this.getValue();
	
	var val = (userObject != null &&
		userObject.nodeType == mxConstants.NODETYPE_ELEMENT) ?
		userObject.getAttribute(name) : null;
		
	return (val != null) ? val : defaultValue;
};

/**
 * Function: setAttribute
 *
 * Sets the specified attribute on the user object if it is an XML node.
 * 
 * Parameters:
 * 
 * name - Name of the attribute whose value should be set.
 * value - New value of the attribute.
 */
mxCell.prototype.setAttribute = function(name, value)
{
	var userObject = this.getValue();
	
	if (userObject != null &&
		userObject.nodeType == mxConstants.NODETYPE_ELEMENT)
	{
		userObject.setAttribute(name, value);
	}
};

/**
 * Function: clone
 *
 * Returns a clone of the cell. Uses <cloneValue> to clone
 * the user object. All fields in <mxTransient> are ignored
 * during the cloning.
 */
mxCell.prototype.clone = function()
{
	var clone = mxUtils.clone(this, this.mxTransient);
	clone.setValue(this.cloneValue());
	
	return clone;
};

/**
 * Function: cloneValue
 *
 * Returns a clone of the cell's user object.
 */
mxCell.prototype.cloneValue = function(value)
{
	value = (value != null) ? value : this.getValue();
	
	if (value != null)
	{
		if (typeof(value.clone) == 'function')
		{
			value = value.clone();
		}
		else if (!isNaN(value.nodeType))
		{
			value = value.cloneNode(true);
		}
	}
	
	return value;
};
;
/**
 * Copyright (c) 2006-2018, JGraph Holdings Ltd
 * Copyright (c) 2006-2018, draw.io AG
 */
/**
 * Class: mxGraphModel
 * 
 * Extends <mxEventSource> to implement a graph model. The graph model acts as
 * a wrapper around the cells which are in charge of storing the actual graph
 * datastructure. The model acts as a transactional wrapper with event
 * notification for all changes, whereas the cells contain the atomic
 * operations for updating the actual datastructure.
 * 
 * Layers:
 * 
 * The cell hierarchy in the model must have a top-level root cell which
 * contains the layers (typically one default layer), which in turn contain the
 * top-level cells of the layers. This means each cell is contained in a layer.
 * If no layers are required, then all new cells should be added to the default
 * layer.
 * 
 * Layers are useful for hiding and showing groups of cells, or for placing
 * groups of cells on top of other cells in the display. To identify a layer,
 * the <isLayer> function is used. It returns true if the parent of the given
 * cell is the root of the model.
 * 
 * Events:
 * 
 * See events section for more details. There is a new set of events for
 * tracking transactional changes as they happen. The events are called
 * startEdit for the initial beginUpdate, executed for each executed change
 * and endEdit for the terminal endUpdate. The executed event contains a
 * property called change which represents the change after execution.
 * 
 * Encoding the model:
 * 
 * To encode a graph model, use the following code:
 * 
 * (code)
 * var enc = new mxCodec();
 * var node = enc.encode(graph.getModel());
 * (end)
 * 
 * This will create an XML node that contains all the model information.
 * 
 * Encoding and decoding changes:
 * 
 * For the encoding of changes, a graph model listener is required that encodes
 * each change from the given array of changes.
 * 
 * (code)
 * model.addListener(mxEvent.CHANGE, function(sender, evt)
 * {
 *   var changes = evt.getProperty('edit').changes;
 *   var nodes = [];
 *   var codec = new mxCodec();
 * 
 *   for (var i = 0; i < changes.length; i++)
 *   {
 *     nodes.push(codec.encode(changes[i]));
 *   }
 *   // do something with the nodes
 * });
 * (end)
 * 
 * For the decoding and execution of changes, the codec needs a lookup function
 * that allows it to resolve cell IDs as follows:
 * 
 * (code)
 * var codec = new mxCodec();
 * codec.lookup = function(id)
 * {
 *   return model.getCell(id);
 * }
 * (end)
 * 
 * For each encoded change (represented by a node), the following code can be
 * used to carry out the decoding and create a change object.
 * 
 * (code)
 * var changes = [];
 * var change = codec.decode(node);
 * change.model = model;
 * change.execute();
 * changes.push(change);
 * (end)
 * 
 * The changes can then be dispatched using the model as follows.
 * 
 * (code)
 * var edit = new mxUndoableEdit(model, false);
 * edit.changes = changes;
 * 
 * edit.notify = function()
 * {
 *   edit.source.fireEvent(new mxEventObject(mxEvent.CHANGE,
 *   	'edit', edit, 'changes', edit.changes));
 *   edit.source.fireEvent(new mxEventObject(mxEvent.NOTIFY,
 *   	'edit', edit, 'changes', edit.changes));
 * }
 * 
 * model.fireEvent(new mxEventObject(mxEvent.UNDO, 'edit', edit));
 * model.fireEvent(new mxEventObject(mxEvent.CHANGE,
 * 		'edit', edit, 'changes', changes));
 * (end)
 *
 * Event: mxEvent.CHANGE
 *
 * Fires when an undoable edit is dispatched. The <code>edit</code> property
 * contains the <mxUndoableEdit>. The <code>changes</code> property contains
 * the array of atomic changes inside the undoable edit. The changes property
 * is <strong>deprecated</strong>, please use edit.changes instead.
 *
 * Example:
 * 
 * For finding newly inserted cells, the following code can be used:
 * 
 * (code)
 * graph.model.addListener(mxEvent.CHANGE, function(sender, evt)
 * {
 *   var changes = evt.getProperty('edit').changes;
 * 
 *   for (var i = 0; i < changes.length; i++)
 *   {
 *     var change = changes[i];
 *     
 *     if (change instanceof mxChildChange &&
 *       change.change.previous == null)
 *     {
 *       graph.startEditingAtCell(change.child);
 *       break;
 *     }
 *   }
 * });
 * (end)
 * 
 * 
 * Event: mxEvent.NOTIFY
 *
 * Same as <mxEvent.CHANGE>, this event can be used for classes that need to
 * implement a sync mechanism between this model and, say, a remote model. In
 * such a setup, only local changes should trigger a notify event and all
 * changes should trigger a change event.
 * 
 * Event: mxEvent.EXECUTE
 * 
 * Fires between begin- and endUpdate and after an atomic change was executed
 * in the model. The <code>change</code> property contains the atomic change
 * that was executed.
 * 
 * Event: mxEvent.EXECUTED
 * 
 * Fires between START_EDIT and END_EDIT after an atomic change was executed.
 * The <code>change</code> property contains the change that was executed.
 *
 * Event: mxEvent.BEGIN_UPDATE
 *
 * Fires after the <updateLevel> was incremented in <beginUpdate>. This event
 * contains no properties.
 * 
 * Event: mxEvent.START_EDIT
 *
 * Fires after the <updateLevel> was changed from 0 to 1. This event
 * contains no properties.
 * 
 * Event: mxEvent.END_UPDATE
 * 
 * Fires after the <updateLevel> was decreased in <endUpdate> but before any
 * notification or change dispatching. The <code>edit</code> property contains
 * the <currentEdit>.
 * 
 * Event: mxEvent.END_EDIT
 *
 * Fires after the <updateLevel> was changed from 1 to 0. This event
 * contains no properties.
 * 
 * Event: mxEvent.BEFORE_UNDO
 * 
 * Fires before the change is dispatched after the update level has reached 0
 * in <endUpdate>. The <code>edit</code> property contains the <curreneEdit>.
 * 
 * Event: mxEvent.UNDO
 * 
 * Fires after the change was dispatched in <endUpdate>. The <code>edit</code>
 * property contains the <currentEdit>.
 * 
 * Constructor: mxGraphModel
 * 
 * Constructs a new graph model. If no root is specified then a new root
 * <mxCell> with a default layer is created.
 * 
 * Parameters:
 * 
 * root - <mxCell> that represents the root cell.
 */
function mxGraphModel(root)
{
	this.currentEdit = this.createUndoableEdit();
	
	if (root != null)
	{
		this.setRoot(root);
	}
	else
	{
		this.clear();
	}
};

/**
 * Extends mxEventSource.
 */
mxGraphModel.prototype = new mxEventSource();
mxGraphModel.prototype.constructor = mxGraphModel;

/**
 * Variable: root
 * 
 * Holds the root cell, which in turn contains the cells that represent the
 * layers of the diagram as child cells. That is, the actual elements of the
 * diagram are supposed to live in the third generation of cells and below.
 */
mxGraphModel.prototype.root = null;

/**
 * Variable: cells
 * 
 * Maps from Ids to cells.
 */
mxGraphModel.prototype.cells = null;

/**
 * Variable: maintainEdgeParent
 * 
 * Specifies if edges should automatically be moved into the nearest common
 * ancestor of their terminals. Default is true.
 */
mxGraphModel.prototype.maintainEdgeParent = true;

/**
 * Variable: ignoreRelativeEdgeParent
 * 
 * Specifies if relative edge parents should be ignored for finding the nearest
 * common ancestors of an edge's terminals. Default is true.
 */
mxGraphModel.prototype.ignoreRelativeEdgeParent = true;

/**
 * Variable: createIds
 * 
 * Specifies if the model should automatically create Ids for new cells.
 * Default is true.
 */
mxGraphModel.prototype.createIds = true;

/**
 * Variable: prefix
 * 
 * Defines the prefix of new Ids. Default is an empty string.
 */
mxGraphModel.prototype.prefix = '';

/**
 * Variable: postfix
 * 
 * Defines the postfix of new Ids. Default is an empty string.
 */
mxGraphModel.prototype.postfix = '';

/**
 * Variable: nextId
 * 
 * Specifies the next Id to be created. Initial value is 0.
 */
mxGraphModel.prototype.nextId = 0;

/**
 * Variable: currentEdit
 * 
 * Holds the changes for the current transaction. If the transaction is
 * closed then a new object is created for this variable using
 * <createUndoableEdit>.
 */
mxGraphModel.prototype.currentEdit = null;

/**
 * Variable: updateLevel
 * 
 * Counter for the depth of nested transactions. Each call to <beginUpdate>
 * will increment this number and each call to <endUpdate> will decrement
 * it. When the counter reaches 0, the transaction is closed and the
 * respective events are fired. Initial value is 0.
 */
mxGraphModel.prototype.updateLevel = 0;

/**
 * Variable: endingUpdate
 * 
 * True if the program flow is currently inside endUpdate.
 */
mxGraphModel.prototype.endingUpdate = false;

/**
 * Function: clear
 *
 * Sets a new root using <createRoot>.
 */
mxGraphModel.prototype.clear = function()
{
	this.setRoot(this.createRoot());
};

/**
 * Function: isCreateIds
 *
 * Returns <createIds>.
 */
mxGraphModel.prototype.isCreateIds = function()
{
	return this.createIds;
};

/**
 * Function: setCreateIds
 *
 * Sets <createIds>.
 */
mxGraphModel.prototype.setCreateIds = function(value)
{
	this.createIds = value;
};

/**
 * Function: createRoot
 *
 * Creates a new root cell with a default layer (child 0).
 */
mxGraphModel.prototype.createRoot = function()
{
	var cell = new mxCell();
	cell.insert(new mxCell());
	
	return cell;
};

/**
 * Function: getCells
 *
 * Returns the cells in this model.
 */
mxGraphModel.prototype.getCells = function()
{
	var result = [];

	if (this.cells != null)
	{
		for (var key in this.cells)
		{
			result.push(this.cells[key]);
		}
	}

	return result;
};

/**
 * Function: getCellCount
 *
 * Returns the number of cells in the model.
 */
mxGraphModel.prototype.getCellCount = function()
{
	var count = 0;

	if (this.cells != null)
	{
		for (var key in this.cells)
		{
			count++;
		}
	}

	return count;
};

/**
 * Function: getCell
 *
 * Returns the <mxCell> for the specified Id or null if no cell can be
 * found for the given Id.
 *
 * Parameters:
 * 
 * id - A string representing the Id of the cell.
 */
mxGraphModel.prototype.getCell = function(id)
{
	return (this.cells != null) ? this.cells[id] : null;
};

/**
 * Function: filterCells
 * 
 * Returns the cells from the given array where the given filter function
 * returns true.
 */
mxGraphModel.prototype.filterCells = function(cells, filter)
{
	var result = null;
	
	if (cells != null)
	{
		result = [];
		
		for (var i = 0; i < cells.length; i++)
		{
			if (filter(cells[i]))
			{
				result.push(cells[i]);
			}
		}
	}
	
	return result;
};

/**
 * Function: getDescendants
 * 
 * Returns all descendants of the given cell and the cell itself in an array.
 * 
 * Parameters:
 * 
 * parent - <mxCell> whose descendants should be returned.
 */
mxGraphModel.prototype.getDescendants = function(parent)
{
	return this.filterDescendants(null, parent);
};

/**
 * Function: filterDescendants
 * 
 * Visits all cells recursively and applies the specified filter function
 * to each cell. If the function returns true then the cell is added
 * to the resulting array. The parent and result paramters are optional.
 * If parent is not specified then the recursion starts at <root>.
 * 
 * Example:
 * The following example extracts all vertices from a given model:
 * (code)
 * var filter = function(cell)
 * {
 * 	return model.isVertex(cell);
 * }
 * var vertices = model.filterDescendants(filter);
 * (end)
 * 
 * Parameters:
 * 
 * filter - JavaScript function that takes an <mxCell> as an argument
 * and returns a boolean.
 * parent - Optional <mxCell> that is used as the root of the recursion.
 * result - Optional array to add the cells to.
 */
mxGraphModel.prototype.filterDescendants = function(filter, parent, result)
{
	// Creates a new array for storing the result
	result = (result != null) ? result : [];

	// Recursion starts at the root of the model
	parent = parent || this.getRoot();
	
	// Checks if the filter returns true for the cell
	// and adds it to the result array
	if (filter == null || filter(parent))
	{
		result.push(parent);
	}
	
	// Visits the children of the cell and adds them to the same
	// array, which avoids copying the result for each child
	var childCount = this.getChildCount(parent);
	
	for (var i = 0; i < childCount; i++)
	{
		this.filterDescendants(filter, this.getChildAt(parent, i), result);
	}

	return result;
};

/**
 * Function: getRoot
 * 
 * Returns the root of the model or the topmost parent of the given cell.
 *
 * Parameters:
 * 
 * cell - Optional <mxCell> that specifies the child.
 */
mxGraphModel.prototype.getRoot = function(cell)
{
	var root = cell || this.root;
	
	if (cell != null)
	{
		while (cell != null)
		{
			root = cell;
			cell = this.getParent(cell);
		}
	}
	
	return root;
};

/**
 * Function: setRoot
 * 
 * Sets the <root> of the model using <mxRootChange> and adds the change to
 * the current transaction. This resets all datastructures in the model and
 * is the preferred way of clearing an existing model. Returns the new
 * root.
 * 
 * Example:
 * 
 * (code)
 * var root = new mxCell();
 * root.insert(new mxCell());
 * model.setRoot(root);
 * (end)
 *
 * Parameters:
 * 
 * root - <mxCell> that specifies the new root.
 */
mxGraphModel.prototype.setRoot = function(root)
{
	this.execute(new mxRootChange(this, root));
	
	return root;
};

/**
 * Function: rootChanged
 * 
 * Inner callback to change the root of the model and update the internal
 * datastructures, such as <cells> and <nextId>. Returns the previous root.
 *
 * Parameters:
 * 
 * root - <mxCell> that specifies the new root.
 */
mxGraphModel.prototype.rootChanged = function(root)
{
	var oldRoot = this.root;
	this.root = root;
	
	// Resets counters and datastructures
	this.nextId = 0;
	this.cells = null;
	this.cellAdded(root);
	
	return oldRoot;
};

/**
 * Function: isRoot
 * 
 * Returns true if the given cell is the root of the model and a non-null
 * value.
 *
 * Parameters:
 * 
 * cell - <mxCell> that represents the possible root.
 */
mxGraphModel.prototype.isRoot = function(cell)
{
	return cell != null && this.root == cell;
};

/**
 * Function: isLayer
 * 
 * Returns true if <isRoot> returns true for the parent of the given cell.
 *
 * Parameters:
 * 
 * cell - <mxCell> that represents the possible layer.
 */
mxGraphModel.prototype.isLayer = function(cell)
{
	return this.isRoot(this.getParent(cell));
};

/**
 * Function: isAncestor
 * 
 * Returns true if the given parent is an ancestor of the given child. Note 
 * returns true if child == parent.
 *
 * Parameters:
 * 
 * parent - <mxCell> that specifies the parent.
 * child - <mxCell> that specifies the child.
 */
mxGraphModel.prototype.isAncestor = function(parent, child)
{
	while (child != null && child != parent)
	{
		child = this.getParent(child);
	}
	
	return child == parent;
};

/**
 * Function: contains
 * 
 * Returns true if the model contains the given <mxCell>.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell.
 */
mxGraphModel.prototype.contains = function(cell)
{
	return this.isAncestor(this.root, cell);
};

/**
 * Function: getParent
 * 
 * Returns the parent of the given cell.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose parent should be returned.
 */
mxGraphModel.prototype.getParent = function(cell)
{
	return (cell != null) ? cell.getParent() : null;
};

/**
 * Function: add
 * 
 * Adds the specified child to the parent at the given index using
 * <mxChildChange> and adds the change to the current transaction. If no
 * index is specified then the child is appended to the parent's array of
 * children. Returns the inserted child.
 * 
 * Parameters:
 * 
 * parent - <mxCell> that specifies the parent to contain the child.
 * child - <mxCell> that specifies the child to be inserted.
 * index - Optional integer that specifies the index of the child.
 */
mxGraphModel.prototype.add = function(parent, child, index)
{
	if (child != parent && parent != null && child != null)
	{	
		// Appends the child if no index was specified
		if (index == null)
		{
			index = this.getChildCount(parent);
		}
		
		var parentChanged = parent != this.getParent(child);
		this.execute(new mxChildChange(this, parent, child, index));

		// Maintains the edges parents by moving the edges
		// into the nearest common ancestor of its terminals
		if (this.maintainEdgeParent && parentChanged)
		{
			this.updateEdgeParents(child);
		}
	}
	
	return child;
};

/**
 * Function: cellAdded
 * 
 * Inner callback to update <cells> when a cell has been added. This
 * implementation resolves collisions by creating new Ids. To change the
 * ID of a cell after it was inserted into the model, use the following
 * code:
 * 
 * (code
 * delete model.cells[cell.getId()];
 * cell.setId(newId);
 * model.cells[cell.getId()] = cell;
 * (end)
 *
 * If the change of the ID should be part of the command history, then the
 * cell should be removed from the model and a clone with the new ID should
 * be reinserted into the model instead.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell that has been added.
 */
mxGraphModel.prototype.cellAdded = function(cell)
{
	if (cell != null)
	{
		// Creates an Id for the cell if not Id exists
		if (cell.getId() == null && this.createIds)
		{
			cell.setId(this.createId(cell));
		}
		
		if (cell.getId() != null)
		{
			var collision = this.getCell(cell.getId());
			
			if (collision != cell)
			{	
				// Creates new Id for the cell
				// as long as there is a collision
				while (collision != null)
				{
					cell.setId(this.createId(cell));
					collision = this.getCell(cell.getId());
				}
				
				// Lazily creates the cells dictionary. Uses a null prototype
				// as cell IDs are untrusted so that IDs such as __proto__ or
				// constructor are stored as regular entries instead of being
				// looked up (getCell) or written as inherited object members.
				if (this.cells == null)
				{
					this.cells = Object.create(null);
				}

				this.cells[cell.getId()] = cell;
			}
		}
		
		// Makes sure IDs of deleted cells are not reused. Ignores IDs from 2^52:
		// createId can never count up to them, and from 2^53 nextId++ no longer
		// changes nextId so that the loop above would never find a free ID.
		if (mxUtils.isNumeric(cell.getId()) && cell.getId() < 4503599627370496)
		{
			this.nextId = Math.max(this.nextId, cell.getId());
		}
		
		// Recursively processes child cells
		var childCount = this.getChildCount(cell);
		
		for (var i=0; i<childCount; i++)
		{
			this.cellAdded(this.getChildAt(cell, i));
		}
	}
};

/**
 * Function: createId
 * 
 * Hook method to create an Id for the specified cell. This implementation
 * concatenates <prefix>, id and <postfix> to create the Id and increments
 * <nextId>. The cell is ignored by this implementation, but can be used in
 * overridden methods to prefix the Ids with eg. the cell type.
 *
 * Parameters:
 *
 * cell - <mxCell> to create the Id for.
 */
mxGraphModel.prototype.createId = function(cell)
{
	var id = this.nextId;
	this.nextId++;
	
	return this.prefix + id + this.postfix;
};

/**
 * Function: updateEdgeParents
 * 
 * Updates the parent for all edges that are connected to cell or one of
 * its descendants using <updateEdgeParent>.
 */
mxGraphModel.prototype.updateEdgeParents = function(cell, root)
{
	// Gets the topmost node of the hierarchy
	root = root || this.getRoot(cell);
	
	// Updates edges on children first
	var childCount = this.getChildCount(cell);
	
	for (var i = 0; i < childCount; i++)
	{
		var child = this.getChildAt(cell, i);
		this.updateEdgeParents(child, root);
	}
	
	// Updates the parents of all connected edges
	var edgeCount = this.getEdgeCount(cell);
	var edges = [];

	for (var i = 0; i < edgeCount; i++)
	{
		edges.push(this.getEdgeAt(cell, i));
	}
	
	for (var i = 0; i < edges.length; i++)
	{
		var edge = edges[i];
		
		// Updates edge parent if edge and child have
		// a common root node (does not need to be the
		// model root node)
		if (this.isAncestor(root, edge))
		{
			this.updateEdgeParent(edge, root);
		}
	}
};

/**
 * Function: updateEdgeParent
 *
 * Inner callback to update the parent of the specified <mxCell> to the
 * nearest-common-ancestor of its two terminals.
 *
 * Parameters:
 * 
 * edge - <mxCell> that specifies the edge.
 * root - <mxCell> that represents the current root of the model.
 */
mxGraphModel.prototype.updateEdgeParent = function(edge, root)
{
	var source = this.getTerminal(edge, true);
	var target = this.getTerminal(edge, false);
	var cell = null;
	
	// Uses the first non-relative descendants of the source terminal
	while (source != null && !this.isEdge(source) &&
		source.geometry != null && source.geometry.relative)
	{
		source = this.getParent(source);
	}
	
	// Uses the first non-relative descendants of the target terminal
	while (target != null && this.ignoreRelativeEdgeParent &&
		!this.isEdge(target) && target.geometry != null && 
		target.geometry.relative)
	{
		target = this.getParent(target);
	}
	
	if (this.isAncestor(root, source) && this.isAncestor(root, target))
	{
		if (source == target)
		{
			cell = this.getParent(source);
		}
		else
		{
			cell = this.getNearestCommonAncestor(source, target);
		}

		if (cell != null && (this.getParent(cell) != this.root ||
			this.isAncestor(cell, edge)) && this.getParent(edge) != cell)
		{
			var geo = this.getGeometry(edge);
			
			if (geo != null)
			{
				var origin1 = this.getOrigin(this.getParent(edge));
				var origin2 = this.getOrigin(cell);
				
				var dx = origin2.x - origin1.x;
				var dy = origin2.y - origin1.y;
				
				geo = geo.clone();
				geo.translate(-dx, -dy);
				this.setGeometry(edge, geo);
			}

			this.add(cell, edge, this.getChildCount(cell));
		}
	}
};

/**
 * Function: getOrigin
 * 
 * Returns the absolute, accumulated origin for the children inside the
 * given parent as an <mxPoint>.
 */
mxGraphModel.prototype.getOrigin = function(cell)
{
	var result = null;
	
	if (cell != null)
	{
		result = this.getOrigin(this.getParent(cell));
		
		if (!this.isEdge(cell))
		{
			var geo = this.getGeometry(cell);
			
			if (geo != null)
			{
				result.x += geo.x;
				result.y += geo.y;
			}
		}
	}
	else
	{
		result = new mxPoint();
	}
	
	return result;
};

/**
 * Function: getNearestCommonAncestor
 * 
 * Returns the nearest common ancestor for the specified cells.
 *
 * Parameters:
 * 
 * cell1 - <mxCell> that specifies the first cell in the tree.
 * cell2 - <mxCell> that specifies the second cell in the tree.
 */
mxGraphModel.prototype.getNearestCommonAncestor = function(cell1, cell2)
{
	if (cell1 != null && cell2 != null)
	{
		// Walks up from the same depth instead of comparing cell paths, which
		// look up the index of each ancestor in its parent (linear in the
		// number of siblings)
		var getDepth = mxUtils.bind(this, function(cell)
		{
			var depth = 0;

			for (var p = this.getParent(cell); p != null; p = this.getParent(p))
			{
				depth++;
			}

			return depth;
		});

		var depth1 = getDepth(cell1);
		var depth2 = getDepth(cell2);
		var deeper = (depth1 > depth2) ? cell1 : cell2;
		var cell = (depth1 > depth2) ? cell2 : cell1;

		// The result is the shallower cell or one of its ancestors and
		// must be an ancestor of the deeper cell (not the cell itself)
		for (var i = Math.abs(depth1 - depth2); i > 0; i--)
		{
			deeper = this.getParent(deeper);
		}

		if (depth1 == depth2)
		{
			cell = this.getParent(cell);
			deeper = this.getParent(deeper);
		}

		while (cell != null && cell != deeper)
		{
			cell = this.getParent(cell);
			deeper = this.getParent(deeper);
		}

		// Never returns the root
		if (cell != null && this.getParent(cell) != null)
		{
			return cell;
		}
	}

	return null;
};

/**
 * Function: remove
 * 
 * Removes the specified cell from the model using <mxChildChange> and adds
 * the change to the current transaction. This operation will remove the
 * cell and all of its children from the model. Returns the removed cell.
 *
 * Parameters:
 * 
 * cell - <mxCell> that should be removed.
 */
mxGraphModel.prototype.remove = function(cell)
{
	if (cell == this.root)
	{
		this.setRoot(null);
	}
	else if (this.getParent(cell) != null)
	{
		this.execute(new mxChildChange(this, null, cell));
	}
	
	return cell;
};

/**
 * Function: cellRemoved
 * 
 * Inner callback to update <cells> when a cell has been removed.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell that has been removed.
 */
mxGraphModel.prototype.cellRemoved = function(cell)
{
	if (cell != null && this.cells != null)
	{
		// Recursively processes child cells
		var childCount = this.getChildCount(cell);
		
		for (var i = childCount - 1; i >= 0; i--)
		{
			this.cellRemoved(this.getChildAt(cell, i));
		}
		
		// Removes the dictionary entry for the cell
		if (this.cells != null && cell.getId() != null)
		{
			delete this.cells[cell.getId()];
		}
	}
};

/**
 * Function: parentForCellChanged
 * 
 * Inner callback to update the parent of a cell using <mxCell.insert>
 * on the parent and return the previous parent.
 *
 * Parameters:
 * 
 * cell - <mxCell> to update the parent for.
 * parent - <mxCell> that specifies the new parent of the cell.
 * index - Optional integer that defines the index of the child
 * in the parent's child array.
 */
mxGraphModel.prototype.parentForCellChanged = function(cell, parent, index)
{
	var previous = this.getParent(cell);
	
	if (parent != null)
	{
		if (parent != previous || previous.getIndex(cell) != index)
		{
			parent.insert(cell, index);
		}
	}
	else if (previous != null)
	{
		var oldIndex = previous.getIndex(cell);
		previous.remove(oldIndex);
	}
	
	// Adds or removes the cell from the model
	var par = this.contains(parent);
	var pre = this.contains(previous);
	
	if (par && !pre)
	{
		this.cellAdded(cell);
	}
	else if (pre && !par)
	{
		this.cellRemoved(cell);
	}
	
	return previous;
};

/**
 * Function: getChildCount
 *
 * Returns the number of children in the given cell.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose number of children should be returned.
 */
mxGraphModel.prototype.getChildCount = function(cell)
{
	return (cell != null) ? cell.getChildCount() : 0;
};

/**
 * Function: getChildAt
 *
 * Returns the child of the given <mxCell> at the given index.
 * 
 * Parameters:
 * 
 * cell - <mxCell> that represents the parent.
 * index - Integer that specifies the index of the child to be returned.
 */
mxGraphModel.prototype.getChildAt = function(cell, index)
{
	return (cell != null) ? cell.getChildAt(index) : null;
};

/**
 * Function: getChildren
 * 
 * Returns all children of the given <mxCell> as an array of <mxCells>. The
 * return value should be only be read.
 *
 * Parameters:
 * 
 * cell - <mxCell> the represents the parent.
 */
mxGraphModel.prototype.getChildren = function(cell)
{
	return (cell != null) ? cell.children : null;
};
	
/**
 * Function: getChildVertices
 * 
 * Returns the child vertices of the given parent.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose child vertices should be returned.
 */
mxGraphModel.prototype.getChildVertices = function(parent)
{
	return this.getChildCells(parent, true, false);
};
		
/**
 * Function: getChildEdges
 * 
 * Returns the child edges of the given parent.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose child edges should be returned.
 */
mxGraphModel.prototype.getChildEdges = function(parent)
{
	return this.getChildCells(parent, false, true);
};

/**
 * Function: getChildCells
 * 
 * Returns the children of the given cell that are vertices and/or edges
 * depending on the arguments.
 *
 * Parameters:
 * 
 * cell - <mxCell> the represents the parent.
 * vertices - Boolean indicating if child vertices should be returned.
 * Default is false.
 * edges - Boolean indicating if child edges should be returned.
 * Default is false.
 */
mxGraphModel.prototype.getChildCells = function(parent, vertices, edges)
{
	vertices = (vertices != null) ? vertices : false;
	edges = (edges != null) ? edges : false;
	
	var childCount = this.getChildCount(parent);
	var result = [];

	for (var i = 0; i < childCount; i++)
	{
		var child = this.getChildAt(parent, i);

		if ((!edges && !vertices) || (edges && this.isEdge(child)) ||
			(vertices && this.isVertex(child)))
		{
			result.push(child);
		}
	}

	return result;
};
		
/**
 * Function: getTerminal
 * 
 * Returns the source or target <mxCell> of the given edge depending on the
 * value of the boolean parameter.
 *
 * Parameters:
 * 
 * edge - <mxCell> that specifies the edge.
 * isSource - Boolean indicating which end of the edge should be returned.
 */
mxGraphModel.prototype.getTerminal = function(edge, isSource)
{
	return (edge != null) ? edge.getTerminal(isSource) : null;
};

/**
 * Function: setTerminal
 * 
 * Sets the source or target terminal of the given <mxCell> using
 * <mxTerminalChange> and adds the change to the current transaction.
 * This implementation updates the parent of the edge using <updateEdgeParent>
 * if required.
 *
 * Parameters:
 * 
 * edge - <mxCell> that specifies the edge.
 * terminal - <mxCell> that specifies the new terminal.
 * isSource - Boolean indicating if the terminal is the new source or
 * target terminal of the edge.
 */
mxGraphModel.prototype.setTerminal = function(edge, terminal, isSource)
{
	var terminalChanged = terminal != this.getTerminal(edge, isSource);
	this.execute(new mxTerminalChange(this, edge, terminal, isSource));
	
	if (this.maintainEdgeParent && terminalChanged)
	{
		this.updateEdgeParent(edge, this.getRoot());
	}
	
	return terminal;
};
	
/**
 * Function: setTerminals
 * 
 * Sets the source and target <mxCell> of the given <mxCell> in a single
 * transaction using <setTerminal> for each end of the edge.
 *
 * Parameters:
 * 
 * edge - <mxCell> that specifies the edge.
 * source - <mxCell> that specifies the new source terminal.
 * target - <mxCell> that specifies the new target terminal.
 */
mxGraphModel.prototype.setTerminals = function(edge, source, target)
{
	this.beginUpdate();
	try
	{
		this.setTerminal(edge, source, true);
		this.setTerminal(edge, target, false);
	}
	finally
	{
		this.endUpdate();
	}
};

/**
 * Function: terminalForCellChanged
 * 
 * Inner helper function to update the terminal of the edge using
 * <mxCell.insertEdge> and return the previous terminal.
 * 
 * Parameters:
 * 
 * edge - <mxCell> that specifies the edge to be updated.
 * terminal - <mxCell> that specifies the new terminal.
 * isSource - Boolean indicating if the terminal is the new source or
 * target terminal of the edge.
 */
mxGraphModel.prototype.terminalForCellChanged = function(edge, terminal, isSource)
{
	var previous = this.getTerminal(edge, isSource);
	
	if (terminal != null)
	{
		terminal.insertEdge(edge, isSource);
	}
	else if (previous != null)
	{
		previous.removeEdge(edge, isSource);
	}
	
	return previous;
};

/**
 * Function: getEdgeCount
 * 
 * Returns the number of distinct edges connected to the given cell.
 *
 * Parameters:
 * 
 * cell - <mxCell> that represents the vertex.
 */
mxGraphModel.prototype.getEdgeCount = function(cell)
{
	return (cell != null) ? cell.getEdgeCount() : 0;
};

/**
 * Function: getEdgeAt
 * 
 * Returns the edge of cell at the given index.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the vertex.
 * index - Integer that specifies the index of the edge
 * to return.
 */
mxGraphModel.prototype.getEdgeAt = function(cell, index)
{
	return (cell != null) ? cell.getEdgeAt(index) : null;
};
	
/**
 * Function: getDirectedEdgeCount
 * 
 * Returns the number of incoming or outgoing edges, ignoring the given
 * edge.
 * 
 * Parameters:
 * 
 * cell - <mxCell> whose edge count should be returned.
 * outgoing - Boolean that specifies if the number of outgoing or
 * incoming edges should be returned.
 * ignoredEdge - <mxCell> that represents an edge to be ignored.
 */
mxGraphModel.prototype.getDirectedEdgeCount = function(cell, outgoing, ignoredEdge)
{
	var count = 0;
	var edgeCount = this.getEdgeCount(cell);

	for (var i = 0; i < edgeCount; i++)
	{
		var edge = this.getEdgeAt(cell, i);

		if (edge != ignoredEdge && this.getTerminal(edge, outgoing) == cell)
		{
			count++;
		}
	}

	return count;
};

/**
 * Function: getConnections
 * 
 * Returns all edges of the given cell without loops.
 * 
 * Parameters:
 * 
 * cell - <mxCell> whose edges should be returned.
 * 
 */
mxGraphModel.prototype.getConnections = function(cell)
{
	return this.getEdges(cell, true, true, false);
};

/**
 * Function: getIncomingEdges
 * 
 * Returns the incoming edges of the given cell without loops.
 * 
 * Parameters:
 * 
 * cell - <mxCell> whose incoming edges should be returned.
 * 
 */
mxGraphModel.prototype.getIncomingEdges = function(cell)
{
	return this.getEdges(cell, true, false, false);
};

/**
 * Function: getOutgoingEdges
 * 
 * Returns the outgoing edges of the given cell without loops.
 * 
 * Parameters:
 * 
 * cell - <mxCell> whose outgoing edges should be returned.
 * 
 */
mxGraphModel.prototype.getOutgoingEdges = function(cell)
{
	return this.getEdges(cell, false, true, false);
};

/**
 * Function: getEdges
 * 
 * Returns all distinct edges connected to this cell as a new array of
 * <mxCells>. If at least one of incoming or outgoing is true, then loops
 * are ignored, otherwise if both are false, then all edges connected to
 * the given cell are returned including loops.
 * 
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell.
 * incoming - Optional boolean that specifies if incoming edges should be
 * returned. Default is true.
 * outgoing - Optional boolean that specifies if outgoing edges should be
 * returned. Default is true.
 * includeLoops - Optional boolean that specifies if loops should be returned.
 * Default is true. 
 */
mxGraphModel.prototype.getEdges = function(cell, incoming, outgoing, includeLoops)
{
	incoming = (incoming != null) ? incoming : true;
	outgoing = (outgoing != null) ? outgoing : true;
	includeLoops = (includeLoops != null) ? includeLoops : true;
	
	var edgeCount = this.getEdgeCount(cell);
	var result = [];

	for (var i = 0; i < edgeCount; i++)
	{
		var edge = this.getEdgeAt(cell, i);
		var source = this.getTerminal(edge, true);
		var target = this.getTerminal(edge, false);

		if ((includeLoops && source == target) || ((source != target) && ((incoming && target == cell) ||
			(outgoing && source == cell))))
		{
			result.push(edge);
		}
	}

	return result;
};

/**
 * Function: getEdgesBetween
 * 
 * Returns all edges between the given source and target pair. If directed
 * is true, then only edges from the source to the target are returned,
 * otherwise, all edges between the two cells are returned.
 * 
 * Parameters:
 * 
 * source - <mxCell> that defines the source terminal of the edge to be
 * returned.
 * target - <mxCell> that defines the target terminal of the edge to be
 * returned.
 * directed - Optional boolean that specifies if the direction of the
 * edge should be taken into account. Default is false.
 */
mxGraphModel.prototype.getEdgesBetween = function(source, target, directed)
{
	directed = (directed != null) ? directed : false;
	
	var tmp1 = this.getEdgeCount(source);
	var tmp2 = this.getEdgeCount(target);
	
	// Assumes the source has less connected edges
	var terminal = source;
	var edgeCount = tmp1;
	
	// Uses the smaller array of connected edges
	// for searching the edge
	if (tmp2 < tmp1)
	{
		edgeCount = tmp2;
		terminal = target;
	}
	
	var result = [];
	
	// Checks if the edge is connected to the correct
	// cell and returns the first match
	for (var i = 0; i < edgeCount; i++)
	{
		var edge = this.getEdgeAt(terminal, i);
		var src = this.getTerminal(edge, true);
		var trg = this.getTerminal(edge, false);
		var directedMatch = (src == source) && (trg == target);
		var oppositeMatch = (trg == source) && (src == target);

		if (directedMatch || (!directed && oppositeMatch))
		{
			result.push(edge);
		}
	}
	
	return result;
};

/**
 * Function: getOpposites
 * 
 * Returns all opposite vertices wrt terminal for the given edges, only
 * returning sources and/or targets as specified. The result is returned
 * as an array of <mxCells>.
 * 
 * Parameters:
 * 
 * edges - Array of <mxCells> that contain the edges to be examined.
 * terminal - <mxCell> that specifies the known end of the edges.
 * sources - Boolean that specifies if source terminals should be contained
 * in the result. Default is true.
 * targets - Boolean that specifies if target terminals should be contained
 * in the result. Default is true.
 */
mxGraphModel.prototype.getOpposites = function(edges, terminal, sources, targets)
{
	sources = (sources != null) ? sources : true;
	targets = (targets != null) ? targets : true;
	
	var terminals = [];
	
	if (edges != null)
	{
		for (var i = 0; i < edges.length; i++)
		{
			var source = this.getTerminal(edges[i], true);
			var target = this.getTerminal(edges[i], false);
			
			// Checks if the terminal is the source of
			// the edge and if the target should be
			// stored in the result
			if (source == terminal && target != null && target != terminal && targets)
			{
				terminals.push(target);
			}
			
			// Checks if the terminal is the taget of
			// the edge and if the source should be
			// stored in the result
			else if (target == terminal && source != null && source != terminal && sources)
			{
				terminals.push(source);
			}
		}
	}
	
	return terminals;
};

/**
 * Function: getTopmostCells
 * 
 * Returns the topmost cells of the hierarchy in an array that contains no
 * descendants for each <mxCell> that it contains. Duplicates should be
 * removed in the cells array to improve performance.
 * 
 * Parameters:
 * 
 * cells - Array of <mxCells> whose topmost ancestors should be returned.
 */
mxGraphModel.prototype.getTopmostCells = function(cells)
{
	var dict = new mxDictionary();
	var tmp = [];
	
	for (var i = 0; i < cells.length; i++)
	{
		dict.put(cells[i], true);
	}
	
	for (var i = 0; i < cells.length; i++)
	{
		var cell = cells[i];
		var topmost = true;
		var parent = this.getParent(cell);
		
		while (parent != null)
		{
			if (dict.get(parent))
			{
				topmost = false;
				break;
			}
			
			parent = this.getParent(parent);
		}
		
		if (topmost)
		{
			tmp.push(cell);
		}
	}
	
	return tmp;
};

/**
 * Function: isVertex
 * 
 * Returns true if the given cell is a vertex.
 *
 * Parameters:
 * 
 * cell - <mxCell> that represents the possible vertex.
 */
mxGraphModel.prototype.isVertex = function(cell)
{
	return (cell != null) ? cell.isVertex() : false;
};

/**
 * Function: isEdge
 * 
 * Returns true if the given cell is an edge.
 *
 * Parameters:
 * 
 * cell - <mxCell> that represents the possible edge.
 */
mxGraphModel.prototype.isEdge = function(cell)
{
	return (cell != null) ? cell.isEdge() : false;
};

/**
 * Function: isConnectable
 * 
 * Returns true if the given <mxCell> is connectable. If <edgesConnectable>
 * is false, then this function returns false for all edges else it returns
 * the return value of <mxCell.isConnectable>.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose connectable state should be returned.
 */
mxGraphModel.prototype.isConnectable = function(cell)
{
	return (cell != null) ? cell.isConnectable() : false;
};

/**
 * Function: getValue
 * 
 * Returns the user object of the given <mxCell> using <mxCell.getValue>.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose user object should be returned.
 */
mxGraphModel.prototype.getValue = function(cell)
{
	return (cell != null) ? cell.getValue() : null;
};

/**
 * Function: setValue
 * 
 * Sets the user object of then given <mxCell> using <mxValueChange>
 * and adds the change to the current transaction.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose user object should be changed.
 * value - Object that defines the new user object.
 */
mxGraphModel.prototype.setValue = function(cell, value)
{
	this.execute(new mxValueChange(this, cell, value));
	
	return value;
};

/**
 * Function: valueForCellChanged
 * 
 * Inner callback to update the user object of the given <mxCell>
 * using <mxCell.valueChanged> and return the previous value,
 * that is, the return value of <mxCell.valueChanged>.
 * 
 * To change a specific attribute in an XML node, the following code can be
 * used.
 * 
 * (code)
 * graph.getModel().valueForCellChanged = function(cell, value)
 * {
 *   var previous = cell.value.getAttribute('label');
 *   cell.value.setAttribute('label', value);
 *   
 *   return previous;
 * };
 * (end) 
 */
mxGraphModel.prototype.valueForCellChanged = function(cell, value)
{
	return cell.valueChanged(value);
};

/**
 * Function: getGeometry
 * 
 * Returns the <mxGeometry> of the given <mxCell>.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose geometry should be returned.
 */
mxGraphModel.prototype.getGeometry = function(cell)
{
	return (cell != null) ? cell.getGeometry() : null;
};

/**
 * Function: setGeometry
 * 
 * Sets the <mxGeometry> of the given <mxCell>. The actual update
 * of the cell is carried out in <geometryForCellChanged>. The
 * <mxGeometryChange> action is used to encapsulate the change.
 * 
 * Parameters:
 * 
 * cell - <mxCell> whose geometry should be changed.
 * geometry - <mxGeometry> that defines the new geometry.
 */
mxGraphModel.prototype.setGeometry = function(cell, geometry)
{
	if (geometry != this.getGeometry(cell))
	{
		this.execute(new mxGeometryChange(this, cell, geometry));
	}
	
	return geometry;
};

/**
 * Function: geometryForCellChanged
 * 
 * Inner callback to update the <mxGeometry> of the given <mxCell> using
 * <mxCell.setGeometry> and return the previous <mxGeometry>.
 */
mxGraphModel.prototype.geometryForCellChanged = function(cell, geometry)
{
	var previous = this.getGeometry(cell);
	cell.setGeometry(geometry);
	
	return previous;
};

/**
 * Function: getStyle
 * 
 * Returns the style of the given <mxCell>.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose style should be returned.
 */
mxGraphModel.prototype.getStyle = function(cell)
{
	return (cell != null) ? cell.getStyle() : null;
};

/**
 * Function: setStyle
 * 
 * Sets the style of the given <mxCell> using <mxStyleChange> and
 * adds the change to the current transaction.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose style should be changed.
 * style - String of the form [stylename;|key=value;] to specify
 * the new cell style.
 */
mxGraphModel.prototype.setStyle = function(cell, style)
{
	if (style != this.getStyle(cell))
	{
		this.execute(new mxStyleChange(this, cell, style));
	}
	
	return style;
};

/**
 * Function: styleForCellChanged
 * 
 * Inner callback to update the style of the given <mxCell>
 * using <mxCell.setStyle> and return the previous style.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell to be updated.
 * style - String of the form [stylename;|key=value;] to specify
 * the new cell style.
 */
mxGraphModel.prototype.styleForCellChanged = function(cell, style)
{
	var previous = this.getStyle(cell);
	cell.setStyle(style);
	
	return previous;
};

/**
 * Function: isCollapsed
 * 
 * Returns true if the given <mxCell> is collapsed.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose collapsed state should be returned.
 */
mxGraphModel.prototype.isCollapsed = function(cell)
{
	return (cell != null) ? cell.isCollapsed() : false;
};

/**
 * Function: setCollapsed
 * 
 * Sets the collapsed state of the given <mxCell> using <mxCollapseChange>
 * and adds the change to the current transaction.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose collapsed state should be changed.
 * collapsed - Boolean that specifies the new collpased state.
 */
mxGraphModel.prototype.setCollapsed = function(cell, collapsed)
{
	if (collapsed != this.isCollapsed(cell))
	{
		this.execute(new mxCollapseChange(this, cell, collapsed));
	}
	
	return collapsed;
};
	
/**
 * Function: collapsedStateForCellChanged
 *
 * Inner callback to update the collapsed state of the
 * given <mxCell> using <mxCell.setCollapsed> and return
 * the previous collapsed state.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell to be updated.
 * collapsed - Boolean that specifies the new collpased state.
 */
mxGraphModel.prototype.collapsedStateForCellChanged = function(cell, collapsed)
{
	var previous = this.isCollapsed(cell);
	cell.setCollapsed(collapsed);
	
	return previous;
};

/**
 * Function: isVisible
 * 
 * Returns true if the given <mxCell> is visible.
 * 
 * Parameters:
 * 
 * cell - <mxCell> whose visible state should be returned.
 */
mxGraphModel.prototype.isVisible = function(cell)
{
	return (cell != null) ? cell.isVisible() : false;
};

/**
 * Function: setVisible
 * 
 * Sets the visible state of the given <mxCell> using <mxVisibleChange> and
 * adds the change to the current transaction.
 *
 * Parameters:
 * 
 * cell - <mxCell> whose visible state should be changed.
 * visible - Boolean that specifies the new visible state.
 */
mxGraphModel.prototype.setVisible = function(cell, visible)
{
	if (visible != this.isVisible(cell))
	{
		this.execute(new mxVisibleChange(this, cell, visible));
	}
	
	return visible;
};
	
/**
 * Function: visibleStateForCellChanged
 *
 * Inner callback to update the visible state of the
 * given <mxCell> using <mxCell.setCollapsed> and return
 * the previous visible state.
 *
 * Parameters:
 * 
 * cell - <mxCell> that specifies the cell to be updated.
 * visible - Boolean that specifies the new visible state.
 */
mxGraphModel.prototype.visibleStateForCellChanged = function(cell, visible)
{
	var previous = this.isVisible(cell);
	cell.setVisible(visible);
	
	return previous;
};

/**
 * Function: execute
 * 
 * Executes the given edit and fires events if required. The edit object
 * requires an execute function which is invoked. The edit is added to the
 * <currentEdit> between <beginUpdate> and <endUpdate> calls, so that
 * events will be fired if this execute is an individual transaction, that
 * is, if no previous <beginUpdate> calls have been made without calling
 * <endUpdate>. This implementation fires an <execute> event before
 * executing the given change.
 * 
 * Parameters:
 * 
 * change - Object that described the change.
 */
mxGraphModel.prototype.execute = function(change)
{
	change.execute();
	this.beginUpdate();
	this.currentEdit.add(change);
	this.fireEvent(new mxEventObject(mxEvent.EXECUTE, 'change', change));
	// New global executed event
	this.fireEvent(new mxEventObject(mxEvent.EXECUTED, 'change', change));
	this.endUpdate();
};

/**
 * Function: beginUpdate
 * 
 * Increments the <updateLevel> by one. The event notification
 * is queued until <updateLevel> reaches 0 by use of
 * <endUpdate>.
 *
 * All changes on <mxGraphModel> are transactional,
 * that is, they are executed in a single undoable change
 * on the model (without transaction isolation).
 * Therefore, if you want to combine any
 * number of changes into a single undoable change,
 * you should group any two or more API calls that
 * modify the graph model between <beginUpdate>
 * and <endUpdate> calls as shown here:
 * 
 * (code)
 * var model = graph.getModel();
 * var parent = graph.getDefaultParent();
 * var index = model.getChildCount(parent);
 * model.beginUpdate();
 * try
 * {
 *   model.add(parent, v1, index);
 *   model.add(parent, v2, index+1);
 * }
 * finally
 * {
 *   model.endUpdate();
 * }
 * (end)
 * 
 * Of course there is a shortcut for appending a
 * sequence of cells into the default parent:
 * 
 * (code)
 * graph.addCells([v1, v2]).
 * (end)
 */
mxGraphModel.prototype.beginUpdate = function()
{
	this.updateLevel++;
	this.fireEvent(new mxEventObject(mxEvent.BEGIN_UPDATE));
	
	if (this.updateLevel == 1)
	{
		this.fireEvent(new mxEventObject(mxEvent.START_EDIT));
	}
};

/**
 * Function: endUpdate
 * 
 * Decrements the <updateLevel> by one and fires an <undo>
 * event if the <updateLevel> reaches 0. This function
 * indirectly fires a <change> event by invoking the notify
 * function on the <currentEdit> und then creates a new
 * <currentEdit> using <createUndoableEdit>.
 *
 * The <undo> event is fired only once per edit, whereas
 * the <change> event is fired whenever the notify
 * function is invoked, that is, on undo and redo of
 * the edit.
 */
mxGraphModel.prototype.endUpdate = function()
{
	this.updateLevel--;
	
	if (this.updateLevel == 0)
	{
		this.fireEvent(new mxEventObject(mxEvent.END_EDIT));
	}
	
	if (!this.endingUpdate)
	{
		this.endingUpdate = this.updateLevel == 0;
		this.fireEvent(new mxEventObject(mxEvent.END_UPDATE, 'edit', this.currentEdit));

		try
		{		
			if (this.endingUpdate && !this.currentEdit.isEmpty())
			{
				this.fireEvent(new mxEventObject(mxEvent.BEFORE_UNDO, 'edit', this.currentEdit));
				var tmp = this.currentEdit;
				this.currentEdit = this.createUndoableEdit();
				tmp.notify();
				this.fireEvent(new mxEventObject(mxEvent.UNDO, 'edit', tmp));
			}
		}
		finally
		{
			this.endingUpdate = false;
		}
	}
};

/**
 * Function: createUndoableEdit
 * 
 * Creates a new <mxUndoableEdit> that implements the
 * notify function to fire a <change> and <notify> event
 * through the <mxUndoableEdit>'s source.
 * 
 * Parameters:
 * 
 * significant - Optional boolean that specifies if the edit to be created is
 * significant. Default is true.
 */
mxGraphModel.prototype.createUndoableEdit = function(significant)
{
	var edit = new mxUndoableEdit(this, (significant != null) ? significant : true);
	
	edit.notify = function()
	{
		// LATER: Remove changes property (deprecated)
		edit.source.fireEvent(new mxEventObject(mxEvent.CHANGE,
			'edit', edit, 'changes', edit.changes));
		edit.source.fireEvent(new mxEventObject(mxEvent.NOTIFY,
			'edit', edit, 'changes', edit.changes));
	};
	
	return edit;
};

/**
 * Function: mergeChildren
 *
 * Deprecated. Merges the children of the given cell into the given target cell inside
 * this model. All cells are cloned unless there is a corresponding cell in
 * the model with the same id, in which case the source cell is ignored and
 * all edges are connected to the corresponding cell in this model. Edges
 * are considered to have no identity and are always cloned unless the
 * cloneAllEdges flag is set to false, in which case edges with the same
 * id in the target model are reconnected to reflect the terminals of the
 * source edges.
 */
mxGraphModel.prototype.mergeChildren = function(from, to, cloneAllEdges)
{
	mxUtils.deprecated('mxGraphModel.mergeChildren');
	cloneAllEdges = (cloneAllEdges != null) ? cloneAllEdges : true;
	
	this.beginUpdate();
	try
	{
		var mapping = new Object();
		this.mergeChildrenImpl(from, to, cloneAllEdges, mapping);
		
		// Post-processes all edges in the mapping and
		// reconnects the terminals to the corresponding
		// cells in the target model
		for (var key in mapping)
		{
			var cell = mapping[key];
			var terminal = this.getTerminal(cell, true);

			if (terminal != null)
			{
				terminal = mapping[mxCellPath.create(terminal)];
				this.setTerminal(cell, terminal, true);
			}
			
			terminal = this.getTerminal(cell, false);
			
			if (terminal != null)
			{
				terminal = mapping[mxCellPath.create(terminal)];
				this.setTerminal(cell, terminal, false);
			}
		}
	}
	finally
	{
		this.endUpdate();
	}
};

/**
 * Function: mergeChildrenImpl
 *
 * Deprecated. Clones the children of the source cell into the given target cell in
 * this model and adds an entry to the mapping that maps from the source
 * cell to the target cell with the same id or the clone of the source cell
 * that was inserted into this model.
 */
mxGraphModel.prototype.mergeChildrenImpl = function(from, to, cloneAllEdges, mapping)
{
	mxUtils.deprecated('mxGraphModel.mergeChildrenImpl');
	this.beginUpdate();
	try
	{
		var childCount = from.getChildCount();
		
		for (var i = 0; i < childCount; i++)
		{
			var cell = from.getChildAt(i);
			
			if (typeof(cell.getId) == 'function')
			{
				var id = cell.getId();
				var target = (id != null && (!this.isEdge(cell) || !cloneAllEdges)) ?
						this.getCell(id) : null;
				
				// Clones and adds the child if no cell exists for the id
				if (target == null)
				{
					var clone = cell.clone();
					clone.setId(id);
					
					// Sets the terminals from the original cell to the clone
					// because the lookup uses strings not cells in JS
					clone.setTerminal(cell.getTerminal(true), true);
					clone.setTerminal(cell.getTerminal(false), false);
					
					// Do *NOT* use model.add as this will move the edge away
					// from the parent in updateEdgeParent if maintainEdgeParent
					// is enabled in the target model
					target = to.insert(clone);
					this.cellAdded(target);
				}
				
				// Stores the mapping for later reconnecting edges
				mapping[mxCellPath.create(cell)] = target;
				
				// Recurses
				this.mergeChildrenImpl(cell, target, cloneAllEdges, mapping);
			}
		}
	}
	finally
	{
		this.endUpdate();
	}
};

/**
 * Function: getParents
 * 
 * Returns an array that represents the set (no duplicates) of all parents
 * for the given array of cells.
 * 
 * Parameters:
 * 
 * cells - Array of cells whose parents should be returned.
 */
mxGraphModel.prototype.getParents = function(cells)
{
	var parents = [];
	
	if (cells != null)
	{
		var dict = new mxDictionary();
		
		for (var i = 0; i < cells.length; i++)
		{
			var parent = this.getParent(cells[i]);
			
			if (parent != null && !dict.get(parent))
			{
				dict.put(parent, true);
				parents.push(parent);
			}
		}
	}
	
	return parents;
};

//
// Cell Cloning
//

/**
 * Function: cloneCell
 * 
 * Returns a deep clone of the given <mxCell> (including
 * the children) which is created using <cloneCells>.
 *
 * Parameters:
 * 
 * cell - <mxCell> to be cloned.
 * includeChildren - Optional boolean indicating if the cells should be cloned
 * with all descendants. Default is true.
 * identical - Optional boolean to keep the identity of cloned cells.
 * Default is false.
 */
mxGraphModel.prototype.cloneCell = function(cell, includeChildren, identical)
{
	if (cell != null)
	{
		return this.cloneCells([cell], includeChildren, null, identical)[0];
	}
	
	return null;
};

/**
 * Function: cloneCells
 * 
 * Returns an array of clones for the given array of <mxCells>.
 * Depending on the value of includeChildren, a deep clone is created for
 * each cell. Connections are restored based if the corresponding
 * cell is contained in the passed in array.
 *
 * Parameters:
 * 
 * cells - Array of <mxCell> to be cloned.
 * includeChildren - Optional boolean indicating if the cells should be cloned
 * with all descendants. Default is true.
 * mapping - Optional mapping for existing clones.
 * identical - Optional boolean to keep the cell IDs. Default is false.
 */
mxGraphModel.prototype.cloneCells = function(cells, includeChildren, mapping, identical)
{
	includeChildren = (includeChildren != null) ? includeChildren : true;
	mapping = (mapping != null) ? mapping : new Object();
	identical = (identical != null) ? identical : false;
	var clones = [];
	
	for (var i = 0; i < cells.length; i++)
	{
		if (cells[i] != null)
		{
			clones.push(this.cloneCellImpl(cells[i], mapping, includeChildren, identical));
		}
		else
		{
			clones.push(null);
		}
	}
	
	for (var i = 0; i < clones.length; i++)
	{
		if (clones[i] != null)
		{
			this.restoreClone(clones[i], cells[i], mapping);
		}
	}
	
	return clones;
};
			
/**
 * Function: cloneCellImpl
 * 
 * Inner helper method for cloning cells recursively.
 */
mxGraphModel.prototype.cloneCellImpl = function(cell, mapping, includeChildren, identical)
{
	var ident = mxObjectIdentity.get(cell);
	var clone = mapping[ident];
	
	if (clone == null)
	{
		clone = this.cellCloned(cell);
		mapping[ident] = clone;

		if (identical)
		{
			clone.id = cell.id;
		}

		if (includeChildren)
		{
			var childCount = this.getChildCount(cell);
			
			for (var i = 0; i < childCount; i++)
			{
				var cloneChild = this.cloneCellImpl(
					this.getChildAt(cell, i),
					mapping, true, identical);
				clone.insert(cloneChild);
			}
		}
	}
	
	return clone;
};

/**
 * Function: cellCloned
 * 
 * Hook for cloning the cell. This returns cell.clone() or
 * any possible exceptions.
 */
mxGraphModel.prototype.cellCloned = function(cell)
{
	return cell.clone();
};

/**
 * Function: restoreClone
 * 
 * Inner helper method for restoring the connections in
 * a network of cloned cells.
 */
mxGraphModel.prototype.restoreClone = function(clone, cell, mapping)
{
	var source = this.getTerminal(cell, true);
	
	if (source != null)
	{
		var tmp = mapping[mxObjectIdentity.get(source)];
		
		if (tmp != null)
		{
			tmp.insertEdge(clone, true);
		}
	}
	
	var target = this.getTerminal(cell, false);
	
	if (target != null)
	{
		var tmp = mapping[mxObjectIdentity.get(target)];
		
		if (tmp != null)
		{	
			tmp.insertEdge(clone, false);
		}
	}
	
	var childCount = this.getChildCount(clone);
	
	for (var i = 0; i < childCount; i++)
	{
		this.restoreClone(this.getChildAt(clone, i),
			this.getChildAt(cell, i), mapping);
	}
};

//
// Atomic changes
//

/**
 * Class: mxRootChange
 * 
 * Action to change the root in a model.
 *
 * Constructor: mxRootChange
 * 
 * Constructs a change of the root in the
 * specified model.
 */
function mxRootChange(model, root)
{
	this.model = model;
	this.root = root;
	this.previous = root;
};

/**
 * Function: execute
 * 
 * Carries out a change of the root using
 * <mxGraphModel.rootChanged>.
 */
mxRootChange.prototype.execute = function()
{
	this.root = this.previous;
	this.previous = this.model.rootChanged(this.previous);
};

/**
 * Class: mxChildChange
 * 
 * Action to add or remove a child in a model.
 *
 * Constructor: mxChildChange
 * 
 * Constructs a change of a child in the
 * specified model.
 */
function mxChildChange(model, parent, child, index)
{
	this.model = model;
	this.parent = parent;
	this.previous = parent;
	this.child = child;
	this.index = index;
	this.previousIndex = index;
};

/**
 * Function: execute
 * 
 * Changes the parent of <child> using
 * <mxGraphModel.parentForCellChanged> and
 * removes or restores the cell's
 * connections.
 */
mxChildChange.prototype.execute = function()
{
	if (this.child != null)
	{
		var tmp = this.model.getParent(this.child);
		var tmp2 = (tmp != null) ? tmp.getIndex(this.child) : 0;
		
		if (this.previous == null)
		{
			this.connect(this.child, false);
		}
		
		tmp = this.model.parentForCellChanged(
			this.child, this.previous, this.previousIndex);
			
		if (this.previous != null)
		{
			this.connect(this.child, true);
		}
		
		this.parent = this.previous;
		this.previous = tmp;
		this.index = this.previousIndex;
		this.previousIndex = tmp2;
	}
};

/**
 * Function: disconnect
 * 
 * Disconnects the given cell recursively from its
 * terminals and stores the previous terminal in the
 * cell's terminals.
 */
mxChildChange.prototype.connect = function(cell, isConnect)
{
	isConnect = (isConnect != null) ? isConnect : true;
	
	var source = cell.getTerminal(true);
	var target = cell.getTerminal(false);
	
	if (source != null)
	{
		if (isConnect)
		{
			this.model.terminalForCellChanged(cell, source, true);
		}
		else
		{
			this.model.terminalForCellChanged(cell, null, true);
		}
	}
	
	if (target != null)
	{
		if (isConnect)
		{
			this.model.terminalForCellChanged(cell, target, false);
		}
		else
		{
			this.model.terminalForCellChanged(cell, null, false);
		}
	}
	
	cell.setTerminal(source, true);
	cell.setTerminal(target, false);
	
	var childCount = this.model.getChildCount(cell);
	
	for (var i=0; i<childCount; i++)
	{
		this.connect(this.model.getChildAt(cell, i), isConnect);
	}
};

/**
 * Class: mxTerminalChange
 * 
 * Action to change a terminal in a model.
 *
 * Constructor: mxTerminalChange
 * 
 * Constructs a change of a terminal in the 
 * specified model.
 */
function mxTerminalChange(model, cell, terminal, source)
{
	this.model = model;
	this.cell = cell;
	this.terminal = terminal;
	this.previous = terminal;
	this.source = source;
};

/**
 * Function: execute
 * 
 * Changes the terminal of <cell> to <previous> using
 * <mxGraphModel.terminalForCellChanged>.
 */
mxTerminalChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		this.terminal = this.previous;
		this.previous = this.model.terminalForCellChanged(
			this.cell, this.previous, this.source);
	}
};

/**
 * Class: mxValueChange
 * 
 * Action to change a user object in a model.
 *
 * Constructor: mxValueChange
 * 
 * Constructs a change of a user object in the 
 * specified model.
 */
function mxValueChange(model, cell, value)
{
	this.model = model;
	this.cell = cell;
	this.value = value;
	this.previous = value;
};

/**
 * Function: execute
 * 
 * Changes the value of <cell> to <previous> using
 * <mxGraphModel.valueForCellChanged>.
 */
mxValueChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		this.value = this.previous;
		this.previous = this.model.valueForCellChanged(
			this.cell, this.previous);
	}
};

/**
 * Class: mxStyleChange
 * 
 * Action to change a cell's style in a model.
 *
 * Constructor: mxStyleChange
 * 
 * Constructs a change of a style in the
 * specified model.
 */
function mxStyleChange(model, cell, style)
{
	this.model = model;
	this.cell = cell;
	this.style = style;
	this.previous = style;
};

/**
 * Function: execute
 * 
 * Changes the style of <cell> to <previous> using
 * <mxGraphModel.styleForCellChanged>.
 */
mxStyleChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		this.style = this.previous;
		this.previous = this.model.styleForCellChanged(
			this.cell, this.previous);
	}
};

/**
 * Class: mxGeometryChange
 * 
 * Action to change a cell's geometry in a model.
 *
 * Constructor: mxGeometryChange
 * 
 * Constructs a change of a geometry in the
 * specified model.
 */
function mxGeometryChange(model, cell, geometry)
{
	this.model = model;
	this.cell = cell;
	this.geometry = geometry;
	this.previous = geometry;
};

/**
 * Function: execute
 * 
 * Changes the geometry of <cell> ro <previous> using
 * <mxGraphModel.geometryForCellChanged>.
 */
mxGeometryChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		this.geometry = this.previous;
		this.previous = this.model.geometryForCellChanged(
			this.cell, this.previous);
	}
};

/**
 * Class: mxCollapseChange
 * 
 * Action to change a cell's collapsed state in a model.
 *
 * Constructor: mxCollapseChange
 * 
 * Constructs a change of a collapsed state in the
 * specified model.
 */
function mxCollapseChange(model, cell, collapsed)
{
	this.model = model;
	this.cell = cell;
	this.collapsed = collapsed;
	this.previous = collapsed;
};

/**
 * Function: execute
 * 
 * Changes the collapsed state of <cell> to <previous> using
 * <mxGraphModel.collapsedStateForCellChanged>.
 */
mxCollapseChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		this.collapsed = this.previous;
		this.previous = this.model.collapsedStateForCellChanged(
			this.cell, this.previous);
	}
};

/**
 * Class: mxVisibleChange
 * 
 * Action to change a cell's visible state in a model.
 *
 * Constructor: mxVisibleChange
 * 
 * Constructs a change of a visible state in the
 * specified model.
 */
function mxVisibleChange(model, cell, visible)
{
	this.model = model;
	this.cell = cell;
	this.visible = visible;
	this.previous = visible;
};

/**
 * Function: execute
 * 
 * Changes the visible state of <cell> to <previous> using
 * <mxGraphModel.visibleStateForCellChanged>.
 */
mxVisibleChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		this.visible = this.previous;
		this.previous = this.model.visibleStateForCellChanged(
			this.cell, this.previous);
	}
};

/**
 * Class: mxCellAttributeChange
 * 
 * Action to change the attribute of a cell's user object.
 * There is no method on the graph model that uses this
 * action. To use the action, you can use the code shown
 * in the example below.
 * 
 * Example:
 * 
 * To change the attributeName in the cell's user object
 * to attributeValue, use the following code:
 * 
 * (code)
 * model.beginUpdate();
 * try
 * {
 *   var edit = new mxCellAttributeChange(
 *     cell, attributeName, attributeValue);
 *   model.execute(edit);
 * }
 * finally
 * {
 *   model.endUpdate();
 * } 
 * (end)
 *
 * Constructor: mxCellAttributeChange
 * 
 * Constructs a change of a attribute of the DOM node
 * stored as the value of the given <mxCell>.
 */
function mxCellAttributeChange(cell, attribute, value)
{
	this.cell = cell;
	this.attribute = attribute;
	this.value = value;
	this.previous = value;
};

/**
 * Function: execute
 * 
 * Changes the attribute of the cell's user object by
 * using <mxCell.setAttribute>.
 */
mxCellAttributeChange.prototype.execute = function()
{
	if (this.cell != null)
	{
		var tmp = this.cell.getAttribute(this.attribute);
		
		if (this.previous == null)
		{
			this.cell.value.removeAttribute(this.attribute);
		}
		else
		{
			this.cell.setAttribute(this.attribute, this.previous);
		}
		
		this.previous = tmp;
	}
};
