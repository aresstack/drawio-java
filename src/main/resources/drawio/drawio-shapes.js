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
 * Class: mxAbstractCanvas2D
 *
 * Base class for all canvases. A description of the public API is available in <mxXmlCanvas2D>.
 * All color values of <mxConstants.NONE> will be converted to null in the state.
 * 
 * Constructor: mxAbstractCanvas2D
 *
 * Constructs a new abstract canvas.
 */
function mxAbstractCanvas2D()
{
	/**
	 * Variable: converter
	 * 
	 * Holds the <mxUrlConverter> to convert image URLs.
	 */
	this.converter = this.createUrlConverter();
	
	this.reset();
};

/**
 * Variable: state
 * 
 * Holds the current state.
 */
mxAbstractCanvas2D.prototype.state = null;

/**
 * Variable: states
 * 
 * Stack of states.
 */
mxAbstractCanvas2D.prototype.states = null;

/**
 * Variable: path
 * 
 * Holds the current path as an array.
 */
mxAbstractCanvas2D.prototype.path = null;

/**
 * Variable: rotateHtml
 * 
 * Switch for rotation of HTML. Default is false.
 */
mxAbstractCanvas2D.prototype.rotateHtml = true;

/**
 * Variable: lastX
 * 
 * Holds the last x coordinate.
 */
mxAbstractCanvas2D.prototype.lastX = 0;

/**
 * Variable: lastY
 * 
 * Holds the last y coordinate.
 */
mxAbstractCanvas2D.prototype.lastY = 0;

/**
 * Variable: moveOp
 * 
 * Contains the string used for moving in paths. Default is 'M'.
 */
mxAbstractCanvas2D.prototype.moveOp = 'M';

/**
 * Variable: lineOp
 * 
 * Contains the string used for moving in paths. Default is 'L'.
 */
mxAbstractCanvas2D.prototype.lineOp = 'L';

/**
 * Variable: quadOp
 * 
 * Contains the string used for quadratic paths. Default is 'Q'.
 */
mxAbstractCanvas2D.prototype.quadOp = 'Q';

/**
 * Variable: curveOp
 * 
 * Contains the string used for bezier curves. Default is 'C'.
 */
mxAbstractCanvas2D.prototype.curveOp = 'C';

/**
 * Variable: closeOp
 * 
 * Holds the operator for closing curves. Default is 'Z'.
 */
mxAbstractCanvas2D.prototype.closeOp = 'Z';

/**
 * Variable: pointerEvents
 * 
 * Boolean value that specifies if events should be handled. Default is false.
 */
mxAbstractCanvas2D.prototype.pointerEvents = false;

/**
 * Function: createUrlConverter
 * 
 * Create a new <mxUrlConverter> and returns it.
 */
mxAbstractCanvas2D.prototype.createUrlConverter = function()
{
	return new mxUrlConverter();
};

/**
 * Function: reset
 * 
 * Resets the state of this canvas.
 */
mxAbstractCanvas2D.prototype.reset = function()
{
	this.state = this.createState();
	this.states = [];
};

/**
 * Function: createState
 * 
 * Creates the state of the this canvas.
 */
mxAbstractCanvas2D.prototype.createState = function()
{
	return {
		dx: 0,
		dy: 0,
		scale: 1,
		alpha: 1,
		fillAlpha: 1,
		strokeAlpha: 1,
		fillColor: null,
		gradientFillAlpha: 1,
		gradientColor: null,
		gradientAlpha: 1,
		gradientDirection: null,
		strokeColor: null,
		strokeWidth: 1,
		dashed: false,
		dashPattern: '3 3',
		fixDash: false,
		lineCap: 'flat',
		lineJoin: 'miter',
		miterLimit: 10,
		fontColor: '#000000',
		fontBackgroundColor: null,
		fontBorderColor: null,
		fontBorderWidth: 1,
		labelPadding: null,
		fontSize: mxConstants.DEFAULT_FONTSIZE,
		fontFamily: mxConstants.DEFAULT_FONTFAMILY,
		fontStyle: 0,
		shadow: false,
		shadowStyle: null,
		shadowColor: mxConstants.SHADOWCOLOR,
		shadowAlpha: mxConstants.SHADOW_OPACITY,
		shadowDx: mxConstants.SHADOW_OFFSET_X,
		shadowDy: mxConstants.SHADOW_OFFSET_Y,
		rotation: 0,
		rotationCx: 0,
		rotationCy: 0
	};
};

/**
 * Function: format
 * 
 * Rounds all numbers to integers.
 */
mxAbstractCanvas2D.prototype.format = function(value)
{
	return Math.round(parseFloat(value));
};

/**
 * Function: addOp
 * 
 * Adds the given operation to the path.
 */
mxAbstractCanvas2D.prototype.addOp = function()
{
	if (this.path != null)
	{
		this.path.push(arguments[0]);
		
		if (arguments.length > 2)
		{
			var s = this.state;

			for (var i = 2; i < arguments.length; i += 2)
			{
				this.lastX = arguments[i - 1];
				this.lastY = arguments[i];
				
				this.path.push(this.format((this.lastX + s.dx) * s.scale));
				this.path.push(this.format((this.lastY + s.dy) * s.scale));
			}
		}
	}
};

/**
 * Function: rotatePoint
 * 
 * Rotates the given point and returns the result as an <mxPoint>.
 */
mxAbstractCanvas2D.prototype.rotatePoint = function(x, y, theta, cx, cy)
{
	var rad = theta * (Math.PI / 180);
	
	return mxUtils.getRotatedPoint(new mxPoint(x, y), Math.cos(rad),
		Math.sin(rad), new mxPoint(cx, cy));
};

/**
 * Function: save
 * 
 * Saves the current state.
 */
mxAbstractCanvas2D.prototype.save = function()
{
	this.states.push(this.state);
	this.state = mxUtils.clone(this.state);
};

/**
 * Function: restore
 * 
 * Restores the current state.
 */
mxAbstractCanvas2D.prototype.restore = function()
{
	if (this.states.length > 0)
	{
		this.state = this.states.pop();
	}
};

/**
 * Function: setTitle
 * 
 * Sets the current title text. Hook for subclassers.
 */
mxAbstractCanvas2D.prototype.setTitle = function(title)
{
	// nop
};

/**
 * Function: setLink
 * 
 * Sets the current link. Hook for subclassers.
 */
mxAbstractCanvas2D.prototype.setLink = function(link, target)
{
	// nop
};

/**
 * Function: scale
 * 
 * Scales the current state.
 */
mxAbstractCanvas2D.prototype.scale = function(value)
{
	this.state.scale *= value;
	this.state.strokeWidth *= value;
};

/**
 * Function: translate
 * 
 * Translates the current state.
 */
mxAbstractCanvas2D.prototype.translate = function(dx, dy)
{
	this.state.dx += dx;
	this.state.dy += dy;
};

/**
 * Function: rotate
 * 
 * Rotates the current state.
 */
mxAbstractCanvas2D.prototype.rotate = function(theta, flipH, flipV, cx, cy)
{
	// nop
};

/**
 * Function: setAlpha
 * 
 * Sets the current alpha.
 */
mxAbstractCanvas2D.prototype.setAlpha = function(value)
{
	this.state.alpha = value;
};

/**
 * Function: setFillAlpha
 * 
 * Sets the current solid fill alpha.
 */
mxAbstractCanvas2D.prototype.setFillAlpha = function(value)
{
	this.state.fillAlpha = value;
};

/**
 * Function: setStrokeAlpha
 * 
 * Sets the current stroke alpha.
 */
mxAbstractCanvas2D.prototype.setStrokeAlpha = function(value)
{
	this.state.strokeAlpha = value;
};

/**
 * Function: setFillColor
 * 
 * Sets the current fill color. Colors that reference
 * resources are ignored (see <mxUtils.hasResourceReference>).
 */
mxAbstractCanvas2D.prototype.setFillColor = function(value)
{
	if (value == mxConstants.NONE || mxUtils.hasResourceReference(value))
	{
		value = null;
	}
	
	this.state.fillColor = value;
	this.state.gradientColor = null;
};

/**
 * Function: setFillStyle
 *
 * Sets the current fill style and the optional hachure gap, fill weight
 * and hachure angle that configure pattern fill styles.
 */
 mxAbstractCanvas2D.prototype.setFillStyle = function(value, hachureGap, fillWeight, hachureAngle)
 {
	 if (value == mxConstants.NONE)
	 {
		 value = null;
	 }

	 this.state.fillStyle = value;
	 this.state.hachureGap = hachureGap;
	 this.state.fillWeight = fillWeight;
	 this.state.hachureAngle = hachureAngle;
 };
 
/**
 * Function: setGradient
 * 
 * Sets the current gradient. Colors that reference resources are
 * ignored (see <mxUtils.hasResourceReference>).
 */
mxAbstractCanvas2D.prototype.setGradient = function(color1, color2, x, y, w, h, direction, alpha1, alpha2)
{
	var s = this.state;
	s.fillColor = (mxUtils.hasResourceReference(color1)) ? null : color1;
	s.gradientFillAlpha = (alpha1 != null) ? alpha1 : 1;
	s.gradientColor = (mxUtils.hasResourceReference(color2)) ? null : color2;
	s.gradientAlpha = (alpha2 != null) ? alpha2 : 1;
	s.gradientDirection = direction;
};

/**
 * Function: setStrokeColor
 * 
 * Sets the current stroke color. Colors that reference
 * resources are ignored (see <mxUtils.hasResourceReference>).
 */
mxAbstractCanvas2D.prototype.setStrokeColor = function(value)
{
	if (value == mxConstants.NONE || mxUtils.hasResourceReference(value))
	{
		value = null;
	}
	
	this.state.strokeColor = value;
};

/**
 * Function: setStrokeWidth
 * 
 * Sets the current stroke width.
 */
mxAbstractCanvas2D.prototype.setStrokeWidth = function(value)
{
	this.state.strokeWidth = value;
};

/**
 * Function: setDashed
 * 
 * Enables or disables dashed lines.
 */
mxAbstractCanvas2D.prototype.setDashed = function(value, fixDash)
{
	this.state.dashed = value;
	this.state.fixDash = fixDash;
};

/**
 * Function: setDashPattern
 * 
 * Sets the current dash pattern.
 */
mxAbstractCanvas2D.prototype.setDashPattern = function(value)
{
	this.state.dashPattern = value;
};

/**
 * Function: setLineCap
 * 
 * Sets the current line cap. Possible values are flat, round and square.
 * Default is flat.
 */
mxAbstractCanvas2D.prototype.setLineCap = function(value)
{
	this.state.lineCap = value;
};

/**
 * Function: setLineJoin
 * 
 * Sets the current line join. Possible values are arcs, bevel, miter,
 * miter-clip and round. Default is miter.
 */
mxAbstractCanvas2D.prototype.setLineJoin = function(value)
{
	this.state.lineJoin = value;
};

/**
 * Function: setMiterLimit
 * 
 * Sets the current miter limit.
 */
mxAbstractCanvas2D.prototype.setMiterLimit = function(value)
{
	this.state.miterLimit = value;
};

/**
 * Function: setFontColor
 * 
 * Sets the current font color. Colors that reference
 * resources are ignored (see <mxUtils.hasResourceReference>).
 */
mxAbstractCanvas2D.prototype.setFontColor = function(value)
{
	if (value == mxConstants.NONE || mxUtils.hasResourceReference(value))
	{
		value = null;
	}
	
	this.state.fontColor = value;
};

/**
 * Function: setFontBackgroundColor
 * 
 * Sets the current font background color. Colors that reference
 * resources are ignored (see <mxUtils.hasResourceReference>).
 */
mxAbstractCanvas2D.prototype.setFontBackgroundColor = function(value)
{
	if (value == mxConstants.NONE || mxUtils.hasResourceReference(value))
	{
		value = null;
	}
	
	this.state.fontBackgroundColor = value;
};

/**
 * Function: setLabelPadding
 *
 * Sets the current label padding, a CSS-style spacing shorthand of 1-4
 * space-separated numbers for the padding between the label text and
 * its background box (see <mxUtils.parseCssSpacing>).
 */
mxAbstractCanvas2D.prototype.setLabelPadding = function(value)
{
	this.state.labelPadding = mxUtils.parseCssSpacing(value);
};

/**
 * Function: setFontBorderColor
 *
 * Sets the current font border color. Colors that reference
 * resources are ignored (see <mxUtils.hasResourceReference>).
 */
mxAbstractCanvas2D.prototype.setFontBorderColor = function(value)
{
	if (value == mxConstants.NONE || mxUtils.hasResourceReference(value))
	{
		value = null;
	}
	
	this.state.fontBorderColor = value;
};

