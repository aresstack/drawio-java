/*
 * Members copied verbatim from the original draw.io js/grapheditor/Graph.js
 * (src/main/webapp/js/grapheditor/Graph.js).
 * Upstream commit: c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3
 * Apache-2.0; see upstream/LICENSE and THIRD-PARTY-NOTICES.md.
 * Loaded after drawio-headless.js, which defines the headless Graph.
 */
Graph.prototype.isTable = function(cell)
{
	var style = this.getChildLayoutStyle(cell);
	
	return style != null && style['childLayout'] == 'tableLayout';
};

Graph.prototype.isTableRow = function(cell)
{
	return this.model.isVertex(cell) && this.isTable(this.model.getParent(cell));
};

Graph.prototype.isTableCell = function(cell)
{
	return this.model.isVertex(cell) && this.isTableRow(this.model.getParent(cell));
};

Graph.prototype.getChildLayoutStyle = function(cell)
{
	var cache = this.childLayoutStyles;
	var raw = this.model.getStyle(cell);

	if (cache != null && (cache.getCellStyle !== this.getCellStyle ||
		cache.stylesheet !== this.stylesheet || cache.styles !== this.stylesheet.styles))
	{
		cache = null;
	}

	var entry = (cache != null) ? cache.cells.get(cell) : null;

	if (entry == null || entry.raw !== raw)
	{
		var style = this.getCellStyle(cell);
		entry = {raw: raw, style: (style != null && style['childLayout'] != null) ? style : null};

		if (cache != null)
		{
			cache.cells.set(cell, entry);
		}
	}

	return entry.style;
};

Graph.prototype.visitTableCells = function(cell, visitor)
{
	var lastRow = null;
	var rows = this.model.getChildCells(cell, true);
	var start = this.getActualStartSize(cell, true);

	for (var i = 0; i < rows.length; i++)
	{
		var rowStart = this.getActualStartSize(rows[i], true);
		var cols = this.model.getChildCells(rows[i], true);
		var rowStyle = this.getCellStyle(rows[i], true);
		var lastCol = null;
		var row = [];

		for (var j = 0; j < cols.length; j++)
		{
			var geo = this.getCellGeometry(cols[j]);

			// Skips cells with no geometry (consistent with TableLayout.layoutRow).
			// Pushes null to keep lastRow[j] indexing aligned with the cols array.
			if (geo == null)
			{
				row.push(null);
				continue;
			}

			var col = {cell: cols[j], rowspan: 1, colspan: 1, row: i, col: j, geo: geo};
			geo = (geo.alternateBounds != null) ? geo.alternateBounds : geo;
			col.point = new mxPoint(geo.width + (lastCol != null ? lastCol.point.x : start.x + rowStart.x),
				geo.height + (lastRow != null && lastRow[0] != null ? lastRow[0].point.y : start.y + rowStart.y));
			col.actual = col;

			if (lastRow != null && lastRow[j] != null && lastRow[j].rowspan > 1)
			{
				col.rowspan = lastRow[j].rowspan - 1;
				col.colspan = lastRow[j].colspan;
				col.actual = lastRow[j].actual;
			}
			else
			{
				if (lastCol != null && lastCol.colspan > 1)
				{
					col.rowspan = lastCol.rowspan;
					col.colspan = lastCol.colspan - 1;
					col.actual = lastCol.actual;
				}
				else
				{
					var style = this.getCurrentCellStyle(cols[j], true);

					if (style != null)
					{
						col.rowspan = parseInt(style['rowspan'] || 1);
						col.colspan = parseInt(style['colspan'] || 1);
					}
				}
			}

			var head = mxUtils.getValue(rowStyle, mxConstants.STYLE_SWIMLANE_HEAD, 1) == 1 &&
				mxUtils.getValue(rowStyle, mxConstants.STYLE_STROKECOLOR,
					mxConstants.NONE) != mxConstants.NONE;
				
			visitor(col, cols.length, rows.length,
				start.x + ((head) ? rowStart.x : 0),
				start.y + ((head) ? rowStart.y : 0));
			row.push(col);
			lastCol = col;
		}

		lastRow = row;
	}

};

Graph.prototype.getTableLines = function(cell, horizontal, vertical)
{
	var hl = [];
	var vl = [];

	if (horizontal || vertical)
	{
		this.visitTableCells(cell, mxUtils.bind(this, function(iter, colCount, rowCount, x0, y0)
		{
			// Constructs horizontal lines
			if (horizontal && iter.row < rowCount - 1)
			{
				if (hl[iter.row] == null)
				{
					hl[iter.row] = [new mxPoint(x0, iter.point.y)];
				}

				if (iter.rowspan > 1)
				{
					hl[iter.row].push(null);
				}
				
				hl[iter.row].push(iter.point);
			}

			// Constructs vertical lines
			if (vertical && iter.col < colCount - 1)
			{
				if (vl[iter.col] == null)
				{
					vl[iter.col] = [new mxPoint(iter.point.x, y0)];
				}

				if (iter.colspan > 1)
				{
					vl[iter.col].push(null);
				}
				
				vl[iter.col].push(iter.point);
			}
		}));
	}

	return hl.concat(vl);
};

Graph.prototype.getTableRowFirstCellBounds = function(row)
{
	var rowGeo = this.getCellGeometry(row);
	var cells = this.model.getChildCells(row, true);

	for (var i = 0; rowGeo != null && i < cells.length; i++)
	{
		var geo = this.getCellGeometry(cells[i]);

		if (geo != null)
		{
			var visible = this.model.isVisible(cells[i]);
			var size = (!visible && geo.alternateBounds != null) ?
				geo.alternateBounds : geo;
			var start = (visible) ? this.getActualStartSize(cells[i], true) :
				new mxRectangle();
			var h = Math.min(size.height, rowGeo.height - geo.y);

			return new mxRectangle(geo.x + start.x, geo.y + start.y,
				Math.max(0, size.width - start.x - start.width),
				Math.max(0, h - start.y - start.height));
		}
	}

	return null;
};

Graph.prototype.paintTableCellLines = function(c, cell, x, y, w, h, stroke, strokeWidth, map)
{
	// Collapsed tables paint all borders above the fills
	if (stroke == null || stroke == mxConstants.NONE ||
		this.getCollapsedTableForCell(cell) != null)
	{
		return;
	}

	var model = this.model;
	var top = false, bottom = false, left = false, right = false;

	if (this.isTableRow(cell))
	{
		var rows = model.getChildCells(model.getParent(cell), true);
		var index = mxUtils.indexOf(rows, cell);
		var rowLines = mxUtils.getValue(this.getCurrentCellStyle(
			model.getParent(cell)), 'rowLines', '1') != '0';

		top = rowLines && index > 0;
		bottom = rowLines && index < rows.length - 1;
	}
	else if (this.isTableCell(cell))
	{
		var row = model.getParent(cell);
		var table = model.getParent(row);
		var rows = model.getChildCells(table, true);
		var cols = model.getChildCells(row, true);
		var style = this.getCurrentCellStyle(table);
		var rowLines = mxUtils.getValue(style, 'rowLines', '1') != '0';
		var columnLines = mxUtils.getValue(style, 'columnLines', '1') != '0';
		var rowIndex = mxUtils.indexOf(rows, row);
		var colIndex = mxUtils.indexOf(cols, cell);

		top = rowLines && rowIndex > 0;
		bottom = rowLines && rowIndex < rows.length - 1;
		left = columnLines && colIndex > 0;
		right = columnLines && colIndex < cols.length - 1;
	}
	else
	{
		return;
	}

	if (top || bottom || left || right)
	{
		c.setStrokeColor(stroke);
		c.setStrokeWidth(strokeWidth);
		c.setDashed(false);
		c.setShadow(false);

		var line = function(x0, y0, x1, y1)
		{
			var p0 = (map != null) ? map(x0, y0) : new mxPoint(x0, y0);
			var p1 = (map != null) ? map(x1, y1) : new mxPoint(x1, y1);

			c.begin();
			c.moveTo(p0.x, p0.y);
			c.lineTo(p1.x, p1.y);
			c.stroke();
		};

		if (top)
		{
			line(x, y, x + w, y);
		}

		if (bottom)
		{
			line(x, y + h, x + w, y + h);
		}

		if (left)
		{
			line(x, y, x, y + h);
		}

		if (right)
		{
			line(x + w, y, x + w, y + h);
		}
	}
};

Graph.prototype.isCollapsedTable = function(cell)
{
	if (cell != null && this.model.isVertex(cell))
	{
		var style = this.getCurrentCellStyle(cell);

		return style != null && style['tableRender'] == 'collapsed' &&
			style['childLayout'] == 'tableLayout' &&
			style[mxConstants.STYLE_SHAPE] == 'table';
	}

	return false;
};

Graph.prototype.getCollapsedTableForCell = function(cell)
{
	var model = this.model;

	if (cell != null && model.isVertex(cell))
	{
		var parent = model.getParent(cell);

		if (model.isVertex(parent))
		{
			if (this.isCollapsedTable(parent))
			{
				return parent;
			}

			var table = model.getParent(parent);

			if (this.isCollapsedTable(table))
			{
				return table;
			}
		}
	}

	return null;
};

Graph.prototype.getCollapsedTableShapeInfo = function(state)
{
	var shape = (state != null) ? state.shape : null;
	var result = null;

	if (shape != null)
	{
		var saved = (shape.collapsedTableStyle != null) ?
			shape.collapsedTableStyle : shape;
		result = {fill: saved.fill, gradient: saved.gradient,
			laneFill: saved.laneFill, gradientDirection: shape.gradientDirection,
			opacity: (shape.opacity != null) ? shape.opacity : 100,
			fillOpacity: (shape.fillOpacity != null) ? shape.fillOpacity : 100,
			border: this.createCollapsedTableBorder(saved.stroke, shape)};
	}

	return result;
};

Graph.prototype.isCollapsedTableChildShape = function(state)
{
	if (state != null && state.style != null &&
		this.getCollapsedTableForCell(state.cell) != null)
	{
		var shape = state.style[mxConstants.STYLE_SHAPE];

		return shape == 'tableRow' || shape == 'partialRectangle' ||
			((shape == null || shape == mxConstants.SHAPE_RECTANGLE ||
			shape == mxConstants.SHAPE_SWIMLANE) && mxUtils.getValue(
			state.style, mxConstants.STYLE_ROUNDED, 0) != 1);
	}

	return false;
};

Graph.prototype.getCollapsedTableBorderKey = function(border)
{
	return (border != null && border.color != null && border.color != mxConstants.NONE &&
		border.width > 0) ? [border.color, border.width, (border.dashed) ? 1 : 0,
		border.dashPattern, (border.fixDash) ? 1 : 0, border.opacity,
		border.strokeOpacity].join('|') : null;
};

Graph.prototype.getCollapsedTableFillMode = function(style, start)
{
	var shape = style[mxConstants.STYLE_SHAPE];
	var empty = start.x == 0 && start.y == 0 &&
		start.width == 0 && start.height == 0;

	if (shape == 'table' || shape == 'tableRow')
	{
		var fixed = mxUtils.getValue(style, mxConstants.STYLE_FIXED_HEADER, false);

		// Headerless rounded tables without collapsed borders fill their
		// rounded rectangle with the fill color (see TableShape)
		var rounded = style['tableRender'] != 'collapsed' &&
			mxUtils.getValue(style, mxConstants.STYLE_ROUNDED, 0) == 1;

		return (empty && (!fixed || rounded)) ? 'full' : 'lane';
	}
	else if (shape == mxConstants.SHAPE_SWIMLANE)
	{
		var fixed = mxUtils.getValue(style, mxConstants.STYLE_FIXED_HEADER, true);

		return (empty && !fixed) ? 'none' : 'lane';
	}

	return 'full';
};

Graph.prototype.getCollapsedTableBands = function(x, y, w, h, start)
{
	var top = Math.min(start.y, h);
	var bottom = Math.min(start.height, h - top);
	var left = Math.min(start.x, w);
	var right = Math.min(start.width, w - left);
	var bh = h - top - bottom;
	var bands = [];
	var sides = [];

	if (top > 0)
	{
		bands.push(new mxRectangle(x, y, w, top));
		sides.push(mxConstants.DIRECTION_NORTH);
	}

	if (bottom > 0)
	{
		bands.push(new mxRectangle(x, y + h - bottom, w, bottom));
		sides.push(mxConstants.DIRECTION_SOUTH);
	}

	if (left > 0 && bh > 0)
	{
		bands.push(new mxRectangle(x, y + top, left, bh));
		sides.push(mxConstants.DIRECTION_WEST);
	}

	if (right > 0 && bh > 0)
	{
		bands.push(new mxRectangle(x + w - right, y + top, right, bh));
		sides.push(mxConstants.DIRECTION_EAST);
	}

	return {bands: bands, sides: sides, body: new mxRectangle(x + left, y + top,
		Math.max(0, w - left - right), Math.max(0, bh))};
};

Graph.prototype.getCollapsedTableRegions = function(table, w, h)
{
	var model = this.model;
	var tableStyle = this.getCurrentCellStyle(table);
	var start = this.getActualStartSize(table, true);
	var title = this.getCollapsedTableBands(0, 0, w, h, start);
	var result = {start: start, title: title, rows: [], cells: [], regions: []};
	var rowLookup = new mxDictionary();

	// Visible sides as in PartialRectangleShape (missing flags are true)
	var getSides = function(style)
	{
		return {top: mxUtils.getValue(style, 'top', '1') == '1',
			left: mxUtils.getValue(style, 'left', '1') == '1',
			bottom: mxUtils.getValue(style, 'bottom', '1') == '1',
			right: mxUtils.getValue(style, 'right', '1') == '1'};
	};

	var addRegion = function(kind, rect, data)
	{
		if (rect.width > 0 && rect.height > 0)
		{
			result.regions.push({kind: kind, x: rect.x, y: rect.y,
				width: rect.width, height: rect.height, data: data});
		}
	};

	var titleLine = mxUtils.getValue(tableStyle,
		mxConstants.STYLE_SWIMLANE_LINE, 1) != 0;

	for (var i = 0; i < title.bands.length; i++)
	{
		addRegion('title', title.bands[i], {line: titleLine});
	}

	var rows = model.getChildCells(table, true);

	for (var i = 0; i < rows.length; i++)
	{
		var geo = this.getCellGeometry(rows[i]);

		if (geo != null && model.isVisible(rows[i]))
		{
			var state = this.view.getState(rows[i]);
			var style = this.getCurrentCellStyle(rows[i]);
			var rowStart = this.getActualStartSize(rows[i], true);
			var info = this.getCollapsedTableShapeInfo(state);
			var takeover = this.isCollapsedTableChildShape(state);
			var row = {cell: rows[i], index: i, state: state, info: info,
				takeover: takeover, sides: getSides(style),
				line: mxUtils.getValue(style, mxConstants.STYLE_SWIMLANE_LINE, 1) != 0,
				x: geo.x, y: geo.y, width: geo.width, height: geo.height,
				mode: this.getCollapsedTableFillMode(style, rowStart),
				split: this.getCollapsedTableBands(geo.x, geo.y,
					geo.width, geo.height, rowStart)};

			for (var j = 0; j < row.split.bands.length; j++)
			{
				addRegion('strip', row.split.bands[j], {row: row, line: row.line});
			}

			rowLookup.put(rows[i], row);
			result.rows.push(row);
		}
	}

	this.visitTableCells(table, mxUtils.bind(this, function(iter)
	{
		var row = rowLookup.get(model.getParent(iter.cell));

		if (row != null && iter.actual.cell == iter.cell &&
			model.isVisible(iter.cell))
		{
			var geo = iter.geo;
			var state = this.view.getState(iter.cell);
			var style = this.getCurrentCellStyle(iter.cell);
			var cellStart = this.getActualStartSize(iter.cell, true);
			var info = this.getCollapsedTableShapeInfo(state);
			var takeover = this.isCollapsedTableChildShape(state);
			var x = row.x + geo.x;
			var y = row.y + geo.y;
			var cell = {cell: iter.cell, row: row, state: state, info: info,
				takeover: takeover, sides: getSides(style),
				line: mxUtils.getValue(style, mxConstants.STYLE_SWIMLANE_LINE, 1) != 0,
				x: x, y: y, width: geo.width, height: geo.height,
				mode: this.getCollapsedTableFillMode(style, cellStart),
				split: this.getCollapsedTableBands(x, y, geo.width,
					geo.height, cellStart)};
			result.cells.push(cell);

			if (cell.mode == 'lane')
			{
				for (var j = 0; j < cell.split.bands.length; j++)
				{
					addRegion('cellStrip', cell.split.bands[j],
						{row: row, cell: cell, line: cell.line});
				}

				addRegion('cell', cell.split.body, {row: row, cell: cell, line: cell.line});
			}
			else
			{
				addRegion('cell', cell, {row: row, cell: cell, line: cell.line});
			}
		}
	}));

	return result;
};

Graph.prototype.getCollapsedTableBorders = function(table, w, h, tableBorder, data)
{
	data = (data != null) ? data : this.getCollapsedTableRegions(table, w, h);
	var style = this.getCurrentCellStyle(table);
	var rowLines = mxUtils.getValue(style, 'rowLines', '1') != '0';
	var columnLines = mxUtils.getValue(style, 'columnLines', '1') != '0';
	var tableKey = this.getCollapsedTableBorderKey(tableBorder);
	var result = {lines: [], outline: {top: [], right: [], bottom: [], left: []}};
	var bounds = new mxRectangle(0, 0, w, h);
	var eps = 0.01;
	var graph = this;

	function round(value)
	{
		return Math.round(value * 100) / 100;
	};

	function addSide(map, pos, a, b, region, before, side)
	{
		pos = round(pos);
		var sides = map[pos];

		if (sides == null)
		{
			sides = [];
			map[pos] = sides;
		}

		sides.push({a: round(Math.min(a, b)), b: round(Math.max(a, b)),
			region: region, before: before, side: side, pos: pos});
	};

	// Returns true if the given side at pos is the given side of the given
	// table, row or cell (as opposed to an inner title divider)
	function isOwnerSide(owner, side, pos)
	{
		var value = (side == 'top') ? owner.y : ((side == 'bottom') ?
			owner.y + owner.height : ((side == 'left') ? owner.x :
			owner.x + owner.width));

		return Math.abs(round(value) - pos) < eps;
	};

	// Returns true if the given region side divides a title strip from
	// the body of the table, row or cell of the region
	function isDivider(entry)
	{
		var region = entry.region;
		var owner = (region.kind == 'title') ? bounds : ((region.kind == 'strip') ?
			region.data.row : region.data.cell);

		return !isOwnerSide(owner, entry.side, entry.pos);
	};

	// Returns true if the given region side is hidden by a 0 flag of its
	// cell or row or by swimlaneLine=0 for a divider
	function isHidden(entry)
	{
		var cell = entry.region.data.cell;
		var row = entry.region.data.row;

		if (isDivider(entry))
		{
			return !entry.region.data.line;
		}

		return (cell != null && isOwnerSide(cell, entry.side, entry.pos) &&
			!cell.sides[entry.side]) || (row != null && isOwnerSide(row,
			entry.side, entry.pos) && !row.sides[entry.side]);
	};

	// Returns the most specific stroke for the given region side
	function getClaim(entry)
	{
		var cell = entry.region.data.cell;
		var row = entry.region.data.row;
		var rowKey = (row != null && row.info != null) ?
			graph.getCollapsedTableBorderKey(row.info.border) : tableKey;

		if (cell != null && cell.takeover && cell.info != null &&
			graph.getCollapsedTableBorderKey(cell.info.border) != rowKey)
		{
			return {level: 2, border: cell.info.border};
		}
		else if (row != null && row.takeover && row.info != null &&
			rowKey != tableKey)
		{
			return {level: 1, border: row.info.border};
		}

		return {level: 0, border: tableBorder};
	};

	function resolve(claims, horizontal, outer)
	{
		var divider = false;
		var best = null;

		for (var i = 0; i < claims.length; i++)
		{
			if (isHidden(claims[i]))
			{
				return null;
			}

			divider = divider || isDivider(claims[i]);
			var claim = getClaim(claims[i]);

			if (claim.level > 0 && (best == null || claim.level > best.level ||
				(claim.level == best.level && (claim.border.width > best.border.width ||
				(claim.border.width == best.border.width && claims[i].before &&
				!best.before)))))
			{
				best = {level: claim.level, border: claim.border,
					before: claims[i].before};
			}
		}

		if (best != null)
		{
			return (graph.getCollapsedTableBorderKey(best.border) != null) ?
				best.border : null;
		}
		else if (!outer && !divider && ((horizontal) ? !rowLines : !columnLines))
		{
			return null;
		}

		return (tableKey != null) ? tableBorder : null;
	};

	function process(map, horizontal)
	{
		for (var key in map)
		{
			var sides = map[key];
			var pos = parseFloat(key);
			var points = [];

			sides.sort(function(s1, s2)
			{
				return s1.a - s2.a;
			});

			for (var i = 0; i < sides.length; i++)
			{
				points.push(sides[i].a, sides[i].b);
			}

			points.sort(function(p1, p2)
			{
				return p1 - p2;
			});

			var outerSide = null;

			if (horizontal)
			{
				outerSide = (Math.abs(pos) < eps) ? result.outline.top :
					((Math.abs(pos - round(h)) < eps) ? result.outline.bottom : null);
			}
			else
			{
				outerSide = (Math.abs(pos) < eps) ? result.outline.left :
					((Math.abs(pos - round(w)) < eps) ? result.outline.right : null);
			}

			var active = [];
			var next = 0;
			var current = null;

			for (var i = 0; i < points.length - 1; i++)
			{
				var p = points[i];
				var q = points[i + 1];

				if (q - p <= eps)
				{
					continue;
				}

				while (next < sides.length && sides[next].a <= p + eps)
				{
					active.push(sides[next++]);
				}

				var claims = [];
				var outer = false;
				var temp = [];

				for (var j = 0; j < active.length; j++)
				{
					if (active[j].b > p + eps)
					{
						temp.push(active[j]);

						if (active[j].b >= q - eps)
						{
							if (active[j].region == null)
							{
								outer = true;
							}
							else
							{
								claims.push(active[j]);
							}
						}
					}
				}

				active = temp;

				if (outer && outerSide != null)
				{
					var border = resolve(claims, horizontal, true);
					var last = (outerSide.length > 0) ? outerSide[outerSide.length - 1] : null;

					if (last != null && Math.abs(last.b - p) < eps &&
						graph.getCollapsedTableBorderKey(last.border) ==
						graph.getCollapsedTableBorderKey(border))
					{
						last.b = q;
					}
					else
					{
						outerSide.push({a: p, b: q, border: border});
					}

					current = null;
				}
				else if (claims.length > 0)
				{
					var border = resolve(claims, horizontal, false);
					var borderKey = graph.getCollapsedTableBorderKey(border);

					if (borderKey == null)
					{
						current = null;
					}
					else if (current != null && current.key == borderKey &&
						Math.abs(current.b - p) < eps)
					{
						current.b = q;
					}
					else
					{
						current = {key: borderKey, border: border, a: p, b: q, pos: pos};
						result.lines.push(current);
					}
				}
				else
				{
					current = null;
				}
			}
		}
	};

	var hor = {};
	var ver = {};

	for (var i = 0; i < data.regions.length; i++)
	{
		var r = data.regions[i];
		addSide(hor, r.y, r.x, r.x + r.width, r, false, 'top');
		addSide(hor, r.y + r.height, r.x, r.x + r.width, r, true, 'bottom');
		addSide(ver, r.x, r.y, r.y + r.height, r, false, 'left');
		addSide(ver, r.x + r.width, r.y, r.y + r.height, r, true, 'right');
	}

	addSide(hor, 0, 0, w, null, false);
	addSide(hor, h, 0, w, null, true);
	addSide(ver, 0, 0, h, null, false);
	addSide(ver, w, 0, h, null, true);

	var count = result.lines.length;
	process(hor, true);

	// Converts intervals to segments
	for (var i = 0; i < result.lines.length; i++)
	{
		var line = result.lines[i];
		result.lines[i] = (i < count) ? line : {x1: line.a, y1: line.pos,
			x2: line.b, y2: line.pos, border: line.border, key: line.key};
	}

	count = result.lines.length;
	process(ver, false);

	for (var i = count; i < result.lines.length; i++)
	{
		var line = result.lines[i];
		result.lines[i] = {x1: line.pos, y1: line.a, x2: line.pos,
			y2: line.b, border: line.border, key: line.key};
	}

	return result;
};

Graph.prototype.createCollapsedTableBorder = function(stroke, shape)
{
	var style = (shape.style != null) ? shape.style : {};

	return {color: (stroke != null) ? stroke : mxConstants.NONE,
		width: (shape.strokewidth != null) ? Number(shape.strokewidth) : 1,
		dashed: shape.isDashed == true, dashPattern: (shape.isDashed) ?
			style['dashPattern'] : null, fixDash: mxUtils.getValue(style,
			mxConstants.STYLE_FIX_DASH, 0) == 1,
		opacity: (shape.opacity != null) ? shape.opacity : 100,
		strokeOpacity: (shape.strokeOpacity != null) ? shape.strokeOpacity : 100};
};

Graph.lineJumpsEnabled = true;

Graph.getMultipleCopies = function(style)
{
	var copies = null;

	if (style != null && mxUtils.getValue(style, 'multiple', '0') == '1')
	{
		var count = Math.min(Graph.maxMultipleCount, parseInt(mxUtils.getValue(
			style, 'multipleCount', Graph.defaultMultipleCount)));
		var dx = parseFloat(mxUtils.getValue(style,
			'multipleDx', Graph.defaultMultipleDx));
		var dy = parseFloat(mxUtils.getValue(style,
			'multipleDy', Graph.defaultMultipleDy));

		if (count > 0 && isFinite(dx) && isFinite(dy))
		{
			copies = {count: count, dx: dx, dy: dy};
		}
	}

	return copies;
};

Graph.getEdgeCurvePoints = function(state)
{
	var pts = state.absolutePoints;
	var style = state.style;

	return (pts != null && pts.length > 2 && pts.indexOf(null) < 0 &&
		style != null && (style[mxConstants.STYLE_CURVED] == 1 ||
		style[mxConstants.STYLE_BEZIER] == 1) && Graph.edgeSupportsCurved(style)) ?
		Graph.getCurvePoints(pts, style[mxConstants.STYLE_BEZIER] == 1) : null;
};

Graph.curvePointSteps = 16;

Graph.defaultJumpSize = 6;

Graph.getCurveSegments = function(pts, bezier)
{
	var segs = [];
	var n = pts.length;

	if (bezier && (n - 1) % 3 == 0)
	{
		for (var i = 1; i + 2 < n; i += 3)
		{
			segs.push([pts[i - 1], pts[i], pts[i + 1], pts[i + 2]]);
		}
	}
	else
	{
		var prev = pts[0];

		for (var i = 1; i < n - 2; i++)
		{
			var mid = new mxPoint((pts[i].x + pts[i + 1].x) / 2,
				(pts[i].y + pts[i + 1].y) / 2);
			segs.push([prev, pts[i], mid]);
			prev = mid;
		}

		segs.push([prev, pts[n - 2], pts[n - 1]]);
	}

	return segs;
};

Graph.decompress = function(data, inflate, checked)
{
   	if (data == null || data.length == 0 || typeof(pako) === 'undefined')
	{
		return data;
	}
	else
	{
		var tmp = Graph.stringToArrayBuffer(atob(data));
		var inflated = decodeURIComponent((inflate) ?
			pako.inflate(tmp, {to: 'string'}) :
			pako.inflateRaw(tmp, {to: 'string'}));

		return (checked) ? inflated : Graph.zapGremlins(inflated);
	}
};

Graph.prototype.isEdgeLabelCell = function(cell)
{
	var geo = this.getCellGeometry(cell);

	return geo != null && this.model.isVertex(cell) &&
		this.model.isEdge(this.model.getParent(cell)) &&
		!(geo.width > 0 && geo.height > 0);
};

Graph.prototype.isContainer = function(cell)
{
	var style = this.getCurrentCellStyle(cell);

	if (this.isSwimlane(cell))
	{
		return style['container'] != '0';
	}
	else
	{
		return style['container'] == '1';
	}
};

Graph.prototype.convertValueToTooltip = function(cell, limitWidth)
{
	var tmp = null;

	if (mxUtils.isNode(cell.value))
	{
		if (Graph.translateDiagram && Graph.diagramLanguage != null)
		{
			tmp = cell.value.getAttribute('tooltip_' + Graph.diagramLanguage);
		}
		
		if (tmp == null)
		{
			tmp = cell.value.getAttribute('tooltip');
		}
		
		if (tmp != null)
		{
			if (tmp != null && this.isReplacePlaceholders(cell))
			{
				tmp = this.replacePlaceholders(cell, tmp, null, null, true);
			}
			
			tmp = Graph.sanitizeHtml(tmp);

			if (limitWidth && tmp.length > 0 && Editor.tooltipMaxWidth > 0)
			{
				tmp = '<div style="max-width:' + Editor.tooltipMaxWidth +
					'px;text-overflow:ellipsis;overflow:hidden;">' + tmp + '</div>';
			}
		}
	}

	return tmp;
};

