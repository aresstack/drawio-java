/*
 * Offline subset of the original draw.io js/stencils.min.js.
 * Upstream commit: c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3
 * Included stencil sets: arrows.xml, basic.xml, bpmn.xml, flowchart.xml
 * Preamble, entries and decoder are copied verbatim by js-bundle/build.mjs.
 * Apache-2.0 plus upstream stencil restrictions; see docs/STENCILS.md.
 */
(function() {
var f = Object.create(null);
f['arrows.xml'] = '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';
f['basic.xml'] = '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';
f['bpmn.xml'] = '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';
f['flowchart.xml'] = '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';

var l = mxStencilRegistry.loadStencil;
var draw = Object.setPrototypeOf({move: 1, line: 1, quad: 1, curve: 1,
	arc: 1, rect: 1, roundrect: 1, ellipse: 1, text: 1, image: 1,
	'include-shape': 1}, null);
var xs = Object.setPrototypeOf({x: 1, x1: 1, x2: 1, x3: 1}, null);
var ys = Object.setPrototypeOf({y: 1, y1: 1, y2: 1, y3: 1}, null);
var td = null;

// Attributes do not inherit from Object.prototype, so a polluted
// prototype cannot add attributes. Object.create(null) per element
// would make them dictionaries, which takes 2.5x the memory.
function Attributes() {};
Attributes.prototype = Object.freeze(Object.create(null));
var none = Object.freeze(new Attributes());

// Light element for mxStencil, see Xml2Js.java
function StencilNode(name, parent)
{
	this.nodeName = name;
	this.parentNode = parent;
	this.firstChild = null;
	this.nextSibling = null;
	this.attrs = none;
};

StencilNode.prototype.nodeType = 1;

StencilNode.prototype.getAttribute = function(name)
{
	var value = this.attrs[name];

	return (typeof value === 'string') ? value : null;
};

StencilNode.prototype.getElementsByTagName = function(name)
{
	var result = [];

	function collect(node)
	{
		for (var child = node.firstChild; child != null; child = child.nextSibling)
		{
			if (child.nodeName == name)
			{
				result.push(child);
			}

			collect(child);
		}
	};

	collect(this);

	return result;
};

Object.defineProperty(StencilNode.prototype, 'attributes', {get: function()
{
	var names = Object.keys(this.attrs);
	var result = [];

	for (var i = 0; i < names.length; i++)
	{
		result.push({name: names[i], nodeName: names[i],
			value: this.attrs[names[i]]});
	}

	return result;
}});

function decode(t)
{
	// Plain loop: Uint8Array.from with a mapping callback is about 18x slower
	var b = atob(t);
	var a = new Uint8Array(b.length);

	for (var i = 0; i < b.length; i++)
	{
		a[i] = b.charCodeAt(i);
	}

	var d = pako.inflateRaw(a);
	var v = new DataView(d.buffer, d.byteOffset, d.byteLength);
	var s = [];

	for (var i = 0, o = 20; i < 5; i++)
	{
		var n = v.getUint32(4 * i);
		s.push(d.subarray(o, o + n));
		o += n;
	}

	td = (td != null) ? td : new TextDecoder();
	var ops = s[0], strs = td.decode(s[4]).split('\0');
	var p = [0, 0, 0, 0], si = 0, names = [], attrNames = [];
	var root = new StencilNode(null, null), parent = root, prev = null;
	var px = 0, py = 0, pn = new Map(), cache = new Map();

	// Reads the next zigzag varint from number stream k
	function read(k)
	{
		var bytes = s[k], n = 0, m = 1, c;

		do
		{
			c = bytes[p[k]++];
			n += (c & 127) * m;
			m *= 128;
		}
		while (c & 128);

		return (n % 2 == 1) ? -(n + 1) / 2 : n / 2;
	};

	// Shares the strings of repeated values (86% in aws4.xml)
	function format(n)
	{
		var value = cache.get(n);

		if (value === undefined)
		{
			value = String(n / 1000);
			cache.set(n, value);
		}

		return value;
	};

	while (p[0] < ops.length)
	{
		var op = ops[p[0]++];

		if (op == 255)
		{
			prev = parent;
			parent = parent.parentNode;
		}
		else
		{
			if ((op >> 1) == names.length)
			{
				names.push(strs[si++]);
			}

			var name = names[op >> 1];
			var node = new StencilNode(name, parent);
			var count = ops[p[0]++];
			var coords = draw[name] === 1;

			if (prev == null)
			{
				parent.firstChild = node;
			}
			else
			{
				prev.nextSibling = node;
			}

			if (name == 'shape')
			{
				px = 0;
				py = 0;
				pn = new Map();
			}

			if (count > 0)
			{
				node.attrs = new Attributes();
			}

			for (var i = 0; i < count; i++)
			{
				var id = ops[p[0]++];

				if (id == attrNames.length)
				{
					attrNames.push(strs[si++]);
				}

				var attr = attrNames[id];

				if (ops[p[0]++] == 0)
				{
					node.attrs[attr] = strs[si++];
				}
				else if (coords && xs[attr] === 1)
				{
					px += read(1);
					node.attrs[attr] = format(px);
				}
				else if (coords && ys[attr] === 1)
				{
					py += read(2);
					node.attrs[attr] = format(py);
				}
				else
				{
					var key = name + ' ' + attr;
					var next = (pn.get(key) || 0) + read(3);
					pn.set(key, next);
					node.attrs[attr] = format(next);
				}
			}

			if (op & 1)
			{
				prev = node;
			}
			else
			{
				parent = node;
				prev = null;
			}
		}
	}

	return {documentElement: root.firstChild};
};

mxStencilRegistry.loadStencil = function(filename, fn)
{
	var t = f[filename.substring(STENCIL_PATH.length + 1)];

	if (typeof t !== 'string')
	{
		return l.apply(this, arguments);
	}
	else if (fn != null)
	{
		window.setTimeout(function()
		{
			fn(decode(t));
		}, 0);
	}
	else
	{
		return decode(t);
	}
};
})();