/**
 * Function: setFontBorderWidth
 *
 * Sets the width of the font border in pixels. Values that are not finite
 * numbers of at least 0 are ignored. Default is 1.
 */
mxAbstractCanvas2D.prototype.setFontBorderWidth = function(value)
{
	value = parseFloat(value);
	this.state.fontBorderWidth = (isFinite(value) && value >= 0) ? value : 1;
};

/**
 * Function: setFontSize
 * 
 * Sets the current font size.
 */
mxAbstractCanvas2D.prototype.setFontSize = function(value)
{
	this.state.fontSize = parseFloat(value);
};

/**
 * Function: setFontFamily
 * 
 * Sets the current font family.
 */
mxAbstractCanvas2D.prototype.setFontFamily = function(value)
{
	this.state.fontFamily = value;
};

/**
 * Function: setFontStyle
 * 
 * Sets the current font style.
 */
mxAbstractCanvas2D.prototype.setFontStyle = function(value)
{
	if (value == null)
	{
		value = 0;
	}
	
	this.state.fontStyle = value;
};

/**
 * Function: setShadow
 * 
 * Enables or disables and configures the current shadow.
 */
mxAbstractCanvas2D.prototype.setShadow = function(enabled, style)
{
	this.state.shadow = enabled;
	this.state.shadowStyle = style;
};

/**
 * Function: setShadowColor
 * 
 * Enables or disables and configures the current shadow.
 */
mxAbstractCanvas2D.prototype.setShadowColor = function(value)
{
	if (value == mxConstants.NONE)
	{
		value = null;
	}
	
	this.state.shadowColor = value;
};

/**
 * Function: setShadowAlpha
 * 
 * Enables or disables and configures the current shadow.
 */
mxAbstractCanvas2D.prototype.setShadowAlpha = function(value)
{
	this.state.shadowAlpha = value;
};

/**
 * Function: setShadowOffset
 * 
 * Enables or disables and configures the current shadow.
 */
mxAbstractCanvas2D.prototype.setShadowOffset = function(dx, dy)
{
	this.state.shadowDx = dx;
	this.state.shadowDy = dy;
};

/**
 * Function: begin
 * 
 * Starts a new path.
 */
mxAbstractCanvas2D.prototype.begin = function()
{
	this.lastX = 0;
	this.lastY = 0;
	this.path = [];
};

/**
 * Function: moveTo
 * 
 *  Moves the current path the given coordinates.
 */
mxAbstractCanvas2D.prototype.moveTo = function(x, y)
{
	this.addOp(this.moveOp, x, y);
};

/**
 * Function: lineTo
 * 
 * Draws a line to the given coordinates. Uses moveTo with the op argument.
 */
mxAbstractCanvas2D.prototype.lineTo = function(x, y)
{
	this.addOp(this.lineOp, x, y);
};

/**
 * Function: quadTo
 * 
 * Adds a quadratic curve to the current path.
 */
mxAbstractCanvas2D.prototype.quadTo = function(x1, y1, x2, y2)
{
	this.addOp(this.quadOp, x1, y1, x2, y2);
};

/**
 * Function: curveTo
 * 
 * Adds a bezier curve to the current path.
 */
mxAbstractCanvas2D.prototype.curveTo = function(x1, y1, x2, y2, x3, y3)
{
	this.addOp(this.curveOp, x1, y1, x2, y2, x3, y3);
};

/**
 * Function: arcTo
 * 
 * Adds the given arc to the current path. This is a synthetic operation that
 * is broken down into curves.
 */
mxAbstractCanvas2D.prototype.arcTo = function(rx, ry, angle, largeArcFlag, sweepFlag, x, y)
{
	var curves = mxUtils.arcToCurves(this.lastX, this.lastY, rx, ry, angle, largeArcFlag, sweepFlag, x, y);
	
	if (curves != null)
	{
		for (var i = 0; i < curves.length; i += 6) 
		{
			this.curveTo(curves[i], curves[i + 1], curves[i + 2],
				curves[i + 3], curves[i + 4], curves[i + 5]);
		}
	}
};

/**
 * Function: close
 * 
 * Closes the current path.
 */
mxAbstractCanvas2D.prototype.close = function(x1, y1, x2, y2, x3, y3)
{
	this.addOp(this.closeOp);
};

/**
 * Function: end
 * 
 * Empty implementation for backwards compatibility. This will be removed.
 */
mxAbstractCanvas2D.prototype.end = function() { };
;
/**
 * Copyright (c) 2006-2010, JGraph Holdings Ltd
 */
/**
 * Class: mxBpmnShape (DEPRECATED)
 *
 * Extends <mxShape> to implement an cylinder shape. If a
 * custom shape with one filled area and an overlay path is
 * needed, then this shape's <redrawPath> should be overridden.
 * This shape is registered under <mxConstants.SHAPE_CYLINDER>
 * in <mxCellRenderer>.
 * 
 * Constructor: mxBpmnShape
 *
 * Constructs a new cylinder shape.
 * 
 * Parameters:
 * 
 * bounds - <mxRectangle> that defines the bounds. This is stored in
 * <mxShape.bounds>.
 * fill - String that defines the fill color. This is stored in <fill>.
 * stroke - String that defines the stroke color. This is stored in <stroke>.
 * strokewidth - Optional integer that defines the stroke width. Default is
 * 1. This is stored in <strokewidth>.
 */
function mxBpmnShape(bounds, fill, stroke, strokewidth)
{
	mxShape.call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
};

/**
 * Extends mxShape.
 */
mxUtils.extend(mxBpmnShape, mxShape);

mxBpmnShape.prototype.customProperties = [
	{name: 'symbol', dispName: 'Event', type: 'enum', defVal:'general', 
		enumList: [{val: 'general', dispName: 'General'}, 
				   {val: 'message', dispName: 'Message'}, 
				   {val: 'timer', dispName: 'Timer'}, 
				   {val: 'escalation', dispName: 'Escalation'}, 
				   {val: 'conditional', dispName: 'Conditional'}, 
				   {val: 'link', dispName: 'Link'}, 
				   {val: 'error', dispName: 'Error'}, 
				   {val: 'cancel', dispName: 'Cancel'}, 
				   {val: 'compensation', dispName: 'Compensation'}, 
				   {val: 'signal', dispName: 'Signal'}, 
				   {val: 'multiple', dispName: 'Multiple'}, 
				   {val: 'parallelMultiple', dispName: 'Parallel Multiple'}, 
				   {val: 'terminate', dispName: 'Terminate'}, 
				   {val: 'exclusiveGw', dispName: 'Exclusive Gw'}, 
				   {val: 'parallelGw', dispName: 'Parallel Gw'}, 
				   {val: 'complexGw', dispName: 'Complex Gw'}]
	},
	{name: 'outline', dispName: 'Event Type', type: 'enum', defVal:'standard', 
		enumList: [{val: 'standard', dispName: 'Standard'}, 
				   {val: 'eventInt', dispName: 'Interrupting'}, 
				   {val: 'eventNonint', dispName: 'Non-Interrupting'}, 
				   {val: 'catching', dispName: 'Catching'}, 
				   {val: 'boundInt', dispName: 'Bound Interrupting'}, 
				   {val: 'boundNonint', dispName: 'Bound Non-Interrupting'}, 
				   {val: 'throwing', dispName: 'Throwing'}, 
				   {val: 'end', dispName: 'End'}, 
				   {val: 'none', dispName: 'None'}]
	},
	{name: 'background', dispName: 'Background', type: 'enum', defVal:'none',
		enumList: [{val: 'gateway', dispName: 'Gateway'}, 
				   {val: 'none', dispName: 'None'}]
}];

mxBpmnShape.prototype.eventTypeEnum = { 
		START_STANDARD : 'standard', 
		EVENT_SP_INT : 'eventInt', 
		EVENT_SP_NONINT : 'eventNonint',
		CATCHING : 'catching',
		BOUND_INT : 'boundInt',
		BOUND_NONINT : 'boundNonint',
		THROWING : 'throwing',
		END : 'end',
		NONE : 'none',
		GATEWAY : 'gateway'};

mxBpmnShape.prototype.eventEnum = { 
		GENERAL 		: 'general', 
		MESSAGE 		: 'message', 
		TIMER 			: 'timer', 
		ESCALATION 		: 'escalation', 
		CONDITIONAL 	: 'conditional', 
		LINK 			: 'link', 
		ERROR			: 'error', 
		CANCEL			: 'cancel', 
		COMPENSATION 	: 'compensation', 
		SIGNAL 			: 'signal', 
		MULTIPLE		: 'multiple', 
		PAR_MULTI		: 'parallelMultiple', 
		TERMINATE		: 'terminate',
		GW_EXCLUSIVE 	: 'exclusiveGw',
		GW_PARALLEL		: 'parallelGw',
		GW_COMPLEX		: 'complexGw'};

mxBpmnShape.prototype.miscEnum = {
		OUTLINE			: 'outline',
		BACKGROUND		: 'background',
		SYMBOL			: 'symbol',
		GATEWAY			: 'gateway'};

/**
 * Function: paintVertexShape
 * 
 * Paints the vertex shape.
 */
mxBpmnShape.prototype.paintVertexShape = function(c, x, y, w, h)
{
	this.redrawPath(c, x, y, w, h, mxBpmnShape.prototype.miscEnum.BACKGROUND);
	var bg = mxUtils.getValue(this.style, mxBpmnShape.prototype.miscEnum.BACKGROUND, mxBpmnShape.prototype.eventTypeEnum.NONE);

	if (bg === mxBpmnShape.prototype.eventTypeEnum.GATEWAY)
	{
		c.setShadow(false);
	}

	this.redrawPath(c, x, y, w, h, mxBpmnShape.prototype.miscEnum.OUTLINE);
	this.redrawPath(c, x, y, w, h, mxBpmnShape.prototype.miscEnum.SYMBOL);
}

/**
 * Function: redrawPath
 *
 * Draws the path for this shape.
 */