Graph.prototype.addFlowAnimationToNode = function(node, style, scale, id, root)
{
	if (node != null && id != null && this.getFlowAnimationType(style) != 'dash')
	{
		this.addFlowRingsToNode(node, style, scale, id, root);
	}
	else if (node != null && id != null)
	{
		var dashArray = node.getAttribute('stroke-dasharray');
		var tokens = [];

		if (dashArray == '' || dashArray == null)
		{
			tokens = String(mxUtils.getValue(style, mxConstants.STYLE_DASH_PATTERN, '8')).split(' ');
			var sw = (mxUtils.getValue(style, mxConstants.STYLE_FIX_DASH, false) == 1 ||
				style['dashPattern'] == null) ? 1 : mxUtils.getNumber(style,
					mxConstants.STYLE_STROKEWIDTH, 1);

			if (tokens.length > 0)
			{
				for (var i = 0; i < tokens.length; i++)
				{
					tokens[i] = Math.round(Number(tokens[i]) * scale * sw * 100) / 100;
				}
			}

			node.setAttribute('stroke-dasharray', tokens.join(' '));
		}
		else
		{
			tokens = dashArray.split(' ');
		}

		if (tokens.length > 0)
		{
			var sum = 0;

			for (var i = 0; i < tokens.length; i++)
			{
				var temp = parseFloat(tokens[i]);

				if (!isNaN(temp))
				{
					sum += parseFloat(tokens[i]);
				}
			}
			
			// If an odd number of values is provided, then the list of
			// values is repeated to yield an even number of values
			if (tokens.length % 2 != 0)
			{
				sum *= 2;
			}

			var d = Math.round((sum / scale / 16) * parseInt(mxUtils.getValue(
				style, 'flowAnimationDuration', 500)));
			var tf = mxUtils.getValue(style, 'flowAnimationTimingFunction', 'linear');
			var ad = mxUtils.getValue(style, 'flowAnimationDirection', 'normal');
			node.style.animation = id + ' ' + d + 'ms ' + mxUtils.htmlEntities(tf) +
				' infinite ' + mxUtils.htmlEntities(ad);
			node.style.strokeDashoffset = sum;
		}
	}
};

Graph.prototype.addFlowAnimationStyle = function()
{
	var head = document.getElementsByTagName('head')[0];
	
	if (head != null && this.flowAnimationId == null)
	{
		this.flowAnimationId = 'ge-flow-animation-' + Editor.guid();
		var style = document.createElement('style');
		style.innerHTML = this.createFlowAnimationCss(
			this.flowAnimationId);
		head.appendChild(style);
	}

	return this.flowAnimationId;
};

Graph.prototype.getAdaptiveColors = function()
{
	return (this.adaptiveColors != null) ? this.adaptiveColors : Graph.defaultAdaptiveColors;
};

Graph.prototype.cacheLockedStates = function(fn, reset)
{
	var result = null;

	if (this.lockedStatesCache != null)
	{
		if (reset)
		{
			this.resetLockedStatesCache();
		}

		try
		{
			result = fn();
		}
		finally
		{
			if (reset)
			{
				this.resetLockedStatesCache();
			}
		}
	}
	else
	{
		var resetCache = mxUtils.bind(this, this.resetLockedStatesCache);
		resetCache();
		this.model.addListener(mxEvent.EXECUTED, resetCache);
		this.model.addListener(mxEvent.CHANGE, resetCache);

		try
		{
			result = this.cacheChildLayoutStyles(fn);
		}
		finally
		{
			this.model.removeListener(resetCache);
			this.lockedStatesCache = null;
		}
	}

	return result;
};

Graph.prototype.cachePlaceholderCells = function(fn)
{
	if (this.placeholderCells != null)
	{
		return fn();
	}

	this.placeholderCells = new Map();
	this.placeholderCellCount = 0;

	try
	{
		return fn();
	}
	finally
	{
		this.placeholderCells = null;
	}
};

Graph.prototype.isTransparentBounds = function(cell)
{
	var style = this.getCurrentCellStyle(cell);

	return style['transparentBounds'] == '1';
};

Graph.prototype.getTransparentBounds = function(cell, state, ignoreState, cache)
{
	// Bounds of cells whose style is updated later in the validation of
	// the view are not cached (see mxGraphView.validateCellState)
	var viewState = (cache != null) ? this.view.getState(cell) : null;
	var cached = cache != null && (viewState == null || !viewState.invalidStyle);
	var result = (cached) ? cache.get(cell) : undefined;

	if (result === undefined)
	{
		var bounds = this.getTransparentChildBounds(cell, state, cache);
		result = null;

		if (bounds != null)
		{
			var pad = this.getTransparentBoundsPadding(cell, ignoreState);
			var start = this.isSwimlane(cell) ?
				this.getActualStartSize(cell) : new mxRectangle();
			var footer = this.getActualFooterSize(cell);

			result = new mxRectangle(
				bounds.x - pad.w - start.x - footer.x,
				bounds.y - pad.n - start.y - footer.y,
				bounds.width + pad.w + pad.e + start.x + start.width + footer.x + footer.width,
				bounds.height + pad.n + pad.s + start.y + start.height + footer.y + footer.height);
		}

		if (cached)
		{
			cache.put(cell, result);
		}
	}

	return result;
};

Graph.prototype.getLayerForCell = function(cell)
{
	while (cell != null && !this.model.isLayer(cell))
	{
		cell = this.model.getParent(cell);
	}

	return cell;
};

Graph.prototype.getTieIndex = function()
{
	// Pages are switched without a change of the model (see SelectPage)
	if (this.tieIndex == null || this.tieIndex.root != this.model.getRoot())
	{
		var ties = Object.create(null);
		var edges = Object.create(null);
		var positions = Object.create(null);
		var stack = [this.model.getRoot()];

		while (stack.length > 0)
		{
			var cell = stack.pop();

			if (cell != null)
			{
				var ids = (this.model.isEdge(cell)) ?
					this.getTieIdsForStyle(cell.style) : null;

				if (ids != null)
				{
					var pos = Object.create(null);
					edges[cell.id] = ids;
					positions[cell.id] = pos;

					for (var i = 0; i < ids.length; i++)
					{
						pos[ids[i]] = i;
						ties[ids[i]] = ties[ids[i]] || [];
						ties[ids[i]].push(cell);
					}
				}

				for (var i = this.model.getChildCount(cell) - 1; i >= 0; i--)
				{
					stack.push(this.model.getChildAt(cell, i));
				}
			}
		}

		this.tieIndex = {root: this.model.getRoot(), ties: ties,
			edges: edges, positions: positions};
	}

	return this.tieIndex;
};

Graph.prototype.isTie = function(cell)
{
	return this.model.isVertex(cell) && mxUtils.getValue(
		this.getCurrentCellStyle(cell), 'tie', '0') == '1';
};

Graph.prototype.getTieMembers = function(id)
{
	return this.getTieIndex().ties[id] || [];
};

Graph.prototype.isTieStyle = function(style)
{
	return typeof style === 'string' && (style.indexOf('tie=1') >= 0 ||
		style.indexOf('ties=') >= 0);
};

Graph.prototype.addChangedTieStyle = function(cell, style)
{
	var view = this.view;
	var ids = this.getTieIdsForStyle(style);
	view.changedTies = view.changedTies || Object.create(null);

	for (var i = 0; ids != null && i < ids.length; i++)
	{
		view.changedTies[ids[i]] = true;
	}

	if (style.indexOf('tie=1') >= 0)
	{
		view.changedTies[cell.id] = true;
	}
};

Graph.maxMultipleCount = 10;

Graph.defaultMultipleCount = 2;

Graph.defaultMultipleDx = 8;

Graph.defaultMultipleDy = -8;

Graph.edgeSupportsCurved = function(style)
{
	var shape = mxUtils.getValue(style, mxConstants.STYLE_SHAPE, null);

	return shape == null || shape == 'connector' || shape == 'filledEdge' ||
		shape == 'wire' || shape == 'pipe' || shape == 'link' ||
		shape == 'flexArrow' || shape == 'taperedArrow';
};

Graph.getCurvePoints = function(pts, bezier)
{
	var n = pts.length;

	if (n < 3 || pts.indexOf(null) >= 0)
	{
		return pts;
	}

	var steps = Graph.curvePointSteps;
	var result = [pts[0]];

	var quad = function(p0, pc, p1)
	{
		for (var t = 1; t <= steps; t++)
		{
			var u = t / steps, iu = 1 - u;
			result.push(new mxPoint(
				iu * iu * p0.x + 2 * iu * u * pc.x + u * u * p1.x,
				iu * iu * p0.y + 2 * iu * u * pc.y + u * u * p1.y));
		}
	};

	if (bezier && (n - 1) % 3 == 0)
	{
		// Points are cubic control points: [anchor, cp1, cp2, anchor, ...]
		for (var i = 1; i + 2 < n; i += 3)
		{
			var p0 = pts[i - 1], c1 = pts[i], c2 = pts[i + 1], p1 = pts[i + 2];

			for (var t = 1; t <= steps; t++)
			{
				var u = t / steps, iu = 1 - u;
				result.push(new mxPoint(
					iu * iu * iu * p0.x + 3 * iu * iu * u * c1.x + 3 * iu * u * u * c2.x + u * u * u * p1.x,
					iu * iu * iu * p0.y + 3 * iu * iu * u * c1.y + 3 * iu * u * u * c2.y + u * u * u * p1.y));
			}
		}
	}
	else
	{
		// Quadratic through the midpoints of consecutive control points
		var prev = pts[0];

		for (var i = 1; i < n - 2; i++)
		{
			var mid = new mxPoint((pts[i].x + pts[i + 1].x) / 2,
				(pts[i].y + pts[i + 1].y) / 2);
			quad(prev, pts[i], mid);
			prev = mid;
		}

		quad(prev, pts[n - 2], pts[n - 1]);
	}

	return result;
};

Graph.stringToArrayBuffer = function(data)
{
	return Uint8Array.from(data, function (c)
	{
	  return c.charCodeAt(0);
	});
};

Graph.zapGremlins = function(text)
{
	return mxUtils.zapGremlins(text);
};

Graph.translateDiagram = urlParams['translate-diagram'] == '1';

Graph.diagramLanguage = (urlParams['diagram-language'] != null) ? urlParams['diagram-language'] : mxClient.language;

Graph.sanitizeHtml = function(value, editing)
{
	return Graph.domPurify(value, false);
};

Graph.prototype.getFlowAnimationType = function(style)
{
	var type = mxUtils.getValue(style, 'flowAnimationType', null);

	return (type == 'ring' || type == 'dot') ? type : 'dash';
};

Graph.prototype.addFlowRingsToNode = function(node, style, scale, id, root)
{
	var parent = node.parentNode;

	// Removes the rings of an earlier call for the same paint
	if (parent != null)
	{
		var child = parent.lastChild;

		while (child != null)
		{
			var prev = child.previousSibling;

			if (child.nodeType == mxConstants.NODETYPE_ELEMENT)
			{
				if ((child.nodeName == 'mask' && String(child.getAttribute('id')).
					substring(0, 13) == 'ge-flow-ring-') || (child.nodeName == 'path' &&
					child.getAttribute('pathLength') != null))
				{
					parent.removeChild(child);
				}
				else if (String(child.getAttribute('mask')).indexOf('#ge-flow-ring-') >= 0)
				{
					child.removeAttribute('mask');
				}
			}

			child = prev;
		}
	}

	// The first path of the shape is the plain geometry of the line, also for
	// sketch=1, where the line has two rough strokes (see getFlowAnimationPath)
	// and the sketch paint keeps the plain line in exports without pointer
	// events. With opacityGroup=1 each pass of the sketch paint is a group of
	// its own, so the plain line is not in the parent of the rough strokes.
	root = (root != null) ? root : parent;
	var first = (root != null) ? root.getElementsByTagName('path')[0] : null;
	var d = (first != null) ? first.getAttribute('d') : null;
	var bounds = Graph.getPathDataBounds(d);
	d = Graph.getContinuousPathData(d);

	if (bounds != null)
	{
		var doc = node.ownerDocument;
		var hollow = this.getFlowAnimationType(style) == 'ring';
		var sw = parseFloat(node.getAttribute('stroke-width'));
		sw = (sw > 0 && isFinite(sw)) ? sw : 1;
		var size = parseFloat(mxUtils.getValue(style, 'flowAnimationSize', null));

		// The hole of a ring is at least as wide as the line
		size = (isFinite(size)) ? Math.max(((hollow) ? 3 : 2) * sw,
			Math.min(size, Graph.maxFlowRingSize) * scale) :
			((hollow) ? Math.max(4 * sw, Graph.defaultFlowRingSize * scale) :
			Math.max(3 * sw, Graph.defaultFlowDotSize * scale));
		var count = parseInt(mxUtils.getValue(style, 'flowAnimationCount', 1));
		count = (count > 1) ? Math.min(count, Graph.maxFlowRingCount) : 1;
		var loop = mxUtils.getValue(style, 'flowAnimationLoop', '1') != '0';
		var dur = parseInt(mxUtils.getValue(style,
			'flowAnimationDuration', Graph.defaultFlowRingDuration));
		dur = (dur > 0) ? dur : Graph.defaultFlowRingDuration;
		var tf = mxUtils.getValue(style, 'flowAnimationTimingFunction', 'linear');
		var ad = mxUtils.getValue(style, 'flowAnimationDirection', 'normal');
		var spacing = Math.round(Graph.flowRingPeriod / count * 10000) / 10000;
		var dashArray = '0 ' + spacing;
		var offset = Graph.flowRingOffset;
		var delay = -Math.round(dur / 2);

		if (!loop)
		{
			dashArray = '0 ' + (3 * Graph.flowRingPeriod);
			offset = -2 * Graph.flowRingPeriod;
			delay = 0;

			for (var i = 1; i < count; i++)
			{
				dashArray += ' 0 ' + spacing;
			}

			// A reverse pass starts when the last ring enters the end
			if (ad == 'reverse' || ad == 'alternate-reverse')
			{
				delay = -Math.round(dur * (Graph.flowRingPeriod + 1 -
					(count - 1) * spacing) / Graph.flowRingPeriod);
			}
		}

		var animation = id + ((loop) ? '-ring ' : '-ring-once ') +
			((loop) ? dur : 2 * dur) + 'ms ' + mxUtils.htmlEntities(tf) +
			' ' + delay + 'ms ' + ((loop) ? 'infinite' : '1') +
			' ' + mxUtils.htmlEntities(ad);
		var masked = [];

		// Masks the painted line and markers under rings and translucent dots
		if (hollow || node.getAttribute('stroke-opacity') != null)
		{
			for (var child = parent.firstChild; child != null; child = child.nextSibling)
			{
				if ((child.nodeName == 'path' || child.nodeName == 'ellipse') &&
					child.getAttribute('visibility') != 'hidden' &&
					(child.getAttribute('stroke') != 'none' ||
					child.getAttribute('fill') != 'none'))
				{
					if (child.nodeName == 'path')
					{
						var b = Graph.getPathDataBounds(child.getAttribute('d'));

						if (b != null)
						{
							bounds.add(b);
						}
					}
					else
					{
						var rx = parseFloat(child.getAttribute('rx'));
						var ry = parseFloat(child.getAttribute('ry'));
						var b = new mxRectangle(parseFloat(child.getAttribute('cx')) - rx,
							parseFloat(child.getAttribute('cy')) - ry, 2 * rx, 2 * ry);

						if (isFinite(b.x) && isFinite(b.y) && isFinite(b.width) && isFinite(b.height))
						{
							bounds.add(b);
						}
					}

					masked.push(child);
				}
			}
		}

		// The default mask region is empty for horizontal and vertical lines,
		// and the stroke can go out by 5 times its width (miter limit)
		bounds.grow(Math.max(size / 2, 5 * sw) + 1);

		// Workaround for references with a base tag in the page
		var base = (doc == document && mxSvgCanvas2D.prototype.useAbsoluteIds) ?
			mxSvgCanvas2D.prototype.getBaseUrl().replace(/([\(\)])/g, '\\$1') : '';

		var createDots = function(width, color)
		{
			var dots = doc.createElementNS(mxConstants.NS_SVG, 'path');
			dots.setAttribute('d', d);
			dots.setAttribute('fill', 'none');
			dots.setAttribute('stroke', color);
			dots.setAttribute('stroke-width', Math.round(width * 100) / 100);
			dots.setAttribute('stroke-linecap', 'round');
			dots.setAttribute('stroke-dasharray', dashArray);
			dots.setAttribute('pathLength', '100');
			dots.setAttribute('pointer-events', 'none');
			dots.style.animation = animation;
			dots.style.strokeDashoffset = offset;

			return dots;
		};

		// Returns the reference of a mask that hides discs of the given size
		var createMask = function(width)
		{
			var mask = doc.createElementNS(mxConstants.NS_SVG, 'mask');
			var maskId = 'ge-flow-ring-' + Editor.guid();
			mask.setAttribute('id', maskId);
			mask.setAttribute('maskUnits', 'userSpaceOnUse');

			var rect = doc.createElementNS(mxConstants.NS_SVG, 'rect');
			rect.setAttribute('fill', 'white');

			var attrs = ['x', 'y', 'width', 'height'];

			for (var i = 0; i < attrs.length; i++)
			{
				var value = Math.round(bounds[attrs[i]] * 100) / 100;
				mask.setAttribute(attrs[i], value);
				rect.setAttribute(attrs[i], value);
			}

			mask.appendChild(rect);
			mask.appendChild(createDots(width, 'black'));
			parent.appendChild(mask);

			return 'url(' + base + '#' + maskId + ')';
		};

		if (masked.length > 0)
		{
			var lineMask = createMask(size);

			for (var i = 0; i < masked.length; i++)
			{
				masked[i].setAttribute('mask', lineMask);
			}
		}

		// A ring is a dot in the color of the line with a hole
		var dots = createDots(size, node.getAttribute('stroke'));
		dots.style.stroke = node.style.stroke;

		if (node.getAttribute('stroke-opacity') != null)
		{
			dots.setAttribute('stroke-opacity', node.getAttribute('stroke-opacity'));
		}

		if (hollow)
		{
			dots.setAttribute('mask', createMask(size - 2 * sw));
		}

		parent.appendChild(dots);
	}
};

Graph.prototype.createFlowAnimationCss = function(id)
{
	return '@keyframes ' + id + ' {\n' +
	'  to {\n' +
	'    stroke-dashoffset: 0;\n' +
	'  }\n' +
	'}\n' +
	'@keyframes ' + id + '-ring {\n' +
	'  from {\n' +
	'    stroke-dashoffset: ' + Graph.flowRingPeriod + ';\n' +
	'  }\n' +
	'  to {\n' +
	'    stroke-dashoffset: 0;\n' +
	'  }\n' +
	'}\n' +
	'@keyframes ' + id + '-ring-once {\n' +
	'  from {\n' +
	'    stroke-dashoffset: 0;\n' +
	'  }\n' +
	'  to {\n' +
	'    stroke-dashoffset: ' + (-2 * Graph.flowRingPeriod) + ';\n' +
	'  }\n' +
	'}\n' +
	'@media (prefers-reduced-motion: reduce) {\n' +
	'  [style*="' + id + '"] {\n' +
	'    animation: none !important;\n' +
	'  }\n' +
	'}';
};

Graph.defaultAdaptiveColors = (urlParams['contrast'] == '0') ? 'simple' : 'auto';

Graph.prototype.resetLockedStatesCache = function()
{
	this.lockedStatesCache = {locked: new mxDictionary(), groups: new mxDictionary()};
};

Graph.prototype.cacheChildLayoutStyles = function(fn)
{
	if (this.childLayoutStyles != null)
	{
		return fn();
	}

	this.childLayoutStyles = {cells: new Map(), getCellStyle: this.getCellStyle,
		stylesheet: this.stylesheet, styles: this.stylesheet.styles};

	try
	{
		return fn();
	}
	finally
	{
		this.childLayoutStyles = null;
	}
};

Graph.prototype.getTransparentChildBounds = function(cell, state, cache)
{
	var result = null;
	var children = (state != null) ? state.children(cell) :
		this.model.getChildCells(cell);

	var getGeometry = mxUtils.bind(this, function(child)
	{
		return (state != null) ? state.geometry(child) : this.getCellGeometry(child);
	});

	for (var i = 0; i < children.length; i++)
	{
		var child = children[i];
		var rect = null;

		if (this.model.isVertex(child))
		{
			var geo = getGeometry(child);

			if (geo != null && !geo.relative)
			{
				if (this.isTransparentBounds(child))
				{
					var local = this.getTransparentBounds(child, state, null, cache);

					if (local != null)
					{
						rect = new mxRectangle(geo.x + local.x,
							geo.y + local.y, local.width, local.height);
					}
				}
				else
				{
					rect = new mxRectangle(geo.x, geo.y,
						geo.width, geo.height);
				}
			}
		}
		else if (this.model.isEdge(child))
		{
			// Edge children contribute their waypoints and any disconnected
			// terminal points (stored relative to the group — the same local
			// space as vertex geometry) so an edge that bows outside the vertex
			// union, or a group whose only children are edges, still expands the
			// derived bounds instead of being ignored.
			var geo = getGeometry(child);

			if (geo != null)
			{
				var pts = [];

				if (this.model.getTerminal(child, true) == null &&
					geo.getTerminalPoint(true) != null)
				{
					pts.push(geo.getTerminalPoint(true));
				}

				if (this.model.getTerminal(child, false) == null &&
					geo.getTerminalPoint(false) != null)
				{
					pts.push(geo.getTerminalPoint(false));
				}

				if (geo.points != null)
				{
					pts = pts.concat(geo.points);
				}

				for (var j = 0; j < pts.length; j++)
				{
					if (pts[j] != null)
					{
						if (rect == null)
						{
							rect = new mxRectangle(pts[j].x, pts[j].y, 0, 0);
						}
						else
						{
							rect.add(new mxRectangle(pts[j].x, pts[j].y, 0, 0));
						}
					}
				}
			}
		}

		if (rect != null)
		{
			if (result == null)
			{
				result = mxRectangle.fromRectangle(rect);
			}
			else
			{
				result.add(rect);
			}
		}
	}

	return result;
};

Graph.prototype.getTransparentBoundsPadding = function(cell, ignoreState)
{
	var style = this.getCurrentCellStyle(cell, ignoreState);

	return this.parsePadding(mxUtils.getValue(style,
		mxConstants.STYLE_GROUP_PADDING, 0));
};

Graph.prototype.getTieIdsForStyle = function(style)
{
	var result = null;

	if (typeof style === 'string' && style.indexOf('ties=') >= 0)
	{
		var pairs = style.split(';');

		for (var i = 0; i < pairs.length && result == null; i++)
		{
			if (pairs[i].substring(0, 5) == 'ties=')
			{
				result = this.getTieIds({ties: pairs[i].substring(5)});
			}
		}
	}

	return result;
};

Graph.domPurify = function(value, inPlace)
{
	var config = window.DOM_PURIFY_CONFIG;

	// Returns strings without markup unchanged under the same conditions
	// as DOMPurify. DOMPurify only returns them after parsing the config
	// and cloning the allowed tags and attributes (for the attribute hook),
	// which takes about 0.2 ms for each label.
	if (!inPlace && typeof value === 'string' && value.length > 0 &&
		value.indexOf('<') < 0 && !config.RETURN_DOM &&
		!config.RETURN_DOM_FRAGMENT && !config.RETURN_TRUSTED_TYPE &&
		!config.SAFE_FOR_TEMPLATES && !config.WHOLE_DOCUMENT)
	{
		return value;
	}

	config.IN_PLACE = inPlace;

	return DOMPurify.sanitize(value, config);
};

Graph.getPathDataBounds = function(d)
{
	var values = (d != null) ? d.match(/-?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?/gi) : null;
	var x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;

	if (values != null)
	{
		for (var i = 0; i < values.length - 1; i += 2)
		{
			var x = parseFloat(values[i]);
			var y = parseFloat(values[i + 1]);

			if (isFinite(x) && isFinite(y))
			{
				x0 = Math.min(x0, x);
				y0 = Math.min(y0, y);
				x1 = Math.max(x1, x);
				y1 = Math.max(y1, y);
			}
		}
	}

	return (x0 <= x1) ? new mxRectangle(x0, y0, x1 - x0, y1 - y0) : null;
};

Graph.getContinuousPathData = function(d)
{
	var result = d;

	if (d != null && d.indexOf('M', 1) > 0)
	{
		var parts = d.split(/(?=M)/);
		var subpaths = [];

		for (var i = 0; i < parts.length; i++)
		{
			var values = parts[i].match(/-?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?/gi);

			if (values != null && values.length >= 4)
			{
				subpaths.push({data: parts[i].replace(/^\s*M/, ''),
					values: values.map(parseFloat)});
			}
		}

		var isNear = function(x, y, values, index)
		{
			return Math.abs(x - values[index]) < 0.1 &&
				Math.abs(y - values[index + 1]) < 0.1;
		};

		result = '';

		for (var i = 0; i < subpaths.length; i++)
		{
			var v = subpaths[i].values;
			var bar = false;

			if (v.length == 4 && i > 0 && i < subpaths.length - 1 &&
				/^[\s\d.e+-]*L[\s\d.e+-]*$/i.test(subpaths[i].data))
			{
				var cx = (v[0] + v[2]) / 2;
				var cy = (v[1] + v[3]) / 2;
				var prev = subpaths[i - 1].values;
				bar = isNear(cx, cy, prev, prev.length - 2) ||
					isNear(cx, cy, subpaths[i + 1].values, 0);
			}

			if (!bar)
			{
				result += ((result.length == 0) ? 'M' : ' L') + subpaths[i].data.replace(/\s+$/, '');
			}
		}

		result = (result.length > 0) ? result : d;
	}

	return result;
};

Graph.maxFlowRingSize = 1000;

Graph.defaultFlowRingSize = 12;

Graph.defaultFlowDotSize = 8;

Graph.maxFlowRingCount = 100;

Graph.defaultFlowRingDuration = 1500;

Graph.flowRingPeriod = 101;

Graph.flowRingOffset = 51;

Graph.prototype.parsePadding = function(value)
{
	return mxUtils.parsePadding(value);
};

Graph.prototype.getTieIds = function(style)
{
	var value = (style != null) ? style['ties'] : null;
	var result = null;

	if (value != null && String(value).length > 0)
	{
		var ids = String(value).split(',');
		var seen = Object.create(null);
		result = [];

		for (var i = 0; i < ids.length; i++)
		{
			if (ids[i].length > 0 && !seen[ids[i]])
			{
				seen[ids[i]] = true;
				result.push(ids[i]);
			}
		}
	}

	return result;
};

Graph.prototype.getPlaceholderCell = function(cell, key, found)
{
	var results = null;

	if (this.placeholderCells != null)
	{
		results = this.placeholderCells.get(key);

		// Keeps results only for keys that are looked up again, so that the
		// distinct placeholders of one cell keep nothing and do not use up
		// maxPlaceholderCells for the keys of the other cells
		if (results === undefined)
		{
			this.placeholderCells.set(key, null);
		}
		else if (results === null)
		{
			results = new mxDictionary();
			this.placeholderCells.set(key, results);
			this.placeholderCellCount++;
		}
	}

	var path = [];
	var result = null;
	var current = cell;

	while (current != null)
	{
		// Only the results of the ancestors are kept
		if (results != null && current != cell)
		{
			var cached = results.get(current);

			if (cached !== undefined)
			{
				result = cached;
				break;
			}

			path.push(current);
		}

		if (found(current))
		{
			result = current;
			break;
		}

		current = this.getPlaceholderParent(current);
	}

	if (results != null && path.length > 0)
	{
		// Keeps the nearest ancestors up to maxPlaceholderCells and then
		// only the parent, so that shared placeholders of many cells under
		// a deep cell do not test all ancestors again
		var count = Math.max(1, Math.min(path.length,
			this.maxPlaceholderCells - this.placeholderCellCount));

		for (var i = 0; i < count; i++)
		{
			results.put(path[i], result);
		}

		this.placeholderCellCount += count;
	}

	return result;
};

Graph.prototype.addChangedTies = function(change)
{
	var view = this.view;

	if (change instanceof mxStyleChange)
	{
		var styles = [change.previous, this.model.getStyle(change.cell)];

		for (var i = 0; i < styles.length; i++)
		{
			if (this.isTieStyle(styles[i]))
			{
				this.addChangedTieStyle(change.cell, styles[i]);
				this.tieIndex = null;
			}
		}
	}
	else if (change instanceof mxChildChange)
	{
		view.changedTieCells = view.changedTieCells || [];
		view.changedTieCells.push(change.child);
	}
};

Graph.prototype.getPlaceholderParent = function(cell)
{
	return this.model.getParent(cell);
};

