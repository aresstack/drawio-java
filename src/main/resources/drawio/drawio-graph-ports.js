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