mxBpmnShape.prototype.redrawPath = function(c, x, y, w, h, layer)
{
	var bg = mxUtils.getValue(this.style, mxBpmnShape.prototype.miscEnum.BACKGROUND, mxBpmnShape.prototype.eventTypeEnum.NONE);

	if (layer == mxBpmnShape.prototype.miscEnum.BACKGROUND)
	{
		if (bg != null)
		{
			var f = this.backgrounds[bg];

			if (f != null)
			{
				c.translate(x, y);
				f.call(this, c, x, y, w, h, layer);
			}
		}
	}
	else if (layer == mxBpmnShape.prototype.miscEnum.OUTLINE)
	{
		if (bg === mxBpmnShape.prototype.eventTypeEnum.GATEWAY)
		{
			c.translate(w / 4, h / 4);
			h /= 2;
			w /= 2;
			
			//add rhombus connections here
			this.constraints = [
			                                      new mxConnectionConstraint(new mxPoint(0.5, 0), true),
			                                      new mxConnectionConstraint(new mxPoint(0.5, 1), true),
			                                      new mxConnectionConstraint(new mxPoint(0, 0.5), true),
			                                      new mxConnectionConstraint(new mxPoint(1, 0.5), true),
			                                      new mxConnectionConstraint(new mxPoint(0.25, 0.25), false),
			                                      new mxConnectionConstraint(new mxPoint(0.25, 0.75), false),
			                                      new mxConnectionConstraint(new mxPoint(0.75, 0.25), false),
			                                      new mxConnectionConstraint(new mxPoint(0.75, 0.75), false)
			                                      ];
		}
		else
		{
			//add ellipse connections here
			this.constraints = [
			                                      new mxConnectionConstraint(new mxPoint(0.5, 0), true),
			                                      new mxConnectionConstraint(new mxPoint(0.5, 1), true),
			                                      new mxConnectionConstraint(new mxPoint(0, 0.5), true),
			                                      new mxConnectionConstraint(new mxPoint(1, 0.5), true),
			                                      new mxConnectionConstraint(new mxPoint(0.145, 0.145), false),
			                                      new mxConnectionConstraint(new mxPoint(0.145, 0.855), false),
			                                      new mxConnectionConstraint(new mxPoint(0.855, 0.145), false),
			                                      new mxConnectionConstraint(new mxPoint(0.855, 0.855), false)
			                                      ];
		}

		var o = mxUtils.getValue(this.style, mxBpmnShape.prototype.miscEnum.OUTLINE, mxBpmnShape.prototype.eventTypeEnum.NONE);

		if (o != null)
		{
			var f = this.outlines[o];

			if (f != null)
			{
				f.call(this, c, x, y, w, h, bg === mxBpmnShape.prototype.eventTypeEnum.GATEWAY);
			}
		}
	}
	else if (layer == mxBpmnShape.prototype.miscEnum.SYMBOL)
	{
		if (bg === mxBpmnShape.prototype.eventTypeEnum.GATEWAY)
		{
			h /= 2;
			w /= 2;
		}

		var s = mxUtils.getValue(this.style, mxBpmnShape.prototype.miscEnum.SYMBOL, null);

		if (s != null)
		{
			var f = this.symbols[s];

			if (f != null)
			{
				var strokeColor = c.state.strokeColor;
				var fillColor = c.state.fillColor;
				var o = mxUtils.getValue(this.style, mxBpmnShape.prototype.miscEnum.OUTLINE, mxBpmnShape.prototype.eventTypeEnum.NONE);

				if (s === mxBpmnShape.prototype.eventEnum.MESSAGE)
				{
					c.translate(w * 0.15, h * 0.3);
					w = w * 0.7;
					h = h * 0.4;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.TIMER)
				{
					c.translate(w * 0.11, h * 0.11);
					w = w * 0.78;
					h = h * 0.78;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.ESCALATION)
				{
					c.translate(w * 0.19, h * 0.15);
					w = w * 0.62;
					h = h * 0.57;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.CONDITIONAL)
				{
					c.translate(w * 0.3, h * 0.16);
					w = w * 0.4;
					h = h * 0.68;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.LINK)
				{
					c.translate(w * 0.27, h * 0.33);
					w = w * 0.46;
					h = h * 0.34;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.ERROR)
				{
					c.translate(w * 0.212, h * 0.243);
					w = w * 0.58;
					h = h * 0.507;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.CANCEL)
				{
					c.translate(w * 0.22, h * 0.22);
					w = w * 0.56;
					h = h * 0.56;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.COMPENSATION)
				{
					c.translate(w * 0.28, h * 0.35);
					w = w * 0.44;
					h = h * 0.3;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.SIGNAL)
				{
					c.translate(w * 0.19, h * 0.15);
					w = w * 0.62;
					h = h * 0.57;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.MULTIPLE)
				{
					c.translate(w * 0.2, h * 0.19);
					w = w * 0.6;
					h = h * 0.565;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.PAR_MULTI)
				{
					c.translate(w * 0.2, h * 0.2);
					w = w * 0.6;
					h = h * 0.6;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.TERMINATE)
				{
					c.translate(w * 0.05, h * 0.05);
					w = w * 0.9;
					h = h * 0.9;
				}
				else if (s === mxBpmnShape.prototype.eventEnum.GW_EXCLUSIVE)
				{
					c.translate(w * 0.12, 0);
					w = w * 0.76;
				}
				
				var isInverse = false;
				
				if (s === 'star')
				{
					c.setFillColor(strokeColor);
				}
				else if (o === mxBpmnShape.prototype.eventTypeEnum.THROWING || o === mxBpmnShape.prototype.eventTypeEnum.END)
				{
					c.setStrokeColor(fillColor);
					c.setFillColor(strokeColor);
					isInverse = true;
				}

				f.call(this, c, x, y, w, h, layer, isInverse);
				
				if (s === 'star')
				{
					c.setFillColor(fillColor);
				}
				else if (o === mxBpmnShape.prototype.eventTypeEnum.THROWING || o === mxBpmnShape.prototype.eventTypeEnum.END)
				{
					c.setStrokeColor(strokeColor);
					c.setFillColor(fillColor);
				}
			}
		}
	}
};

//Contains all possible backgrounds
mxBpmnShape.prototype.backgrounds = {
		'none': function(c, x, y, w, h)
		{
		},
		'gateway': function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(w / 2, 0);
			c.lineTo(w, h / 2);
			c.lineTo(w / 2, h);
			c.lineTo(0, h / 2);
			c.close();
			c.fillAndStroke();
		}
};

//Contains all possible outlines
mxBpmnShape.prototype.outlines = {
		'none' : function(c, x, y, w, h, isGateway)
		{
			if (!isGateway)
			{
				c.setShadow(false);
			}
		},
		'standard': function(c, x, y, w, h, isGateway)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			if (!isGateway)
			{
				c.setShadow(false);
			}
		},
		'eventInt': function(c, x, y, w, h, isGateway)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			if (!isGateway)
			{
				c.setShadow(false);
			}
		},
		'eventNonint': function(c, x, y, w, h, isGateway)
		{
			var dashed = c.state.dashed;
			c.setDashed(true);
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();
			c.setDashed(dashed);

			if (!isGateway)
			{
				c.setShadow(false);
			}

		},
		'catching': function(c, x, y, w, h, isGateway)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			if (!isGateway)
			{
				c.setShadow(false);
			}

			var inset = 2;
			c.ellipse(inset, inset, w - 2 *inset, h - 2 * inset);
			c.stroke();
		},
		'boundInt': function(c, x, y, w, h, isGateway)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			if (!isGateway)
			{
				c.setShadow(false);
			}
			var inset = 2;
			c.ellipse(inset, inset, w - 2 *inset, h - 2 * inset);
			c.stroke();
		},
		'boundNonint': function(c, x, y, w, h, isGateway)
		{
			var dashed = c.state.dashed;
			c.setDashed(true);
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			if (!isGateway)
			{
				c.setShadow(false);
			}

			var inset = 2;
			c.ellipse(inset, inset, w - 2 *inset, h - 2 * inset);
			c.stroke();
			c.setDashed(dashed);
		},
		'throwing': function(c, x, y, w, h, isGateway)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			if (!isGateway)
			{
				c.setShadow(false);
			}

			var inset = 2;
			c.ellipse(w * 0.02 + inset, h * 0.02 + inset, w * 0.96 - 2 *inset, h * 0.96 - 2 * inset);
			c.stroke();
		},
		'end': function(c, x, y, w, h, isGateway)
		{
			var sw = c.state.strokeWidth;
			c.setStrokeWidth(sw * 3);
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();
			c.setStrokeWidth(sw);

			if (!isGateway)
			{
				c.setShadow(false);
			}
		}
};

//Contains all possible symbols
mxBpmnShape.prototype.symbols = {
		'general' : function(c, x, y, w, h)
		{
		},
		'message': function(c, x, y, w, h, layer, isInverse)
		{
			c.rect(0, 0, w, h);
			c.fillAndStroke();

			var fc = mxUtils.getValue(this.style, "fillColor", "none");

			if (fc === 'none')
			{
				if (isInverse)
				{
					c.setStrokeColor('#ffffff');
				}
			}
			
			c.begin();
			c.moveTo(0, 0);
			c.lineTo(w * 0.5, h * 0.5);
			c.lineTo(w, 0);
			c.stroke();
		},
		'timer' : function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.begin();
			c.moveTo(w * 0.5, 0);
			c.lineTo(w * 0.5, h * 0.0642);
			c.moveTo(w * 0.7484, h * 0.0654);
			c.lineTo(w * 0.7126, h * 0.1281);
			c.moveTo(w * 0.93, h * 0.2471);
			c.lineTo(w * 0.8673, h * 0.2854);
			c.moveTo(w, h * 0.5);
			c.lineTo(w * 0.9338, h * 0.5);
			c.moveTo(w * 0.93, h * 0.7509);
			c.lineTo(w * 0.8673, h * 0.7126);
			c.moveTo(w * 0.7484, h * 0.9326);
			c.lineTo(w * 0.7126, h * 0.8699);
			c.moveTo(w * 0.5, h * 0.9338);
			c.lineTo(w * 0.5, h);
			c.moveTo(w * 0.2496, h * 0.9325);
			c.lineTo(w * 0.2854, h * 0.8699);
			c.moveTo(w * 0.068, h * 0.7509);
			c.lineTo(w * 0.1307, h * 0.7126);
			c.moveTo(0, h * 0.5);
			c.lineTo(w * 0.0642, h * 0.5);
			c.moveTo(w * 0.068, h * 0.2471);
			c.lineTo(w * 0.1307, h * 0.2854);
			c.moveTo(w * 0.2496, h * 0.0654);
			c.lineTo(w * 0.2854, h * 0.1281);
			c.moveTo(w * 0.5246, h * 0.0706);
			c.lineTo(w * 0.5, h * 0.5);
			c.lineTo(w * 0.7804, h * 0.5118);	
			c.stroke();
		},
		'escalation' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(0, h);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w, h);
			c.lineTo(w * 0.5, h * 0.5);
			c.close();
			c.fillAndStroke();
		},
		'conditional' : function(c, x, y, w, h)
		{
			c.rect(0, 0, w, h);
			c.fillAndStroke();
			c.begin();
			c.moveTo(0, h * 0.1027);
			c.lineTo(w * 0.798, h * 0.1027);
			c.moveTo(0, h * 0.3669);
			c.lineTo(w * 0.798, h * 0.3669);
			c.moveTo(0, h * 0.6311);
			c.lineTo(w * 0.798, h * 0.6311);
			c.moveTo(0, h * 0.8953);
			c.lineTo(w * 0.798, h * 0.8953);
			c.stroke();
		},
		'link' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(0, h * 0.76);
			c.lineTo(0, h * 0.24);
			c.lineTo(w * 0.63, h * 0.24);
			c.lineTo(w * 0.63, 0);
			c.lineTo(w, h * 0.5);
			c.lineTo(w * 0.63, h);
			c.lineTo(w * 0.63, h * 0.76);
			c.close();
			c.fillAndStroke();
		},
		'error' : function(c, x, y, w, h, layer, isInverse)
		{
			c.begin();
			c.moveTo(0, h);
			c.lineTo(w * 0.3287, h * 0.123);
			c.lineTo(w * 0.6194, h * 0.6342);
			c.lineTo(w, 0);
			c.lineTo(w * 0.6625, h * 0.939);
			c.lineTo(w * 0.3717, h * 0.5064);
			c.close();
			if(isInverse)
			{
				c.fill();
			}
			else
			{
				c.fillAndStroke();
			}
		},
		'cancel' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(w * 0.1051, 0);
			c.lineTo(w * 0.5, h * 0.3738);
			c.lineTo(w * 0.8909, 0);
			c.lineTo(w, h * 0.1054);
			c.lineTo(w * 0.623, h * 0.5);
			c.lineTo(w, h * 0.8926);
			c.lineTo(w * 0.8909, h);
			c.lineTo(w * 0.5, h * 0.6242);
			c.lineTo(w * 0.1051, h);
			c.lineTo(0, h * 0.8926);
			c.lineTo(w * 0.373, h * 0.5);
			c.lineTo(0, h * 0.1054);
			c.close();
			c.fillAndStroke();
		},
		'compensation' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(0, h * 0.5);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w * 0.5, h);
			c.close();
			c.moveTo(w * 0.5, h * 0.5);
			c.lineTo(w, 0);
			c.lineTo(w, h);
			c.close();
			c.fillAndStroke();
		},
		'signal' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(0, h);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w, h);
			c.close();
			c.fillAndStroke();
		},
		'multiple' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(0, h * 0.39);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w, h * 0.39);
			c.lineTo(w * 0.815, h);
			c.lineTo(w * 0.185, h);
			c.close();
			c.fillAndStroke();
		},
		'parallelMultiple' : function(c, x, y, w, h)
		{
			c.begin();
			c.moveTo(w * 0.38, 0);
			c.lineTo(w * 0.62, 0);
			c.lineTo(w * 0.62, h * 0.38);
			c.lineTo(w, h * 0.38);
			c.lineTo(w, h * 0.62);
			c.lineTo(w * 0.62, h * 0.62);
			c.lineTo(w * 0.62, h);
			c.lineTo(w * 0.38, h);
			c.lineTo(w * 0.38, h * 0.62);
			c.lineTo(0, h * 0.62);
			c.lineTo(0, h * 0.38);
			c.lineTo(w * 0.38, h * 0.38);
			c.close();
			c.fillAndStroke();
		},
		'terminate' : function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();
		},
		'exclusiveGw' : function(c, x, y, w, h)
		{
			var strokeColor = c.state.strokeColor;
			var fillColor = c.state.fillColor;
			c.setStrokeColor(fillColor);
			c.setFillColor(strokeColor);

			c.begin();
			c.moveTo(w * 0.105, 0);
			c.lineTo(w * 0.5, h * 0.38);
			c.lineTo(w * 0.895, h * 0);
			c.lineTo(w, h * 0.11);
			c.lineTo(w * 0.6172, h * 0.5);
			c.lineTo(w, h * 0.89);
			c.lineTo(w * 0.895, h);
			c.lineTo(w * 0.5, h * 0.62);
			c.lineTo(w * 0.105, h);
			c.lineTo(0, h * 0.89);
			c.lineTo(w * 0.3808, h * 0.5);
			c.lineTo(0, h * 0.11);
			c.close();
			c.fillAndStroke();

			c.setStrokeColor(strokeColor);
			c.setFillColor(fillColor);

		},
		'parallelGw' : function(c, x, y, w, h)
		{
			var strokeColor = c.state.strokeColor;
			var fillColor = c.state.fillColor;
			c.setStrokeColor(fillColor);
			c.setFillColor(strokeColor);

			c.begin();
			c.moveTo(w * 0.38, 0);
			c.lineTo(w * 0.62, 0);
			c.lineTo(w * 0.62, h * 0.38);
			c.lineTo(w, h * 0.38);
			c.lineTo(w, h * 0.62);
			c.lineTo(w * 0.62, h * 0.62);
			c.lineTo(w * 0.62, h);
			c.lineTo(w * 0.38, h);
			c.lineTo(w * 0.38, h * 0.62);
			c.lineTo(0, h * 0.62);
			c.lineTo(0, h * 0.38);
			c.lineTo(w * 0.38, h * 0.38);
			c.close();
			c.fillAndStroke();

			c.setStrokeColor(strokeColor);
			c.setFillColor(fillColor);
		},
		'complexGw' : function(c, x, y, w, h)
		{
			var strokeColor = c.state.strokeColor;
			var fillColor = c.state.fillColor;
			c.setStrokeColor(fillColor);
			c.setFillColor(strokeColor);

			c.begin();
			c.moveTo(0, h * 0.44);
			c.lineTo(w * 0.36, h * 0.44);
			c.lineTo(w * 0.1, h * 0.18);
			c.lineTo(w * 0.18, h * 0.1);
			c.lineTo(w * 0.44, h * 0.36);
			c.lineTo(w * 0.44, 0);
			c.lineTo(w * 0.56, 0);
			c.lineTo(w * 0.56, h * 0.36);
			c.lineTo(w * 0.82, h * 0.1);
			c.lineTo(w * 0.90, h * 0.18);
			c.lineTo(w * 0.64, h * 0.44);
			c.lineTo(w, h * 0.44);
			c.lineTo(w, h * 0.56);
			c.lineTo(w * 0.64, h * 0.56);
			c.lineTo(w * 0.9, h * 0.82);
			c.lineTo(w * 0.82, h * 0.9);
			c.lineTo(w * 0.56, h * 0.64);
			c.lineTo(w * 0.56, h);
			c.lineTo(w * 0.44, h);
			c.lineTo(w * 0.44, h * 0.64);
			c.lineTo(w * 0.18, h * 0.9);
			c.lineTo(w * 0.1, h * 0.82);
			c.lineTo(w * 0.36, h * 0.56);
			c.lineTo(0, h * 0.56);
			c.close();
			c.fillAndStroke();

			c.setStrokeColor(strokeColor);
			c.setFillColor(fillColor);
		},
		'star': function(c, x, y, w, h)
		{
			c.translate(w / 5, h / 6);
			h *= 2 / 3;
			w *= 3 / 5;
			
			c.begin();
			c.moveTo(0, h / 4);
			c.lineTo(w / 3, h / 4);
			c.lineTo(w / 2, 0);
			c.lineTo(2 * w / 3, h / 4);
			c.lineTo(w, h / 4);
			c.lineTo(5 * w / 6, h / 2);
			c.lineTo(w, 3 * h / 4);
			c.lineTo(2 * w / 3, 3 * h / 4);
			c.lineTo(w / 2, h);
			c.lineTo(w / 3, 3 * h / 4);
			c.lineTo(0, 3 * h / 4);
			c.lineTo(w / 6, h / 2);
			c.close();
			c.fillAndStroke();
		}
};