// Graph.js lines 27671-27885
(function()
{
	/**
	 * Keeps the resolved fill and stroke of rows and cells that are painted
	 * by their collapsed table and clears them on the shape so that the shape
	 * only paints its transparent area for event handling.
	 */
	var mxCellRendererPostConfigureShape = mxCellRenderer.prototype.postConfigureShape;
	mxCellRenderer.prototype.postConfigureShape = function(state)
	{
		mxCellRendererPostConfigureShape.apply(this, arguments);

		if (state.shape != null)
		{
			var shape = state.shape;
			shape.collapsedTableStyle = null;

			if (state.view.graph.isCollapsedTableChildShape(state))
			{
				shape.collapsedTableStyle = {fill: shape.fill, gradient: shape.gradient,
					laneFill: shape.laneFill, stroke: shape.stroke};
				shape.fill = mxConstants.NONE;
				shape.gradient = null;
				shape.stroke = mxConstants.NONE;
				shape.isShadow = false;
				shape.glass = false;

				if (shape.laneFill != null)
				{
					shape.laneFill = mxConstants.NONE;
				}
			}
		}
	};

	/**
	 * Resolves inherit, parentFillColor and parentStrokeColor from the colors
	 * that a row or cell of a collapsed table had before they were cleared
	 * (see postConfigureShape) as the cleared colors would be inherited.
	 */
	var mxCellRendererResolveColor = mxCellRenderer.prototype.resolveColor;
	mxCellRenderer.prototype.resolveColor = function(state, field, key)
	{
		var shape = (key == mxConstants.STYLE_FONTCOLOR) ? state.text : state.shape;
		var value = (shape != null) ? shape[field] : null;
		mxCellRendererResolveColor.apply(this, arguments);

		if (shape != null && key != mxConstants.STYLE_FONTCOLOR &&
			(value == 'inherit' || value == 'parentFillColor' ||
			value == 'parentStrokeColor'))
		{
			var pstate = state.view.getState(state.view.graph.model.getParent(state.cell));
			var saved = (pstate != null && pstate.shape != null) ?
				pstate.shape.collapsedTableStyle : null;
			var rfield = (value == 'parentFillColor') ? 'fill' :
				((value == 'parentStrokeColor') ? 'stroke' : field);

			if (saved != null && (rfield == 'fill' || rfield == 'gradient' ||
				rfield == 'stroke'))
			{
				shape[field] = saved[rfield];
			}
		}
	};

	/**
	 * Reconfigures the shape of a row or cell if it was added to or removed
	 * from a collapsed table, eg. if tableRender of the table was changed.
	 */
	var mxCellRendererCheckPlaceholderStyles = mxCellRenderer.prototype.checkPlaceholderStyles;
	mxCellRenderer.prototype.checkPlaceholderStyles = function(state)
	{
		return mxCellRendererCheckPlaceholderStyles.apply(this, arguments) ||
			(state.shape != null && (state.shape.collapsedTableStyle != null) !=
			state.view.graph.isCollapsedTableChildShape(state));
	};

	/**
	 * Compares the saved colors of rows and cells of collapsed tables as the
	 * colors of their shapes are cleared (see postConfigureShape).
	 */
	var mxCellRendererIsShapeConfigurationChanged = mxCellRenderer.prototype.isShapeConfigurationChanged;
	mxCellRenderer.prototype.isShapeConfigurationChanged = function(shape, probe)
	{
		if (mxCellRendererIsShapeConfigurationChanged.apply(this, arguments))
		{
			return true;
		}

		var s1 = shape.collapsedTableStyle;
		var s2 = probe.collapsedTableStyle;

		return (s1 != null) != (s2 != null) || (s1 != null && (s1.fill != s2.fill ||
			s1.gradient != s2.gradient || s1.laneFill != s2.laneFill ||
			s1.stroke != s2.stroke));
	};

	/**
	 * Schedules a repaint of the collapsed table if one of its rows or cells
	 * was repainted.
	 */
	var mxCellRendererRedrawShape = mxCellRenderer.prototype.redrawShape;
	mxCellRenderer.prototype.redrawShape = function(state, force, rendering)
	{
		var shapeChanged = mxCellRendererRedrawShape.apply(this, arguments);

		if (shapeChanged && state.shape != null)
		{
			var table = state.view.graph.getCollapsedTableForCell(state.cell);

			if (table != null)
			{
				state.view.scheduleCollapsedTableRepaint(table);
			}
		}

		return shapeChanged;
	};

	/**
	 * Adds the given collapsed table to the tables to be repainted after the
	 * validation of their rows and cells. The table is stored as the value
	 * since the keys of a dictionary are object identities, not cells.
	 */
	mxGraphView.prototype.scheduleCollapsedTableRepaint = function(table)
	{
		if (this.collapsedTableRepaints == null)
		{
			this.collapsedTableRepaints = new mxDictionary();
		}

		this.collapsedTableRepaints.put(table, table);
	};

	/**
	 * Repaints the given collapsed table if it was scheduled for a repaint or
	 * all scheduled tables if no table is given.
	 */
	mxGraphView.prototype.flushCollapsedTableRepaints = function(table)
	{
		if (this.collapsedTableRepaints != null)
		{
			var tables = null;

			if (table != null)
			{
				if (this.collapsedTableRepaints.get(table))
				{
					this.collapsedTableRepaints.remove(table);
					tables = [table];
				}
			}
			else
			{
				tables = this.collapsedTableRepaints.getValues();
				this.collapsedTableRepaints = null;
			}

			if (tables != null)
			{
				for (var i = 0; i < tables.length; i++)
				{
					var state = this.getState(tables[i]);

					if (state != null && !state.invalid && state.shape != null)
					{
						this.graph.cellRenderer.redraw(state, true, this.isRendering());
					}
				}
			}
		}
	};

	/**
	 * Repaints scheduled collapsed tables after their rows and cells were
	 * validated and all remaining tables at the end of the validation.
	 */
	var mxGraphViewValidateCellState = mxGraphView.prototype.validateCellState;
	mxGraphView.prototype.validateCellState = function(cell, recurse)
	{
		var state = mxGraphViewValidateCellState.apply(this, arguments);

		if (this.collapsedTableRepaints != null && cell != null &&
			(recurse == null || recurse))
		{
			if (cell == this.currentRoot || this.graph.model.getParent(cell) == null)
			{
				this.flushCollapsedTableRepaints();
			}
			else
			{
				this.flushCollapsedTableRepaints(cell);
			}
		}

		return state;
	};

	/**
	 * Repaints the scheduled collapsed tables at the end of a validation
	 * that does not walk all cells.
	 */
	var mxGraphViewValidateInvalidCells = mxGraphView.prototype.validateInvalidCells;
	mxGraphView.prototype.validateInvalidCells = function(root)
	{
		var result = mxGraphViewValidateInvalidCells.apply(this, arguments);

		if (result)
		{
			this.flushCollapsedTableRepaints();
		}

		return result;
	};
})();

// Graph.js lines 27897-28621
(function()
{
	var gapCounter = 0;

	/**
	 * Returns the labels with a gap that are cut out of the shape of the given
	 * state as objects with the state of the label and the points of its box
	 * (see getLabelGap): the label of the state and, for edges, the labels of
	 * their children.
	 */
	mxCellRenderer.prototype.getLabelGaps = function(state)
	{
		var model = state.view.graph.getModel();
		var gaps = [];

		var addGap = mxUtils.bind(this, function(s)
		{
			if (s != null && s.text != null && s.style != null &&
				s.style['defaultLabelBackgroundColor'] == 'page')
			{
				var pts = this.getLabelGap(s);

				if (pts != null)
				{
					gaps.push({state: s, points: pts});
				}
			}
		});

		addGap(state);

		if (model.isEdge(state.cell))
		{
			var childCount = model.getChildCount(state.cell);

			for (var i = 0; i < childCount; i++)
			{
				addGap(state.view.getState(model.getChildAt(state.cell, i)));
			}
		}

		return gaps;
	};

	/**
	 * Returns the box of the label of the given state that is cut out of the
	 * shapes as an array of four points in screen units or null. HTML labels
	 * use their painted box (see getPaintedLabelBox), as they are placed in
	 * whole units of the canvas, which are model units in model coordinates,
	 * and their size is rounded in mxText.updateBoundingBox. The label
	 * padding is added as the label paints no background (see
	 * mxSvgCanvas2D.createCss) unless it has a border.
	 */
	mxCellRenderer.prototype.getLabelGap = function(state)
	{
		state.view.updateBoundingBox(state);
		var text = state.text;
		var rot = (text.unrotatedBoundingBox != null) ?
			Number(text.getTextRotation()) : 0;
		var box = (rot != 0) ? text.unrotatedBoundingBox : text.boundingBox;
		var result = null;

		if (box != null)
		{
			var rad = mxUtils.toRadians(rot);
			var cos = Math.cos(rad);
			var sin = Math.sin(rad);
			var w = box.width;
			var h = box.height;
			var ct = new mxPoint(box.getCenterX(), box.getCenterY());

			// Rotates around the anchor of the label (see mxText.updateBoundingBox)
			if (rot != 0 && text.margin != null)
			{
				ct = mxUtils.getRotatedPoint(ct, cos, sin, new mxPoint(
					box.x - text.margin.x * w, box.y - text.margin.y * h));
			}

			// The painted box of rotated labels is their bounding box
			var painted = this.getPaintedLabelBox(state);

			if (painted != null)
			{
				var quarter = mxUtils.mod(rot, 180);
				ct = new mxPoint(painted.getCenterX(), painted.getCenterY());

				if (quarter == 0)
				{
					w = painted.width;
					h = painted.height;
				}
				else if (quarter == 90)
				{
					w = painted.height;
					h = painted.width;
				}
			}

			var x0 = -w / 2;
			var y0 = -h / 2;
			var x1 = w / 2;
			var y1 = h / 2;
			var p = (text.border == null || text.border == mxConstants.NONE) ?
				mxUtils.parseCssSpacing(text.labelPadding) : null;

			if (p != null)
			{
				x0 -= p.left * text.scale;
				y0 -= p.top * text.scale;
				x1 += p.right * text.scale;
				y1 += p.bottom * text.scale;
			}

			result = [new mxPoint(x0, y0), new mxPoint(x1, y0),
				new mxPoint(x1, y1), new mxPoint(x0, y1)];

			for (var i = 0; i < result.length; i++)
			{
				var pt = result[i];
				result[i] = new mxPoint(ct.x + pt.x * cos - pt.y * sin,
					ct.y + pt.x * sin + pt.y * cos);

				// Ignores invalid rotations or sizes which would hide the shape
				if (!isFinite(result[i].x) || !isFinite(result[i].y))
				{
					return null;
				}
			}
		}

		return result;
	};

	/**
	 * Returns the painted box of the element of the given HTML label that has
	 * the background (see mxSvgCanvas2D.updateTextNodes and createCss) in
	 * screen units, or null if the label has no foreignObject or is not
	 * displayed. This is the bounding box of the element for rotated labels.
	 */
	mxCellRenderer.prototype.getPaintedLabelBox = function(state)
	{
		var text = state.text;
		var g = (text.node != null) ? text.node.firstChild : null;
		var fo = (g != null) ? g.firstChild : null;

		if (fo != null && fo.nodeName == 'title')
		{
			fo = fo.nextSibling;
		}

		// Skips the alternate content of labels in other documents, such as
		// the print window (see mxSvgCanvas2D.text)
		if (fo != null && fo.nodeName == 'switch')
		{
			fo = fo.firstChild;
		}

		var elt = (fo != null && fo.nodeName == 'foreignObject' &&
			fo.firstChild != null) ? fo.firstChild.firstChild : null;

		if (elt != null && (text.clipped || (text.overflow != 'fill' &&
			text.overflow != 'width')))
		{
			elt = elt.firstChild;
		}

		var canvas = state.view.getCanvas();

		if (elt != null && elt.getBoundingClientRect != null && canvas != null &&
			canvas.ownerSVGElement != null && canvas.getScreenCTM != null)
		{
			var r = elt.getBoundingClientRect();
			var m = canvas.getScreenCTM();

			if (m != null && r.width > 0 && r.height > 0)
			{
				// Client coordinates to the units of the view
				m = m.inverse();
				var p0 = canvas.ownerSVGElement.createSVGPoint();
				p0.x = r.left;
				p0.y = r.top;
				p0 = p0.matrixTransform(m);
				var p1 = canvas.ownerSVGElement.createSVGPoint();
				p1.x = r.right;
				p1.y = r.bottom;
				p1 = p1.matrixTransform(m);

				return new mxRectangle(p0.x, p0.y, p1.x - p0.x, p1.y - p0.y);
			}
		}

		return null;
	};

	/**
	 * Returns the path data of the clip path that cuts the given gaps (see
	 * getLabelGaps) out of the shape of the given state. The function maps the
	 * given x and y in screen units to a pair of coordinates in the units of
	 * the path. The optional offset is added to the points of each gap. The
	 * outer rectangle contains the bounding box of the shape with a margin for
	 * markers and shadows.
	 */
	mxCellRenderer.prototype.createLabelGapPath = function(state, gaps, map, offset)
	{
		gaps = gaps.map(function(gap)
		{
			var d = (offset != null) ? offset(gap) : null;

			return gap.points.map(function(pt)
			{
				return (d != null) ? new mxPoint(pt.x + d.x, pt.y + d.y) : pt;
			});
		});

		var b = new mxRectangle(state.x, state.y, state.width, state.height);
		var bbox = (state.shape != null) ? state.shape.boundingBox : null;

		if (bbox != null && isFinite(bbox.x) && isFinite(bbox.y) &&
			isFinite(bbox.width) && isFinite(bbox.height))
		{
			b.add(bbox);
		}

		for (var i = 0; i < gaps.length; i++)
		{
			for (var j = 0; j < gaps[i].length; j++)
			{
				b.add(new mxRectangle(gaps[i][j].x, gaps[i][j].y, 0, 0));
			}
		}

		b.grow(Math.max(b.width, b.height) + 100 * state.view.scale);

		var d = 'M ' + map(b.x, b.y) + ' L ' + map(b.x + b.width, b.y) +
			' L ' + map(b.x + b.width, b.y + b.height) +
			' L ' + map(b.x, b.y + b.height) + ' Z';

		for (var i = 0; i < gaps.length; i++)
		{
			for (var j = 0; j < gaps[i].length; j++)
			{
				d += ((j == 0) ? ' M ' : ' L ') + map(gaps[i][j].x, gaps[i][j].y);
			}

			d += ' Z';
		}

		return d;
	};

	/**
	 * Returns the shapes of the given state that the label gaps are cut out
	 * of: the shape of the state and, for edges, the selection line of the
	 * edge handler (see mxEdgeHandler.getLabelGapShape) and the parent
	 * highlight of their selected labels (see
	 * mxVertexHandler.updateParentHighlight).
	 */
	mxCellRenderer.prototype.getLabelGapShapes = function(state)
	{
		var graph = state.view.graph;
		var shapes = [state.shape];

		if (graph.getModel().isEdge(state.cell))
		{
			var handler = graph.selectionCellsHandler.getHandler(state.cell);
			shapes.push(state.parentHighlight, (handler != null) ?
				handler.getLabelGapShape() : null);
		}

		return shapes;
	};

	/**
	 * Updates the clip paths of the shapes of the given state (see
	 * getLabelGapShapes) for the given gaps or for the labels with a gap (see
	 * getLabelGaps).
	 */
	mxCellRenderer.prototype.updateLabelGaps = function(state, gaps)
	{
		var shapes = this.getLabelGapShapes(state);

		for (var i = 0; i < shapes.length; i++)
		{
			var node = (shapes[i] != null) ? shapes[i].node : null;

			if (node != null && node.ownerSVGElement != null)
			{
				gaps = (gaps != null) ? gaps : this.getLabelGaps(state);
				this.updateLabelGapClip(state, shapes[i], gaps);
			}
		}
	};

	/**
	 * Returns true if the given state has a shape that label gaps are cut out
	 * of (see updateLabelGaps).
	 */
	mxCellRenderer.prototype.hasLabelGapShapes = function(state)
	{
		var shapes = this.getLabelGapShapes(state);

		for (var i = 0; i < shapes.length; i++)
		{
			if (shapes[i] != null && shapes[i].node != null &&
				shapes[i].node.ownerSVGElement != null)
			{
				return true;
			}
		}

		return false;
	};

	/**
	 * Updates the clip path of the given shape for the given label gaps of
	 * the given state. The clip path is added to the SVG root so that it is
	 * kept if the shape is repainted and removed if the shape is destroyed.
	 */
	mxCellRenderer.prototype.updateLabelGapClip = function(state, shape, gaps)
	{
		var node = shape.node;
		var clip = shape.labelGapClip;

		if (gaps.length > 0)
		{
			var model = shape.isModelCoordinates();
			var s = state.view.scale;
			var tr = state.view.translate;

			var d = this.createLabelGapPath(state, gaps, function(x, y)
			{
				if (model)
				{
					x = x / s - tr.x;
					y = y / s - tr.y;
				}

				return (Math.round(x * 100) / 100) + ' ' +
					(Math.round(y * 100) / 100);
			});

			if (clip == null)
			{
				clip = node.ownerDocument.createElementNS(mxConstants.NS_SVG, 'clipPath');
				clip.setAttribute('id', 'mx-label-gap-' + (++gapCounter));
				var path = node.ownerDocument.createElementNS(mxConstants.NS_SVG, 'path');
				path.setAttribute('clip-rule', 'evenodd');
				clip.appendChild(path);
				shape.labelGapClip = clip;
			}

			if (clip.parentNode != node.ownerSVGElement)
			{
				node.ownerSVGElement.appendChild(clip);
			}

			if (clip.firstChild.getAttribute('d') != d)
			{
				clip.firstChild.setAttribute('d', d);
			}

			shape.updateLabelGapOffset();

			// Workaround for references with a base tag in the page
			var base = (mxSvgCanvas2D.prototype.useAbsoluteIds) ?
				mxSvgCanvas2D.prototype.getBaseUrl().replace(/([\(\)])/g, '\\$1') : '';
			node.setAttribute('clip-path', 'url(' + base + '#' + clip.getAttribute('id') + ')');
		}
		else if (clip != null)
		{
			node.removeAttribute('clip-path');
			shape.removeLabelGapClip();
		}
	};

	/**
	 * Updates the label gaps of the given state after the validation if its
	 * label has a gap or it is an edge with children, and of the parent edge
	 * if the label has a gap. Clip paths that are no longer needed are
	 * removed in the same way.
	 */
	mxCellRenderer.prototype.invalidateLabelGaps = function(state)
	{
		var model = state.view.graph.getModel();
		var gap = state.style != null && state.style['defaultLabelBackgroundColor'] == 'page';

		if (state.shape != null && (gap || state.shape.labelGapClip != null ||
			(model.isEdge(state.cell) && model.getChildCount(state.cell) > 0)))
		{
			state.view.scheduleLabelGaps(state);
		}

		var parent = state.view.getState(model.getParent(state.cell));

		if (parent != null && parent.shape != null && model.isEdge(parent.cell) &&
			(gap || parent.shape.labelGapClip != null))
		{
			state.view.scheduleLabelGaps(parent);
		}
	};

	/**
	 * Invalidates the label gaps of repainted states.
	 */
	var mxCellRendererRedraw = mxCellRenderer.prototype.redraw;
	mxCellRenderer.prototype.redraw = function(state, force, rendering)
	{
		mxCellRendererRedraw.apply(this, arguments);

		if (state.shape != null && (rendering == null || rendering))
		{
			this.invalidateLabelGaps(state);
		}
	};

	/**
	 * Removes the gap of the label of the given state from the parent edge,
	 * which is not repainted if the label is hidden.
	 */
	var mxCellRendererDestroy = mxCellRenderer.prototype.destroy;
	mxCellRenderer.prototype.destroy = function(state)
	{
		var gap = state.shape != null && state.style != null &&
			state.style['defaultLabelBackgroundColor'] == 'page';

		mxCellRendererDestroy.apply(this, arguments);

		if (gap)
		{
			var model = state.view.graph.getModel();
			var parent = state.view.getState(model.getParent(state.cell));

			if (parent != null && parent.shape != null && parent.shape.labelGapClip != null)
			{
				state.view.scheduleLabelGaps(parent);
			}
		}
	};

	/**
	 * Removes the clip path of the label gaps (see updateLabelGaps).
	 */
	mxShape.prototype.removeLabelGapClip = function()
	{
		if (this.labelGapClip != null)
		{
			if (this.labelGapClip.parentNode != null)
			{
				this.labelGapClip.parentNode.removeChild(this.labelGapClip);
			}

			this.labelGapClip = null;
		}
	};

	/**
	 * Moves the clip path of the label gaps by the inverse of the offset of the
	 * node for crisp strokes (see updateSvgScreenOffset), which also applies to
	 * the clip path, so that the gaps match the painted labels.
	 */
	mxShape.prototype.updateLabelGapOffset = function()
	{
		if (this.labelGapClip != null)
		{
			var off = (this.svgScreenOffset != null) ? this.svgScreenOffset : 0;

			if (off != 0)
			{
				this.labelGapClip.firstChild.setAttribute('transform',
					'translate(' + (-off) + ',' + (-off) + ')');
			}
			else
			{
				this.labelGapClip.firstChild.removeAttribute('transform');
			}
		}
	};

	/**
	 * Updates the offset of the clip path of the label gaps.
	 */
	var mxShapeUpdateSvgScreenOffset = mxShape.prototype.updateSvgScreenOffset;
	mxShape.prototype.updateSvgScreenOffset = function()
	{
		mxShapeUpdateSvgScreenOffset.apply(this, arguments);
		this.updateLabelGapOffset();
	};

	/**
	 * Removes the clip path of the label gaps.
	 */
	var mxShapeDestroy = mxShape.prototype.destroy;
	mxShape.prototype.destroy = function()
	{
		this.removeLabelGapClip();
		mxShapeDestroy.apply(this, arguments);
	};

	/**
	 * Updates the label gaps of the given state at the end of the validation
	 * or now if no validation is running, after the bounding boxes of the
	 * labels have been measured.
	 */
	mxGraphView.prototype.scheduleLabelGaps = function(state)
	{
		if (this.labelGapStates == null)
		{
			this.graph.cellRenderer.updateLabelGaps(state);
		}
		else if (!state.invalidLabelGaps)
		{
			state.invalidLabelGaps = true;
			this.labelGapStates.push(state);
		}
	};

	/**
	 * Updates the scheduled label gaps after the validation.
	 */
	var mxGraphViewValidate = mxGraphView.prototype.validate;
	mxGraphView.prototype.validate = function(cell)
	{
		var prev = this.labelGapStates;
		var states = [];
		this.labelGapStates = states;

		try
		{
			mxGraphViewValidate.apply(this, arguments);
		}
		finally
		{
			this.labelGapStates = prev;

			for (var i = 0; i < states.length; i++)
			{
				states[i].invalidLabelGaps = false;
			}
		}

		// Measures all labels before the clip paths are written, as each write
		// made the next measurement lay out the page again (8,000 labels: 4.5 s)
		var renderer = this.graph.cellRenderer;
		var gaps = [];

		for (var i = 0; i < states.length; i++)
		{
			gaps.push((this.getState(states[i].cell) == states[i] &&
				renderer.hasLabelGapShapes(states[i])) ?
				renderer.getLabelGaps(states[i]) : null);
		}

		for (var i = 0; i < states.length; i++)
		{
			if (gaps[i] != null)
			{
				renderer.updateLabelGaps(states[i], gaps[i]);
			}
		}
	};

	/**
	 * Cuts the label gaps out of the exported shapes after the labels have been
	 * exported (see drawShape and drawText).
	 */
	var mxImageExportDrawState = mxImageExport.prototype.drawState;
	mxImageExport.prototype.drawState = function(state, canvas)
	{
		var prev = canvas.labelGaps;
		canvas.labelGaps = (canvas instanceof mxSvgCanvas2D) ?
			{shapes: [], positions: new mxDictionary()} : null;

		try
		{
			mxImageExportDrawState.apply(this, arguments);

			if (canvas.labelGaps != null)
			{
				this.addLabelGaps(canvas, canvas.labelGaps);
			}
		}
		finally
		{
			canvas.labelGaps = prev;
		}
	};

	/**
	 * Stores the exported shapes with label gaps.
	 */
	var mxImageExportDrawShape = mxImageExport.prototype.drawShape;
	mxImageExport.prototype.drawShape = function(state, canvas)
	{
		var root = canvas.root;
		var last = (root != null) ? root.lastChild : null;

		mxImageExportDrawShape.apply(this, arguments);

		var node = (root != null) ? root.lastChild : null;
		var renderer = state.view.graph.cellRenderer;

		if (canvas.labelGaps != null && node != null && node != last &&
			renderer.getLabelGaps != null)
		{
			var gaps = renderer.getLabelGaps(state);

			if (gaps.length > 0)
			{
				var s = canvas.state;

				canvas.labelGaps.shapes.push({state: state, node: node,
					root: root, gaps: gaps, dx: s.dx, dy: s.dy, scale: s.scale,
					offset: this.getSvgShapeOffset(state.shape, canvas)});
			}
		}
	};

	/**
	 * Stores the unrounded positions of the exported labels with a gap (see
	 * mxSvgCanvas2D.updateTextNodes).
	 */
	var mxImageExportDrawText = mxImageExport.prototype.drawText;
	mxImageExport.prototype.drawText = function(state, canvas)
	{
		var root = canvas.root;
		var last = (root != null) ? root.lastChild : null;

		mxImageExportDrawText.apply(this, arguments);

		var node = (root != null) ? root.lastChild : null;

		if (canvas.labelGaps != null && node != null && node != last &&
			node.firstChild != null && node.firstChild.textPosition != null &&
			state.style != null && state.style['defaultLabelBackgroundColor'] == 'page')
		{
			canvas.labelGaps.positions.put(state, node.firstChild.textPosition);
		}
	};

	/**
	 * Cuts the given label gaps out of the exported shapes with clip paths
	 * whose IDs depend on their path, so that identical clip paths are shared
	 * and different clip paths keep their IDs if exported SVGs are combined.
	 * The boxes of the painted labels are moved by the difference of the
	 * rounding of the exported and the painted labels, which are placed in
	 * whole units of their canvas (see mxSvgCanvas2D.updateTextNodes).
	 */
	mxImageExport.prototype.addLabelGaps = function(canvas, labelGaps)
	{
		var round = function(value)
		{
			return Math.round(value) - value;
		};

		var getOffset = function(gap)
		{
			var text = gap.state.text;
			var g = (text != null && text.node != null) ? text.node.firstChild : null;
			var p0 = (g != null) ? g.textPosition : null;
			var p1 = labelGaps.positions.get(gap.state);

			if (p0 != null && p1 != null)
			{
				var dx = round(p1.x) - round(p0.x);
				var dy = round(p1.y) - round(p0.y);
				var rad = mxUtils.toRadians(Number(text.getTextRotation()));
				var cos = Math.cos(rad);
				var sin = Math.sin(rad);

				var ox = (dx * cos - dy * sin) * text.scale;
				var oy = (dx * sin + dy * cos) * text.scale;

				if (isFinite(ox) && isFinite(oy))
				{
					return new mxPoint(ox, oy);
				}
			}

			return null;
		};

		for (var i = 0; i < labelGaps.shapes.length; i++)
		{
			var item = labelGaps.shapes[i];
			var state = item.state;

			// Shapes are painted in units of their scale (see mxShape.paint)
			var ss = state.shape.scale;

			var d = state.view.graph.cellRenderer.createLabelGapPath(state,
				item.gaps, function(x, y)
			{
				return canvas.format((x / ss + item.dx) * item.scale * ss - item.offset) +
					' ' + canvas.format((y / ss + item.dy) * item.scale * ss - item.offset);
			}, getOffset);

			if (canvas.labelGapClips == null)
			{
				canvas.labelGapClips = {};
			}

			var id = 'mx-label-gap-' + mxUtils.hashCode(d);

			// Resolves hash collisions
			while (canvas.labelGapClips[id] != null && canvas.labelGapClips[id] != d)
			{
				id += '-';
			}

			if (canvas.labelGapClips[id] == null)
			{
				var clip = canvas.createElement('clipPath');
				clip.setAttribute('id', id);
				var path = canvas.createElement('path');
				path.setAttribute('clip-rule', 'evenodd');
				path.setAttribute('d', d);
				clip.appendChild(path);
				((canvas.defs != null) ? canvas.defs : item.root).appendChild(clip);
				canvas.labelGapClips[id] = d;
			}

			item.node.setAttribute('clip-path', 'url(#' + id + ')');
		}
	};
})();