mxCellRenderer.registerShape('mxgraph.bpmn.shape', mxBpmnShape);

//**********************************************************************************************************************************************************
//Send / Receive marker
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2SendMarker(bounds, fill, stroke, strokewidth)
{
	mxShape.call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
	this.dy = 0.5;
	this.dx = 0.5;
	this.notch = 0;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2SendMarker, mxActor);

mxShapeBpmn2SendMarker.prototype.cst = {
		SEND : 'mxgraph.bpmn.sendMarker'
};

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2SendMarker.prototype.paintVertexShape = function(c, x, y, w, h)
{
	c.translate(x, y);

	c.rect(0, 0, w, h);
	c.fillAndStroke();
	
	c.setShadow(false);

	c.begin();
	c.moveTo(0,0);
	c.lineTo(w * 0.5, h * 0.5);
	c.lineTo(w, 0);
	c.stroke();
};

mxCellRenderer.registerShape(mxShapeBpmn2SendMarker.prototype.cst.SEND, mxShapeBpmn2SendMarker);


// BPMN event shape
function mxShapeBpmnEvent(bounds, fill, stroke, strokewidth)
{
	mxShape.call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
};

/**
 * Extends mxShape.
 */
mxUtils.extend(mxShapeBpmnEvent, mxShape);

mxShapeBpmnEvent.prototype.eventTypeEnum = { 
		START_STANDARD : 'standard', 
		EVENT_SP_INT : 'eventInt', 
		EVENT_SP_NONINT : 'eventNonint',
		CATCHING : 'catching',
		BOUND_INT : 'boundInt',
		BOUND_NONINT : 'boundNonint',
		THROWING : 'throwing',
		END : 'end',
		NONE : 'none'};

mxShapeBpmnEvent.prototype.eventEnum = { 
		GENERAL 		: 'general', 
		MESSAGE 		: 'message', 
		TIMER 			: 'timer', 
		ESCALATION 		: 'escalation', 
		CONDITIONAL 	: 'conditional', 
		LINK 			: 'link', 
		ERROR			: 'error', 
		CANCEL			: 'cancel', 
		COMPENSATION 	: 'compensation', 
		SIGNAL 			: 'signal', 
		MULTIPLE		: 'multiple', 
		PAR_MULTI		: 'parallelMultiple', 
		TERMINATE		: 'terminate'};

mxShapeBpmnEvent.prototype.miscEnum = {
		OUTLINE			: 'outline',
		SYMBOL			: 'symbol'};

mxShapeBpmnEvent.prototype.customProperties = [
	{name: mxShapeBpmnEvent.prototype.miscEnum.SYMBOL, dispName: 'Event', type: 'enum', defVal:mxShapeBpmnEvent.prototype.eventEnum.GENERAL, 
		enumList: [{val: mxShapeBpmnEvent.prototype.eventEnum.GENERAL, dispName: 'General'}, 
			   	   {val: mxShapeBpmnEvent.prototype.eventEnum.MESSAGE, dispName: 'Message'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.TIMER, dispName: 'Timer'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.ESCALATION, dispName: 'Escalation'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.CONDITIONAL, dispName: 'Conditional'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.LINK, dispName: 'Link'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.ERROR, dispName: 'Error'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.CANCEL, dispName: 'Cancel'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.COMPENSATION, dispName: 'Compensation'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.SIGNAL, dispName: 'Signal'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.MULTIPLE, dispName: 'Multiple'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.PAR_MULTI, dispName: 'Parallel Multiple'}, 
				   {val: mxShapeBpmnEvent.prototype.eventEnum.TERMINATE, dispName: 'Terminate'}]
	},
	{name: mxShapeBpmnEvent.prototype.miscEnum.OUTLINE, dispName: 'Event Type', type: 'enum', defVal:mxShapeBpmnEvent.prototype.eventTypeEnum.START_STANDARD, 
		enumList: [{val: mxShapeBpmnEvent.prototype.eventTypeEnum.START_STANDARD, dispName: 'Standard'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.EVENT_SP_INT, dispName: 'Interrupting'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.EVENT_SP_NONINT, dispName: 'Non-Interrupting'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.CATCHING, dispName: 'Catching'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.BOUND_INT, dispName: 'Bound Interrupting'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.BOUND_NONINT, dispName: 'Bound Non-Interrupting'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.THROWING, dispName: 'Throwing'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.END, dispName: 'End'}, 
				   {val: mxShapeBpmnEvent.prototype.eventTypeEnum.NONE, dispName: 'None'}]
}];

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmnEvent.prototype.paintVertexShape = function(c, x, y, w, h)
{
	c.translate(x, y);
	this.strictDrawShape(c, 0, 0, w, h, null);
}

mxShapeBpmnEvent.prototype.strictDrawShape = function(c, x, y, w, h, outline, symbol)
{
	// draw event outline
	if (outline == null)
	{
		outline = mxUtils.getValue(this.style, this.miscEnum.OUTLINE, this.eventTypeEnum.NONE);
	}

	if (outline != null)
	{
		var f = mxShapeBpmnEvent.prototype.outlines[outline];

		if (f != null)
		{
			c.translate(x,y);
			f.call(this, c, x, y, w, h);
		}
	}
	
	// draw event symbol
	if (symbol == null)
	{
		symbol = mxUtils.getValue(this.style, this.miscEnum.SYMBOL, null);
	}

	if (symbol != null)
	{
		var f = mxShapeBpmnEvent.prototype.symbols[symbol];

		if (f != null)
		{
			var strokeColor = c.state.strokeColor;
			var fillColor = c.state.fillColor;

			if (symbol === this.eventEnum.MESSAGE)
			{
				c.translate(w * 0.15, h * 0.3);
				w = w * 0.7;
				h = h * 0.4;
			}
			else if (symbol === this.eventEnum.TIMER)
			{
				c.translate(w * 0.11, h * 0.11);
				w = w * 0.78;
				h = h * 0.78;
			}
			else if (symbol === this.eventEnum.ESCALATION)
			{
				c.translate(w * 0.19, h * 0.15);
				w = w * 0.62;
				h = h * 0.57;
			}
			else if (symbol === this.eventEnum.CONDITIONAL)
			{
				c.translate(w * 0.3, h * 0.16);
				w = w * 0.4;
				h = h * 0.68;
			}
			else if (symbol === this.eventEnum.LINK)
			{
				c.translate(w * 0.27, h * 0.33);
				w = w * 0.46;
				h = h * 0.34;
			}
			else if (symbol === this.eventEnum.ERROR)
			{
				c.translate(w * 0.212, h * 0.243);
				w = w * 0.58;
				h = h * 0.507;
			}
			else if (symbol === this.eventEnum.CANCEL)
			{
				c.translate(w * 0.22, h * 0.22);
				w = w * 0.56;
				h = h * 0.56;
			}
			else if (symbol === this.eventEnum.COMPENSATION)
			{
				c.translate(w * 0.28, h * 0.35);
				w = w * 0.44;
				h = h * 0.3;
			}
			else if (symbol === this.eventEnum.SIGNAL)
			{
				c.translate(w * 0.19, h * 0.15);
				w = w * 0.62;
				h = h * 0.57;
			}
			else if (symbol === this.eventEnum.MULTIPLE)
			{
				c.translate(w * 0.2, h * 0.19);
				w = w * 0.6;
				h = h * 0.565;
			}
			else if (symbol === this.eventEnum.PAR_MULTI)
			{
				c.translate(w * 0.2, h * 0.2);
				w = w * 0.6;
				h = h * 0.6;
			}
			else if (symbol === this.eventEnum.TERMINATE)
			{
				c.translate(w * 0.05, h * 0.05);
				w = w * 0.9;
				h = h * 0.9;
			}
			else if (symbol === this.eventEnum.GW_EXCLUSIVE)
			{
				c.translate(w * 0.12, 0);
				w = w * 0.76;
			}
			
			var isInverse = false;
			
			if (symbol === 'star')
			{
				c.setFillColor(strokeColor);
			}
			else if (outline === this.eventTypeEnum.THROWING || outline === this.eventTypeEnum.END)
			{
				c.setStrokeColor(fillColor);
				c.setFillColor(strokeColor);
				isInverse = true;
			}

			f.call(this, c, x, y, w, h, isInverse);
			
			if (symbol === 'star')
			{
				c.setFillColor(fillColor);
			}
			else if (outline === this.eventTypeEnum.THROWING || outline === this.eventTypeEnum.END)
			{
				c.setStrokeColor(strokeColor);
				c.setFillColor(fillColor);
			}
		}
	}
}

//Contains all possible outlines
mxShapeBpmnEvent.prototype.outlines = {
		'none' : function(c, x, y, w, h)
		{
				c.setShadow(false);
		},
		'standard': function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.setShadow(false);
		},
		'eventInt': function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.setShadow(false);
		},
		'eventNonint': function(c, x, y, w, h)
		{
			var dashed = c.state.dashed;
			c.setDashed(true);
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();
			c.setDashed(dashed);

			c.setShadow(false);
		},
		'catching': function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.setShadow(false);

			var inset = 2;
			c.ellipse(inset, inset, w - 2 *inset, h - 2 * inset);
			c.stroke();
		},
		'boundInt': function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.setShadow(false);
			
			var inset = 2;
			c.ellipse(inset, inset, w - 2 *inset, h - 2 * inset);
			c.stroke();
		},
		'boundNonint': function(c, x, y, w, h)
		{
			var dashed = c.state.dashed;
			c.setDashed(true);
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.setShadow(false);

			var inset = 2;
			c.ellipse(inset, inset, w - 2 *inset, h - 2 * inset);
			c.stroke();
			c.setDashed(dashed);
		},
		'throwing': function(c, x, y, w, h)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.setShadow(false);

			var inset = 2;
			c.ellipse(w * 0.02 + inset, h * 0.02 + inset, w * 0.96 - 2 *inset, h * 0.96 - 2 * inset);
			c.stroke();
		},
		'end': function(c, x, y, w, h)
		{
			var sw = c.state.strokeWidth;
			c.setStrokeWidth(sw * 3);
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();
			c.setStrokeWidth(sw);

			c.setShadow(false);
		}
};