// Graph.js lines 28656-30109
(function()
{
	/**
	 * Largest dy / dx of a segment that counts as horizontal (about 8.5
	 * degrees), and dx / dy for vertical.
	 */
	Graph.labelPlacementSlope = 0.15;

	/**
	 * Distance of an automatically placed label to bends, in px at 100 %.
	 * Arrowheads add their size.
	 */
	Graph.labelPlacementMargin = 4;

	/**
	 * Returns true if the label of the given state is placed automatically.
	 * Vertical text keeps its alignment, and sized vertices in edges are not
	 * labels (see isEdgeLabelCell).
	 */
	Graph.prototype.isAutoLabelPlacement = function(state)
	{
		return state != null && state.style != null &&
			state.style['labelPlacement'] == 'auto' &&
			mxUtils.getValue(state.style, mxConstants.STYLE_HORIZONTAL, true) != 0 &&
			(this.model.isEdge(state.cell) || this.isEdgeLabelCell(state.cell));
	};

	/**
	 * Returns the direction of the given segment: 'h', 'v' or null (diagonal).
	 */
	Graph.prototype.getLabelSegmentDirection = function(p0, p1)
	{
		var dx = Math.abs(p1.x - p0.x);
		var dy = Math.abs(p1.y - p0.y);

		if (dx > 0 && dy <= dx * Graph.labelPlacementSlope)
		{
			return 'h';
		}
		else if (dy > 0 && dx <= dy * Graph.labelPlacementSlope)
		{
			return 'v';
		}

		return null;
	};

	/**
	 * Returns the size of the label of the given state in screen units: the
	 * painted box or, before the label is painted, the size of its text.
	 * Preview states of the edge handler use the label of the cell. A label
	 * that was painted but not measured yet is measured, except in the first
	 * pass of a validation, where skipped.invalid is set to true if skipped
	 * is given (see isLabelBoxFree). There, a label that was never measured
	 * has no size, as measuring each label between the repaints of the other
	 * cells takes a layout of the page each time; the labels are measured at
	 * once and placed again at the end (see updateAutoLabelPlacements). An
	 * empty label has no size.
	 */
	Graph.prototype.getAutoLabelSize = function(state, skipped)
	{
		var current = (state.text != null) ? state : this.view.getState(state.cell);
		var text = (current != null) ? current.text : null;

		if (text != null && text.invalidBoundingBox)
		{
			if (this.view.autoLabelPending == null)
			{
				this.view.updateBoundingBox(current);
			}
			else if (skipped != null)
			{
				skipped.invalid = true;
			}
		}

		if (text != null && text.boundingBox != null && text.boundingBox.width > 0)
		{
			// The box of the last paint, which may have been at another scale
			var f = (text.scale > 0) ? this.view.scale / text.scale : 1;

			return new mxRectangle(0, 0, text.boundingBox.width * f,
				text.boundingBox.height * f);
		}

		if (this.view.autoLabelPending != null && skipped != null)
		{
			skipped.invalid = true;

			return new mxRectangle(0, 0, 0, 0);
		}

		var label = this.getLabel(state.cell);

		if (label == null || label.length == 0)
		{
			return new mxRectangle(0, 0, 0, 0);
		}

		var s = this.view.scale;
		var size = mxUtils.getSizeForString(label,
			mxUtils.getValue(state.style, mxConstants.STYLE_FONTSIZE, mxConstants.DEFAULT_FONTSIZE),
			mxUtils.getValue(state.style, mxConstants.STYLE_FONTFAMILY, mxConstants.DEFAULT_FONTFAMILY),
			null, mxUtils.getValue(state.style, mxConstants.STYLE_FONTSTYLE, 0));

		return new mxRectangle(0, 0, (size.width + 4) * s, (size.height + 4) * s);
	};

	/**
	 * Returns the margins of the given segment of an edge in screen units, at
	 * its first and its last point: the bend margin, plus the size of the
	 * arrowhead at the ends of the edge.
	 */
	Graph.prototype.getLabelSegmentMargins = function(edgeState, index)
	{
		var s = this.view.scale;
		var pts = edgeState.absolutePoints;
		var bend = Graph.labelPlacementMargin * s;

		var marker = function(key, sizeKey)
		{
			var type = mxUtils.getValue(edgeState.style, key, mxConstants.NONE);

			return (type == mxConstants.NONE) ? bend : bend + mxUtils.getNumber(
				edgeState.style, sizeKey, mxConstants.DEFAULT_MARKERSIZE) * s;
		};

		return [(index == 0) ? marker(mxConstants.STYLE_STARTARROW, mxConstants.STYLE_STARTSIZE) : bend,
			(index == pts.length - 2) ? marker(mxConstants.STYLE_ENDARROW, mxConstants.STYLE_ENDSIZE) : bend];
	};

	/**
	 * Returns the point at the given distance along the given segment.
	 */
	var pointOnSegment = function(p0, p1, dist)
	{
		var len = Math.sqrt((p1.x - p0.x) * (p1.x - p0.x) + (p1.y - p0.y) * (p1.y - p0.y));
		var f = (len > 0) ? dist / len : 0;

		return new mxPoint(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f);
	};

	/**
	 * Returns the room along the given segment for a label of the given size
	 * and horizontal alignment as the usable range [from, to] of the distances
	 * of its anchor from the first point, or null if the label does not fit.
	 * The text of a left aligned label starts at its anchor and the text of a
	 * right aligned label ends there (see mxText). Labels on diagonal segments
	 * need no room.
	 */
	Graph.prototype.getLabelSegmentRoom = function(edgeState, index, size, align)
	{
		var pts = edgeState.absolutePoints;
		var p0 = pts[index];
		var p1 = pts[index + 1];

		if (p0 == null || p1 == null)
		{
			return null;
		}

		var dir = this.getLabelSegmentDirection(p0, p1);
		var len = Math.sqrt((p1.x - p0.x) * (p1.x - p0.x) + (p1.y - p0.y) * (p1.y - p0.y));
		var margins = this.getLabelSegmentMargins(edgeState, index);
		var extent = 0;

		// Start of the label along the segment, relative to its anchor
		var start = 0;

		if (dir == 'h')
		{
			extent = size.width;
			start = mxUtils.getAlignmentAsPoint(align, null).x * extent;

			// Along a segment that goes to the left, the text ends at its start
			if (p1.x < p0.x)
			{
				start = -start - extent;
			}
		}
		else if (dir == 'v')
		{
			extent = size.height;
			start = -extent / 2;
		}

		return (len - margins[0] - margins[1] >= extent) ?
			[margins[0] - start, len - margins[1] - start - extent] : null;
	};

	/**
	 * Returns true if the given box in screen units is clear of the shapes,
	 * the other labels and the lines, except the given segment of the edge of
	 * the label. Containers are only in the way with their title bar, shapes
	 * that hold the box and the ancestors of the edge not at all, and diamonds
	 * and ellipses only where their outline is. In the first pass of a
	 * validation, states that are not validated yet and labels that are not
	 * measured yet (see mxGraphView.updateBoundingBox) are ignored, and
	 * skipped.invalid is set to true if skipped is given, so that the label
	 * is placed again at the end (see flushAutoLabelPlacements). Otherwise
	 * such labels are measured. With view.labelBoxIndex (see
	 * createLabelBoxIndex), only the states near the box are tested, after
	 * the first pass.
	 */
	Graph.prototype.isLabelBoxFree = function(state, edgeState, index, box, skipped)
	{
		var model = this.getModel();
		var b = new mxRectangle(box.x + 1, box.y + 1, Math.max(0, box.width - 2),
			Math.max(0, box.height - 2));

		var overlaps = function(r)
		{
			return r != null && r.x < b.x + b.width && r.x + r.width > b.x &&
				r.y < b.y + b.height && r.y + r.height > b.y;
		};

		var holds = function(r)
		{
			return r.x <= b.x && r.y <= b.y && r.x + r.width >= b.x + b.width &&
				r.y + r.height >= b.y + b.height;
		};

		// Half width of a diamond or an ellipse at the row of the box closest to its middle
		var hitsOutline = function(s)
		{
			var shape = mxUtils.getValue(s.style, mxConstants.STYLE_SHAPE, null);
			var perimeter = mxUtils.getValue(s.style, mxConstants.STYLE_PERIMETER, null);
			var rhombus = shape == 'rhombus' || perimeter == 'rhombusPerimeter';
			var ellipse = shape == 'ellipse' || shape == 'doubleEllipse' || perimeter == 'ellipsePerimeter';

			if (!rhombus && !ellipse)
			{
				return true;
			}

			var cx = s.x + s.width / 2;
			var cy = s.y + s.height / 2;
			var y = Math.max(b.y, Math.min(cy, b.y + b.height));
			var t = Math.min(1, Math.abs(y - cy) / Math.max(1, s.height / 2));
			var half = s.width / 2 * ((rhombus) ? 1 - t : Math.sqrt(1 - t * t));

			return b.x < cx + half && b.x + b.width > cx - half;
		};

		// Title bar of a swimlane, or nothing for other containers
		var titleBar = mxUtils.bind(this, function(s)
		{
			if (mxUtils.getValue(s.style, mxConstants.STYLE_SHAPE, null) != mxConstants.SHAPE_SWIMLANE)
			{
				return null;
			}

			var size = mxUtils.getNumber(s.style, mxConstants.STYLE_STARTSIZE,
				mxConstants.DEFAULT_STARTSIZE) * this.view.scale;

			return (mxUtils.getValue(s.style, mxConstants.STYLE_HORIZONTAL, 1) != 0) ?
				new mxRectangle(s.x, s.y, s.width, Math.min(size, s.height)) :
				new mxRectangle(s.x, s.y, Math.min(size, s.width), s.height);
		});

		var view = this.view;
		var deferred = view.autoLabelPending != null;

		var skip = function()
		{
			if (skipped != null && deferred)
			{
				skipped.invalid = true;
			}
		};

		// Returns true if the given state is in the way of the box
		var blocks = mxUtils.bind(this, function(s)
		{
			// Compares cells as the edge handler places the labels of a
			// preview state of the edge
			if (s == null || s.cell == state.cell)
			{
				return false;
			}
			else if (s.invalid)
			{
				skip();

				return false;
			}

			if (model.isVertex(s.cell) && s.shape != null && s.width > 0 && s.height > 0 &&
				!model.isEdge(model.getParent(s.cell)) && !holds(s) &&
				!model.isAncestor(s.cell, edgeState.cell))
			{
				var container = model.getChildCount(s.cell) > 0 || this.isContainer(s.cell);

				if ((container) ? overlaps(titleBar(s)) : (overlaps(s) && hitsOutline(s)))
				{
					return true;
				}
			}

			// Labels of all other cells, also outside their shapes
			if (s.text != null && s.text.invalidBoundingBox)
			{
				if (deferred)
				{
					skip();
				}
				else
				{
					view.updateBoundingBox(s);
				}
			}

			if (s.text != null && s.text.boundingBox != null &&
				!s.text.invalidBoundingBox && overlaps(s.text.boundingBox))
			{
				return true;
			}

			if (model.isEdge(s.cell) && s.absolutePoints != null)
			{
				for (var i = 0; i < s.absolutePoints.length - 1; i++)
				{
					var p0 = s.absolutePoints[i];
					var p1 = s.absolutePoints[i + 1];

					if ((s.cell != edgeState.cell || i != index) && p0 != null && p1 != null &&
						mxUtils.rectangleIntersectsSegment(b, p0, p1))
					{
						return true;
					}
				}
			}

			return false;
		});

		if (view.labelBoxIndex != null && !deferred)
		{
			return view.labelBoxIndex.isFree(b, blocks);
		}
		else
		{
			var free = true;

			view.states.visit(function(key, s)
			{
				if (free && blocks(s))
				{
					free = false;
				}
			});

			return free;
		}
	};

	/**
	 * Smallest number of labels placed again at the end of a validation (see
	 * updateAutoLabelPlacements) for which their boxes are only tested
	 * against the states near them (see createLabelBoxIndex), as the index
	 * takes a walk over all states.
	 */
	Graph.labelBoxIndexMinLabels = 16;

	/**
	 * Size of the buckets of the index of the label boxes in px at 100 %.
	 */
	Graph.labelBoxIndexSize = 64;

	/**
	 * Largest number of buckets of a state or a box in the index of the label
	 * boxes. Larger states are tested for every box and larger boxes against
	 * all states, so that huge or crafted sizes need no memory.
	 */
	Graph.labelBoxIndexMaxBuckets = 256;

	/**
	 * Largest number of states that the index tests for a label box. A box
	 * with more states near it is not free, so that crafted diagrams with
	 * many cells at one place, such as edges on the same line, stay linear.
	 * The entries of the buckets that are looked at, also old ones of states
	 * that were added again, are limited to four times this number.
	 */
	Graph.labelBoxIndexMaxTests = 256;

	/**
	 * Returns true if the given first and last bucket of the index of the
	 * label boxes can be counted: past 2^53, as for a coordinate of about
	 * 6e17 at 100 %, i + 1 is i and a loop over the buckets never ends.
	 */
	var isLabelBoxBucketRange = function(first, last)
	{
		return Number.isSafeInteger(first) && Number.isSafeInteger(last);
	};

	/**
	 * Index of the states that can be in the way of a label box (see
	 * isLabelBoxFree) in a grid of buckets in screen units: the sides of
	 * the shapes, as a shape that holds the box is not in the way and one
	 * that is in the way crosses it with a side, the boxes of the labels,
	 * which are measured first, and the segments of the edges. A box is only
	 * tested against the states in its buckets, so that placing all labels is
	 * linear and not quadratic in the number of cells. Invalid states are
	 * left out, as isLabelBoxFree ignores them after the first pass. A state
	 * that changes is added again with add, which drops its old entries.
	 * The large states and all states are maps from the state to its entry,
	 * so that a state that is added again is in them only once.
	 */
	var LabelBoxIndex = function(graph)
	{
		this.graph = graph;
		this.size = Math.max(1, Graph.labelBoxIndexSize * graph.view.scale);
		this.buckets = new Map();
		this.versions = new Map();
		this.seen = new Map();
		this.stamp = 0;
		this.entries = new Map();
		this.large = new Map();

		graph.view.states.visit(mxUtils.bind(this, function(key, state)
		{
			this.add(state);
		}));
	};

	/**
	 * Adds the given state or replaces its entries.
	 */
	LabelBoxIndex.prototype.add = function(s)
	{
		var version = (this.versions.get(s) || 0) + 1;
		this.versions.set(s, version);
		this.entries.delete(s);
		this.large.delete(s);

		if (s == null || s.invalid)
		{
			return;
		}

		var view = this.graph.view;
		var model = this.graph.getModel();
		var size = this.size;
		var max = Graph.labelBoxIndexMaxBuckets;
		var keys = new Set();
		var large = false;

		// Buckets of the given rectangle, with a margin of 1 px for rounding
		var addRect = function(x0, y0, x1, y1)
		{
			var i0 = Math.floor((x0 - 1) / size);
			var i1 = Math.floor((x1 + 1) / size);
			var j0 = Math.floor((y0 - 1) / size);
			var j1 = Math.floor((y1 + 1) / size);

			if (!large && isLabelBoxBucketRange(i0, i1) && isLabelBoxBucketRange(j0, j1) &&
				(i1 - i0 + 1) * (j1 - j0 + 1) <= max - keys.size)
			{
				for (var i = i0; i <= i1; i++)
				{
					for (var j = j0; j <= j1; j++)
					{
						keys.add(i + ',' + j);
					}
				}
			}
			else
			{
				large = true;
			}
		};

		// Buckets of the given segment, row by row
		var addSegment = function(p0, p1)
		{
			var y0 = Math.min(p0.y, p1.y);
			var y1 = Math.max(p0.y, p1.y);
			var j0 = Math.floor(y0 / size);
			var j1 = Math.floor(y1 / size);

			if (large || !isLabelBoxBucketRange(j0, j1) || j1 - j0 >= max)
			{
				large = true;

				return;
			}

			for (var j = j0; j <= j1 && !large; j++)
			{
				var ya = Math.max(y0, j * size);
				var yb = Math.min(y1, (j + 1) * size);
				var xa = p0.x;
				var xb = p1.x;

				if (p1.y != p0.y)
				{
					xa = p0.x + (p1.x - p0.x) * (ya - p0.y) / (p1.y - p0.y);
					xb = p0.x + (p1.x - p0.x) * (yb - p0.y) / (p1.y - p0.y);
				}

				addRect(Math.min(xa, xb), ya, Math.max(xa, xb), yb);
			}
		};

		if (model.isVertex(s.cell) && s.shape != null && s.width > 0 && s.height > 0 &&
			!model.isEdge(model.getParent(s.cell)))
		{
			addRect(s.x, s.y, s.x + s.width, s.y);
			addRect(s.x, s.y + s.height, s.x + s.width, s.y + s.height);
			addRect(s.x, s.y, s.x, s.y + s.height);
			addRect(s.x + s.width, s.y, s.x + s.width, s.y + s.height);
		}

		if (s.text != null && s.text.invalidBoundingBox)
		{
			view.updateBoundingBox(s);
		}

		if (s.text != null && s.text.boundingBox != null)
		{
			var bb = s.text.boundingBox;
			addRect(bb.x, bb.y, bb.x + bb.width, bb.y + bb.height);
		}

		if (model.isEdge(s.cell) && s.absolutePoints != null)
		{
			for (var i = 0; i < s.absolutePoints.length - 1; i++)
			{
				if (s.absolutePoints[i] != null && s.absolutePoints[i + 1] != null)
				{
					addSegment(s.absolutePoints[i], s.absolutePoints[i + 1]);
				}
			}
		}

		var entry = [s, version];
		this.entries.set(s, entry);

		if (large)
		{
			this.large.set(s, entry);
		}
		else
		{
			keys.forEach(mxUtils.bind(this, function(key)
			{
				var bucket = this.buckets.get(key);

				if (bucket == null)
				{
					bucket = [];
					this.buckets.set(key, bucket);
				}

				bucket.push(entry);
			}));
		}
	};

	/**
	 * Returns false if blocks returns true for one of the states near the
	 * given box in screen units or for one of the large states, or if there
	 * are more than labelBoxIndexMaxTests of them.
	 */
	LabelBoxIndex.prototype.isFree = function(b, blocks)
	{
		var versions = this.versions;
		var seen = this.seen;
		var stamp = ++this.stamp;
		var max = Graph.labelBoxIndexMaxTests;
		var visits = 0;
		var tests = 0;

		// Returns true if the state of the given entry is in the way or the
		// box has too many states near it
		var test = function(entry)
		{
			var s = entry[0];

			if (++visits > 4 * max)
			{
				return true;
			}
			else if (versions.get(s) != entry[1] || seen.get(s) === stamp)
			{
				return false;
			}

			seen.set(s, stamp);

			return ++tests > max || blocks(s);
		};

		var testAll = function(entries)
		{
			if (Array.isArray(entries))
			{
				for (var i = 0; i < entries.length; i++)
				{
					if (test(entries[i]))
					{
						return true;
					}
				}
			}
			else
			{
				for (var entry of entries.values())
				{
					if (test(entry))
					{
						return true;
					}
				}
			}

			return false;
		};

		var size = this.size;
		var i0 = Math.floor((b.x - 1) / size);
		var i1 = Math.floor((b.x + b.width + 1) / size);
		var j0 = Math.floor((b.y - 1) / size);
		var j1 = Math.floor((b.y + b.height + 1) / size);

		if (!isLabelBoxBucketRange(i0, i1) || !isLabelBoxBucketRange(j0, j1) ||
			(i1 - i0 + 1) * (j1 - j0 + 1) > Graph.labelBoxIndexMaxBuckets)
		{
			return !testAll(this.entries);
		}

		if (testAll(this.large))
		{
			return false;
		}

		for (var i = i0; i <= i1; i++)
		{
			for (var j = j0; j <= j1; j++)
			{
				var bucket = this.buckets.get(i + ',' + j);

				if (bucket != null && testAll(bucket))
				{
					return false;
				}
			}
		}

		return true;
	};

	/**
	 * Returns a new index of the states that can be in the way of label boxes
	 * for view.labelBoxIndex (see isLabelBoxFree), which is valid while the
	 * states do not change, except the ones that are added again.
	 */
	Graph.prototype.createLabelBoxIndex = function()
	{
		return new LabelBoxIndex(this);
	};

	/**
	 * Returns the placement of the label of the given state with
	 * labelPlacement=auto as an object with the vertical alignment (valign),
	 * the distances (dx, dy) in model units by which the label moves from its
	 * position on the edge, pending, which is true if states were ignored
	 * that are not validated yet, and placedValign, the vertical alignment
	 * that the label gets once it has an offset, or null if the label is not
	 * placed automatically.
	 */
	Graph.prototype.getAutoLabelPlacement = function(state)
	{
		if (!this.isAutoLabelPlacement(state))
		{
			return null;
		}

		var model = this.getModel();
		var edgeState = (model.isEdge(state.cell)) ? state :
			this.view.getState(model.getParent(state.cell));
		var geo = this.getCellGeometry(state.cell);
		var result = {valign: mxConstants.ALIGN_MIDDLE, dx: 0, dy: 0, pending: false,
			placedValign: mxConstants.ALIGN_MIDDLE};

		if (edgeState == null || geo == null || !geo.relative ||
			edgeState.absolutePoints == null ||
			edgeState.absolutePoints.length < 2)
		{
			return result;
		}

		// Point on the line under the label without its offsets
		var line = geo.clone();
		line.y = 0;
		line.offset = null;
		var pt = this.view.getPoint(edgeState, line);
		var pts = edgeState.absolutePoints;
		var index = -1;
		var dist = null;

		if (pt == null)
		{
			return result;
		}

		for (var i = 0; i < pts.length - 1; i++)
		{
			if (pts[i] != null && pts[i + 1] != null)
			{
				var d = mxUtils.ptSegDistSq(pts[i].x, pts[i].y,
					pts[i + 1].x, pts[i + 1].y, pt.x, pt.y);

				if (dist == null || d < dist)
				{
					dist = d;
					index = i;
				}
			}
		}

		if (index < 0)
		{
			return result;
		}

		// A label with an offset keeps its place and is above a horizontal line
		if (this.getLabelSegmentDirection(pts[index], pts[index + 1]) == 'h')
		{
			result.placedValign = mxConstants.ALIGN_BOTTOM;
		}

		if (geo.y != 0 || (geo.offset != null && (geo.offset.x != 0 || geo.offset.y != 0)))
		{
			result.valign = result.placedValign;

			return result;
		}

		var skipped = {invalid: false};
		var size = this.getAutoLabelSize(state, skipped);
		var align = mxUtils.getValue(state.style, mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
		var room = this.getLabelSegmentRoom(edgeState, index, size, align);
		var along = Math.sqrt((pt.x - pts[index].x) * (pt.x - pts[index].x) +
			(pt.y - pts[index].y) * (pt.y - pts[index].y));

		// A label in the middle of its edge moves to the nearest segment that holds it
		if (room == null && geo.x == 0 && model.isEdge(state.cell))
		{
			var best = null;

			for (var i = 0; i < pts.length - 1; i++)
			{
				var r = (i != index) ? this.getLabelSegmentRoom(edgeState, i, size, align) : null;

				if (r != null)
				{
					var mid = pointOnSegment(pts[i], pts[i + 1], (r[0] + r[1]) / 2);
					var d = (mid.x - pt.x) * (mid.x - pt.x) + (mid.y - pt.y) * (mid.y - pt.y);

					if (best == null || d < best.d)
					{
						best = {index: i, room: r, d: d, along: (r[0] + r[1]) / 2};
					}
				}
			}

			if (best != null)
			{
				index = best.index;
				room = best.room;
				along = best.along;
			}
		}

		// Slides along the segment to keep clear of bends and arrowheads
		if (room != null)
		{
			along = Math.max(room[0], Math.min(room[1], along));
		}

		var p0 = pts[index];
		var p1 = pts[index + 1];
		var anchor = pointOnSegment(p0, p1, along);
		result.dx = (anchor.x - pt.x) / this.view.scale;
		result.dy = (anchor.y - pt.y) / this.view.scale;

		// Above the line, below where that is taken, on the line where both are
		if (this.getLabelSegmentDirection(p0, p1) == 'h')
		{
			var gap = 2 * this.view.scale;
			var x = anchor.x + mxUtils.getAlignmentAsPoint(align, null).x * size.width;
			var candidates = [[mxConstants.ALIGN_BOTTOM, anchor.y - size.height - gap],
				[mxConstants.ALIGN_TOP, anchor.y + gap]];
			result.valign = mxConstants.ALIGN_MIDDLE;

			// The first pass of a validation keeps the side of the last
			// placement, or puts the label above the line, and places it again
			// at the end, when all states are valid and measured and the boxes
			// are tested against the states near them, as testing each box
			// against all states would be quadratic (see LabelBoxIndex)
			if (this.view.autoLabelPending != null)
			{
				var last = state.autoLabelPlacement;
				result.valign = (last != null) ? last.valign : candidates[0][0];
				skipped.invalid = true;
			}
			else
			{
				for (var i = 0; i < candidates.length; i++)
				{
					if (this.isLabelBoxFree(state, edgeState, index, new mxRectangle(
						x, candidates[i][1], size.width, size.height), skipped))
					{
						result.valign = candidates[i][0];
						break;
					}
				}
			}
		}

		result.pending = skipped.invalid;

		return result;
	};

	/**
	 * Labels with auto placement get the vertical alignment of their placement,
	 * whatever their own.
	 */
	var graphGetVerticalAlign = Graph.prototype.getVerticalAlign;

	Graph.prototype.getVerticalAlign = function(state)
	{
		if (this.isAutoLabelPlacement(state))
		{
			var placement = (state.autoLabelPlacement != null) ?
				state.autoLabelPlacement : this.getAutoLabelPlacement(state);

			if (placement != null)
			{
				return placement.valign;
			}
		}

		return graphGetVerticalAlign.apply(this, arguments);
	};

	/**
	 * Repaints a label whose vertical alignment changed with its placement,
	 * as the vertical alignment is only read for a new style.
	 */
	var cellRendererRedrawLabel = mxCellRenderer.prototype.redrawLabel;

	mxCellRenderer.prototype.redrawLabel = function(state, forced)
	{
		var placement = state.autoLabelPlacement;

		if (!forced && state.text != null && ((placement != null) ?
			state.text.valign != placement.valign : state.text.autoLabelPlaced))
		{
			forced = true;
		}

		cellRendererRedrawLabel.apply(this, [state, forced]);

		if (state.text != null)
		{
			state.text.autoLabelPlaced = placement != null;
		}
	};

	/**
	 * Moves the label of the given state by its automatic placement: the
	 * absolute offset of an edge, which is where its label and the label
	 * handle are, or the position of a label cell, so that the handles, the
	 * in-place editor and moving the label use the place where it is painted.
	 * The cached bounds of model coordinates keep the placement on a zoom.
	 * Placements of a validation that ignored states which were not validated
	 * yet are computed again at its end (see flushAutoLabelPlacements).
	 */
	mxGraphView.prototype.updateAutoLabelPlacement = function(state)
	{
		var graph = this.graph;
		var placement = (typeof graph.getAutoLabelPlacement === 'function') ?
			graph.getAutoLabelPlacement(state) : null;
		state.autoLabelPlacement = placement;

		if (placement != null)
		{
			if (placement.dx != 0 || placement.dy != 0)
			{
				var dx = placement.dx * this.scale;
				var dy = placement.dy * this.scale;

				if (graph.model.isEdge(state.cell))
				{
					state.absoluteOffset.x += dx;
					state.absoluteOffset.y += dy;
				}
				else
				{
					state.origin.x += placement.dx;
					state.origin.y += placement.dy;
					state.x += dx;
					state.y += dy;
				}
			}

			if (placement.pending && this.autoLabelPending != null &&
				!state.autoLabelPending && this.getState(state.cell) == state)
			{
				state.autoLabelPending = true;
				this.autoLabelPending.push(state);
			}
		}
	};

	/**
	 * Places the labels of edges.
	 */
	var graphViewUpdateEdgeLabelOffset = mxGraphView.prototype.updateEdgeLabelOffset;

	mxGraphView.prototype.updateEdgeLabelOffset = function(state)
	{
		graphViewUpdateEdgeLabelOffset.apply(this, arguments);
		this.updateAutoLabelPlacement(state);
	};

	/**
	 * Places the label cells of edges.
	 */
	var graphViewUpdateVertexState = mxGraphView.prototype.updateVertexState;

	mxGraphView.prototype.updateVertexState = function(state, geo)
	{
		graphViewUpdateVertexState.apply(this, arguments);
		this.updateAutoLabelPlacement(state);
	};

	/**
	 * Computes the placements again that ignored states which were not
	 * validated or measured yet, when all states are valid, and repaints the
	 * labels whose placement changed. A validation paints each state after it
	 * is updated and measures the labels at its end, so the cells after a
	 * label in the order of the validation were not in its way, such as the
	 * shapes after an edge that is inserted before its source, and after a
	 * zoom the size of the label was not known.
	 */
	mxGraphView.prototype.flushAutoLabelPlacements = function()
	{
		var states = this.autoLabelPending;

		if (states != null && states.length > 0)
		{
			// Measures the labels in the way (see Graph.isLabelBoxFree)
			this.autoLabelPending = null;

			try
			{
				this.updateAutoLabelPlacements(states);
			}
			finally
			{
				this.autoLabelPending = [];
			}
		}
	};

	/**
	 * Returns true if the given label is HTML in a foreignObject, which
	 * mxText.updateBoundingBox measures by the size of its text.
	 */
	var isForeignObjectLabel = function(text)
	{
		var node = text.node;

		return node != null && node.ownerSVGElement != null && node.firstChild != null &&
			node.firstChild.firstChild != null && node.firstChild.firstChild.nodeName == 'foreignObject' &&
			text.bounds != null && text.getTextRotation() == 0;
	};

	/**
	 * Returns the group with the lines of the given plain text label (see
	 * mxSvgCanvas2D.plainText) if it has no transform, or null if the label
	 * is not plain text in SVG.
	 */
	var getPlainTextGroup = function(text)
	{
		var node = text.node;
		var group = null;

		if (node != null && node.ownerSVGElement != null && text.bounds != null)
		{
			for (var child = node.firstChild; child != null; child = child.nextSibling)
			{
				if (child.nodeName == 'g' && group == null)
				{
					group = child;
				}
				else if (child.nodeName != 'clipPath')
				{
					return null;
				}
			}

			for (var child = (group != null) ? group.firstChild : null; child != null; child = child.nextSibling)
			{
				if (child.nodeName != 'text' && child.nodeName != 'title' &&
					(child.nodeName != 'rect' || child.textBackground == null))
				{
					return null;
				}
			}
		}

		return (group != null && group.getAttribute('transform') == null) ? group : null;
	};

	/**
	 * Returns the first child of the given group of a plain text label with the
	 * given name, or null if there is none.
	 */
	var getPlainTextChild = function(group, name)
	{
		for (var child = group.firstChild; child != null; child = child.nextSibling)
		{
			if (child.nodeName == name)
			{
				return child;
			}
		}

		return null;
	};

	/**
	 * Returns the position of the first line of the given group of a plain
	 * text label in the units of the label, or null if it has no lines.
	 */
	var getPlainTextPosition = function(group)
	{
		var line = getPlainTextChild(group, 'text');

		return (line != null) ? new mxPoint(parseFloat(line.getAttribute('x')),
			parseFloat(line.getAttribute('y'))) : null;
	};

	/**
	 * Returns the measured size of the given HTML label for moveLabelBox and
	 * the measured box and the position of the given plain text label that is
	 * measured in the DOM for moveTextBox, or null if it is not measured or
	 * not painted in a foreignObject or as plain text. The box of the text
	 * of a background is only known if it was measured (see
	 * mxSvgCanvas2D.addTextBackground).
	 */
	var getLabelBoxSize = function(text)
	{
		if (text != null && !text.invalidBoundingBox && text.bounds != null)
		{
			if (text.boundingBox != null && isForeignObjectLabel(text))
			{
				return {width: text.boundingBox.width, height: text.boundingBox.height,
					value: text.value, scale: text.scale, bounds: text.bounds.clone()};
			}

			var group = (text.isStringSizeMeasured()) ? getPlainTextGroup(text) : null;

			if (group != null)
			{
				var bg = getPlainTextChild(group, 'rect');
				var tb = (bg != null) ? bg.textBackground : null;
				var pos = getPlainTextPosition(group);

				if (tb == null || (tb.pending == null && tb.dy == 1) || pos == null)
				{
					return {plain: text, value: text.value, scale: text.scale, bounds: text.bounds.clone(),
						align: text.align, position: pos, boundingBox: (text.boundingBox != null) ?
						text.boundingBox.clone() : null, text: (tb != null && pos != null) ?
						new mxRectangle(tb.x, tb.y, tb.width, tb.height) : null};
				}
			}
		}

		return null;
	};

	/**
	 * Sets the box of the given HTML label that was painted again at another
	 * place with the same text and size, as mxText.updateBoundingBox does
	 * with the measured size, so that it is not measured after each repaint,
	 * which takes a layout of the page each time.
	 */
	var moveLabelBox = function(text, size)
	{
		if (size != null && size.plain == null && text.invalidBoundingBox && text.margin != null &&
			text.value === size.value && text.scale == size.scale && isForeignObjectLabel(text) &&
			text.bounds.width == size.bounds.width && text.bounds.height == size.bounds.height)
		{
			text.boundingBox = new mxRectangle(text.bounds.x + text.margin.x * size.width,
				text.bounds.y + text.margin.y * size.height, size.width, size.height);
			text.invalidBoundingBox = false;
		}
	};

	/**
	 * Sets an estimate of the box of the given plain text label that was
	 * painted again at another place with the same text, size and alignment
	 * and returns true, so that it is not measured after each repaint. The
	 * box is the measured box moved by the move of the first line, and the
	 * background is placed on the pixels around the moved box of its text, as
	 * mxSvgCanvas2D.updateTextBackground does with the measured box. As getBBox
	 * returns float32 values and the positions are rounded (see
	 * mxSvgCanvas2D.format), the estimate can be off by a fraction of a pixel
	 * (or by a pixel of a background), so it is only used to place the labels
	 * and the labels are measured once the placements are done (see
	 * updateAutoLabelPlacements). Labels with a transform, such as rotated
	 * labels, are not moved by the move of their lines.
	 */
	var moveTextBox = function(text, size)
	{
		var group = (size != null && size.plain == text && text.invalidBoundingBox &&
			text.value === size.value && text.scale == size.scale && text.align == size.align &&
			text.bounds.width == size.bounds.width && text.bounds.height == size.bounds.height &&
			text.isStringSizeMeasured()) ? getPlainTextGroup(text) : null;

		if (group != null)
		{
			var pos = getPlainTextPosition(group);
			var bg = getPlainTextChild(group, 'rect');
			var tb = (bg != null) ? bg.textBackground : null;

			// A label without lines has no box (see mxText.updateBoundingBox)
			if (pos == null && size.position == null && size.boundingBox == null)
			{
				text.boundingBox = null;
				text.invalidBoundingBox = false;

				return true;
			}
			else if (pos != null && size.position != null && size.boundingBox != null &&
				(tb == null) == (size.text == null) && (tb == null || tb.pending != null) &&
				isFinite(pos.x - size.position.x) && isFinite(pos.y - size.position.y))
			{
				var dx = pos.x - size.position.x;
				var dy = pos.y - size.position.y;

				if (tb == null)
				{
					var p0 = text.getScreenRectangle(new mxRectangle(size.position.x, size.position.y, 0, 0));
					var p1 = text.getScreenRectangle(new mxRectangle(pos.x, pos.y, 0, 0));
					text.boundingBox = size.boundingBox.clone();
					text.boundingBox.x += p1.x - p0.x;
					text.boundingBox.y += p1.y - p0.y;
				}
				else
				{
					var box = new mxRectangle(size.text.x + dx, size.text.y + dy,
						size.text.width, size.text.height);
					var p = tb.pending;
					var r = mxSvgCanvas2D.getTextBackgroundBounds(bg, (p.px != null) ? p.px : 1,
						(p.ox != null) ? p.ox : 0, (p.oy != null) ? p.oy : 0, box);
					box.add(new mxRectangle(r.x + r.offset, r.y + r.offset, r.width, r.height));
					text.boundingBox = text.getScreenRectangle(box);
				}

				text.invalidBoundingBox = false;

				return true;
			}
		}

		return false;
	};

	/**
	 * Places the labels of the given states again and repaints the labels
	 * whose placement changed. Many labels are tested against the states near
	 * them (see createLabelBoxIndex), and each label that is placed again is
	 * added to the index again, so that the next labels see where it is. The
	 * labels are measured before the first repaint and only moved after it
	 * (see moveLabelBox), as measuring a label after each repaint makes the
	 * placement quadratic in the size of the page. Plain text labels get an
	 * estimate of their box (see moveTextBox) and are measured at once after
	 * the last repaint.
	 */
	mxGraphView.prototype.updateAutoLabelPlacements = function(states)
	{
		for (var i = 0; i < states.length; i++)
		{
			if (!states[i].invalid)
			{
				this.updateBoundingBox(states[i]);
			}
		}

		var index = (this.labelBoxIndex == null && states.length >= Graph.labelBoxIndexMinLabels &&
			typeof this.graph.createLabelBoxIndex === 'function') ?
			this.graph.createLabelBoxIndex() : null;
		var estimated = [];

		if (index != null)
		{
			this.labelBoxIndex = index;
		}

		try
		{
			for (var i = 0; i < states.length; i++)
			{
				var state = states[i];
				state.autoLabelPending = false;

				if (!state.invalid && this.getState(state.cell) == state)
				{
					var prev = state.autoLabelPlacement;
					var size = getLabelBoxSize(state.text);

					if (this.graph.model.isEdge(state.cell))
					{
						this.updateEdgeLabelOffset(state);
						state.updateCachedBounds();
					}
					else
					{
						this.updateCellState(state);
					}

					var next = state.autoLabelPlacement;

					if (prev == null || next == null || prev.valign != next.valign ||
						prev.dx != next.dx || prev.dy != next.dy)
					{
						this.graph.cellRenderer.redraw(state, false, this.isRendering());

						if (state.text != null)
						{
							moveLabelBox(state.text, size);

							if (moveTextBox(state.text, size))
							{
								estimated.push(state.text);
							}
						}

						state.updateCachedBounds();

						if (this.labelBoxIndex != null)
						{
							this.labelBoxIndex.add(state);
						}
					}
				}
			}
		}
		finally
		{
			if (index != null)
			{
				this.labelBoxIndex = null;
			}

			// Measures the estimated boxes, so that their pending backgrounds
			// are measured at once and the page is laid out once for all
			for (var i = 0; i < estimated.length; i++)
			{
				estimated[i].invalidBoundingBox = true;
			}

			for (var i = 0; i < estimated.length; i++)
			{
				if (estimated[i].state != null && estimated[i].state.text == estimated[i])
				{
					this.updateBoundingBox(estimated[i].state);
				}
			}
		}
	};

	/**
	 * Computes the pending placements at the end of the walk over all cells
	 * and of the validation of the invalid cells, before the graph bounds and
	 * the label gaps are updated.
	 */
	var graphViewValidateCellState = mxGraphView.prototype.validateCellState;

	mxGraphView.prototype.validateCellState = function(cell, recurse)
	{
		var state = graphViewValidateCellState.apply(this, arguments);

		if (cell != null && (recurse == null || recurse) && (cell == this.currentRoot ||
			this.graph.getModel().getParent(cell) == null))
		{
			this.flushAutoLabelPlacements();
		}

		return state;
	};

	var graphViewValidateInvalidCells = mxGraphView.prototype.validateInvalidCells;

	mxGraphView.prototype.validateInvalidCells = function(root)
	{
		var result = graphViewValidateInvalidCells.apply(this, arguments);

		if (result)
		{
			this.flushAutoLabelPlacements();
		}

		return result;
	};

	/**
	 * Collects the pending placements of a validation, which are computed
	 * again before its end or, if a part of the graph was validated, after it.
	 */
	var graphViewValidate = mxGraphView.prototype.validate;

	mxGraphView.prototype.validate = function(cell)
	{
		var prev = this.autoLabelPending;
		this.autoLabelPending = [];

		try
		{
			graphViewValidate.apply(this, arguments);
			this.flushAutoLabelPlacements();
		}
		finally
		{
			for (var i = 0; i < this.autoLabelPending.length; i++)
			{
				this.autoLabelPending[i].autoLabelPending = false;
			}

			this.autoLabelPending = prev;
		}
	};

	/**
	 * Returns the distance in model units by which the anchor of the painted
	 * label of the given state moves so that the box of the label stays where
	 * it is with the given vertical alignment (see mxText.getSpacing).
	 */
	Graph.prototype.getLabelValignOffset = function(state, valign)
	{
		var text = state.text;

		if (text == null || text.boundingBox == null || text.valign == valign)
		{
			return 0;
		}

		var s = this.view.scale;
		var h = text.boundingBox.height / s;
		var bh = (text.bounds != null) ? text.bounds.height / s : 0;

		// Top of the box relative to the top of the label bounds
		var top = function(v)
		{
			if (v == mxConstants.ALIGN_MIDDLE)
			{
				return (bh - h + text.spacingTop - text.spacingBottom) / 2;
			}
			else if (v == mxConstants.ALIGN_BOTTOM)
			{
				return bh - h - text.spacingBottom - text.baseSpacingBottom;
			}
			else
			{
				return text.spacingTop + text.baseSpacingTop;
			}
		};

		return top(text.valign) - top(valign);
	};

	/**
	 * A label cell with auto placement that is moved keeps the box where it
	 * was painted plus the move, as the offset turns the placement off and
	 * gives it the vertical alignment of a placed label (see
	 * getAutoLabelPlacement).
	 */
	var graphTranslateCell = Graph.prototype.translateCell;

	Graph.prototype.translateCell = function(cell, dx, dy)
	{
		var state = this.view.getState(cell);
		var placement = (state != null) ? state.autoLabelPlacement : null;

		if (placement != null && this.isEdgeLabelCell(cell))
		{
			var shift = this.getLabelValignOffset(state, placement.placedValign);

			if (placement.dx != 0 || placement.dy != 0 || shift != 0)
			{
				dx = parseFloat(dx) + placement.dx;
				dy = parseFloat(dy) + placement.dy + shift;
			}
		}

		graphTranslateCell.call(this, cell, dx, dy);
	};

	/**
	 * A label of an edge with auto placement that is moved off the line by its
	 * handle keeps the box where the preview painted it: the placed label is
	 * above a horizontal line and centered on the others (see
	 * getAutoLabelPlacement). A label moved along the line stays placed
	 * automatically.
	 */
	if (typeof mxEdgeHandler !== 'undefined')
	{
		var edgeHandlerMoveLabel = mxEdgeHandler.prototype.moveLabel;

		mxEdgeHandler.prototype.moveLabel = function(edgeState, x, y)
		{
			var pts = edgeState.absolutePoints;

			if (edgeState.autoLabelPlacement != null && pts != null &&
				this.graph.isAutoLabelPlacement(edgeState))
			{
				var index = -1;
				var dist = null;

				for (var i = 0; i < pts.length - 1; i++)
				{
					if (pts[i] != null && pts[i + 1] != null)
					{
						var d = mxUtils.ptSegDistSq(pts[i].x, pts[i].y,
							pts[i + 1].x, pts[i + 1].y, x, y);

						if (dist == null || d < dist)
						{
							dist = d;
							index = i;
						}
					}
				}

				// Rounded to the geometry as in moveLabel
				if (index >= 0 && Math.sqrt(dist) / this.graph.view.scale >= 0.5)
				{
					y += this.graph.getLabelValignOffset(edgeState, (this.graph.getLabelSegmentDirection(
						pts[index], pts[index + 1]) == 'h') ? mxConstants.ALIGN_BOTTOM :
						mxConstants.ALIGN_MIDDLE) * this.graph.view.scale;
				}
			}

			edgeHandlerMoveLabel.apply(this, [edgeState, x, y]);
		};
	}
})();

// Graph.js lines 30111-33594
(function()
{
	/**
	 * Reset the list of processed edges.
	 */
	var mxGraphViewResetValidationState = mxGraphView.prototype.resetValidationState;
	mxGraphView.prototype.resetValidationState = function()
	{
		mxGraphViewResetValidationState.apply(this, arguments);
		
		this.validEdges = [];
		this.validEdgeIndex = null;
		this.lineJumpTests = 0;
	};

	/**
	 * Adds, updates or removes the icon overlay of the given cell that has
	 * the given flag (eg. isLinkOverlay). The overlay is shown for a non-empty
	 * value, created with create and updated with the optional update
	 * function if it exists.
	 */
	mxGraphView.prototype.updateIconOverlay = function(cell, flag, value, create, update)
	{
		var overlay = null;

		if (cell.overlays != null)
		{
			for (var i = 0; i < cell.overlays.length; i++)
			{
				if (cell.overlays[i][flag])
				{
					overlay = cell.overlays[i];
					break;
				}
			}
		}

		if (value != null && value.length > 0)
		{
			if (overlay == null)
			{
				overlay = create();

				if (overlay != null)
				{
					if (cell.overlays == null)
					{
						cell.overlays = [];
					}

					cell.overlays.push(overlay);
				}
			}
			else if (update != null)
			{
				update(overlay);
			}
		}
		else if (overlay != null)
		{
			for (var i = cell.overlays.length - 1; i >= 0; i--)
			{
				if (cell.overlays[i][flag])
				{
					cell.overlays.splice(i, 1);
				}
			}

			if (cell.overlays.length == 0)
			{
				cell.overlays = null;
			}
		}
	};

	/**
	 * Updates jumps for valid edges and repaints if needed.
	 */
	var mxGraphViewValidateCellState = mxGraphView.prototype.validateCellState;
	mxGraphView.prototype.validateCellState = function(cell, recurse)
	{
		recurse = (recurse != null) ? recurse : true;
		var state = this.getState(cell);

		// Forces repaint if jumps change on a valid edge
		if (state != null && recurse && this.graph.model.isEdge(state.cell) &&
			state.style != null && this.isLineJumpEdge(state) &&
			!state.invalid && this.updateLineJumps(state))
		{
			this.graph.cellRenderer.redraw(state, false, this.isRendering());
		}

		// Updates link, tooltip and note overlays before redraw in base call
		if (state != null && state.invalid)
		{
			var graph = this.graph;
			var link = (graph.showLinkIcons) ? graph.getLinkForCell(cell) : null;

			this.updateIconOverlay(cell, 'isLinkOverlay', link, function()
			{
				return graph.createLinkOverlay(cell);
			}, function(overlay)
			{
				// Shows a changed link, the click handler reads the
				// current link of the cell
				overlay.tooltip = graph.getLinkOverlayTooltip(link);
			});

			var tooltip = (graph.showTooltipIcons) ? graph.convertValueToTooltip(cell) : null;

			this.updateIconOverlay(cell, 'isTooltipOverlay', tooltip, function()
			{
				return graph.createTooltipOverlay(cell);
			}, function(overlay)
			{
				// Shows a changed tooltip
				overlay.tooltip = tooltip;
			});

			// The click handler of the note overlay reads the current note
			this.updateIconOverlay(cell, 'isNoteOverlay', (graph.showNoteIcons) ?
				graph.getNoteForCell(cell) : null, function()
			{
				return graph.createNoteOverlay(cell);
			});
		}

		state = mxGraphViewValidateCellState.apply(this, arguments);

		// Line jumps depend on the edges before this edge in the walk over
		// all cells (see mxGraphView.validateInvalidCells)
		if (state != null && Graph.lineJumpsEnabled && this.graph.model.isEdge(state.cell) &&
			state.style != null && mxUtils.getValue(state.style, 'jumpStyle', 'none') != 'none')
		{
			this.fullValidationRequired = true;
		}

		// Adds to the list of edges that may intersect with later edges
		if (state != null && recurse && this.graph.model.isEdge(state.cell) &&
			state.style != null && this.isLineJumpEdge(state))
		{
			// LATER: Reuse jumps for valid edges
			state.lineJumpCurve = this.getLineJumpCurve(state);
			state.lineJumpSegments = null;
			state.lineJumpRawSegments = null;
			this.validEdges.push(state);

			if (this.validEdgeIndex != null)
			{
				this.validEdgeIndex.add(state, this.validEdges.length - 1);
			}
		}

		return state;
	};

	mxShape.prototype.isFlowAnimationEnabled = function()
	{
		return this.state != null && this.state.view.graph.enableFlowAnimation &&
			this.state.view.graph.model.isEdge(this.state.cell) &&
			mxUtils.getValue(this.state.style, 'flowAnimation', '0') == '1';
	};

	mxShape.prototype.getFlowAnimationPath = function(index)
	{
		var paths = (this.node != null) ? this.node.
			getElementsByTagName('path') : null;

		if (paths != null)
		{
			var d = null;
			var matched = false;

			// Returns the first (or index-th) visible line stroke
			for (var i = 0; i < paths.length; i++)
			{
				var path = paths[i];

				if (path.getAttribute('visibility') == 'hidden')
				{
					// Tracks the hit tolerance clone that precedes each
					// stroked path: for sketch=1 the rough.js stroke only
					// matches its own clone's d, never the plain hit path's.
					// Frozen once a counted match locked the line geometry,
					// so a marker's clone cannot re-seed an indexed walk
					// (PipeShape uses index=2 to reach the inner stroke).
					if (!matched)
					{
						d = path.getAttribute('d');
					}
				}
				else if (path.getAttribute('stroke') == 'none')
				{
					// Event-only path: the sketch=1 double paint emits the
					// plain geometry as an invisible hit path (no visibility
					// attribute) before the rough.js paths. It consumes its
					// own tolerance clone, so the next stroked path is
					// accepted even without a clone of its own (the sketch
					// paint bypasses addTolerance for filled shapes).
					if (!matched && d == path.getAttribute('d'))
					{
						d = null;
					}
				}
				else if (path.getAttribute('data-rough-fill') == null &&
					path.getAttribute('pathLength') == null &&
					(d == null || d == path.getAttribute('d')))
				{
					// Skips rough.js hachure fill paths (data-rough-fill),
					// which are stroked in the fill color, and the copies of
					// the line for flow rings and dots and their masks
					// (pathLength, see Graph.addFlowRingsToNode), which have
					// the d of the line and would win the indexed walk of
					// hollow pipes.
					if (index == null || --index == 0)
					{
						return path;
					}

					// Locks the line geometry for indexed walks
					d = path.getAttribute('d');
					matched = true;
				}
			}
		}

		return null;
	};

	mxShape.prototype.addFlowAnimationToShape = function()
	{
		if (this.state != null)
		{
			this.state.view.graph.addFlowAnimationToNode(
				this.getFlowAnimationPath(), this.state.style, this.scale,
				this.state.view.graph.addFlowAnimationStyle(), this.node);
		}
	};
	
	/**
	 * Overrides paint to add flowAnimation style. Only the node that the
	 * canvas paints into gets the animation, not the node of a shape that is
	 * painted into another canvas, eg. for a thumbnail.
	 */
	var mxShapePaint = mxShape.prototype.paint;
	
	mxShape.prototype.paint = function(canvas)
	{
		mxShapePaint.apply(this, arguments);

		if (canvas != null && canvas.root == this.node &&
			this.isFlowAnimationEnabled())
		{
			this.addFlowAnimationToShape();
		}
	};

	/**
	 * Returns the number of copies and the offset between the copies on
	 * the page for vertices with multiple=1, or null if no copies are
	 * painted. Highlights and previews (outline) have no copies.
	 */
	mxShape.prototype.getMultipleCopies = function()
	{
		return (this.points == null && !this.outline) ?
			Graph.getMultipleCopies(this.style) : null;
	};

	/**
	 * Overrides paintShape to paint the copies of multiple=1 behind the
	 * shape, the last copy first.
	 */
	var mxShapePaintShape = mxShape.prototype.paintShape;

	mxShape.prototype.paintShape = function(c, x, y, w, h)
	{
		var copies = this.getMultipleCopies();

		if (copies != null)
		{
			// The offset is on the page, so it is reversed through the
			// rotation and the flips of mxShape.updateTransform
			var rad = mxUtils.toRadians(this.getShapeRotation());
			var cos = Math.cos(rad);
			var sin = Math.sin(rad);
			var dx = copies.dx * cos + copies.dy * sin;
			var dy = copies.dy * cos - copies.dx * sin;
			dx = (this.flipH) ? -dx : dx;
			dy = (this.flipV) ? -dy : dy;

			for (var i = copies.count; i > 0; i--)
			{
				c.save();
				c.translate(i * dx, i * dy);
				mxShapePaintShape.apply(this, arguments);
				c.restore();
			}
		}

		mxShapePaintShape.apply(this, arguments);
	};

	/**
	 * Overrides updateBoundingBox to add the copies of multiple=1.
	 */
	var mxShapeUpdateBoundingBox = mxShape.prototype.updateBoundingBox;

	mxShape.prototype.updateBoundingBox = function()
	{
		mxShapeUpdateBoundingBox.apply(this, arguments);
		var copies = (this.boundingBox != null) ? this.getMultipleCopies() : null;

		if (copies != null)
		{
			var bbox = this.boundingBox;
			var s = this.scale * copies.count;
			bbox.add(new mxRectangle(bbox.x + copies.dx * s,
				bbox.y + copies.dy * s, bbox.width, bbox.height));
		}
	};

	/**
	 * Overrides updateFloatingTerminalPoints to end edges on the copies of
	 * multiple=1 once both ends and the route are known. Connection points
	 * and other perimeter points stay on the front shape. Connection points
	 * with fixedPointSpacing=1 end on the copies in updateFixedPointSpacing,
	 * before the spacing is applied.
	 */
	var mxGraphViewUpdateFloatingTerminalPoints = mxGraphView.prototype.updateFloatingTerminalPoints;

	mxGraphView.prototype.updateFloatingTerminalPoints = function(state, source, target)
	{
		var pts = state.absolutePoints;

		// Connected ends that were already set are fixed connection points
		var fixedSource = pts != null && pts[0] != null;
		var fixedTarget = pts != null && pts[pts.length - 1] != null;
		var spacing = state.style != null && mxUtils.getValue(state.style,
			mxConstants.STYLE_FIXED_POINT_SPACING, 0) == 1;

		mxGraphViewUpdateFloatingTerminalPoints.apply(this, arguments);

		if (state.absolutePoints != null && state.absolutePoints.length > 1)
		{
			if (!fixedSource || !spacing)
			{
				this.updateMultipleTerminalPoint(state, source, true, fixedSource);
			}

			if (!fixedTarget || !spacing)
			{
				this.updateMultipleTerminalPoint(state, target, false, fixedTarget);
			}
		}
	};

	/**
	 * Moves the end of the given edge on the given terminal to where the line
	 * to the next point of the edge leaves the copies of multiple=1 (see
	 * getMultiplePerimeterPoint). Fixed is true if the end is a connection
	 * point, which leaves the copies without the perimeter spacing of the
	 * edge: with fixedPointSpacing=1, updateFixedPointSpacing calls this
	 * before it applies the spacing, so that the gap starts at the outline
	 * of the copies.
	 */
	mxGraphView.prototype.updateMultipleTerminalPoint = function(edge, terminal, source, fixed)
	{
		var pts = edge.absolutePoints;
		var point = pts[(source) ? 0 : pts.length - 1];
		var next = pts[(source) ? 1 : pts.length - 2];

		if (!fixed)
		{
			terminal = this.getTerminalPort(edge, terminal, source);
		}

		if (point != null && next != null && terminal != null &&
			this.graph.model.isVertex(terminal.cell) &&
			Graph.getMultipleCopies(terminal.style) != null)
		{
			var border = 0;

			if (!fixed)
			{
				border = parseFloat(edge.style[mxConstants.STYLE_PERIMETER_SPACING] || 0);
				border += parseFloat(edge.style[(source) ?
					mxConstants.STYLE_SOURCE_PERIMETER_SPACING :
					mxConstants.STYLE_TARGET_PERIMETER_SPACING] || 0);
			}

			// The perimeter is not rotated (see getFloatingTerminalPoint)
			var alpha = mxUtils.toRadians(Number(terminal.style[mxConstants.STYLE_ROTATION] || '0'));
			var center = new mxPoint(terminal.getCenterX(), terminal.getCenterY());

			if (alpha != 0)
			{
				var cos = Math.cos(-alpha);
				var sin = Math.sin(-alpha);
				point = mxUtils.getRotatedPoint(point, cos, sin, center);
				next = mxUtils.getRotatedPoint(next, cos, sin, center);
			}

			var pt = this.getMultiplePerimeterPoint(terminal, next, border, point);

			if (pt != point)
			{
				if (alpha != 0)
				{
					pt = mxUtils.getRotatedPoint(pt, Math.cos(alpha), Math.sin(alpha), center);
				}

				edge.setAbsoluteTerminalPoint(pt, source);
			}
		}
	};

	/**
	 * Returns the given point on the perimeter of the given vertex, or the
	 * point where the line from the point to next leaves the copies of
	 * multiple=1 behind the vertex that cover the point, one after the
	 * other, but not beyond next. The point is returned if next is not
	 * outside the point. The perimeter is not rotated.
	 */
	mxGraphView.prototype.getMultiplePerimeterPoint = function(terminal, next, border, point)
	{
		var copies = Graph.getMultipleCopies(terminal.style);
		var perimeter = (copies != null) ? this.getPerimeterFunction(terminal) : null;

		// Lifelines and backbones end edges on a line, which covers nothing,
		// and orthogonal perimeters project onto the rectangle
		if (perimeter == mxPerimeter.LifelinePerimeter ||
			perimeter == mxPerimeter.BackbonePerimeter)
		{
			perimeter = null;
		}
		else if (perimeter == mxPerimeter.OrthogonalPerimeter)
		{
			perimeter = mxPerimeter.RectanglePerimeter;
		}

		if (perimeter != null)
		{
			var bounds = this.getPerimeterBounds(terminal, border);
			var cx = bounds.getCenterX();
			var cy = bounds.getCenterY();
			var ux = next.x - point.x;
			var uy = next.y - point.y;
			var max = Math.sqrt(ux * ux + uy * uy);

			if (max > 0 && ux * (point.x - cx) + uy * (point.y - cy) > 0)
			{
				ux /= max;
				uy /= max;

				// Offset of the copies on the page in the unrotated perimeter
				var rad = mxUtils.toRadians(mxUtils.getValue(terminal.style,
					mxConstants.STYLE_ROTATION, 0));
				var cos = Math.cos(rad);
				var sin = Math.sin(rad);
				var dx = (copies.dx * cos + copies.dy * sin) * this.scale;
				var dy = (copies.dy * cos - copies.dx * sin) * this.scale;
				var flipH = mxUtils.getValue(terminal.style, mxConstants.STYLE_FLIPH, 0) == 1;
				var flipV = mxUtils.getValue(terminal.style, mxConstants.STYLE_FLIPV, 0) == 1;

				// Legacy support for stencilFlipH/V
				if (terminal.shape != null && terminal.shape.stencil != null)
				{
					flipH = flipH || mxUtils.getValue(terminal.style, 'stencilFlipH', 0) == 1;
					flipV = flipV || mxUtils.getValue(terminal.style, 'stencilFlipV', 0) == 1;
				}

				var size = bounds.width + bounds.height;

				// A point is inside a copy if the perimeter of the copy in the
				// direction of the point is beyond the point. Perimeters intersect
				// the line from next to the center, so next is a point far away
				// in that direction, which also makes the whole units of the
				// direction in EllipsePerimeter precise enough.
				var isInside = function(x, y, index)
				{
					x -= index * dx;
					y -= index * dy;
					x = (flipH) ? 2 * cx - x : x;
					y = (flipV) ? 2 * cy - y : y;
					var d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
					var f = 100 * Math.max(1, size) / d;
					var p = (d > 0) ? perimeter(bounds, terminal, new mxPoint(
						cx + (x - cx) * f, cy + (y - cy) * f), false) : null;

					return p != null && (p.x - x) * (x - cx) + (p.y - y) * (y - cy) > 0;
				};

				var x = point.x;
				var y = point.y;

				for (var i = 1; i <= copies.count; i++)
				{
					var t0 = 0;
					var t1 = Math.min(size, max);

					if (isInside(x, y, i) && !isInside(x + t1 * ux, y + t1 * uy, i))
					{
						// Limits the steps for huge sizes, where the precision
						// of the numbers is less than the tolerance
						for (var j = 0; j < 50 && t1 - t0 > 1e-3; j++)
						{
							var t = (t0 + t1) / 2;

							if (isInside(x + t * ux, y + t * uy, i))
							{
								t0 = t;
							}
							else
							{
								t1 = t;
							}
						}

						// Ignores copies that only touch the point
						if (t0 > 0)
						{
							x += t1 * ux;
							y += t1 * uy;
							max -= t1;
						}
					}
				}

				if (x != point.x || y != point.y)
				{
					point = new mxPoint(x, y);
				}
			}
		}

		return point;
	};
	
	/**
	 * Overrides createSvgCanvas to add adaptiveColors.
	 */
	var mxShapeCreateSvgCanvas = mxShape.prototype.createSvgCanvas;
	
	mxShape.prototype.createSvgCanvas = function(canvas)
	{
		var canvas = mxShapeCreateSvgCanvas.apply(this, arguments);

		if (this.state != null)
		{
			canvas.adaptiveColors = this.state.view.graph.getAdaptiveColors();
		}
		
		return canvas;
	};

	/**
	 * Forces repaint if routed points have changed.
	 */
	var mxCellRendererIsShapeInvalid = mxCellRenderer.prototype.isShapeInvalid;
	mxCellRenderer.prototype.isShapeInvalid = function(state, shape)
	{
		return mxCellRendererIsShapeInvalid.apply(this, arguments) ||
			(state.routedPoints != null && shape.routedPoints != null &&
			!mxUtils.equalPoints(shape.routedPoints, state.routedPoints))
	};

	/**
	 * Caches the bounds of transparentBounds cells during the validation so
	 * that the states of nested transparentBounds cells do not walk the same
	 * descendants again in updateCellState, which is quadratic in the depth.
	 * Also caches the locked states of the cells for the handlers of the
	 * selection cells that are created while the shapes are painted (see
	 * mxCellRenderer.redrawShape) and the ancestors with the data of
	 * placeholders (see Graph.getPlaceholderCell). The check is required as
	 * this also runs for plain mxGraph instances (eg. in mxGraphMlCodec).
	 */
	var mxGraphViewValidate = mxGraphView.prototype.validate;
	mxGraphView.prototype.validate = function(cell)
	{
		var args = arguments;
		var cache = this.transparentBoundsCache;
		this.transparentBoundsCache = new mxDictionary();

		try
		{
			if (this.graph.cacheLockedStates != null)
			{
				this.graph.cacheLockedStates(mxUtils.bind(this, function()
				{
					this.graph.cachePlaceholderCells(mxUtils.bind(this, function()
					{
						mxGraphViewValidate.apply(this, args);
					}));
				}), true);
			}
			else
			{
				mxGraphViewValidate.apply(this, args);
			}
		}
		finally
		{
			this.transparentBoundsCache = cache;
		}
	};

	/**
	 * Updates jumps for invalid edges.
	 */
	var mxGraphViewUpdateCellState = mxGraphView.prototype.updateCellState;
	mxGraphView.prototype.updateCellState = function(state)
	{
		mxGraphViewUpdateCellState.apply(this, arguments);

		// Derives the visible bounds of a transparentBounds cell from
		// getTransparentBounds (children union expanded by padding and, for
		// swimlanes, the title-bar start size — encoded so it lands on the
		// correct edge: x for left, y for top, width for right, height for
		// bottom). The stored geometry stays at (0,0,0,0), so state.origin
		// still points at the group's parent origin and is left untouched
		// (children compute their state.x from it).
		// The null check is required as this patches the mxGraphView
		// prototype, so it also runs for plain mxGraph instances (eg. the
		// GraphML import in mxGraphMlCodec) where isTransparentBounds is
		// not defined.
		if (this.graph.model.isVertex(state.cell) &&
			this.graph.isTransparentBounds != null &&
			this.graph.isTransparentBounds(state.cell))
		{
			var bounds = this.graph.getTransparentBounds(state.cell,
				null, null, this.transparentBoundsCache);

			if (bounds != null)
			{
				state.x = this.scale * (this.translate.x + state.origin.x +
					bounds.x);
				state.y = this.scale * (this.translate.y + state.origin.y +
					bounds.y);
				state.width = this.scale * bounds.width;
				state.height = this.scale * bounds.height;
				state.unscaledWidth = bounds.width;
				state.unscaledHeight = bounds.height;
			}
			else
			{
				state.width = 0;
				state.height = 0;
				state.unscaledWidth = 0;
				state.unscaledHeight = 0;
			}
		}

		// Updates jumps on invalid edge before repaint
		if (this.graph.model.isEdge(state.cell) &&
			this.isLineJumpEdge(state))
		{
			this.updateLineJumps(state);
		}
	};

	/**
	 * mxMorphing animates each cell from its pre-update visual position to its
	 * post-update model position by computing delta = origin - state.x where
	 * `origin` comes from getOriginForCell (geometry-only) and `state.x` is
	 * the rendered position. For a transparentBounds cell, the updateCellState
	 * override above adds the child-derived `bounds.x` to state.x so the
	 * visible box hugs the children — but origin doesn't include that offset,
	 * so the morph reads a constant non-zero delta even when nothing changed
	 * and animates the group outward; the preview offset isn't fully unwound
	 * when the animation ends, leaving the group visually displaced until the
	 * next re-validate (e.g. window resize).
	 *
	 * Forcing delta = 0 for transparentBounds cells fixes this without side
	 * effects: the group's stored geometry is pinned (drags translate the
	 * children, not the group itself), so its model-position never actually
	 * moves and there is nothing to animate. Children of the group still get
	 * their own delta computed normally — if a layout moves a child, that
	 * child still animates from pre to post position.
	 */
	var mxMorphingGetDelta = mxMorphing.prototype.getDelta;
	mxMorphing.prototype.getDelta = function(state)
	{
		if (state != null && state.cell != null &&
			this.graph.isTransparentBounds != null &&
			this.graph.isTransparentBounds(state.cell))
		{
			return new mxPoint(0, 0);
		}

		return mxMorphingGetDelta.apply(this, arguments);
	};

	/**
	 * Returns true if the given edge state takes part in line jumps, ie. if it
	 * is not curved or if its line jumps follow the painted curve
	 * (curveGeometry).
	 */
	mxGraphView.prototype.isLineJumpEdge = function(state)
	{
		return state.style[mxConstants.STYLE_CURVED] != 1 ||
			mxUtils.getValue(state.style, 'curveGeometry', '0') == '1';
	};

	/**
	 * Returns the points of the painted curve of the given edge state if its
	 * line jumps follow the curve (curveGeometry) and it is painted as a
	 * curve, or null otherwise.
	 */
	mxGraphView.prototype.getLineJumpCurve = function(state)
	{
		return (mxUtils.getValue(state.style, 'curveGeometry', '0') == '1') ?
			Graph.getEdgeCurvePoints(state) : null;
	};

	/**
	 * Size of the buckets of the index of the processed edges for line jumps
	 * in model units (see LineJumpIndex).
	 */
	Graph.lineJumpIndexSize = 64;

	/**
	 * Largest number of buckets of an edge in the index of the processed edges
	 * for line jumps. Larger edges are tested for every edge with jumps and
	 * larger edges with jumps against all processed edges, so that huge or
	 * crafted sizes need no memory.
	 */
	Graph.lineJumpIndexMaxBuckets = 256;

	/**
	 * Largest amount of work for the line jumps of a validation: the
	 * processed edges and segments that the edges with jumps are tested
	 * against count once, the segments whose bounds overlap 4 times and each
	 * jump 100 times, as it is painted. The edges with jumps after that are
	 * painted without jumps, so that crafted diagrams whose edges cross or
	 * overlap each other many times do not freeze the page. Ordinary dense
	 * diagrams stay below it: 2,000 connectors that cross the page with
	 * almost a million jumps count 142 million and open in less than 3 s, as
	 * before the limit, and 1,000 curves in each other's bounds 99 million.
	 */
	Graph.maxLineJumpTests = 150000000;

	/**
	 * Index of the processed edges of a validation (see validEdges) in a grid
	 * of buckets in screen units, so that an edge with jumps is only tested
	 * against the processed edges whose bounds are near it, and not against
	 * all processed edges, which is quadratic in the number of edges. The
	 * edges are added with their index in validEdges. The buckets and
	 * entries that the last call of get looked at are in scanned.
	 */
	var LineJumpIndex = function(size)
	{
		this.size = size;
		this.buckets = new Map();
		this.large = [];
		this.count = 0;
		this.scanned = 0;
	};

	/**
	 * Returns the first and last column and row of the buckets of the given
	 * state, or null if the state has more than lineJumpIndexMaxBuckets.
	 */
	LineJumpIndex.prototype.getRange = function(state)
	{
		var i0 = Math.floor(state.x / this.size);
		var i1 = Math.floor((state.x + state.width) / this.size);
		var j0 = Math.floor(state.y / this.size);
		var j1 = Math.floor((state.y + state.height) / this.size);

		return (Number.isSafeInteger(i0) && Number.isSafeInteger(i1) &&
			Number.isSafeInteger(j0) && Number.isSafeInteger(j1) &&
			i1 >= i0 && j1 >= j0 && (i1 - i0 + 1) * (j1 - j0 + 1) <=
			Graph.lineJumpIndexMaxBuckets) ? [i0, i1, j0, j1] : null;
	};

	/**
	 * Adds the given state with the given index.
	 */
	LineJumpIndex.prototype.add = function(state, index)
	{
		var r = this.getRange(state);
		this.count++;

		if (r == null)
		{
			this.large.push(index);
		}
		else
		{
			for (var i = r[0]; i <= r[1]; i++)
			{
				for (var j = r[2]; j <= r[3]; j++)
				{
					var key = i + ',' + j;
					var bucket = this.buckets.get(key);

					if (bucket == null)
					{
						bucket = [];
						this.buckets.set(key, bucket);
					}

					bucket.push(index);
				}
			}
		}
	};

	/**
	 * Returns the ascending indices of the states in the buckets of the given
	 * state and of the large states, which contain the states whose bounds
	 * intersect the given state, or null if the given state is large or if
	 * its buckets hold more entries than there are states, as many states in
	 * the same buckets cost more than testing all states.
	 */
	LineJumpIndex.prototype.get = function(state)
	{
		var r = this.getRange(state);
		this.scanned = 0;

		if (r == null)
		{
			return null;
		}

		var result = this.large.slice();
		var seen = new Set();
		this.scanned = (r[1] - r[0] + 1) * (r[3] - r[2] + 1) + result.length;

		for (var i = r[0]; i <= r[1] && this.scanned <= this.count; i++)
		{
			for (var j = r[2]; j <= r[3] && this.scanned <= this.count; j++)
			{
				var bucket = this.buckets.get(i + ',' + j);

				if (bucket != null)
				{
					this.scanned += bucket.length;

					for (var k = 0; k < bucket.length && this.scanned <= this.count; k++)
					{
						if (!seen.has(bucket[k]))
						{
							seen.add(bucket[k]);
							result.push(bucket[k]);
						}
					}
				}
			}
		}

		return (this.scanned <= this.count) ? result.sort(mxUtils.compareNumbers) : null;
	};

	/**
	 * Returns the processed edges whose bounds may intersect the given edge
	 * state, in the order of the validation. The edges are indexed when the
	 * first edge with jumps needs them (see LineJumpIndex), and the entries
	 * of the index that are looked at count as tests of edges (see
	 * Graph.maxLineJumpTests).
	 */
	mxGraphView.prototype.getLineJumpEdges = function(state)
	{
		var edges = this.validEdges;

		if (this.validEdgeIndex == null)
		{
			this.validEdgeIndex = new LineJumpIndex(Math.max(1,
				Graph.lineJumpIndexSize * this.scale));

			for (var i = 0; i < edges.length; i++)
			{
				this.validEdgeIndex.add(edges[i], i);
			}
		}

		var indices = this.validEdgeIndex.get(state);
		this.lineJumpTests += this.validEdgeIndex.scanned;

		if (indices != null)
		{
			var result = [];

			for (var i = 0; i < indices.length; i++)
			{
				result.push(edges[indices[i]]);
			}

			edges = result;
		}

		return edges;
	};

	/**
	 * Returns the bounds of the segment between the given points with a
	 * margin for the tolerance of mxUtils.intersection, so that segments
	 * whose bounds do not overlap do not intersect.
	 */
	var getLineJumpBounds = function(p0, p1)
	{
		var m = 1e-5 * (Math.abs(p1.x - p0.x) + Math.abs(p1.y - p0.y)) + 1;

		return {x0: Math.min(p0.x, p1.x) - m, x1: Math.max(p0.x, p1.x) + m,
			y0: Math.min(p0.y, p1.y) - m, y1: Math.max(p0.y, p1.y) + m};
	};

	/**
	 * Returns true if the given bounds (see getLineJumpBounds) are apart,
	 * and false if they overlap or a coordinate is not a number.
	 */
	var isLineJumpApart = function(a, b)
	{
		return a.x0 > b.x1 || a.x1 < b.x0 || a.y0 > b.y1 || a.y1 < b.y0;
	};

	/**
	 * Returns the segments of the given processed edge that the segments of
	 * the edges with jumps after it are tested against, with the points
	 * before and after each segment and its bounds, and the union of the
	 * bounds. If merge is true then waypoints on straight segments of edges
	 * that are not curves are removed as in updateLineJumps. The segments
	 * are kept in the state for the validation (see validateCellState).
	 */
	mxGraphView.prototype.getLineJumpSegments = function(state, merge)
	{
		var key = (merge) ? 'lineJumpSegments' : 'lineJumpRawSegments';

		if (state[key] == null)
		{
			var curve = state.lineJumpCurve;
			var pts = (curve != null) ? curve : state.absolutePoints;
			var segments = [];
			var bounds = null;
			var pl = null;

			for (var j = 0; pts != null && j < pts.length - 1; j++)
			{
				var p3 = pts[j + 1];
				var p2 = pts[j];
				var pn = pts[j + 2];

				// Ignores waypoints on straight segments
				while (merge && curve == null && j < pts.length - 2 &&
					mxUtils.ptSegDistSq(p2.x, p2.y, pn.x, pn.y,
					p3.x, p3.y) < 1 * this.scale * this.scale)
				{
					p3 = pn;
					j++;
					pn = pts[j + 2];
				}

				var b = getLineJumpBounds(p2, p3);
				segments.push({p2: p2, p3: p3, pn: pn, pl: pl, bounds: b});
				pl = p2;

				bounds = (bounds == null) ? {x0: b.x0, x1: b.x1, y0: b.y0, y1: b.y1} :
					{x0: Math.min(bounds.x0, b.x0), x1: Math.max(bounds.x1, b.x1),
					y0: Math.min(bounds.y0, b.y0), y1: Math.max(bounds.y1, b.y1)};
			}

			state[key] = {segments: segments, bounds: bounds};
		}

		return state[key];
	};

	/**
	 * Updates the jumps between given state and processed edges.
	 */
	mxGraphView.prototype.updateLineJumps = function(state)
	{
		var pts = state.absolutePoints;
		var tr = this.translate;
		var s = this.scale;
		
		if (Graph.lineJumpsEnabled && pts != null && this.validEdges != null &&
			mxUtils.getValue(state.style, 'jumpStyle', 'none') !== 'none')
		{
			var curve = this.getLineJumpCurve(state);

			if (curve != null)
			{
				return this.updateCurveLineJumps(state, curve);
			}
		}

		if (Graph.lineJumpsEnabled)
		{
			var changed = state.routedPoints != null;
			var actual = null;
			
			if (pts != null && this.validEdges != null &&
				mxUtils.getValue(state.style, 'jumpStyle', 'none') !== 'none' &&
				this.lineJumpTests <= Graph.maxLineJumpTests)
			{
				var thresh = 0.5 * this.scale;
				changed = false;
				actual = [];

				// Only jumps edges in the same layer if jumpLayers is 0
				var layer = (mxUtils.getValue(state.style, 'jumpLayers', '1') == '0') ?
					this.graph.getLayerForCell(state.cell) : null;

				// Previous edges whose bounds intersect this edge
				var edges = this.getLineJumpEdges(state);
				var near = [];

				for (var e = 0; e < edges.length; e++)
				{
					var state2 = edges[e];
					var pts2 = (state2.lineJumpCurve != null) ? state2.lineJumpCurve : state2.absolutePoints;
					this.lineJumpTests++;

					if (pts2 != null && mxUtils.intersects(state, state2) && state2.style['noJump'] != '1' &&
						(layer == null || this.graph.getLayerForCell(state2.cell) == layer))
					{
						near.push(state2);
					}
				}
				
				// Type 0 means normal waypoint, 1 means jump. Routed points
				// are stored in model units so that they do not change with
				// the scale or translate of the view.
				function addPoint(type, x, y)
				{
					var rpt = new mxPoint(mxUtils.unscale(x, s, tr.x),
						mxUtils.unscale(y, s, tr.y));
					rpt.type = type;

					actual.push(rpt);
					var curr = (state.routedPoints != null) ? state.routedPoints[actual.length - 1] : null;

					return curr == null || curr.type != type || curr.x != rpt.x || curr.y != rpt.y;
				};

				for (var i = 0; i < pts.length - 1 && actual != null; i++)
				{
					var p1 = pts[i + 1];
					var p0 = pts[i];
					var list = [];
					
					// Ignores waypoints on straight segments
					var pn = pts[i + 2];
					
					while (i < pts.length - 2 &&
						mxUtils.ptSegDistSq(p0.x, p0.y, pn.x, pn.y,
						p1.x, p1.y) < 1 * this.scale * this.scale)
					{
						p1 = pn;
						i++;
						pn = pts[i + 2];
					}
					
					changed = addPoint(0, p0.x, p0.y) || changed;
					
					// Processes the previous edges near this edge
					var bounds = getLineJumpBounds(p0, p1);

					for (var e = 0; e < near.length && actual != null; e++)
					{
						var segments = this.getLineJumpSegments(near[e], true);
						this.lineJumpTests++;

						if (segments.bounds == null || isLineJumpApart(bounds, segments.bounds))
						{
							continue;
						}

						segments = segments.segments;
						this.lineJumpTests += segments.length;

						// Compares each segment of the edge with the current segment
						for (var j = 0; j < segments.length; j++)
						{
							var seg = segments[j];

							// Segments whose bounds are apart do not intersect
							if (isLineJumpApart(bounds, seg.bounds))
							{
								continue;
							}

							var p2 = seg.p2;
							var p3 = seg.p3;
							var pl = seg.pl;
							pn = seg.pn;
							this.lineJumpTests += 4;
							var pt = mxUtils.intersection(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y);

							// Handles intersection between two segments
							if (pt != null && (Math.abs(pt.x - p0.x) > thresh ||
								Math.abs(pt.y - p0.y) > thresh) &&
								(Math.abs(pt.x - p1.x) > thresh ||
								Math.abs(pt.y - p1.y) > thresh) &&
								// Removes jumps on overlapping incoming segments
								(pl == null || mxUtils.ptLineDist(p0.x, p0.y, p1.x, p1.y, pl.x, pl.y) > thresh ||
								mxUtils.ptLineDist(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y) > thresh) &&
								// Removes jumps on overlapping outgoing segments
								(pn == null || mxUtils.ptLineDist(p0.x, p0.y, p1.x, p1.y, pn.x, pn.y) > thresh ||
								mxUtils.ptLineDist(p0.x, p0.y, p1.x, p1.y, p3.x, p3.y) > thresh))
							{
								var dx = pt.x - p0.x;
								var dy = pt.y - p0.y;
								var temp = {distSq: dx * dx + dy * dy, x: pt.x, y: pt.y};

								// Intersections must be ordered by distance from start of
								// segment, after the ones at the same distance
								var idx = 0;
								var hi = list.length;

								while (idx < hi)
								{
									var mid = (idx + hi) >> 1;

									if (list[mid].distSq > temp.distSq)
									{
										hi = mid;
									}
									else
									{
										idx = mid + 1;
									}
								}

								// Ignores multiple intersections at segment joints and
								// where overlapping edges cross the current segment
								if ((idx == 0 || Math.abs(list[idx - 1].x - temp.x) > thresh ||
									Math.abs(list[idx - 1].y - temp.y) > thresh) &&
									(idx == list.length || Math.abs(list[idx].x - temp.x) > thresh ||
									Math.abs(list[idx].y - temp.y) > thresh))
								{
									list.splice(idx, 0, temp);
								}
							}
						}

						// Paints the edge without jumps after too many tests
						if (this.lineJumpTests > Graph.maxLineJumpTests)
						{
							actual = null;
						}
					}
					
					// Adds ordered intersections to routed points
					for (var j = 0; j < list.length && actual != null; j++)
					{
						changed = addPoint(1, list[j].x, list[j].y) || changed;
						this.lineJumpTests += 100;
					}
				}
	
				if (actual != null)
				{
					var pt = pts[pts.length - 1];
					changed = addPoint(0, pt.x, pt.y) || changed;
				}
				else
				{
					changed = state.routedPoints != null;
				}
			}
			
			state.routedPoints = actual;
			
			return changed;
		}
		else
		{
			return false;
		}
	};
	
	/**
	 * Updates the jumps of the given edge state whose line jumps follow the
	 * painted curve with the given points (see getLineJumpCurve) over the
	 * processed edges. The routed points are the start and end of the curve
	 * and the intersections in between, ordered along the curve. Returns true
	 * if the routed points have changed.
	 */
	mxGraphView.prototype.updateCurveLineJumps = function(state, curve)
	{
		var tr = this.translate;
		var s = this.scale;
		var thresh = 0.5 * s;
		var jumps = [];

		// Only jumps edges in the same layer if jumpLayers is 0
		var layer = (mxUtils.getValue(state.style, 'jumpLayers', '1') == '0') ?
			this.graph.getLayerForCell(state.cell) : null;

		var edges = (this.lineJumpTests <= Graph.maxLineJumpTests) ?
			this.getLineJumpEdges(state) : null;
		var curveBounds = [];

		for (var i = 0; edges != null && i < curve.length - 1; i++)
		{
			curveBounds.push(getLineJumpBounds(curve[i], curve[i + 1]));
		}

		for (var e = 0; edges != null && e < edges.length; e++)
		{
			var state2 = edges[e];
			var pts2 = (state2.lineJumpCurve != null) ?
				state2.lineJumpCurve : state2.absolutePoints;
			this.lineJumpTests++;

			if (pts2 != null && mxUtils.intersects(state, state2) &&
				state2.style['noJump'] != '1' && (layer == null ||
				this.graph.getLayerForCell(state2.cell) == layer))
			{
				var segments = this.getLineJumpSegments(state2, false);

				for (var i = 0; i < curve.length - 1 && segments.bounds != null; i++)
				{
					var p0 = curve[i];
					var p1 = curve[i + 1];
					var bounds = curveBounds[i];
					this.lineJumpTests++;

					if (isLineJumpApart(bounds, segments.bounds))
					{
						continue;
					}

					var dx = p1.x - p0.x;
					var dy = p1.y - p0.y;
					var len2 = dx * dx + dy * dy;
					this.lineJumpTests += segments.segments.length;

					for (var j = 0; j < segments.segments.length; j++)
					{
						var seg = segments.segments[j];

						// Segments whose bounds are apart do not intersect
						if (isLineJumpApart(bounds, seg.bounds))
						{
							continue;
						}

						var p2 = seg.p2;
						var p3 = seg.p3;
						this.lineJumpTests += 4;
						var pt = mxUtils.intersection(p0.x, p0.y, p1.x, p1.y,
							p2.x, p2.y, p3.x, p3.y);

						if (pt != null)
						{
							jumps.push({pos: i + ((len2 > 0) ? ((pt.x - p0.x) * dx +
								(pt.y - p0.y) * dy) / len2 : 0), x: pt.x, y: pt.y});
						}
					}

					// Paints the curve without jumps after too many tests
					if (this.lineJumpTests > Graph.maxLineJumpTests)
					{
						jumps = [];
						edges = null;

						break;
					}
				}
			}
		}

		jumps.sort(function(a, b)
		{
			return mxUtils.compareNumbers(a.pos, b.pos);
		});

		// Type 0 is the start or end of the curve, 1 is a jump. Routed
		// points are stored in model units so that they do not change
		// with the scale or translate of the view.
		var actual = [];
		var changed = false;
		var start = curve[0];
		var end = curve[curve.length - 1];

		var addPoint = function(type, x, y)
		{
			var rpt = new mxPoint(mxUtils.unscale(x, s, tr.x),
				mxUtils.unscale(y, s, tr.y));
			rpt.type = type;
			actual.push(rpt);
			var curr = (state.routedPoints != null) ?
				state.routedPoints[actual.length - 1] : null;

			changed = changed || curr == null || curr.type != type ||
				curr.x != rpt.x || curr.y != rpt.y;
		};

		var near = function(p, x, y)
		{
			return Math.abs(p.x - x) <= thresh && Math.abs(p.y - y) <= thresh;
		};

		addPoint(0, start.x, start.y);
		var last = start;

		for (var i = 0; i < jumps.length; i++)
		{
			// Ignores intersections at the ends of the curve and at shared
			// points of the segments of the curve or of the other edges
			if (!near(last, jumps[i].x, jumps[i].y) &&
				!near(end, jumps[i].x, jumps[i].y))
			{
				addPoint(1, jumps[i].x, jumps[i].y);
				this.lineJumpTests += 100;
				last = jumps[i];

				// Curve segment of the intersection for painting
				actual[actual.length - 1].segment =
					Math.floor(jumps[i].pos / Graph.curvePointSteps);
			}
		}

		addPoint(0, end.x, end.y);
		changed = changed || (state.routedPoints != null &&
			state.routedPoints.length != actual.length);
		state.routedPoints = actual;

		return changed;
	};

	/**
	 * Paints curved edges cut at the markers and the jumps of curved edges
	 * along the painted curve.
	 */
	var mxConnectorPaintCurvedLine = mxConnector.prototype.paintCurvedLine;

	mxConnector.prototype.paintCurvedLine = function(c, pts)
	{
		if (!this.paintCutCurve(c, pts, false))
		{
			mxConnectorPaintCurvedLine.apply(this, arguments);
		}
	};

	/**
	 * Paints bezier edges cut at the markers and the jumps of bezier edges
	 * along the painted curve.
	 */
	var mxConnectorPaintBezierLine = mxConnector.prototype.paintBezierLine;

	mxConnector.prototype.paintBezierLine = function(c, pts)
	{
		if (!this.paintCutCurve(c, pts, true))
		{
			mxConnectorPaintBezierLine.apply(this, arguments);
		}
	};

	/**
	 * Returns true if the curve with the given points is cut where the
	 * markers start (trimCurve). Other curves end where the markers move
	 * their ends, which changes the course of the whole curve.
	 */
	mxConnector.prototype.isCurveTrimmed = function(pts)
	{
		return pts.length > 2 && mxUtils.getValue(this.style, 'trimCurve', '0') == '1' &&
			(mxUtils.getValue(this.style, mxConstants.STYLE_CURVED, '0') == '1' ||
			mxUtils.getValue(this.style, mxConstants.STYLE_BEZIER, '0') == '1');
	};

	/**
	 * Stores the ends of curves that are cut where the markers start (see
	 * isCurveTrimmed) before the markers move them.
	 */
	var mxConnectorCreateMarker = mxConnector.prototype.createMarker;

	mxConnector.prototype.createMarker = function(c, pts, source)
	{
		if (this.isCurveTrimmed(pts))
		{
			if (this.curveEnds == null)
			{
				this.curveEnds = [];
			}

			this.curveEnds[(source) ? 0 : 1] = pts[(source) ? 0 : pts.length - 1].clone();
		}

		return mxConnectorCreateMarker.apply(this, arguments);
	};

	/**
	 * Returns true if the jumps in the routed points of the state are painted.
	 * The jumps are computed for the points of the state, so outlines only
	 * paint them if they have these points: the selection border of an edge
	 * does, the preview of an edge handler that changes the edge does not.
	 */
	mxConnector.prototype.isLineJumpsPainted = function()
	{
		return this.state != null && (!this.outline ||
			mxUtils.equalPoints(this.points, this.state.absolutePoints));
	};

	/**
	 * Returns the distance of the ends of a jump from the intersection. The
	 * selection border of an edge has its own stroke width, so its jumps take
	 * the stroke width of the edge to follow the jumps of the edge.
	 */
	mxConnector.prototype.getLineJumpSize = function()
	{
		var sw = (this.outline && this.state.shape != null) ?
			this.state.shape.strokewidth : this.strokewidth;

		return (parseInt(mxUtils.getValue(this.style, 'jumpSize',
			Graph.defaultJumpSize)) - 2) / 2 + sw;
	};

	/**
	 * Paints the curve with the given points (see mxPolyline.paintCurvedLine
	 * and paintBezierLine) with its original ends cut where the markers start
	 * if the curve is trimmed (see isCurveTrimmed) and with the jumps in the
	 * routed points of the state (see mxGraphView.updateCurveLineJumps). Each
	 * jump cuts the curve at the given distance before and after the
	 * intersection and connects the ends with the jump style, the rest of the
	 * curve is painted with the original curve segments. Returns false if the
	 * curve is not trimmed and has no jumps.
	 */
	mxConnector.prototype.paintCutCurve = function(c, pts, bezier)
	{
		// Required for checking dirty state
		this.routedPoints = (this.state != null) ? this.state.routedPoints : null;
		var rpts = this.routedPoints;
		var style = mxUtils.getValue(this.style, 'jumpStyle', 'none');
		var trimmed = this.isCurveTrimmed(pts);
		var hasJumps = false;

		for (var i = 0; rpts != null && i < rpts.length && !hasJumps; i++)
		{
			hasJumps = rpts[i].type == 1;
		}

		hasJumps = hasJumps && style != 'none' && this.isLineJumpsPainted();

		if ((!hasJumps && !trimmed) || pts.length < 2 || pts.indexOf(null) >= 0)
		{
			return false;
		}
		else if (pts.length < 3)
		{
			// Painted as a straight line with the jumps of a straight line
			this.paintLine(c, pts, false);

			return true;
		}

		// Segments of the painted curve
		var segs = Graph.getCurveSegments(pts, bezier);
		var n = pts.length;

		// Points of the polar form (blossom) of a segment, where t0 = t1 =
		// t2 = t is the point on the curve at t and the blossoms of t0 and t1
		// are the control points of the part of the segment from t0 to t1
		var blossom = function(seg, t)
		{
			var x = 0;
			var y = 0;

			if (seg.length == 3)
			{
				var w = [(1 - t[0]) * (1 - t[1]), (1 - t[0]) * t[1] +
					t[0] * (1 - t[1]), t[0] * t[1]];
			}
			else
			{
				var w = [(1 - t[0]) * (1 - t[1]) * (1 - t[2]),
					(1 - t[0]) * (1 - t[1]) * t[2] + (1 - t[0]) * t[1] * (1 - t[2]) +
					t[0] * (1 - t[1]) * (1 - t[2]),
					(1 - t[0]) * t[1] * t[2] + t[0] * (1 - t[1]) * t[2] +
					t[0] * t[1] * (1 - t[2]),
					t[0] * t[1] * t[2]];
			}

			for (var i = 0; i < seg.length; i++)
			{
				x += w[i] * seg[i].x;
				y += w[i] * seg[i].y;
			}

			return new mxPoint(x, y);
		};

		// Positions on the curve are the index of the segment plus the
		// parameter in the segment
		var getPoint = function(pos)
		{
			var i = Math.min(Math.floor(pos), segs.length - 1);
			var t = pos - i;

			return blossom(segs[i], [t, t, t]);
		};

		var dist = function(a, b)
		{
			return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
		};

		// Position on the curve that is nearest to the given point, searched
		// in the given segment and its neighbors if a segment is given
		var getPosition = function(pt, segment)
		{
			var steps = 32;
			var best = 0;
			var bestDist = null;
			var first = (segment != null) ? Math.max(0, segment - 1) : 0;
			var last = (segment != null) ? Math.min(segs.length, segment + 2) : segs.length;

			for (var i = first * steps; i <= last * steps; i++)
			{
				var d = dist(getPoint(i / steps), pt);

				if (bestDist == null || d < bestDist)
				{
					best = i / steps;
					bestDist = d;
				}
			}

			var lo = Math.max(0, best - 1 / steps);
			var hi = Math.min(segs.length, best + 1 / steps);

			for (var i = 0; i < 24; i++)
			{
				var m1 = lo + (hi - lo) / 3;
				var m2 = hi - (hi - lo) / 3;

				if (dist(getPoint(m1), pt) < dist(getPoint(m2), pt))
				{
					hi = m2;
				}
				else
				{
					lo = m1;
				}
			}

			return (lo + hi) / 2;
		};

		// Position at the given distance along the curve from the given
		// position in the given direction, or null at the end of the curve
		var advance = function(pos, length, dir)
		{
			var step = dir / 64;
			var prev = getPoint(pos);

			while (length > 0)
			{
				var next = pos + step;

				if (next < 0 || next > segs.length)
				{
					return null;
				}

				var pt = getPoint(next);
				var d = dist(prev, pt);

				if (d >= length)
				{
					return pos + step * length / d;
				}

				length -= d;
				pos = next;
				prev = pt;
			}

			return pos;
		};

		// Adds the curve from the given start to the given end position
		var addCurve = function(from, to)
		{
			for (var i = Math.floor(from); i < segs.length && i < to; i++)
			{
				var t0 = Math.max(0, from - i);
				var t1 = Math.min(1, to - i);

				if (t1 > t0)
				{
					var seg = segs[i];

					if (seg.length == 3)
					{
						var cp = blossom(seg, [t0, t1]);
						var pe = blossom(seg, [t1, t1]);
						c.quadTo(cp.x, cp.y, pe.x, pe.y);
					}
					else
					{
						var cp1 = blossom(seg, [t0, t0, t1]);
						var cp2 = blossom(seg, [t0, t1, t1]);
						var pe = blossom(seg, [t1, t1, t1]);
						c.curveTo(cp1.x, cp1.y, cp2.x, cp2.y, pe.x, pe.y);
					}
				}
			}
		};

		// First position on the curve at the given distance from the point at
		// the given end position in the given direction, or null if the curve
		// is not that long
		var cut = function(pos, length, dir)
		{
			var pt = getPoint(pos);
			var lo = pos;
			var hi = pos;

			// Steps of 1/16 end exactly at the other end of the curve
			while (hi != null && dist(getPoint(hi), pt) < length)
			{
				lo = hi;
				hi = lo + dir / 16;
				hi = (hi >= 0 && hi <= segs.length) ? hi : null;
			}

			for (var i = 0; i < 40 && hi != null; i++)
			{
				var mid = (lo + hi) / 2;

				if (dist(getPoint(mid), pt) < length)
				{
					lo = mid;
				}
				else
				{
					hi = mid;
				}
			}

			return hi;
		};

		var start = 0;
		var end = segs.length;

		// Paints the curve with the original ends from where the markers
		// start if it is longer than the markers (see createMarker)
		if (trimmed)
		{
			var curve = pts.slice();
			curve[0] = this.curveEnds[0];
			curve[n - 1] = this.curveEnds[1];
			var moved = segs;
			segs = Graph.getCurveSegments(curve, bezier);
			start = cut(0, dist(curve[0], pts[0]), 1);
			end = cut(segs.length, dist(curve[n - 1], pts[n - 1]), -1);

			if (start == null || end == null || start >= end)
			{
				segs = moved;
				start = 0;
				end = segs.length;
			}
		}

		var first = getPoint(start);
		var current = start;

		c.begin();
		c.moveTo(first.x, first.y);

		if (hasJumps)
		{
			var size = this.getLineJumpSize();

			// Routed points are in model units
			var tr = (this.viewTranslate != null) ? this.viewTranslate :
				this.state.view.translate;

			for (var i = 0; i < rpts.length; i++)
			{
				if (rpts[i].type == 1)
				{
					var pos = getPosition(new mxPoint(rpts[i].x + tr.x,
						rpts[i].y + tr.y), rpts[i].segment);
					var from = advance(pos, size, -1);
					var to = advance(pos, size, 1);

					// Ignores jumps that are too close to the previous jump or
					// to the ends of the curve
					if (from != null && to != null && from > current && to <= end)
					{
						addCurve(current, from);
						var p0 = getPoint(from);
						var p1 = getPoint(to);
						var nx = (p1.x - p0.x) / 2;
						var ny = (p1.y - p0.y) / 2;
						var f = (Math.round(nx) < 0 || (Math.round(nx) == 0
							&& Math.round(ny) <= 0)) ? 1 : -1;

						if (style == 'sharp')
						{
							c.lineTo(p0.x - ny * f, p0.y + nx * f);
							c.lineTo(p1.x - ny * f, p1.y + nx * f);
							c.lineTo(p1.x, p1.y);
						}
						else if (style == 'line')
						{
							c.moveTo(p0.x + ny * f, p0.y - nx * f);
							c.lineTo(p0.x - ny * f, p0.y + nx * f);
							c.moveTo(p1.x - ny * f, p1.y + nx * f);
							c.lineTo(p1.x + ny * f, p1.y - nx * f);
							c.moveTo(p1.x, p1.y);
						}
						else if (style == 'arc')
						{
							f *= 1.3;
							c.curveTo(p0.x - ny * f, p0.y + nx * f,
								p1.x - ny * f, p1.y + nx * f,
								p1.x, p1.y);
						}
						else
						{
							c.moveTo(p1.x, p1.y);
						}

						current = to;
					}
				}
			}
		}

		addCurve(current, end);
		c.stroke();

		return true;
	};

	/**
	 * Overrides painting the actual shape for taking into account jump style.
	 */
	var mxConnectorPaintLine = mxConnector.prototype.paintLine;

	mxConnector.prototype.paintLine = function (c, absPts, rounded)
	{
		// Required for checking dirty state
		this.routedPoints = (this.state != null) ? this.state.routedPoints : null;
		
		if (!this.isLineJumpsPainted() || this.style == null ||
			this.state.routedPoints == null || this.state.routedPoints.length == 0)
		{
			mxConnectorPaintLine.apply(this, arguments);
		}
		else
		{
			var arcSize = mxUtils.getValue(this.style, mxConstants.STYLE_ARCSIZE,
				mxConstants.LINE_ARCSIZE) / 2;
			var size = this.getLineJumpSize();
			var style = mxUtils.getValue(this.style, 'jumpStyle', 'none');
			var moveTo = true;
			var last = null;
			var len = null;
			var pts = [];
			var n = null;

			// Routed points are in model units
			var tr = (this.viewTranslate != null) ? this.viewTranslate :
				this.state.view.translate;
			c.begin();
			
			for (var i = 0; i < this.state.routedPoints.length; i++)
			{
				var rpt = this.state.routedPoints[i];
				var pt = new mxPoint(rpt.x + tr.x, rpt.y + tr.y);
				
				// Takes first and last point from passed-in array
				if (i == 0)
				{
					pt = absPts[0];
				}
				else if (i == this.state.routedPoints.length - 1)
				{
					pt = absPts[absPts.length - 1];
				}
				
				var done = false;

				// Type 1 is an intersection
				if (last != null && rpt.type == 1)
				{
					// Checks if next/previous points are too close
					var next = this.state.routedPoints[i + 1];
					var dx = next.x + tr.x - pt.x;
					var dy = next.y + tr.y - pt.y;
					var dist = dx * dx + dy * dy;

					if (n == null)
					{
						n = new mxPoint(pt.x - last.x, pt.y - last.y);
						len = Math.sqrt(n.x * n.x + n.y * n.y);
						
						if (len > 0)
						{
							n.x = n.x * size / len;
							n.y = n.y * size / len;
						}
						else
						{
							n = null;
						}
					}
					
					if (dist > size * size && len > 0)
					{
						var dx = last.x - pt.x;
						var dy = last.y - pt.y;
						var dist = dx * dx + dy * dy;
						
						if (dist > size * size)
						{
							var p0 = new mxPoint(pt.x - n.x, pt.y - n.y);
							var p1 = new mxPoint(pt.x + n.x, pt.y + n.y);
							pts.push(p0);
							
							this.addPoints(c, pts, rounded, arcSize, false, null, moveTo);
							
							var f = (Math.round(n.x) < 0 || (Math.round(n.x) == 0
									&& Math.round(n.y) <= 0)) ? 1 : -1;
							moveTo = false;

							if (style == 'sharp')
							{
								c.lineTo(p0.x - n.y * f, p0.y + n.x * f);
								c.lineTo(p1.x - n.y * f, p1.y + n.x * f);
								c.lineTo(p1.x, p1.y);
							}
							else if (style == 'line')
							{
								c.moveTo(p0.x + n.y * f, p0.y - n.x * f);
								c.lineTo(p0.x - n.y * f, p0.y + n.x * f);
								c.moveTo(p1.x - n.y * f, p1.y + n.x * f);
								c.lineTo(p1.x + n.y * f, p1.y - n.x * f);
								c.moveTo(p1.x, p1.y);
							}
							else if (style == 'arc')
							{
								f *= 1.3;
								c.curveTo(p0.x - n.y * f, p0.y + n.x * f,
									p1.x - n.y * f, p1.y + n.x * f,
									p1.x, p1.y);
							}
							else
							{
								c.moveTo(p1.x, p1.y);
								moveTo = true;
							}
	
							pts = [p1];
							done = true;
						}
					}
				}
				else
				{
					n = null;
				}
				
				if (!done)
				{
					pts.push(pt);
					last = pt;
				}
			}
			
			this.addPoints(c, pts, rounded, arcSize, false, null, moveTo);
			c.stroke();
		}
	};
	
	/**
	 * Adds support for centerPerimeter which is a special case of a fixed point perimeter.
	 */
	var mxGraphViewGetFixedTerminalPoint = mxGraphView.prototype.getFixedTerminalPoint;
	
	mxGraphView.prototype.getFixedTerminalPoint = function(edge, terminal, source, constraint)
	{
		if (terminal != null && terminal.style[mxConstants.STYLE_PERIMETER] == 'centerPerimeter')
		{
			return new mxPoint(terminal.getCenterX(), terminal.getCenterY());
		}
		else
		{
			return mxGraphViewGetFixedTerminalPoint.apply(this, arguments);
		}
	};

	/**
	 * Adds support for snapToPoint style.
	 */
	var mxGraphViewUpdateFloatingTerminalPoint = mxGraphView.prototype.updateFloatingTerminalPoint;
	
	mxGraphView.prototype.updateFloatingTerminalPoint = function(edge, start, end, source)
	{
		if (start != null && edge != null &&
			(start.style['snapToPoint'] == '1' ||
			edge.style['snapToPoint'] == '1'))
		{
		    var pt = this.getFloatingTerminalPoint(edge, start, end, source);
		    start = this.getTerminalPort(edge, start, source);
		    edge.setAbsoluteTerminalPoint(this.snapToAnchorPoint(edge, start, end, source, pt), source);
		}
		else
		{
			mxGraphViewUpdateFloatingTerminalPoint.apply(this, arguments);
		}
	};

	mxGraphView.prototype.snapToAnchorPoint = function(edge, start, end, source, pt)
	{
		if (start != null && edge != null)
		{
	        var constraints = this.graph.getAllConnectionConstraints(start)
	        var nearest = null;
	        var dist = null;

	        // Routes are kept in model units in model coordinates, so the points
	        // are rounded and compared in model units instead of screen pixels
	        // to not depend on the scale of the validation
	        var mc = this.modelCoordinates;
	        var s = this.scale;
	        var tr = this.translate;
	    
	        if (constraints != null)
	        {
		        for (var i = 0; i < constraints.length; i++)
		        {
		            var cp = (mc) ? this.graph.getConnectionPoint(start, constraints[i], false) :
		                this.graph.getConnectionPoint(start, constraints[i]);
		            
		            if (cp != null)
		            {
		                if (mc)
		                {
		                    cp.x = mxEdgeStyle.roundScaled(cp.x, s, tr.x);
		                    cp.y = mxEdgeStyle.roundScaled(cp.y, s, tr.y);
		                }

		                var tmp = (cp.x - pt.x) * (cp.x - pt.x) + (cp.y - pt.y) * (cp.y - pt.y);

		                if (mc)
		                {
		                    tmp = mxUtils.unscale(tmp, s * s);
		                }
		            
		                if (dist == null || tmp < dist)
		                {
		                    nearest = cp;
		                    dist = tmp;
		                }
		            }
		        }
	        }
	        
	        if (nearest != null)
	        {
	            pt = nearest;
	        }
		}
		
		return pt;
	};

	/**
	 * Keeps a pixel of the view instead of a screen pixel of a segment that is
	 * shortened by the spacing at fixed points. Routes are kept in model units
	 * in model coordinates, where the pixel is a model unit, so that the ends
	 * do not depend on the scale of the validation (getPixelSize is 1 on the
	 * old path, so it is unchanged). The ends first leave the copies of
	 * multiple=1 of their terminals, so that the spacing is kept from the
	 * outline of the copies (see updateFloatingTerminalPoints).
	 */
	mxGraphView.prototype.updateFixedPointSpacing = function(state, source, target)
	{
		if (source != null)
		{
			this.updateMultipleTerminalPoint(state, source, true, true);
		}

		if (target != null)
		{
			this.updateMultipleTerminalPoint(state, target, false, true);
		}

		var pts = state.absolutePoints;
		var n = (pts != null) ? pts.length : 0;

		if (n > 1 && pts[0] != null && pts[n - 1] != null)
		{
			var scale = this.scale;

			var getSpacing = function(key)
			{
				var value = parseFloat(state.style[key] || 0);

				return (isNaN(value) || !isFinite(value)) ? 0 : value * scale;
			};

			// Returns the index of the first point that differs from the end
			var getNeighbour = function(index, step)
			{
				var pt = pts[index];

				for (var i = index + step; i >= 0 && i < n; i += step)
				{
					if (pts[i] != null && (pts[i].x != pt.x || pts[i].y != pt.y))
					{
						return i;
					}
				}

				return -1;
			};

			var p0 = pts[0];
			var pe = pts[n - 1];
			var ds = (source) ? getSpacing(mxConstants.STYLE_SOURCE_PERIMETER_SPACING) : 0;
			var dt = (target) ? getSpacing(mxConstants.STYLE_TARGET_PERIMETER_SPACING) : 0;
			var ns = (ds != 0) ? getNeighbour(0, 1) : -1;
			var nt = (dt != 0) ? getNeighbour(n - 1, -1) : -1;

			// Neighbours are taken before any end is moved
			var qs = (ns >= 0) ? pts[ns] : null;
			var qt = (nt >= 0) ? pts[nt] : null;
			var ls = (qs != null) ? Math.sqrt((qs.x - p0.x) * (qs.x - p0.x) +
				(qs.y - p0.y) * (qs.y - p0.y)) : 0;
			var lt = (qt != null) ? Math.sqrt((qt.x - pe.x) * (qt.x - pe.x) +
				(qt.y - pe.y) * (qt.y - pe.y)) : 0;

			// Clamps to the segment and splits a segment that is shared by both ends,
			// keeping a pixel of the segment so that the direction of markers is defined
			var px = this.getPixelSize();
			var ms = Math.max(0, ls - px);
			ds = Math.min(ds, ms);
			dt = Math.min(dt, Math.max(0, lt - px));

			if (ns == n - 1 && nt == 0 && ds > 0 && dt > 0 && ds + dt > ms)
			{
				var f = ms / (ds + dt);
				ds *= f;
				dt *= f;
			}

			if (qs != null && ds != 0)
			{
				state.setAbsoluteTerminalPoint(new mxPoint(
					p0.x + (qs.x - p0.x) * ds / ls,
					p0.y + (qs.y - p0.y) * ds / ls), true);
			}

			if (qt != null && dt != 0)
			{
				state.setAbsoluteTerminalPoint(new mxPoint(
					pe.x + (qt.x - pe.x) * dt / lt,
					pe.y + (qt.y - pe.y) * dt / lt), false);
			}
		}
	};

	/**
	 * Returns true if the given point is in the given state, compared in model
	 * units. The elbow styles test if their points are in a terminal, which is
	 * a tie if a point is on the border of the terminal. Compared in screen
	 * units, floating point noise that depends on the scale and translate of
	 * the view decided these ties, so collinear points were added or dropped
	 * depending on the zoom of the validation.
	 */
	var elbowContains = function(view, state, x, y)
	{
		var s = view.scale;
		var tr = view.translate;
		// Bends next to the bumps of clouds are kept (see getChannelBounds)
		var b = view.getChannelBounds(state);

		return mxUtils.contains(new mxRectangle(mxUtils.unscale(b.x, s, tr.x),
			mxUtils.unscale(b.y, s, tr.y), mxUtils.unscale(b.width, s),
			mxUtils.unscale(b.height, s)), mxUtils.unscale(x, s, tr.x),
			mxUtils.unscale(y, s, tr.y));
	};

	/**
	 * Copies of SideToSide and TopToBottom of mxEdgeStyle that test if a point
	 * is in a terminal in model units (elbowContains). ElbowConnector calls
	 * them through mxEdgeStyle, so it uses the copies.
	 */
	mxEdgeStyle.SideToSide = function(state, source, target, points, result)
	{
		var view = state.view;
		var pt = (points != null && points.length > 0) ? points[0] : null;
		var pts = state.absolutePoints;
		var p0 = pts[0];
		var pe = pts[pts.length-1];

		if (pt != null)
		{
			pt = view.transformControlPoint(state, pt);
		}

		if (p0 != null)
		{
			source = new mxCellState();
			source.x = p0.x;
			source.y = p0.y;
		}

		if (pe != null)
		{
			target = new mxCellState();
			target.x = pe.x;
			target.y = pe.y;
		}

		if (source != null && target != null)
		{
			var l = Math.max(source.x, target.x);
			var r = Math.min(source.x + source.width,
							 target.x + target.width);

			var x = (pt != null) ? pt.x : mxEdgeStyle.roundScaled(
				r + (l - r) / 2, view.scale, view.translate.x);

			var y1 = view.getRoutingCenterY(source);
			var y2 = view.getRoutingCenterY(target);

			if (pt != null)
			{
				// Waypoints level with the cloud bumps only (see getChannelBounds)
				var sc = view.getChannelBounds(source);
				var tc = view.getChannelBounds(target);

				if (pt.y >= sc.y && pt.y <= sc.y + sc.height)
				{
					y1 = pt.y;
				}

				if (pt.y >= tc.y && pt.y <= tc.y + tc.height)
				{
					y2 = pt.y;
				}
			}

			if (!elbowContains(view, target, x, y1) &&
				!elbowContains(view, source, x, y1))
			{
				result.push(new mxPoint(x,  y1));
			}

			if (!elbowContains(view, target, x, y2) &&
				!elbowContains(view, source, x, y2))
			{
				result.push(new mxPoint(x, y2));
			}

			if (result.length == 1)
			{
				if (pt != null)
				{
					if (!elbowContains(view, target, x, pt.y) &&
						!elbowContains(view, source, x, pt.y))
					{
						result.push(new mxPoint(x, pt.y));
					}
				}
				else
				{
					var t = Math.max(source.y, target.y);
					var b = Math.min(source.y + source.height,
							 target.y + target.height);

					result.push(new mxPoint(x, t + (b - t) / 2));
				}
			}
		}
	};

	mxEdgeStyle.TopToBottom = function(state, source, target, points, result)
	{
		var view = state.view;
		var pt = (points != null && points.length > 0) ? points[0] : null;
		var pts = state.absolutePoints;
		var p0 = pts[0];
		var pe = pts[pts.length-1];

		if (pt != null)
		{
			pt = view.transformControlPoint(state, pt);
		}

		if (p0 != null)
		{
			source = new mxCellState();
			source.x = p0.x;
			source.y = p0.y;
		}

		if (pe != null)
		{
			target = new mxCellState();
			target.x = pe.x;
			target.y = pe.y;
		}

		if (source != null && target != null)
		{
			var t = Math.max(source.y, target.y);
			var b = Math.min(source.y + source.height,
							 target.y + target.height);

			var x = view.getRoutingCenterX(source);
			// Waypoints level with the cloud bumps only (see getChannelBounds)
			var sc = view.getChannelBounds(source);
			var tc = view.getChannelBounds(target);

			if (pt != null &&
				pt.x >= sc.x &&
				pt.x <= sc.x + sc.width)
			{
				x = pt.x;
			}

			var y = (pt != null) ? pt.y : mxEdgeStyle.roundScaled(
				b + (t - b) / 2, view.scale, view.translate.y);

			if (!elbowContains(view, target, x, y) &&
				!elbowContains(view, source, x, y))
			{
				result.push(new mxPoint(x, y));
			}

			if (pt != null &&
				pt.x >= tc.x &&
				pt.x <= tc.x + tc.width)
			{
				x = pt.x;
			}
			else
			{
				x = view.getRoutingCenterX(target);
			}

			if (!elbowContains(view, target, x, y) &&
				!elbowContains(view, source, x, y))
			{
				result.push(new mxPoint(x, y));
			}

			if (result.length == 1)
			{
				if (pt != null && result.length == 1)
				{
					if (!elbowContains(view, target, pt.x, y) &&
						!elbowContains(view, source, pt.x, y))
					{
						result.push(new mxPoint(pt.x, y));
					}
				}
				else
				{
					var l = Math.max(source.x, target.x);
					var r = Math.min(source.x + source.width,
							 target.x + target.width);

					result.push(new mxPoint(l + (r - l) / 2, y));
				}
			}
		}
	};

	/**
	 * Copy of ElbowConnector of mxEdgeStyle that tests if the terminals touch
	 * (left == right and top == bottom) in model units.
	 */
	mxEdgeStyle.ElbowConnector = function(state, source, target, points, result)
	{
		var pt = (points != null && points.length > 0) ? points[0] : null;

		var vertical = false;
		var horizontal = false;

		if (source != null && target != null)
		{
			if (pt != null)
			{
				var left = Math.min(source.x, target.x);
				var right = Math.max(source.x + source.width,
					target.x + target.width);

				var top = Math.min(source.y, target.y);
				var bottom = Math.max(source.y + source.height,
					target.y + target.height);

				pt = state.view.transformControlPoint(state, pt);

				vertical = pt.y < top || pt.y > bottom;
				horizontal = pt.x < left || pt.x > right;
			}
			else
			{
				var left = Math.max(source.x, target.x);
				var right = Math.min(source.x + source.width,
					target.x + target.width);

				vertical = mxUtils.unscale(left, state.view.scale, state.view.translate.x) ==
					mxUtils.unscale(right, state.view.scale, state.view.translate.x);

				if (!vertical)
				{
					var top = Math.max(source.y, target.y);
					var bottom = Math.min(source.y + source.height,
						target.y + target.height);

					horizontal = mxUtils.unscale(top, state.view.scale, state.view.translate.y) ==
						mxUtils.unscale(bottom, state.view.scale, state.view.translate.y);
				}
			}
		}

		if (!horizontal && (vertical ||
			state.style[mxConstants.STYLE_ELBOW] == mxConstants.ELBOW_VERTICAL))
		{
			mxEdgeStyle.TopToBottom(state, source, target, points, result);
		}
		else
		{
			mxEdgeStyle.SideToSide(state, source, target, points, result);
		}
	};

	// Replaces the functions that were registered when mxClient was loaded
	mxStyleRegistry.putValue(mxConstants.EDGESTYLE_ELBOW, mxEdgeStyle.ElbowConnector);
	mxStyleRegistry.putValue(mxConstants.EDGESTYLE_SIDETOSIDE, mxEdgeStyle.SideToSide);
	mxStyleRegistry.putValue(mxConstants.EDGESTYLE_TOPTOBOTTOM, mxEdgeStyle.TopToBottom);

	/**
	 * Returns a state with the points of the painted curve instead of the
	 * control points of the given edge state if curveGeometry is 1 and the
	 * edge is painted as a curve, or null otherwise.
	 */
	mxGraphView.prototype.getLabelCurveState = function(state)
	{
		var result = null;

		if (state != null && state.style != null &&
			mxUtils.getValue(state.style, 'curveGeometry', '0') == '1')
		{
			var pts = Graph.getEdgeCurvePoints(state);

			if (pts != null)
			{
				var segments = [];
				var length = 0;

				for (var i = 1; i < pts.length; i++)
				{
					var dx = pts[i].x - pts[i - 1].x;
					var dy = pts[i].y - pts[i - 1].y;
					var seg = Math.sqrt(dx * dx + dy * dy);
					segments.push(seg);
					length += seg;
				}

				result = Object.create(state);
				result.absolutePoints = pts;
				result.segments = segments;
				result.length = length;
			}
		}

		return result;
	};

	/**
	 * Places edge labels along the painted curve if curveGeometry is 1.
	 */
	var mxGraphViewGetPoint = mxGraphView.prototype.getPoint;

	mxGraphView.prototype.getPoint = function(state, geometry)
	{
		var curve = this.getLabelCurveState(state);

		return mxGraphViewGetPoint.call(this, (curve != null) ? curve : state, geometry);
	};

	/**
	 * Returns the relative label position along the painted curve if
	 * curveGeometry is 1.
	 */
	var mxGraphViewGetRelativePoint = mxGraphView.prototype.getRelativePoint;

	mxGraphView.prototype.getRelativePoint = function(edgeState, x, y)
	{
		var curve = this.getLabelCurveState(edgeState);

		return mxGraphViewGetRelativePoint.call(this,
			(curve != null) ? curve : edgeState, x, y);
	};
		
	/**
	 * Adds support for placeholders in text elements of shapes.
	 */
	var mxStencilEvaluateTextAttribute = mxStencil.prototype.evaluateTextAttribute;
	
	mxStencil.prototype.evaluateTextAttribute = function(node, attribute, shape)
	{
		var result = mxStencilEvaluateTextAttribute.apply(this, arguments);
		var placeholders = node.getAttribute('placeholders');
		
		if (placeholders == '1' && shape.state != null)
		{
			var graph = shape.state.view.graph;

			// Changes of the data update these shapes from now on (see
			// invalidateDescendantsWithPlaceholders)
			graph.placeholdersUsed = true;
			result = graph.replacePlaceholders(shape.state.cell, result);
		}
		
		return result;
	};
		
	/**
	 * Adds custom stencils defined via shape=stencil(value) style. The value is a base64 encoded, compressed and
	 * URL encoded XML definition of the shape according to the stencil definition language of mxGraph.
	 * 
	 * Needs to be in this file to make sure its part of the embed client code. Also the check for ZLib is
	 * different than for the Editor code.
	 */
	var mxCellRendererCreateShape = mxCellRenderer.prototype.createShape;
	mxCellRenderer.prototype.createShape = function(state)
	{
		if (state.style != null && typeof(pako) !== 'undefined')
		{
	    	var shape = mxUtils.getValue(state.style, mxConstants.STYLE_SHAPE, null);
	
	    	// Extracts and decodes stencil XML if shape has the form shape=stencil(value)
	    	if (shape != null && typeof shape === 'string' && shape.substring(0, 8) == 'stencil(')
	    	{
	    		try
	    		{
	    			var stencil = shape.substring(8, shape.length - 1);
	    			var doc = mxUtils.parseXml(Graph.decompress(stencil));
	    			
	    			return new mxShape(new mxStencil(doc.documentElement));
	    		}
	    		catch (e)
	    		{
	    			if (window.console != null)
	    			{
	    				console.log('Error in shape: ' + e);
	    			}
	    		}
	    	}
		}
		
		return mxCellRendererCreateShape.apply(this, arguments);
	};

	/**
	 * True once the view has routed a connector with ties or laid out a tie,
	 * so that the invalidation of diagrams without ties is unchanged.
	 */
	mxGraphView.prototype.tiesSeen = false;

	/**
	 * Half length of the straight piece at a tie of connectors without an
	 * edge style in model units, so that their curves pass the tie along it.
	 */
	mxGraphView.prototype.tieLeadLength = 8;

	/**
	 * Maximum number of tie layouts that are updated inside each other. Deeper
	 * ties order their connectors without the ties upstream and without
	 * validating the terminals, so that crafted chains cannot exhaust the stack.
	 */
	mxGraphView.prototype.maxTieLayoutDepth = 30;

	/**
	 * Number of tie layouts that are currently updated inside each other.
	 */
	mxGraphView.prototype.tieLayoutDepth = 0;

	/**
	 * Returns the unit vector of the given vector or null for a zero vector.
	 */
	function normalizeTieVector(dx, dy)
	{
		var length = Math.sqrt(dx * dx + dy * dy);

		return (length > 1e-9) ? new mxPoint(dx / length, dy / length) : null;
	};

	/**
	 * Routes connectors with ties through their lanes.
	 */
	var mxGraphViewUpdatePointsTies = mxGraphView.prototype.updatePoints;
	mxGraphView.prototype.updatePoints = function(edge, points, source, target)
	{
		var lanes = (edge != null && edge.style != null && edge.style['ties'] != null) ?
			this.getTieLanes(edge) : null;

		if (lanes != null && lanes.length > 0)
		{
			this.updateTiedPoints(edge, lanes, source, target);
		}
		else
		{
			mxGraphViewUpdatePointsTies.apply(this, arguments);
		}
	};

	/**
	 * Returns the lanes of the given connector state in the order of its ties,
	 * each with the point and the direction of the connector in the lane.
	 */
	mxGraphView.prototype.getTieLanes = function(edge)
	{
		var ids = (this.graph.getTieIndex != null) ? this.getEdgeTieIds(edge.cell.id) : null;
		var lanes = [];

		for (var i = 0; ids != null && i < ids.length; i++)
		{
			var state = this.getTieState(ids[i]);

			if (state != null && state.invalid)
			{
				state = this.validateCellState(state.cell, false);
			}

			var layout = (state != null) ? this.getTieLayout(state) : null;

			if (layout != null && layout.lanes[edge.cell.id] != null)
			{
				lanes.push({point: layout.lanes[edge.cell.id],
					direction: layout.directions[edge.cell.id]});
			}
		}

		if (ids != null)
		{
			this.tiesSeen = true;
			this.tiesRoot = this.graph.model.getRoot();
		}

		return lanes;
	};

	/**
	 * Returns the IDs of the ties of the connector with the given ID from its
	 * source to its target, including the ties that are previewed while ties
	 * are cloned (see setTieClones).
	 */
	mxGraphView.prototype.getEdgeTieIds = function(id)
	{
		var index = this.graph.getTieIndex();
		var ids = index.edges[id];

		if (ids != null && this.tieClones != null)
		{
			ids = this.getTieCloneEdge(index, id).ids;
		}

		return ids;
	};

	/**
	 * Returns the position of the tie with the given ID in the given ties of
	 * the connector with the given ID (see getEdgeTieIds), or -1.
	 */
	mxGraphView.prototype.getEdgeTiePosition = function(edgeId, ids, tieId)
	{
		var index = this.graph.getTieIndex();
		var positions = index.positions[edgeId];

		if (this.tieClones != null && index.edges[edgeId] != null)
		{
			positions = this.getTieCloneEdge(index, edgeId).positions;
		}

		return (positions != null) ? ((positions[tieId] != null) ?
			positions[tieId] : -1) : mxUtils.indexOf(ids, tieId);
	};

	/**
	 * Returns the ties of the connector with the given ID in the given tie
	 * index with the previewed clones (see setTieClones) and the position of
	 * each. They are created once per preview, as each tie looks them up for
	 * each of its connectors.
	 */
	mxGraphView.prototype.getTieCloneEdge = function(index, id)
	{
		if (this.tieCloneEdges == null || this.tieCloneEdges.index != index)
		{
			this.tieCloneEdges = {index: index, edges: Object.create(null)};
		}

		var result = this.tieCloneEdges.edges[id];

		if (result == null)
		{
			var ids = index.edges[id];
			result = {ids: [], positions: Object.create(null)};

			var add = function(tieId)
			{
				if (result.positions[tieId] == null)
				{
					result.positions[tieId] = result.ids.length;
				}

				result.ids.push(tieId);
			};

			for (var i = 0; i < ids.length; i++)
			{
				var clone = this.tieClones[ids[i]];

				if (clone != null && clone.before[id])
				{
					add(clone.state.cell.id);
				}

				add(ids[i]);

				if (clone != null && !clone.before[id])
				{
					add(clone.state.cell.id);
				}
			}

			this.tieCloneEdges.edges[id] = result;
		}

		return result;
	};

	/**
	 * Returns the state of the tie with the given ID, or of the tie that is
	 * previewed for a clone, or null if the tie does not exist or is hidden.
	 */
	mxGraphView.prototype.getTieState = function(id)
	{
		var state = (this.tieCloneStates != null) ? this.tieCloneStates[id] : null;

		if (state == null)
		{
			// Checks the style of the state, as the style of a cell that is not
			// drawn would be parsed for each section of each connector that lists
			// it (1,000 connectors with a hidden tie with a 500 KB style: 7 s)
			var cell = this.graph.model.getCell(id);
			state = (cell != null) ? this.getState(cell) : null;

			if (state != null && !this.graph.isTie(cell))
			{
				state = null;
			}
		}

		return state;
	};

	/**
	 * Previews the given ties as cloned and moved by dx and dy in view units:
	 * each connector of a tie passes the preview after the tie if the move
	 * goes along the connector and before it otherwise, as in
	 * Graph.addClonedTies. A null argument removes the preview.
	 */
	mxGraphView.prototype.setTieClones = function(ties, dx, dy)
	{
		var previous = this.tieClones;
		var invalid = Object.create(null);

		this.tieClones = null;
		this.tieCloneStates = null;
		this.tieCloneEdges = null;

		for (var i = 0; ties != null && i < ties.length; i++)
		{
			var source = this.getState(ties[i]);
			var layout = (source != null) ? this.getTieLayout(source) : null;

			if (layout != null)
			{
				var clone = (previous != null) ? previous[ties[i].id] : null;
				var state = (clone != null) ? clone.state : null;

				if (state == null)
				{
					var cell = ties[i].clone();
					cell.id = ties[i].id + '-clone';
					state = new mxCellState(this, cell, source.style);
					state.invalid = false;
					state.tieSource = ties[i].id;
				}
				else
				{
					delete previous[ties[i].id];
				}

				state.setRect(layout.center.x + dx - source.width / 2,
					layout.center.y + dy - source.height / 2, source.width, source.height);
				state.tieLayout = null;

				var before = Object.create(null);
				var members = this.graph.getTieMembers(ties[i].id);

				for (var j = 0; j < members.length; j++)
				{
					var dir = layout.directions[members[j].id];
					before[members[j].id] = dir != null && dx * dir.x + dy * dir.y < 0;
				}

				this.tieClones = this.tieClones || Object.create(null);
				this.tieCloneStates = this.tieCloneStates || Object.create(null);
				this.tieClones[ties[i].id] = {state: state, before: before};
				this.tieCloneStates[state.cell.id] = state;
				invalid[ties[i].id] = true;

				// The layouts of the next ties look up the ties with this clone
				this.tieCloneEdges = null;
			}
		}

		for (var id in previous)
		{
			previous[id].state.destroy();
			invalid[id] = true;
		}

		// Keeps the layouts of the ties of the connectors, as the preview of the
		// clone moves the state of the tie that is cloned
		this.ignoreTieChanges = true;

		try
		{
			var seen = Object.create(null);

			for (var id in invalid)
			{
				var members = this.graph.getTieMembers(id);

				for (var j = 0; j < members.length; j++)
				{
					if (!seen[members[j].id])
					{
						seen[members[j].id] = true;
						this.invalidate(members[j], false, true);
					}
				}
			}
		}
		finally
		{
			this.ignoreTieChanges = false;
		}
	};

	/**
	 * Routes each section of the given connector state between its terminals
	 * and its lanes with its edge style. The lanes are terminals with a port
	 * constraint along the tie, so that the connector passes each tie in its
	 * direction. Connectors without an edge style get a straight piece along
	 * the tie.
	 */
	mxGraphView.prototype.updateTiedPoints = function(edge, lanes, source, target)
	{
		var abs = edge.absolutePoints;
		var pe = abs[abs.length - 1];
		var p0 = abs[0];
		var pts = [p0];
		var edgeStyle = this.getEdgeStyle(edge, null, source, target);
		var start = this.getTerminalPort(edge, source, true);
		var end = this.getTerminalPort(edge, target, false);
		var lead = this.tieLeadLength * this.scale;
		var orthogonal = this.graph.isOrthogonal(edge);

		try
		{
			for (var i = 0; i <= lanes.length; i++)
			{
				var lane = lanes[i];
				var corner = (orthogonal && i > 0 && lane != null) ?
					this.getTieCorner(lanes[i - 1], lane) : null;

				if (corner != null)
				{
					pts.push(corner);
				}
				else if (edgeStyle != null)
				{
					edge.absolutePoints = [p0, (lane != null) ? lane.point : pe];
					this.routeTiedSection(edgeStyle, edge, start,
						(lane != null) ? this.createTieTerminal(lane, false) : end, pts);
				}

				if (lane != null)
				{
					if (edgeStyle != null)
					{
						pts.push(lane.point.clone());
					}
					else
					{
						pts.push(new mxPoint(lane.point.x - lane.direction.x * lead,
							lane.point.y - lane.direction.y * lead));
						pts.push(new mxPoint(lane.point.x + lane.direction.x * lead,
							lane.point.y + lane.direction.y * lead));
					}

					start = this.createTieTerminal(lane, true);
					p0 = lane.point;
				}
			}
		}
		finally
		{
			edge.absolutePoints = abs;
		}

		pts.push(pe);
		edge.absolutePoints = pts;
	};

	/**
	 * Returns the corner between two lanes of an orthogonal connector whose
	 * directions are perpendicular if the connector can turn there without
	 * going back, or null. The corners of the lanes of a cable that keep their
	 * side are nested, so the cable turns without crossings.
	 */
	mxGraphView.prototype.getTieCorner = function(from, to)
	{
		var a = from.point;
		var b = to.point;
		var result = null;

		if (Math.abs(from.direction.x * to.direction.x + from.direction.y * to.direction.y) < 1e-6)
		{
			var corner = (Math.abs(from.direction.x) > Math.abs(from.direction.y)) ?
				new mxPoint(b.x, a.y) : new mxPoint(a.x, b.y);

			if ((corner.x - a.x) * from.direction.x + (corner.y - a.y) * from.direction.y > 0 &&
				(b.x - corner.x) * to.direction.x + (b.y - corner.y) * to.direction.y > 0)
			{
				result = corner;
			}
		}

		return result;
	};

	/**
	 * Routes one section of a connector with ties with the given edge style
	 * and adds its points to the given result.
	 */
	mxGraphView.prototype.routeTiedSection = function(edgeStyle, edge, source, target, result)
	{
		var sourceBounds = this.updateBoundsFromStencil(source);
		var targetBounds = (source != target) ? this.updateBoundsFromStencil(target) : null;

		try
		{
			edgeStyle(edge, source, target, null, result);
		}
		finally
		{
			if (sourceBounds != null)
			{
				source.setRect(sourceBounds.x, sourceBounds.y, sourceBounds.width, sourceBounds.height);
			}

			if (targetBounds != null)
			{
				target.setRect(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height);
			}
		}
	};

	/**
	 * Returns a small terminal state beside the given lane whose side at the
	 * lane faces the direction of the connector when it leaves the lane, or
	 * the opposite direction when it enters the lane.
	 */
	mxGraphView.prototype.createTieTerminal = function(lane, exit)
	{
		var d = lane.direction;
		var horizontal = Math.abs(d.x) >= Math.abs(d.y);
		var dx = (horizontal) ? ((d.x < 0) ? -1 : 1) : 0;
		var dy = (horizontal) ? 0 : ((d.y < 0) ? -1 : 1);
		var px = (exit) ? dx : -dx;
		var py = (exit) ? dy : -dy;

		// Edge styles find the side of a fixed point within 1 unit, so the lane
		// must be farther than that from the other sides of the box
		var size = 10 * this.scale;
		var state = new mxCellState(this, null, {portConstraint: (px > 0) ? 'east' :
			((px < 0) ? 'west' : ((py > 0) ? 'south' : 'north'))});

		// The box is behind the lane for the exit and ahead of it for the entry
		state.x = lane.point.x - px * size / 2 - size / 2;
		state.y = lane.point.y - py * size / 2 - size / 2;
		state.width = size;
		state.height = size;

		return state;
	};

	/**
	 * Returns the layout of the given tie state and updates it if the tie or
	 * its connectors have changed.
	 */
	mxGraphView.prototype.getTieLayout = function(state)
	{
		if (!this.isTieLayoutCurrent(state.tieLayout) && !state.tieUpdating)
		{
			// Keeps the layout from before a zoom or pan, so that a change of
			// the lanes is passed on when the tie is invalidated later in the
			// validation (see invalidateTie and updateChangedTies)
			if (state.tieLayout != null && state.previousTieLayout == null)
			{
				state.previousTieLayout = state.tieLayout;
			}

			this.updateTieLayout(state);

			// Repaints a tie that is laid out outside of its validation, eg. in
			// the preview of a move, and paints the preview of a clone
			if (state.shape != null || state.tieSource != null)
			{
				this.graph.cellRenderer.redraw(state, true);
				state.updateCachedBounds();
			}
		}

		return state.tieLayout;
	};

	/**
	 * Returns true if the given tie layout was computed for the current scale
	 * and translate of the view. Model coordinates (fastRendering) change the
	 * camera without validating the cells, so a layout in view coordinates is
	 * updated when it is used after a zoom or pan.
	 */
	mxGraphView.prototype.isTieLayoutCurrent = function(layout)
	{
		return layout != null && layout.scale == this.scale &&
			layout.translateX == this.translate.x &&
			layout.translateY == this.translate.y;
	};

	/**
	 * Returns the center of the tie with the given ID in view coordinates, or
	 * null if the tie does not exist or is not visible. The center of a laid
	 * out tie is the center of its layout, as the preview of a clone moves the
	 * state of the tie that is cloned.
	 */
	mxGraphView.prototype.getTieCenter = function(id)
	{
		var state = this.getTieState(id);
		var result = null;

		if (state != null && this.isTieLayoutCurrent(state.tieLayout))
		{
			result = state.tieLayout.center.clone();
		}
		else if (state != null && state.invalid)
		{
			var cell = state.cell;
			var geo = this.graph.getCellGeometry(cell);
			var parent = this.getState(this.graph.model.getParent(cell));

			if (geo != null)
			{
				result = new mxPoint((((parent != null) ? parent.origin.x : 0) + geo.getCenterX() +
					this.translate.x) * this.scale, (((parent != null) ? parent.origin.y : 0) +
					geo.getCenterY() + this.translate.y) * this.scale);
			}
		}
		else if (state != null)
		{
			result = new mxPoint(state.getCenterX(), state.getCenterY());
		}

		return result;
	};

	/**
	 * Returns the point of the given end of the given connector state that
	 * orders its lane: the connection point or the center of its terminal.
	 */
	mxGraphView.prototype.getTieEnd = function(state, source)
	{
		var cell = this.getVisibleTerminal(state.cell, source);
		var terminal = (this.tieLayoutDepth < this.maxTieLayoutDepth) ?
			this.validateCellState(cell, false) : this.getState(cell);
		var result = null;

		if (terminal != null)
		{
			var constraint = this.graph.getConnectionConstraint(state, terminal, source);

			if (constraint != null && constraint.point != null)
			{
				result = this.graph.getConnectionPoint(terminal, constraint);
			}

			if (result == null)
			{
				result = new mxPoint(terminal.getCenterX(), terminal.getCenterY());
			}
		}
		else
		{
			var geo = this.graph.getCellGeometry(state.cell);
			var pt = (geo != null) ? geo.getTerminalPoint(source) : null;

			if (pt != null)
			{
				result = this.transformControlPoint(state, pt);
			}
		}

		return result;
	};

	/**
	 * Returns the layout of the tie with the given ID upstream of a tie whose
	 * layout is being updated, or null if it is not available.
	 */
	mxGraphView.prototype.getUpstreamTieLayout = function(id)
	{
		var state = this.getTieState(id);
		var result = null;

		if (state != null && !state.tieUpdating &&
			this.tieLayoutDepth < this.maxTieLayoutDepth)
		{
			if (state.invalid)
			{
				this.validateCellState(state.cell, false);
			}

			result = this.getTieLayout(state);
		}

		return result;
	};

	/**
	 * Lays out the lanes of the given tie state and sets its bounds to its
	 * clamp. The tie points in the direction most connectors travel, or along
	 * an axis if all connectors are orthogonal. A connector that comes from a
	 * tie upstream keeps its side relative to its direction from that tie, so
	 * that the lanes do not cross between ties. Other connectors are ordered
	 * by where they come from and go to, then by ID.
	 */
	mxGraphView.prototype.updateTieLayout = function(state)
	{
		var graph = this.graph;
		var members = graph.getTieMembers((state.tieSource != null) ? state.tieSource : state.cell.id);
		var c = new mxPoint(state.getCenterX(), state.getCenterY());
		var spacing = parseFloat(mxUtils.getValue(state.style, 'tieSpacing', graph.defaultTieSpacing));
		spacing = ((isFinite(spacing)) ? Math.max(0, Math.min(100, spacing)) :
			graph.defaultTieSpacing) * this.scale;
		var orthogonal = true;
		var sum = new mxPoint(0, 0);
		var items = [];

		state.tieUpdating = true;
		this.tieLayoutDepth++;

		try
		{
			for (var i = 0; i < members.length; i++)
			{
				var es = this.getState(members[i]);
				var ids = this.getEdgeTieIds(members[i].id);
				var j = (es != null && ids != null) ? this.getEdgeTiePosition(
					members[i].id, ids, state.cell.id) : -1;

				if (j >= 0)
				{
					var item = {id: members[i].id, source: this.getTieEnd(es, true),
						target: this.getTieEnd(es, false), prev: (j > 0) ? ids[j - 1] : null,
						next: (j < ids.length - 1) ? ids[j + 1] : null};
					var prev = (item.prev != null) ? this.getTieCenter(item.prev) : null;
					var next = (item.next != null) ? this.getTieCenter(item.next) : null;
					var from = (prev != null) ? prev : ((next != null) ? c : item.source);
					var to = (next != null) ? next : ((prev != null) ? c : item.target);

					item.vector = (from != null && to != null) ?
						normalizeTieVector(to.x - from.x, to.y - from.y) : null;
					orthogonal = orthogonal && graph.isOrthogonal(es);

					if (item.vector != null)
					{
						sum.x += item.vector.x;
						sum.y += item.vector.y;
					}

					items.push(item);
				}
			}

			var d = normalizeTieVector(sum.x, sum.y) || ((items.length > 0 &&
				items[0].vector != null) ? items[0].vector : new mxPoint(1, 0));

			if (orthogonal && items.length > 0)
			{
				d = (Math.abs(d.x) >= Math.abs(d.y)) ? new mxPoint((d.x < 0) ? -1 : 1, 0) :
					new mxPoint(0, (d.y < 0) ? -1 : 1);
			}

			var n = new mxPoint(-d.y, d.x);

			for (var i = 0; i < items.length; i++)
			{
				var item = items[i];
				item.sign = (item.vector == null || item.vector.x * d.x +
					item.vector.y * d.y >= 0) ? 1 : -1;
				var up = (item.sign > 0) ? item.prev : item.next;
				var upLayout = (up != null) ? this.getUpstreamTieLayout(up) : null;
				var lateral = (upLayout != null) ? upLayout.lateral[item.id] : null;

				if (lateral != null)
				{
					item.key = (upLayout.center.x - c.x) * n.x + (upLayout.center.y - c.y) * n.y;
					item.rank = lateral * item.sign;
				}
				else
				{
					var from = (item.sign > 0) ? item.source : item.target;
					var to = (item.sign > 0) ? item.target : item.source;
					item.key = (from != null) ? (from.x - c.x) * n.x + (from.y - c.y) * n.y : 0;
					item.rank = (to != null) ? (to.x - c.x) * n.x + (to.y - c.y) * n.y : 0;
				}
			}

			var tol = 0.5 * this.scale;

			items.sort(function(a, b)
			{
				return (Math.abs(a.key - b.key) > tol) ? a.key - b.key : ((a.rank != b.rank) ?
					a.rank - b.rank : ((a.id < b.id) ? -1 : ((a.id > b.id) ? 1 : 0)));
			});

			var lanes = Object.create(null);
			var directions = Object.create(null);
			var lateral = Object.create(null);

			for (var i = 0; i < items.length; i++)
			{
				var offset = i - (items.length - 1) / 2;
				lanes[items[i].id] = new mxPoint(c.x + n.x * offset * spacing,
					c.y + n.y * offset * spacing);
				directions[items[i].id] = new mxPoint(d.x * items[i].sign, d.y * items[i].sign);
				lateral[items[i].id] = offset * items[i].sign;
			}

			// The bounds hold the line across the lanes, its head at either end
			// and a margin for selecting thin lines (see TieShape)
			var sw = parseFloat(mxUtils.getValue(state.style, mxConstants.STYLE_STROKEWIDTH, 1));
			sw = (isFinite(sw)) ? Math.max(0, Math.min(100, sw)) : 1;
			var length = Math.max(0, items.length - 1) * spacing + 2 * Math.max(3, sw) * this.scale;
			var extent = length + (3 * sw + 12) * this.scale;
			var thickness = Math.max(12, 2 * sw + 4) * this.scale;
			var w = Math.abs(d.x) * thickness + Math.abs(n.x) * extent;
			var h = Math.abs(d.y) * thickness + Math.abs(n.y) * extent;
			state.setRect(c.x - w / 2, c.y - h / 2, w, h);
			state.unscaledWidth = w / this.scale;
			state.unscaledHeight = h / this.scale;

			state.tieLayout = {center: c, direction: d, normal: n, lanes: lanes,
				directions: directions, lateral: lateral, count: items.length,
				length: length / this.scale, scale: this.scale,
				translateX: this.translate.x, translateY: this.translate.y};
		}
		finally
		{
			state.tieUpdating = false;
			this.tieLayoutDepth--;
		}
	};

	/**
	 * Lays out ties when they are validated.
	 */
	var mxGraphViewUpdateCellStateTies = mxGraphView.prototype.updateCellState;
	mxGraphView.prototype.updateCellState = function(state)
	{
		mxGraphViewUpdateCellStateTies.apply(this, arguments);

		if (state.style != null && state.style['tie'] == '1' &&
			this.graph.getTieIndex != null && this.graph.model.isVertex(state.cell))
		{
			this.tiesSeen = true;
			this.tiesRoot = this.graph.model.getRoot();
			this.updateTieLayout(state);
		}
	};

	/**
	 * Collects the ties of invalidated ties and connectors, so that the view
	 * updates the connectors of these ties before it is validated.
	 */
	var mxGraphViewAddInvalidCellTies = mxGraphView.prototype.addInvalidCell;
	mxGraphView.prototype.addInvalidCell = function(cell)
	{
		mxGraphViewAddInvalidCellTies.apply(this, arguments);

		if (this.tiesSeen && !this.ignoreTieChanges && cell != null &&
			this.graph.isTieStyle(cell.style))
		{
			this.graph.addChangedTieStyle(cell, cell.style);
		}
	};

	/**
	 * Maximum number of passes for passing changed tie layouts on to the
	 * neighbouring ties (see updateChangedTies). Each tie is laid out once per
	 * validation, so a pass only lays out ties along a change.
	 */
	mxGraphView.prototype.maxTiePasses = 1000;

	/**
	 * Resets the ties of the view, eg. for another page.
	 */
	mxGraphView.prototype.resetTies = function()
	{
		this.tiesSeen = false;
		this.changedTies = null;
		this.changedTieCells = null;
	};

	/**
	 * Invalidates the connectors of the changed ties before the view is
	 * validated (see Graph.addChangedTies and addInvalidCell), lays out the
	 * neighbours of ties whose layout has changed and validates again for
	 * their connectors.
	 */
	var mxGraphViewValidateTies = mxGraphView.prototype.validate;
	mxGraphView.prototype.validate = function(cell)
	{
		// Pages are switched without a change of the model (see SelectPage)
		if (this.tiesSeen && this.tiesRoot != this.graph.model.getRoot())
		{
			this.resetTies();
		}

		if (this.changedTies == null && this.changedTieCells == null)
		{
			mxGraphViewValidateTies.apply(this, arguments);
		}
		else
		{
			var stale = this.invalidateChangedTies();
			var done = Object.create(null);
			var passed = false;
			mxGraphViewValidateTies.apply(this, arguments);

			// Passes the changes on to the neighbouring ties on their layouts
			// alone and routes the connectors of all changed ties in one
			// validation, as a layout depends on the ends of the connectors, the
			// centers of the ties and the layouts upstream, not on the routes. A
			// validation per pass routed each connector once for each tie that
			// changed along it: M x P^2 sections for M connectors through P ties
			// (1,000 ties: 35 s after an image trace on open)
			for (var i = 0; i < this.maxTiePasses && stale.length > 0; i++)
			{
				stale = this.updateChangedTies(stale, done);
				passed = passed || stale.length > 0;
			}

			if (passed)
			{
				mxGraphViewValidateTies.apply(this, arguments);
			}
		}
	};

	/**
	 * Adds the ties of the added and removed cells, which are walked once per
	 * batch, invalidates the connectors of all changed ties and returns the
	 * states of these ties.
	 */
	mxGraphView.prototype.invalidateChangedTies = function()
	{
		var graph = this.graph;
		var model = graph.model;
		var cells = this.changedTieCells;
		this.changedTieCells = null;

		if (cells != null)
		{
			var visited = new mxDictionary();
			var found = false;

			while (cells.length > 0)
			{
				var cell = cells.pop();

				if (cell != null && !visited.get(cell))
				{
					var style = model.getStyle(cell);
					visited.put(cell, true);

					if (graph.isTieStyle(style))
					{
						graph.addChangedTieStyle(cell, style);
						found = true;
					}

					for (var i = model.getChildCount(cell) - 1; i >= 0; i--)
					{
						cells.push(model.getChildAt(cell, i));
					}
				}
			}

			if (found)
			{
				graph.tieIndex = null;
			}
		}

		var ids = this.changedTies;
		var stale = [];
		this.changedTies = null;

		for (var id in ids)
		{
			var state = this.invalidateTie(id);

			if (state != null)
			{
				stale.push(state);
			}
		}

		return stale;
	};

	/**
	 * Lays out the given ties that no connector has laid out, eg. ties without
	 * connectors, and returns the states of the neighbouring ties, whose
	 * connectors are invalidated, of the ties whose position, direction or
	 * order of lanes has changed. Ties in done were laid out after their
	 * neighbours had changed in this validation and are not laid out again.
	 */
	mxGraphView.prototype.updateChangedTies = function(states, done)
	{
		var neighbours = Object.create(null);
		var result = [];

		for (var i = 0; i < states.length; i++)
		{
			done[states[i].cell.id] = true;
		}

		for (var i = 0; i < states.length; i++)
		{
			var state = states[i];

			if (this.getState(state.cell) == state)
			{
				var previous = state.previousTieLayout;
				var layout = this.getTieLayout(state);
				state.previousTieLayout = null;

				if (layout != null && this.isTieLayoutChanged(previous, layout))
				{
					var members = this.graph.getTieMembers(state.cell.id);

					for (var j = 0; j < members.length; j++)
					{
						var ids = this.getEdgeTieIds(members[j].id);
						var k = this.getEdgeTiePosition(members[j].id, ids, state.cell.id);

						if (k > 0)
						{
							neighbours[ids[k - 1]] = true;
						}

						if (k >= 0 && k < ids.length - 1)
						{
							neighbours[ids[k + 1]] = true;
						}
					}
				}
			}
		}

		for (var id in neighbours)
		{
			var state = (done[id]) ? null : this.invalidateTie(id);

			if (state != null)
			{
				result.push(state);
			}
		}

		return result;
	};

	/**
	 * Returns true if the given layout of a tie has another position in model
	 * units, direction or order of lanes than the given previous layout.
	 */
	mxGraphView.prototype.isTieLayoutChanged = function(previous, layout)
	{
		var result = previous == null || previous.count != layout.count ||
			Math.abs(previous.direction.x - layout.direction.x) > 1e-6 ||
			Math.abs(previous.direction.y - layout.direction.y) > 1e-6 ||
			Math.abs(previous.center.x / previous.scale - previous.translateX -
				(layout.center.x / layout.scale - layout.translateX)) > 1e-3 ||
			Math.abs(previous.center.y / previous.scale - previous.translateY -
				(layout.center.y / layout.scale - layout.translateY)) > 1e-3;

		for (var id in layout.lateral)
		{
			result = result || previous.lateral[id] !== layout.lateral[id];
		}

		return result;
	};

	/**
	 * Invalidates the connectors of the tie with the given ID and returns the
	 * state of the tie if its layout was up to date. A tie whose layout is not
	 * up to date has already invalidated its connectors. The state of the tie
	 * stays valid and its layout is updated from its current bounds, so that
	 * the preview of a move keeps the position of the moved tie. The other ties
	 * of the connectors are updated only if this layout changes (see
	 * updateChangedTies).
	 */
	mxGraphView.prototype.invalidateTie = function(id)
	{
		var cell = this.graph.model.getCell(id);
		var state = this.getState(cell);
		var result = null;

		if (state == null || state.tieLayout != null)
		{
			var members = this.graph.getTieMembers(id);

			if (state != null)
			{
				// A layout that was updated lazily keeps the previous layout
				if (state.previousTieLayout == null)
				{
					state.previousTieLayout = state.tieLayout;
				}

				state.tieLayout = null;
				result = state;
			}

			this.ignoreTieChanges = true;

			try
			{
				// Invalid connectors have invalidated the edges connected to them
				for (var i = 0; i < members.length; i++)
				{
					var member = this.getState(members[i]);

					if (member != null && !member.invalid)
					{
						this.invalidate(members[i], false, true);
					}
				}
			}
			finally
			{
				this.ignoreTieChanges = false;
			}
		}

		return result;
	};
})();