//Contains all possible symbols
mxShapeBpmnEvent.prototype.symbols = {
		'general' : function(c, x, y, w, h, isInverse)
		{
		},
		'message': function(c, x, y, w, h, isInverse)
		{
			c.rect(0, 0, w, h);
			c.fillAndStroke();

			var fc = mxUtils.getValue(this.style, "fillColor", "none");

			if (fc === 'none')
			{
				if (isInverse)
				{
					c.setStrokeColor('#ffffff');
				}
			}
			
			c.begin();
			c.moveTo(0, 0);
			c.lineTo(w * 0.5, h * 0.5);
			c.lineTo(w, 0);
			c.stroke();
		},
		'timer' : function(c, x, y, w, h, isInverse)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();

			c.begin();
			c.moveTo(w * 0.5, 0);
			c.lineTo(w * 0.5, h * 0.0642);
			c.moveTo(w * 0.7484, h * 0.0654);
			c.lineTo(w * 0.7126, h * 0.1281);
			c.moveTo(w * 0.93, h * 0.2471);
			c.lineTo(w * 0.8673, h * 0.2854);
			c.moveTo(w, h * 0.5);
			c.lineTo(w * 0.9338, h * 0.5);
			c.moveTo(w * 0.93, h * 0.7509);
			c.lineTo(w * 0.8673, h * 0.7126);
			c.moveTo(w * 0.7484, h * 0.9326);
			c.lineTo(w * 0.7126, h * 0.8699);
			c.moveTo(w * 0.5, h * 0.9338);
			c.lineTo(w * 0.5, h);
			c.moveTo(w * 0.2496, h * 0.9325);
			c.lineTo(w * 0.2854, h * 0.8699);
			c.moveTo(w * 0.068, h * 0.7509);
			c.lineTo(w * 0.1307, h * 0.7126);
			c.moveTo(0, h * 0.5);
			c.lineTo(w * 0.0642, h * 0.5);
			c.moveTo(w * 0.068, h * 0.2471);
			c.lineTo(w * 0.1307, h * 0.2854);
			c.moveTo(w * 0.2496, h * 0.0654);
			c.lineTo(w * 0.2854, h * 0.1281);
			c.moveTo(w * 0.5246, h * 0.0706);
			c.lineTo(w * 0.5, h * 0.5);
			c.lineTo(w * 0.7804, h * 0.5118);	
			c.stroke();
		},
		'escalation' : function(c, x, y, w, h, isInverse)
		{
			c.setMiterLimit(6);
			c.begin();
			c.moveTo(0, h);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w, h);
			c.lineTo(w * 0.5, h * 0.5);
			c.close();
			
			if (isInverse)
			{
				c.fill();
			}
			else
			{
				c.fillAndStroke();
			}
		},
		'conditional' : function(c, x, y, w, h, isInverse)
		{
			c.rect(0, 0, w, h);
			c.fillAndStroke();
			c.begin();
			c.moveTo(0, h * 0.1027);
			c.lineTo(w * 0.798, h * 0.1027);
			c.moveTo(0, h * 0.3669);
			c.lineTo(w * 0.798, h * 0.3669);
			c.moveTo(0, h * 0.6311);
			c.lineTo(w * 0.798, h * 0.6311);
			c.moveTo(0, h * 0.8953);
			c.lineTo(w * 0.798, h * 0.8953);
			c.stroke();
		},
		'link' : function(c, x, y, w, h, isInverse)
		{
			c.begin();
			c.moveTo(0, h * 0.76);
			c.lineTo(0, h * 0.24);
			c.lineTo(w * 0.63, h * 0.24);
			c.lineTo(w * 0.63, 0);
			c.lineTo(w, h * 0.5);
			c.lineTo(w * 0.63, h);
			c.lineTo(w * 0.63, h * 0.76);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'error' : function(c, x, y, w, h, isInverse)
		{
			c.setMiterLimit(7);
			c.begin();
			c.moveTo(0, h);
			c.lineTo(w * 0.3287, h * 0.123);
			c.lineTo(w * 0.6194, h * 0.6342);
			c.lineTo(w, 0);
			c.lineTo(w * 0.6625, h * 0.939);
			c.lineTo(w * 0.3717, h * 0.5064);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'cancel' : function(c, x, y, w, h, isInverse)
		{
			c.begin();
			c.moveTo(w * 0.1051, 0);
			c.lineTo(w * 0.5, h * 0.3738);
			c.lineTo(w * 0.8909, 0);
			c.lineTo(w, h * 0.1054);
			c.lineTo(w * 0.623, h * 0.5);
			c.lineTo(w, h * 0.8926);
			c.lineTo(w * 0.8909, h);
			c.lineTo(w * 0.5, h * 0.6242);
			c.lineTo(w * 0.1051, h);
			c.lineTo(0, h * 0.8926);
			c.lineTo(w * 0.373, h * 0.5);
			c.lineTo(0, h * 0.1054);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'compensation' : function(c, x, y, w, h, isInverse)
		{
			c.setMiterLimit(1);
			c.begin();
			c.moveTo(0, h * 0.5);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w * 0.5, h);
			c.close();
			c.moveTo(w * 0.5, h * 0.5);
			c.lineTo(w, 0);
			c.lineTo(w, h);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'signal' : function(c, x, y, w, h, isInverse)
		{
			c.begin();
			c.moveTo(0, h);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w, h);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'multiple' : function(c, x, y, w, h, isInverse)
		{
			c.begin();
			c.moveTo(0, h * 0.39);
			c.lineTo(w * 0.5, 0);
			c.lineTo(w, h * 0.39);
			c.lineTo(w * 0.815, h);
			c.lineTo(w * 0.185, h);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'parallelMultiple' : function(c, x, y, w, h, isInverse)
		{
			c.begin();
			c.moveTo(w * 0.38, 0);
			c.lineTo(w * 0.62, 0);
			c.lineTo(w * 0.62, h * 0.38);
			c.lineTo(w, h * 0.38);
			c.lineTo(w, h * 0.62);
			c.lineTo(w * 0.62, h * 0.62);
			c.lineTo(w * 0.62, h);
			c.lineTo(w * 0.38, h);
			c.lineTo(w * 0.38, h * 0.62);
			c.lineTo(0, h * 0.62);
			c.lineTo(0, h * 0.38);
			c.lineTo(w * 0.38, h * 0.38);
			c.close();
			
			isInverse ? c.fill() : c.fillAndStroke();
		},
		'terminate' : function(c, x, y, w, h, isInverse)
		{
			c.ellipse(0, 0, w, h);
			c.fillAndStroke();
		},
		'star': function(c, x, y, w, h, isInverse)
		{
			c.translate(w / 5, h / 6);
			h *= 2 / 3;
			w *= 3 / 5;
			
			c.begin();
			c.moveTo(0, h / 4);
			c.lineTo(w / 3, h / 4);
			c.lineTo(w / 2, 0);
			c.lineTo(2 * w / 3, h / 4);
			c.lineTo(w, h / 4);
			c.lineTo(5 * w / 6, h / 2);
			c.lineTo(w, 3 * h / 4);
			c.lineTo(2 * w / 3, 3 * h / 4);
			c.lineTo(w / 2, h);
			c.lineTo(w / 3, 3 * h / 4);
			c.lineTo(0, 3 * h / 4);
			c.lineTo(w / 6, h / 2);
			c.close();
			c.fillAndStroke();
		}
};

mxCellRenderer.registerShape('mxgraph.bpmn.event', mxShapeBpmnEvent);

//BPMN gateway shape
function mxShapeBpmnGateway(bounds, fill, stroke, strokewidth)
{
	mxShape.call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
};

/**
 * Extends mxShape.
 */
mxUtils.extend(mxShapeBpmnGateway, mxShape);

mxShapeBpmnGateway.prototype.customProperties = mxShapeBpmnEvent.prototype.customProperties;
mxShapeBpmnGateway.prototype.eventTypeEnum = mxShapeBpmnEvent.prototype.eventTypeEnum; 
mxShapeBpmnGateway.prototype.eventEnum = mxShapeBpmnEvent.prototype.eventEnum; 
mxShapeBpmnGateway.prototype.miscEnum = mxShapeBpmnEvent.prototype.miscEnum;

mxShapeBpmnGateway.prototype.customProperties = mxShapeBpmnGateway.prototype.customProperties.concat(
	{name: 'gwType', dispName: 'Gateway type', type: 'enum', defVal:'event', 
		enumList: [{val: 'event', dispName: 'Event-based'}, 
				   {val: 'exclusive', dispName: 'Exclusive'}, 
				   {val: 'parallel', dispName: 'Parallel'}, 
				   {val: 'complex', dispName: 'Complex'}]});

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmnGateway.prototype.paintVertexShape = function(c, x, y, w, h)
{
	c.translate(x, y);

	c.begin();
	c.moveTo(w * 0.5, 0);
	c.lineTo(w, h * 0.5);
	c.lineTo(w * 0.5, h);
	c.lineTo(0, h * 0.5);
	c.close();
	c.fillAndStroke();

	c.setShadow(false);
	
	var symbolW = w * 0.6;
	var symbolH = h * 0.6;
	
	var gwType = mxUtils.getValue(this.style, 'gwType', 'event');
	var outline = mxUtils.getValue(this.style, 'outline', 'none');
	var symbol = mxUtils.getValue(this.style, 'symbol', 'standard');
	var tmpW = w * 0.5;
	var tmpH = h * 0.5;
	
	if (gwType === 'event' && outline !== null && outline !== 'none')
	{
		mxShapeBpmnEvent.prototype.strictDrawShape.call(this, c, (w - symbolW) * 0.5, (h - symbolH) * 0.5, symbolW, symbolH, outline, symbol);
	}
	else if (gwType == 'exclusive')
	{
		c.translate(w * 0.31, h * 0.25);
		tmpW = tmpW * 0.76;
		var strokeColor = c.state.strokeColor;
		var fillColor = c.state.fillColor;
		c.setFillColor(strokeColor);

		c.begin();
		c.moveTo(tmpW * 0.105, 0);
		c.lineTo(tmpW * 0.5, tmpH * 0.38);
		c.lineTo(tmpW * 0.895, tmpH * 0);
		c.lineTo(tmpW, tmpH * 0.11);
		c.lineTo(tmpW * 0.6172, tmpH * 0.5);
		c.lineTo(tmpW, tmpH * 0.89);
		c.lineTo(tmpW * 0.895, tmpH);
		c.lineTo(tmpW * 0.5, tmpH * 0.62);
		c.lineTo(tmpW * 0.105, tmpH);
		c.lineTo(0, tmpH * 0.89);
		c.lineTo(tmpW * 0.3808, tmpH * 0.5);
		c.lineTo(0, tmpH * 0.11);
		c.close();
		c.fillAndStroke();

		c.setFillColor(fillColor);
		c.translate(-tmpW * 0.12, 0);
	}
	else if (gwType == 'parallel')
	{
		c.translate(w * 0.25, h * 0.25);
		var strokeColor = c.state.strokeColor;
		var fillColor = c.state.fillColor;
		c.setFillColor(strokeColor);

		c.begin();
		c.moveTo(tmpW * 0.38, 0);
		c.lineTo(tmpW * 0.62, 0);
		c.lineTo(tmpW * 0.62, tmpH * 0.38);
		c.lineTo(tmpW, tmpH * 0.38);
		c.lineTo(tmpW, tmpH * 0.62);
		c.lineTo(tmpW * 0.62, tmpH * 0.62);
		c.lineTo(tmpW * 0.62, tmpH);
		c.lineTo(tmpW * 0.38, tmpH);
		c.lineTo(tmpW * 0.38, tmpH * 0.62);
		c.lineTo(0, tmpH * 0.62);
		c.lineTo(0, tmpH * 0.38);
		c.lineTo(tmpW * 0.38, tmpH * 0.38);
		c.close();
		c.fillAndStroke();

		c.setFillColor(fillColor);
	}
	else if (gwType == 'complex')
	{
		c.translate(w * 0.25, h * 0.25);
		var strokeColor = c.state.strokeColor;
		var fillColor = c.state.fillColor;
		c.setFillColor(strokeColor);

		c.begin();
		c.moveTo(0, tmpH * 0.44);
		c.lineTo(tmpW * 0.36, tmpH * 0.44);
		c.lineTo(tmpW * 0.1, tmpH * 0.18);
		c.lineTo(tmpW * 0.18, tmpH * 0.1);
		c.lineTo(tmpW * 0.44, tmpH * 0.36);
		c.lineTo(tmpW * 0.44, 0);
		c.lineTo(tmpW * 0.56, 0);
		c.lineTo(tmpW * 0.56, tmpH * 0.36);
		c.lineTo(tmpW * 0.82, tmpH * 0.1);
		c.lineTo(tmpW * 0.90, tmpH * 0.18);
		c.lineTo(tmpW * 0.64, tmpH * 0.44);
		c.lineTo(tmpW, tmpH * 0.44);
		c.lineTo(tmpW, tmpH * 0.56);
		c.lineTo(tmpW * 0.64, tmpH * 0.56);
		c.lineTo(tmpW * 0.9, tmpH * 0.82);
		c.lineTo(tmpW * 0.82, tmpH * 0.9);
		c.lineTo(tmpW * 0.56, tmpH * 0.64);
		c.lineTo(tmpW * 0.56, tmpH);
		c.lineTo(tmpW * 0.44, tmpH);
		c.lineTo(tmpW * 0.44, tmpH * 0.64);
		c.lineTo(tmpW * 0.18, tmpH * 0.9);
		c.lineTo(tmpW * 0.1, tmpH * 0.82);
		c.lineTo(tmpW * 0.36, tmpH * 0.56);
		c.lineTo(0, tmpH * 0.56);
		c.close();
		c.fillAndStroke();

		c.setFillColor(fillColor);
	}
}

mxCellRenderer.registerShape('mxgraph.bpmn.gateway2', mxShapeBpmnGateway);

//**********************************************************************************************************************************************************
//Task (LEGACY)
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Task(bounds, fill, stroke, strokewidth)
{
	mxCellRenderer.prototype.getShape('mxgraph.basic.rect').call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
	this.dy = 0.5;
	this.dx = 0.5;
	this.notch = 0;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Task, mxCellRenderer.prototype.getShape('mxgraph.basic.rect'));

mxShapeBpmn2Task.prototype.customProperties = [
	{name: 'bpmnShapeType', dispName: 'Type', defVal: 'task', type: 'enum', 
		enumList: [{val: 'task', dispName: 'Task'}, 
				   {val: 'transaction', dispName: 'Transaction'}, 
				   {val: 'call', dispName: 'Call'}, 
				   {val: 'subprocess', dispName: 'Sub-Process'}]}, 
	{name: 'taskMarker', dispName: 'Task Marker', defVal: 'abstract', type: 'enum', 
		enumList: [{val: 'abstract', dispName: 'Abstract'}, 
				   {val: 'service', dispName: 'Service'}, 
				   {val: 'send', dispName: 'Send'}, 
				   {val: 'receive', dispName: 'Receive'}, 
				   {val: 'user', dispName: 'User'},
				   
				   {val: 'nime', dispName: 'Non-Interrupting Message Event'}, 
				   
				   {val: 'manual', dispName: 'Manual'}, 
				   {val: 'businessRule', dispName: 'Business Rule'}, 
				   {val: 'script', dispName: 'Script'}]}, 
	{name: 'isLoopSub', dispName: 'Subprocess', type: 'bool'}, 
	{name: 'isLoopStandard', dispName: 'Standard Loop', type: 'bool'}, 
	{name: 'isLoopMultiParallel', dispName: 'Multi-Instance Parallel Loop', type: 'bool'}, 
	{name: 'isLoopMultiSeq', dispName: 'Multi-Instance Sequential Loop', type: 'bool'}, 
	{name: 'isLoopComp', dispName: 'Compensation Loop', type: 'bool'},
	{name: 'isAdHoc', dispName: 'Ad Hoc', type: 'bool'}
];

mxShapeBpmn2Task.prototype.customProperties = mxShapeBpmn2Task.prototype.customProperties.concat(mxShapeBpmnEvent.prototype.customProperties);
mxShapeBpmn2Task.prototype.customProperties = mxShapeBpmn2Task.prototype.customProperties.concat(mxCellRenderer.prototype.getShape('mxgraph.basic.rect').prototype.customProperties);

mxShapeBpmn2Task.prototype.eventTypeEnum = mxShapeBpmnEvent.prototype.eventTypeEnum; 
mxShapeBpmn2Task.prototype.eventEnum = mxShapeBpmnEvent.prototype.eventEnum; 
mxShapeBpmn2Task.prototype.miscEnum = mxShapeBpmnEvent.prototype.miscEnum;

mxShapeBpmn2Task.prototype.cst = {
		TASK : 'mxgraph.bpmn.task'
};

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Task.prototype.paintVertexShape = function(c, x, y, w, h)
{
	var bpmnShapeType = mxUtils.getValue(this.style, 'bpmnShapeType', 'task');
	var taskMarker = mxUtils.getValue(this.style, 'taskMarker', 'abstract');
	var strokeWidth = mxUtils.getValue(this.style, 'strokeWidth', 1);
	var dashed = mxUtils.getValue(this.style, 'dashed', false);
	var inset = mxUtils.getValue(this.style, 'indent', 3);
	var offsetY = 14;
	var rectOutline = mxUtils.getValue(this.style, 'rectOutline', 'single');
	
	c.translate(x, y);

	var superShape = mxCellRenderer.prototype.getShape('mxgraph.basic.rect');
	
	var overrideStyles = {};
	
	if (bpmnShapeType == 'transaction')
	{
		offsetY += inset;
		overrideStyles.rectOutline = 'double';
		overrideStyles.indent = 3;
	}
	else if (bpmnShapeType == 'subprocess')
	{
		overrideStyles.dashed = true;
	}
	else if (bpmnShapeType == 'call')
	{
		overrideStyles.strokeWidth = 4;
	}
	
	superShape.prototype.strictDrawShape.call(this, c, 0, 0, w, h, overrideStyles);

	c.setStrokeWidth(strokeWidth);
	c.setDashed(dashed);
	
	if (bpmnShapeType == 'call')
	{
		c.setStrokeWidth(strokeWidth);
	}

	c.setDashed(false);
	c.setShadow(false);

	var isLoopSub = mxUtils.getValue(this.style, 'isLoopSub', false);
	var isLoopStandard = mxUtils.getValue(this.style, 'isLoopStandard', false);
	var isLoopMultiParallel = mxUtils.getValue(this.style, 'isLoopMultiParallel', false);
	var isLoopMultiSeq = mxUtils.getValue(this.style, 'isLoopMultiSeq', false);
	var isLoopComp = mxUtils.getValue(this.style, 'isLoopComp', false);
	var isAdHoc = mxUtils.getValue(this.style, 'isAdHoc', false);

	var loopnum = 0;
	
	if (isLoopStandard) loopnum++;
	if (isLoopMultiParallel) loopnum++;
	if (isLoopMultiSeq) loopnum++;
	if (isLoopComp) loopnum++;
	if (isLoopSub) loopnum++;
	if (isAdHoc) loopnum++;
	
	var iconSpaceX = 14;
	var currXOffset = - iconSpaceX * loopnum * 0.5;
	
	if (isLoopStandard)
	{
		var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.loop');
		
		if (stencil != null)
		{
			stencil.drawShape(c, this, w * 0.5 + currXOffset + 1, h - offsetY + 1, 12, 12);
			currXOffset += iconSpaceX;
		}
	}
	
	if (isLoopMultiParallel)
	{
		c.translate(w * 0.5 + currXOffset + 1, h - offsetY + 1);
		
		c.begin();
		c.moveTo(2.4, 0);
		c.lineTo(2.4, 12);
		c.moveTo(6, 0);
		c.lineTo(6, 12);
		c.moveTo(9.6, 0);
		c.lineTo(9.6, 12);
		c.stroke();
		
		c.translate(- w * 0.5 - currXOffset - 1, offsetY - 1 - h);
		currXOffset += iconSpaceX;
	}
	
	if (isLoopMultiSeq)
	{
		c.translate(w * 0.5 + currXOffset + 1, h - offsetY + 1);
		
		c.begin();
		c.moveTo(0, 2.4);
		c.lineTo(12, 2.4);
		c.moveTo(0, 6);
		c.lineTo(12, 6);
		c.moveTo(0, 9.6);
		c.lineTo(12, 9.6);
		c.stroke();
		
		c.translate(- w * 0.5 - currXOffset - 1, offsetY - 1 - h);
		currXOffset += iconSpaceX;
	}
	
	if (isLoopComp)
	{
		var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.compensation');
		
		if (stencil != null)
		{
			stencil.drawShape(c, this, w * 0.5 + currXOffset, h - offsetY + 1, 14, 12);
			currXOffset += iconSpaceX;
		}
	}
	
	if (isLoopSub)
	{
		c.translate(w * 0.5 + currXOffset, h - offsetY);
		
		c.rect(0, 0, 14, 14);
		c.stroke();
		
		c.begin();
		c.moveTo(4, 7);
		c.lineTo(10, 7);
		c.moveTo(7, 4);
		c.lineTo(7, 10);
		c.stroke();
		
		c.translate(- w * 0.5 - currXOffset, offsetY - h);
		currXOffset += iconSpaceX;
	}
	
	if (isAdHoc)
	{
		var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.ad_hoc');
		
		if (stencil != null)
		{
			var strokeColor = mxUtils.getValue(this.style, 'strokeColor', '#000000');
			var fillColor = mxUtils.getValue(this.style, 'fillColor', '#ffffff');
			
			c.setStrokeColor('none');
			c.setFillColor(strokeColor);
			
			stencil.drawShape(c, this, w * 0.5 + currXOffset + 1, h - offsetY + 4, 12, 6);
			currXOffset += iconSpaceX;
			
			c.setStrokeColor(strokeColor);
			c.setFillColor(fillColor);
		}
	}
	
	switch (taskMarker) {
		case 'abstract':
			break;
		case 'service':
		
			c.setFillColor(mxUtils.getValue(this.style, 'fillColor', '#ffffff'));
			
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.service_task');
			
			if (stencil != null)
			{
				stencil.drawShape(c, this, 2, 2, 16, 16);
			}
			
			break;
		case 'send':
			var strokeColor = mxUtils.getValue(this.style, 'strokeColor', '#000000');
			var fillColor = mxUtils.getValue(this.style, 'fillColor', '#ffffff');
			
			c.setStrokeColor(fillColor);
			c.setFillColor(strokeColor);
			
			mxShapeBpmn2SendMarker.prototype.paintVertexShape(c, 4, 4, 18, 13);
			
			break;
		case 'receive':
			
			mxShapeBpmn2SendMarker.prototype.paintVertexShape(c, 4, 4, 18, 13);
			
			break;
		case 'user':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.user_task');
			
			if (stencil != null)
			{
				stencil.drawShape(c, this, 2, 2, 16, 16);
			}
			
			break;
		case 'manual':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.manual_task');
			
			if (stencil != null)
			{
				stencil.drawShape(c, this, 3, 3, 18, 14);
			}
			
			break;
		case 'businessRule':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.business_rule_task');
			
			if (stencil != null)
			{
				stencil.drawShape(c, this, 4, 4, 18, 14);
			}
			
			break;
		case 'script':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.script_task');
			
			if (stencil != null)
			{
				stencil.drawShape(c, this, 3, 3, 19, 18);
			}
			
			break;
	}

	var symbolW = 20;
	var symbolH = 20;
	
	var outline = mxUtils.getValue(this.style, 'outline', 'none');
	var symbol = mxUtils.getValue(this.style, 'symbol', 'standard');
	
	mxShapeBpmnEvent.prototype.strictDrawShape.call(this, c, 0, 0, symbolW, symbolH, outline, symbol);

};

mxCellRenderer.registerShape(mxShapeBpmn2Task.prototype.cst.TASK, mxShapeBpmn2Task);

//**********************************************************************************************************************************************************
//Task v2
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Task2(bounds, fill, stroke, strokewidth)
{
	mxCellRenderer.prototype.getShape('mxgraph.basic.rect').call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
	this.dy = 0.5;
	this.dx = 0.5;
	this.notch = 0;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Task2, mxCellRenderer.prototype.getShape('mxgraph.basic.rect'));

mxShapeBpmn2Task2.prototype.customProperties = [
	{name: 'bpmnShapeType', dispName: 'Type', defVal: 'task', type: 'enum', 
		enumList: [{val: 'task', dispName: 'Task'}, 
				   {val: 'transaction', dispName: 'Transaction'}, 
				   {val: 'call', dispName: 'Call'}, 
				   {val: 'subprocess', dispName: 'Sub-Process'}]}, 
	{name: 'taskMarker', dispName: 'Task Marker', defVal: 'abstract', type: 'enum', 
		enumList: [{val: 'abstract', dispName: 'Abstract'}, 
				   {val: 'service', dispName: 'Service'}, 
				   {val: 'send', dispName: 'Send'}, 
				   {val: 'receive', dispName: 'Receive'}, 
				   {val: 'user', dispName: 'User'},
				   
				   {val: 'nime', dispName: 'Non-Interrupting Message Event'}, 
				   
				   {val: 'manual', dispName: 'Manual'}, 
				   {val: 'businessRule', dispName: 'Business Rule'}, 
				   {val: 'script', dispName: 'Script'}]}, 
	{name: 'isLoopSub', dispName: 'Subprocess', type: 'bool'}, 
	{name: 'isLoopStandard', dispName: 'Standard Loop', type: 'bool'}, 
	{name: 'isLoopMultiParallel', dispName: 'Multi-Instance Parallel Loop', type: 'bool'}, 
	{name: 'isLoopMultiSeq', dispName: 'Multi-Instance Sequential Loop', type: 'bool'}, 
	{name: 'isLoopComp', dispName: 'Compensation Loop', type: 'bool'},
	{name: 'isAdHoc', dispName: 'Ad Hoc', type: 'bool'}
];

mxShapeBpmn2Task2.prototype.customProperties = mxShapeBpmn2Task2.prototype.customProperties.concat(mxShapeBpmnEvent.prototype.customProperties);
mxShapeBpmn2Task2.prototype.customProperties = mxShapeBpmn2Task2.prototype.customProperties.concat(mxCellRenderer.prototype.getShape('mxgraph.basic.rect').prototype.customProperties);

mxShapeBpmn2Task2.prototype.eventTypeEnum = mxShapeBpmnEvent.prototype.eventTypeEnum; 
mxShapeBpmn2Task2.prototype.eventEnum = mxShapeBpmnEvent.prototype.eventEnum; 
mxShapeBpmn2Task2.prototype.miscEnum = mxShapeBpmnEvent.prototype.miscEnum;

mxShapeBpmn2Task2.prototype.cst = {
		TASK : 'mxgraph.bpmn.task2'
};

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Task2.prototype.paintVertexShape = function(c, x, y, w, h)
{
	var bpmnShapeType = mxUtils.getValue(this.style, 'bpmnShapeType', 'task');
	var taskMarker = mxUtils.getValue(this.style, 'taskMarker', 'abstract');
	var strokeWidth = mxUtils.getValue(this.style, 'strokeWidth', 1);
	var dashed = mxUtils.getValue(this.style, 'dashed', false);
	var inset = mxUtils.getValue(this.style, 'indent', 3);
	var offsetY = 14 + strokeWidth * 0.5;

	if (bpmnShapeType == 'call')
	{
		offsetY = 14 + strokeWidth * 2;
	}

	var rectOutline = mxUtils.getValue(this.style, 'rectOutline', 'single');
	
	c.translate(x, y);

	var superShape = mxCellRenderer.prototype.getShape('mxgraph.basic.rect');
	
	var overrideStyles = {};
	
	if (bpmnShapeType == 'transaction')
	{
		offsetY += inset;
		overrideStyles.rectOutline = 'double';
		overrideStyles.indent = 3;
	}
	else if (bpmnShapeType == 'subprocess')
	{
		overrideStyles.dashed = true;
	}
	else if (bpmnShapeType == 'call')
	{
		overrideStyles.strokeWidth = strokeWidth * 4;
	}
	
	superShape.prototype.strictDrawShape.call(this, c, 0, 0, w, h, overrideStyles);

	c.setStrokeWidth(strokeWidth);
	c.setDashed(dashed);
	
	if (bpmnShapeType == 'call')
	{
		c.setStrokeWidth(strokeWidth);
	}

	c.setDashed(false);
	c.setShadow(false);

	var isLoopSub = mxUtils.getValue(this.style, 'isLoopSub', false);
	var isLoopStandard = mxUtils.getValue(this.style, 'isLoopStandard', false);
	var isLoopMultiParallel = mxUtils.getValue(this.style, 'isLoopMultiParallel', false);
	var isLoopMultiSeq = mxUtils.getValue(this.style, 'isLoopMultiSeq', false);
	var isLoopComp = mxUtils.getValue(this.style, 'isLoopComp', false);
	var isAdHoc = mxUtils.getValue(this.style, 'isAdHoc', false);

	var loopnum = 0;

	if (isLoopStandard) loopnum++;
	if (isLoopMultiParallel) loopnum++;
	if (isLoopMultiSeq) loopnum++;
	if (isLoopComp) loopnum++;
	if (isLoopSub) loopnum++;
	if (isAdHoc) loopnum++;
	
	var iconSpaceX = 14;
	var currXOffset = - iconSpaceX * loopnum * 0.5;
	
	c.setStrokeWidth(1);

	if (isLoopStandard)
	{
		var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.loop');
		
		if (stencil != null)
		{
			var previousSetStrokeWidth = c.setStrokeWidth;
			c.setStrokeWidth = function() { };
			stencil.drawShape(c, this, w * 0.5 + currXOffset + 1, h - offsetY + 1, 12, 12);
			c.setStrokeWidth = previousSetStrokeWidth;
			currXOffset += iconSpaceX;
		}
	}
	
	if (isLoopMultiParallel)
	{
		c.translate(w * 0.5 + currXOffset + 1, h - offsetY + 1);
		
		c.begin();
		c.moveTo(2.4, 0);
		c.lineTo(2.4, 12);
		c.moveTo(6, 0);
		c.lineTo(6, 12);
		c.moveTo(9.6, 0);
		c.lineTo(9.6, 12);
		c.stroke();
		
		c.translate(- w * 0.5 - currXOffset - 1, offsetY - 1 - h);
		currXOffset += iconSpaceX;
	}
	
	if (isLoopMultiSeq)
	{
		c.translate(w * 0.5 + currXOffset + 1, h - offsetY + 1);
		
		c.begin();
		c.moveTo(0, 2.4);
		c.lineTo(12, 2.4);
		c.moveTo(0, 6);
		c.lineTo(12, 6);
		c.moveTo(0, 9.6);
		c.lineTo(12, 9.6);
		c.stroke();
		
		c.translate(- w * 0.5 - currXOffset - 1, offsetY - 1 - h);
		currXOffset += iconSpaceX;
	}
	
	if (isLoopComp)
	{
		var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.compensation');
		
		if (stencil != null)
		{
			var previousSetStrokeWidth = c.setStrokeWidth;
			c.setStrokeWidth = function() { };
			stencil.drawShape(c, this, w * 0.5 + currXOffset, h - offsetY + 1, 14, 12);
			c.setStrokeWidth = previousSetStrokeWidth;
			currXOffset += iconSpaceX;
		}
	}
	
	if (isLoopSub)
	{
		c.translate(w * 0.5 + currXOffset, h - offsetY);
		
		c.rect(0, 0, 14, 14);
		c.stroke();
		
		c.begin();
		c.moveTo(4, 7);
		c.lineTo(10, 7);
		c.moveTo(7, 4);
		c.lineTo(7, 10);
		c.stroke();
		
		c.translate(- w * 0.5 - currXOffset, offsetY - h);
		currXOffset += iconSpaceX;
	}
	
	if (isAdHoc)
	{
		var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.ad_hoc');
		
		if (stencil != null)
		{
			var strokeColor = mxUtils.getValue(this.style, 'strokeColor', '#000000');
			var fillColor = mxUtils.getValue(this.style, 'fillColor', '#ffffff');
			
			c.setStrokeColor('none');
			c.setFillColor(strokeColor);
			
			stencil.drawShape(c, this, w * 0.5 + currXOffset + 1, h - offsetY + 4, 12, 6);
			currXOffset += iconSpaceX;
			
			c.setStrokeColor(strokeColor);
			c.setFillColor(fillColor);
			c.setStrokeWidth(1);
		}
	}
	
	var inlet = strokeWidth * 0.5;
	if (bpmnShapeType == 'call')
	{
		inlet = strokeWidth * 2;
	}
	
	
	switch (taskMarker) {
		case 'abstract':
			break;
		case 'service':
		
			c.setFillColor(mxUtils.getValue(this.style, 'fillColor', '#ffffff'));
			
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.service_task');
			
			if (stencil != null)
			{
				var previousSetStrokeWidth = c.setStrokeWidth;
				c.setStrokeWidth = function() { };
				stencil.drawShape(c, this, inlet + 2, inlet + 2, 16, 16);
				c.setStrokeWidth = previousSetStrokeWidth;
			}
			
			break;
		case 'send':
			var strokeColor = mxUtils.getValue(this.style, 'strokeColor', '#000000');
			var fillColor = mxUtils.getValue(this.style, 'fillColor', '#ffffff');
			
			c.setStrokeColor(fillColor);
			c.setFillColor(strokeColor);
			
			mxShapeBpmn2SendMarker.prototype.paintVertexShape(c, inlet + 4, inlet + 4, 18, 13);
			
			break;
		case 'receive':
			
			mxShapeBpmn2SendMarker.prototype.paintVertexShape(c, inlet + 4, inlet + 4, 18, 13);
			
			break;
		case 'user':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.user_task');
			
			if (stencil != null)
			{
				var previousSetStrokeWidth = c.setStrokeWidth;
				c.setStrokeWidth = function() { };
				stencil.drawShape(c, this, inlet + 2, inlet + 2, 16, 16);
				c.setStrokeWidth = previousSetStrokeWidth;
			}
			
			break;
		case 'manual':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.manual_task');
			
			if (stencil != null)
			{
				var previousSetStrokeWidth = c.setStrokeWidth;
				c.setStrokeWidth = function() { };
				stencil.drawShape(c, this, inlet + 3, inlet + 3, 18, 14);
				c.setStrokeWidth = previousSetStrokeWidth;
			}
			
			break;
		case 'businessRule':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.business_rule_task');
			
			if (stencil != null)
			{
				var previousSetStrokeWidth = c.setStrokeWidth;
				c.setStrokeWidth = function() { };
				stencil.drawShape(c, this, inlet + 4, inlet + 4, 18, 14);
				c.setStrokeWidth = previousSetStrokeWidth;
			}
			
			break;
		case 'script':
			var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.script_task');
			
			if (stencil != null)
			{
				var previousSetStrokeWidth = c.setStrokeWidth;
				c.setStrokeWidth = function() { };
				stencil.drawShape(c, this, inlet + 3, inlet + 3, 19, 18);
				c.setStrokeWidth = previousSetStrokeWidth;
			}
			
			break;
	}

	var symbolW = 20;
	var symbolH = 20;
	
	var outline = mxUtils.getValue(this.style, 'outline', 'none');
	var symbol = mxUtils.getValue(this.style, 'symbol', 'standard');
	
	var previousSetStrokeWidth = c.setStrokeWidth;
	c.setStrokeWidth = function() { };
	mxShapeBpmnEvent.prototype.strictDrawShape.call(this, c, inlet, inlet, symbolW, symbolH, outline, symbol);
	c.setStrokeWidth = previousSetStrokeWidth;

};

mxCellRenderer.registerShape(mxShapeBpmn2Task2.prototype.cst.TASK, mxShapeBpmn2Task2);

//**********************************************************************************************************************************************************
//Data (LEGACY)
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Data(bounds, fill, stroke, strokewidth)
{
	mxCellRenderer.prototype.getShape('note').call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
	this.dy = 0.5;
	this.dx = 0.5;
	this.notch = 0;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Data, mxCellRenderer.prototype.getShape('note'));

mxShapeBpmn2Data.prototype.cst = {
		DATA : 'mxgraph.bpmn.data'
};

mxShapeBpmn2Data.prototype.customProperties = [
	{name: 'bpmnTransferType', dispName: 'Transfer Type', defVal: 'none', type: 'enum', 
		enumList: [{val: 'none', dispName: 'None'}, 
				   {val: 'input', dispName: 'Input'}, 
				   {val: 'output', dispName: 'Output'}]}, 
	{name: 'isCollection', dispName: 'Collection', type: 'bool'}
];

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Data.prototype.paintVertexShape = function(c, x, y, w, h)
{
	var superShape = mxCellRenderer.prototype.getShape('note');
	superShape.prototype.paintVertexShape.call(this, c, x, y, w, h);

	var trType = mxUtils.getValue(this.style, 'bpmnTransferType', 'none');
	var isColl = mxUtils.getValue(this.style, 'isCollection', false);
	c.setShadow(false);
	
	if (trType === 'input' || trType === 'output')
	{
		var arrX = 3;
		var arrY = 3;
		var arrW = 14;
		var arrH = 12;
		
		c.translate(arrX, arrY);
		c.begin();
		c.moveTo(0, arrH * 0.3);
		c.lineTo(arrW * 0.55, arrH * 0.3);
		c.lineTo(arrW * 0.55, 0);
		c.lineTo(arrW, arrH * 0.5);
		c.lineTo(arrW * 0.55, arrH);
		c.lineTo(arrW * 0.55, arrH * 0.7);
		c.lineTo(0, arrH * 0.7);
		c.close();
		c.translate(-arrX, -arrY);
		
		if (trType === 'input')
		{
			c.stroke();
		}
		else
		{
			var fillColor = mxUtils.getValue(this.style, 'fillColor', '#ffffff');
			var strokeColor = mxUtils.getValue(this.style, 'strokeColor', '#000000');
			
			c.setFillColor(strokeColor);
			c.fillAndStroke();
			c.setFillColor(fillColor);
		}
	}
	
	if (isColl)
	{
		c.translate(w * 0.5 - 6, h - 12);
		
		c.begin();
		c.moveTo(2.4, 0);
		c.lineTo(2.4, 12);
		c.moveTo(6, 0);
		c.lineTo(6, 12);
		c.moveTo(9.6, 0);
		c.lineTo(9.6, 12);
		c.stroke();
		
		c.translate( - w * 0.5 + 6, - h + 12);
	}
};

mxCellRenderer.registerShape(mxShapeBpmn2Data.prototype.cst.DATA, mxShapeBpmn2Data);

Graph.handleFactory[mxShapeBpmn2Data.prototype.cst.DATA] = Graph.handleFactory['note'];

//**********************************************************************************************************************************************************
//Data v2
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Data2(bounds, fill, stroke, strokewidth)
{
	mxCellRenderer.prototype.getShape('note').call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
	this.dy = 0.5;
	this.dx = 0.5;
	this.notch = 0;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Data2, mxCellRenderer.prototype.getShape('note'));

mxShapeBpmn2Data2.prototype.cst = {
		DATA : 'mxgraph.bpmn.data2'
};

mxShapeBpmn2Data2.prototype.customProperties = [
	{name: 'bpmnTransferType', dispName: 'Transfer Type', defVal: 'none', type: 'enum', 
		enumList: [{val: 'none', dispName: 'None'}, 
				   {val: 'input', dispName: 'Input'}, 
				   {val: 'output', dispName: 'Output'}]}, 
	{name: 'isCollection', dispName: 'Collection', type: 'bool'}
];

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Data2.prototype.paintVertexShape = function(c, x, y, w, h)
{
	var superShape = mxCellRenderer.prototype.getShape('note');
	superShape.prototype.paintVertexShape.call(this, c, x, y, w, h);

	var trType = mxUtils.getValue(this.style, 'bpmnTransferType', 'none');
	var isColl = mxUtils.getValue(this.style, 'isCollection', false);
	var strokeWidth = mxUtils.getValue(this.style, 'strokeWidth', 1);
	
	c.setShadow(false);
	
	c.setStrokeWidth(1);

	if (trType === 'input' || trType === 'output')
	{
		var arrX = 3 + strokeWidth * 0.5;
		var arrY = 3 + strokeWidth * 0.5;
		var arrW = 14;
		var arrH = 12;
		
		c.translate(arrX, arrY);
		c.begin();
		c.moveTo(0, arrH * 0.3);
		c.lineTo(arrW * 0.55, arrH * 0.3);
		c.lineTo(arrW * 0.55, 0);
		c.lineTo(arrW, arrH * 0.5);
		c.lineTo(arrW * 0.55, arrH);
		c.lineTo(arrW * 0.55, arrH * 0.7);
		c.lineTo(0, arrH * 0.7);
		c.close();
		c.translate(-arrX, -arrY);
		
		if (trType === 'input')
		{
			c.stroke();
		}
		else
		{
			var fillColor = mxUtils.getValue(this.style, 'fillColor', '#ffffff');
			var strokeColor = mxUtils.getValue(this.style, 'strokeColor', '#000000');
			
			c.setFillColor(strokeColor);
			c.fillAndStroke();
			c.setFillColor(fillColor);
		}
	}
	
	if (isColl)
	{
		c.translate(w * 0.5 - 6, h - 12 - strokeWidth * 0.5);
		
		c.begin();
		c.moveTo(2.4, 0);
		c.lineTo(2.4, 12);
		c.moveTo(6, 0);
		c.lineTo(6, 12);
		c.moveTo(9.6, 0);
		c.lineTo(9.6, 12);
		c.stroke();
		
		c.translate( - w * 0.5 + 6, - h + 12);
	}
};

mxCellRenderer.registerShape(mxShapeBpmn2Data2.prototype.cst.DATA, mxShapeBpmn2Data2);

Graph.handleFactory[mxShapeBpmn2Data2.prototype.cst.DATA] = Graph.handleFactory['note'];

//**********************************************************************************************************************************************************
//Swimlane
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Swimlane(bounds, fill, stroke, strokewidth)
{
	mxCellRenderer.prototype.getShape('note').call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
	this.dy = 0.5;
	this.dx = 0.5;
	this.notch = 0;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Swimlane, mxSwimlane);

mxShapeBpmn2Swimlane.prototype.cst = {
		SWIMLANE : 'mxgraph.bpmn.swimlane'
};

mxShapeBpmn2Swimlane.prototype.customProperties = [
	{name: 'isCollection', dispName: 'Collection', type: 'bool'}
];

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Swimlane.prototype.paintVertexShape = function(c, x, y, w, h)
{
	mxSwimlane.prototype.paintVertexShape.call(this, c, x, y, w, h);

	var isColl = mxUtils.getValue(this.style, 'isCollection', false);
	c.setShadow(false);
	
	if (isColl)
	{
		c.translate(w * 0.5 - 6, h - 12);
		
		c.begin();
		c.moveTo(2.4, 0);
		c.lineTo(2.4, 12);
		c.moveTo(6, 0);
		c.lineTo(6, 12);
		c.moveTo(9.6, 0);
		c.lineTo(9.6, 12);
		c.stroke();
		
		c.translate( - w * 0.5 + 6, - h + 12);
	}
};

mxCellRenderer.registerShape(mxShapeBpmn2Swimlane.prototype.cst.SWIMLANE, mxShapeBpmn2Swimlane);

Graph.handleFactory[mxShapeBpmn2Swimlane.prototype.cst.SWIMLANE] = Graph.handleFactory['swimlane'];

//**********************************************************************************************************************************************************
//Conversation (LEGACY)
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Conversation(bounds, fill, stroke, strokewidth)
{
	mxShape.call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Conversation, mxHexagon);

mxShapeBpmn2Conversation.prototype.customProperties = [
	{name: 'bpmnConversationType', dispName: 'Type', defVal: 'conv', type: 'enum', 
		enumList: [{val: 'conv', dispName: 'Conversation'}, 
				   {val: 'call', dispName: 'Call'}]}, 
	{name: 'isLoopSub', dispName: 'Subprocess', type: 'bool'} 

];

mxShapeBpmn2Conversation.prototype.cst = {
		CONVERSATION : 'mxgraph.bpmn.conversation'
};

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Conversation.prototype.paintVertexShape = function(c, x, y, w, h)
{
	var bpmnConvType = mxUtils.getValue(this.style, 'bpmnConversationType', 'conv');
	var strokeWidth = mxUtils.getValue(this.style, 'strokeWidth', 1);
	
	if (bpmnConvType == 'call')
	{
		c.setStrokeWidth(strokeWidth * 4);
	}

	c.translate(x, y);
	
	c.begin();
	c.moveTo(0, h * 0.5);
	c.lineTo(w * 0.25, 0);
	c.lineTo(w * 0.75, 0);
	c.lineTo(w, h * 0.5);
	c.lineTo(w * 0.75, h);
	c.lineTo(w * 0.25, h);
	c.close();
	c.fillAndStroke();

	if (bpmnConvType == 'call')
	{
		c.setStrokeWidth(strokeWidth);
	}

	var isLoopSub = mxUtils.getValue(this.style, 'isLoopSub', false);

	if (isLoopSub)
	{
		c.translate(w * 0.5 - 7, h - 14);
		
		c.rect(0, 0, 14, 14);
		c.stroke();
		
		c.begin();
		c.moveTo(4, 7);
		c.lineTo(10, 7);
		c.moveTo(7, 4);
		c.lineTo(7, 10);
		c.stroke();
	}
};

mxCellRenderer.registerShape(mxShapeBpmn2Conversation.prototype.cst.CONVERSATION, mxShapeBpmn2Conversation);

//**********************************************************************************************************************************************************
//Conversation v2
//**********************************************************************************************************************************************************
/**
* Extends mxShape.
*/
function mxShapeBpmn2Conversation2(bounds, fill, stroke, strokewidth)
{
	mxShape.call(this);
	this.bounds = bounds;
	this.fill = fill;
	this.stroke = stroke;
	this.strokewidth = (strokewidth != null) ? strokewidth : 1;
};

/**
* Extends mxShape.
*/
mxUtils.extend(mxShapeBpmn2Conversation2, mxHexagon);

mxShapeBpmn2Conversation2.prototype.customProperties = [
	{name: 'bpmnConversationType', dispName: 'Type', defVal: 'conv', type: 'enum', 
		enumList: [{val: 'conv', dispName: 'Conversation'}, 
				   {val: 'call', dispName: 'Call'}]}, 
	{name: 'isLoopSub', dispName: 'Subprocess', type: 'bool'} 

];

mxShapeBpmn2Conversation2.prototype.cst = {
		CONVERSATION : 'mxgraph.bpmn.conversation2'
};

/**
* Function: paintVertexShape
* 
* Paints the vertex shape.
*/
mxShapeBpmn2Conversation2.prototype.paintVertexShape = function(c, x, y, w, h)
{
	var bpmnConvType = mxUtils.getValue(this.style, 'bpmnConversationType', 'conv');
	var strokeWidth = mxUtils.getValue(this.style, 'strokeWidth', 1);
	
	if (bpmnConvType == 'call')
	{
		c.setStrokeWidth(strokeWidth * 4);
	}

	c.translate(x, y);
	
	c.begin();
	c.moveTo(0, h * 0.5);
	c.lineTo(w * 0.25, 0);
	c.lineTo(w * 0.75, 0);
	c.lineTo(w, h * 0.5);
	c.lineTo(w * 0.75, h);
	c.lineTo(w * 0.25, h);
	c.close();
	c.fillAndStroke();

	c.setStrokeWidth(1);

	var isLoopSub = mxUtils.getValue(this.style, 'isLoopSub', false);

	if (isLoopSub)
	{
		if (bpmnConvType == 'call')
		{
			c.translate(w * 0.5 - 7, h - 14 - strokeWidth * 2);
		}
		else
		{
			c.translate(w * 0.5 - 7, h - 14 - strokeWidth * 0.5);
		}
		
		c.rect(0, 0, 14, 14);
		c.stroke();
		
		c.begin();
		c.moveTo(4, 7);
		c.lineTo(10, 7);
		c.moveTo(7, 4);
		c.lineTo(7, 10);
		c.stroke();
	}
};

mxCellRenderer.registerShape(mxShapeBpmn2Conversation2.prototype.cst.CONVERSATION, mxShapeBpmn2Conversation2);
