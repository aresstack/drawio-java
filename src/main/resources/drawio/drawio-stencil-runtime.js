/*
 * Headless, offline stencil runtime for the original draw.io stencil decoder.
 *
 * Load order:
 *   1. core bundle with original mxUtils, mxConstants, mxShape, mxStencil and
 *      mxStencilRegistry (drawio-headless-env.js + drawio-graph.js),
 *   2. this file,
 *   3. drawio-stencils.js (verbatim subset of upstream js/stencils.min.js).
 *
 * This file must run before drawio-stencils.js because the upstream decoder
 * captures the current mxStencilRegistry.loadStencil as its fallback for sets
 * that are not bundled, and resolves atob, pako, TextDecoder and STENCIL_PATH
 * as globals.
 *
 * Provided here:
 *   - atob, pako.inflateRaw and TextDecoder (UTF-8 only), each only when not
 *     already defined, backed by the budgeted Java DrawioBinaryCodec bridge,
 *   - a fallback loadStencil that never touches the network and reports the
 *     missing stencil set,
 *   - a port of draw.io's dynamic stencil loading (Graph.js) that only reads
 *     the bundled table, never evaluates JavaScript and reports unknown
 *     mxgraph.* stencils instead of letting the renderer paint a rectangle,
 *   - the DrawioStencils diagnostics object.
 */
(function (scope) {
    'use strict';

    if (typeof mxStencilRegistry === 'undefined' || mxStencilRegistry == null) {
        throw new Error('drawio-stencil-runtime.js requires mxStencilRegistry');
    }

    var CHUNK = 4096;
    var codec = null;

    function binaryCodec() {
        if (codec == null) {
            codec = drawioHost.binaryCodec();
        }
        return codec;
    }

    function bytesToBinaryString(bytes) {
        var parts = [];
        for (var i = 0; i < bytes.length; i += CHUNK) {
            parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK)));
        }
        return parts.join('');
    }

    function binaryStringToBytes(text) {
        var bytes = new Uint8Array(text.length);
        for (var i = 0; i < text.length; i++) {
            bytes[i] = text.charCodeAt(i);
        }
        return bytes;
    }

    function toBytes(input) {
        if (input == null) {
            return new Uint8Array(0);
        }
        if (input instanceof Uint8Array) {
            return input;
        }
        if (ArrayBuffer.isView(input)) {
            return new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
        }
        if (input instanceof ArrayBuffer) {
            return new Uint8Array(input);
        }
        throw new TypeError('Expected binary data');
    }

    // ---------------------------------------------------------------------
    // Browser capabilities, backed by explicit and budgeted host methods.
    // ---------------------------------------------------------------------

    if (typeof scope.atob === 'undefined') {
        scope.atob = function (text) {
            return binaryCodec().decodeBase64(String(text));
        };
    }

    if (typeof scope.pako === 'undefined') {
        scope.pako = Object.freeze({
            inflateRaw: function (data, options) {
                var inflated = binaryCodec().inflateRaw(bytesToBinaryString(toBytes(data)));
                // pako's {to: 'string'} decodes the output as UTF-8.
                return (options != null && options.to === 'string')
                    ? binaryCodec().decodeUtf8(inflated)
                    : binaryStringToBytes(inflated);
            }
        });
    }

    if (typeof scope.TextDecoder === 'undefined') {
        var HeadlessTextDecoder = function (label) {
            var encoding = (label == null) ? 'utf-8' : String(label).toLowerCase();
            if (encoding !== 'utf-8' && encoding !== 'utf8') {
                throw new RangeError('Only UTF-8 decoding is available: ' + label);
            }
            this.encoding = 'utf-8';
        };
        HeadlessTextDecoder.prototype.decode = function (input) {
            return binaryCodec().decodeUtf8(bytesToBinaryString(toBytes(input)));
        };
        scope.TextDecoder = HeadlessTextDecoder;
    }

    if (typeof scope.STENCIL_PATH === 'undefined') {
        // Same value as draw.io's Init.js. The decoder strips this prefix.
        scope.STENCIL_PATH = 'stencils';
    }

    // ---------------------------------------------------------------------
    // Diagnostics
    // ---------------------------------------------------------------------

    var reported = Object.create(null);

    var DrawioStencils = {
        /**
         * When true, getStencil throws for an unknown mxgraph.* stencil.
         * When false (the default), the name is recorded in missing and, if
         * the headless graph pipeline is loaded, reported to it as a
         * stencil:<name> diagnostic. The shape is then not painted.
         */
        strict: false,

        /** Unresolved stencils and sets: {name, basename, file, reason}. */
        missing: [],

        /** Stencil set files that were decoded from the bundled table. */
        loadedFiles: [],

        /** Library JavaScript files that were never evaluated. */
        skippedScripts: [],

        report: function (name, basename, file, reason) {
            var key = name + '\u0000' + file + '\u0000' + reason;
            if (reported[key] !== true) {
                reported[key] = true;
                DrawioStencils.missing.push({
                    name: name, basename: basename, file: file, reason: reason
                });
                // A missing set is always followed by an unknown-stencil entry
                // for the requested name; only that one reaches the pipeline.
                if (name != null && typeof DrawioHeadless !== 'undefined'
                        && DrawioHeadless != null && typeof DrawioHeadless.report === 'function') {
                    DrawioHeadless.report('stencil:' + name, 'not defined by the bundled offline'
                        + ' stencil sets (' + file + ')');
                }
            }
        },

        /** Return the names of all registered stencils, sorted. */
        names: function () {
            return Object.keys(mxStencilRegistry.stencils).sort();
        }
    };

    function isDefaultShape(name) {
        return typeof mxCellRenderer !== 'undefined' && mxCellRenderer != null
            && mxCellRenderer.defaultShapes != null
            && mxCellRenderer.defaultShapes[name] != null;
    }

    function endsWith(text, suffix) {
        var lower = text.toLowerCase();
        return lower.substring(lower.length - suffix.length, lower.length) == suffix;
    }

    // ---------------------------------------------------------------------
    // Port of draw.io's dynamic stencil loading.
    // Source: src/main/webapp/js/grapheditor/Graph.js lines 33595-33893 at
    // jgraph/drawio c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3. Deviations are
    // marked with "Headless:".
    // ---------------------------------------------------------------------

    // Headless: null-prototype maps, because names come from untrusted styles.
    mxStencilRegistry.libraries = Object.create(null);
    mxStencilRegistry.dynamicLoading = true;
    // Headless: never evaluate JavaScript libraries.
    mxStencilRegistry.allowEval = false;
    mxStencilRegistry.packages = Object.create(null);
    mxStencilRegistry.filesLoaded = Object.create(null);

    mxStencilRegistry.isFileLoaded = function (name) {
        // Headless: no Firefox workaround.
        return mxStencilRegistry.filesLoaded[name] === true;
    };

    mxStencilRegistry.setFileLoaded = function (name) {
        mxStencilRegistry.filesLoaded[name] = true;
    };

    // Extends the default stencil registry to add dynamic loading
    mxStencilRegistry.getStencil = function (name) {
        var result = mxStencilRegistry.stencils[name];

        if (result == null && !isDefaultShape(name) && mxStencilRegistry.dynamicLoading) {
            var basename = mxStencilRegistry.getBasenameForStencil(name);
            var file = null;

            // Loads stencil files and tries again
            if (basename != null) {
                var libs = mxStencilRegistry.libraries[basename];

                if (libs != null) {
                    if (mxStencilRegistry.packages[basename] == null) {
                        for (var i = 0; i < libs.length; i++) {
                            var fname = libs[i];

                            if (!mxStencilRegistry.isFileLoaded(fname)) {
                                mxStencilRegistry.setFileLoaded(fname);

                                if (endsWith(fname, '.xml')) {
                                    file = fname;
                                    mxStencilRegistry.loadStencilSet(fname, null);
                                } else if (endsWith(fname, '.js')) {
                                    // Headless: JavaScript shapes must be part of a
                                    // pinned bundle. Never fetch or eval them here.
                                    DrawioStencils.skippedScripts.push(fname);
                                }
                            }
                        }

                        mxStencilRegistry.packages[basename] = 1;
                    }
                } else {
                    // Replaces '_-_' with '_'
                    basename = basename.replace('_-_', '_');
                    file = STENCIL_PATH + '/' + basename + '.xml';
                    mxStencilRegistry.loadStencilSet(file, null);
                }

                result = mxStencilRegistry.stencils[name];

                // Headless: never return nothing silently. mxCellRenderer then
                // falls back to its default shape, so the name is always
                // reported (and thrown in strict mode).
                if (result == null) {
                    DrawioStencils.report(name, basename, file, 'unknown-stencil');

                    if (DrawioStencils.strict) {
                        throw new Error('Unsupported draw.io stencil: ' + name
                            + ' (not defined by the bundled offline stencil sets)');
                    }
                }
            }
        }

        return result;
    };

    // Returns the basename for the given stencil or null if no file must be
    // loaded to render the given stencil.
    mxStencilRegistry.getBasenameForStencil = function (name) {
        var tmp = null;

        if (name != null && typeof name === 'string') {
            var parts = name.split('.');

            if (parts.length > 0 && parts[0] == 'mxgraph') {
                tmp = parts[1];

                for (var i = 2; i < parts.length - 1; i++) {
                    tmp += '/' + parts[i];
                }
            }
        }

        return tmp;
    };

    // Loads the given stencil set
    mxStencilRegistry.loadStencilSet = function (stencilFile, postStencilLoad, force, async) {
        if (async) {
            // Headless: the upstream async path needs window.setTimeout.
            throw new Error('Asynchronous stencil loading is not supported headlessly');
        }

        force = (force != null) ? force : false;

        // Uses additional cache for detecting previous load attempts
        var xmlDoc = mxStencilRegistry.packages[stencilFile];

        if (force || xmlDoc == null) {
            var install = false;

            if (xmlDoc == null) {
                // Headless: decoding errors (budget, corrupt data) propagate
                // instead of being logged to a console and swallowed.
                xmlDoc = mxStencilRegistry.loadStencil(stencilFile);
                mxStencilRegistry.packages[stencilFile] = xmlDoc;
                install = true;

                if (xmlDoc != null) {
                    DrawioStencils.loadedFiles.push(stencilFile);
                }
            }

            if (xmlDoc != null && xmlDoc.documentElement != null) {
                mxStencilRegistry.parseStencilSet(xmlDoc.documentElement, postStencilLoad, install);
            }
        }
    };

    // Headless: replaces the XMLHttpRequest based loader. drawio-stencils.js
    // captures this function as fallback for sets missing from its table.
    mxStencilRegistry.loadStencil = function (filename, fn) {
        DrawioStencils.report(null, null, String(filename), 'stencil-set-not-bundled');

        if (fn != null) {
            fn(null);
        }

        return null;
    };

    // Headless: parseStencilSets (strings through mxUtils.parseXml) needs a
    // DOM parser and is intentionally not provided.
    mxStencilRegistry.parseStencilSets = function () {
        throw new Error('Parsing stencil XML strings requires a DOM and is not supported');
    };

    // Parses the given stencil set
    mxStencilRegistry.parseStencilSet = function (root, postStencilLoad, install) {
        if (root.nodeName == 'stencils') {
            var shapes = root.firstChild;

            while (shapes != null) {
                if (shapes.nodeName == 'shapes') {
                    mxStencilRegistry.parseStencilSet(shapes, postStencilLoad, install);
                }

                shapes = shapes.nextSibling;
            }
        } else {
            install = (install != null) ? install : true;
            var shape = root.firstChild;
            var packageName = '';
            var name = root.getAttribute('name');

            if (name != null) {
                packageName = name + '.';
            }

            while (shape != null) {
                if (shape.nodeType == mxConstants.NODETYPE_ELEMENT) {
                    name = shape.getAttribute('name');

                    if (name != null) {
                        packageName = packageName.toLowerCase();
                        var stencilName = name.replace(/ /g, '_');

                        if (install) {
                            mxStencilRegistry.addStencil(packageName + stencilName.toLowerCase(), new mxStencil(shape));
                        }

                        if (postStencilLoad != null) {
                            var w = shape.getAttribute('w');
                            var h = shape.getAttribute('h');

                            w = (w == null) ? 80 : parseInt(w, 10);
                            h = (h == null) ? 80 : parseInt(h, 10);

                            postStencilLoad(packageName, stencilName, name, w, h);
                        }
                    }
                }

                shape = shape.nextSibling;
            }
        }
    };

    // ---------------------------------------------------------------------
    // Inline stencils: shape=stencil(<data>) (js/grapheditor/Graph.js)
    // ---------------------------------------------------------------------

    var MAX_INLINE_STENCILS = 256;
    var inlineStencils = Object.create(null);
    var inlineStencilCount = 0;

    /**
     * Port of Graph.decompress(data) without the zlib variant: base64, raw
     * DEFLATE, URI decoding, then mxUtils.zapGremlins, all through the
     * budgeted host codec.
     */
    DrawioStencils.decompress = function (data) {
        if (data == null || data.length == 0) {
            return data;
        }
        var codec = binaryCodec();
        var inflated = codec.decodeUtf8(codec.inflateRaw(codec.decodeBase64(String(data))));
        return mxUtils.zapGremlins(decodeURIComponent(inflated));
    };

    /**
     * Parse an inline stencil definition into an original mxStencil. Headless,
     * mxUtils.parseXml is replaced by the host's strict XML parser (no DTDs,
     * no entities, size and depth limits) and the drawio-xml-dom.js nodes.
     */
    DrawioStencils.parseInline = function (data) {
        var cached = inlineStencils[data];
        if (cached != null) {
            return cached;
        }
        if (typeof DrawioXml === 'undefined' || DrawioXml == null) {
            throw new Error('Inline stencils need drawio-xml-dom.js');
        }
        var doc = DrawioXml.fromJson(drawioHost.parseXml(DrawioStencils.decompress(data)));
        if (doc.documentElement == null || doc.documentElement.nodeName != 'shape') {
            throw new Error('Inline stencil is not a <shape> definition');
        }
        var stencil = new mxStencil(doc.documentElement);
        if (inlineStencilCount < MAX_INLINE_STENCILS) {
            inlineStencils[data] = stencil;
            inlineStencilCount++;
        }
        return stencil;
    };

    if (typeof mxCellRenderer !== 'undefined') {
        // Graph.js: "Adds custom stencils defined via shape=stencil(value)
        // style". Upstream logs decoding errors to the console and falls back
        // to the default shape; headless, the error is reported as
        // stencil:inline and the cell is not painted.
        var mxCellRendererCreateShape = mxCellRenderer.prototype.createShape;
        mxCellRenderer.prototype.createShape = function (state) {
            if (state.style != null) {
                var shape = mxUtils.getValue(state.style, mxConstants.STYLE_SHAPE, null);

                if (shape != null && typeof shape === 'string' && shape.substring(0, 8) == 'stencil(') {
                    try {
                        return new mxShape(DrawioStencils.parseInline(shape.substring(8, shape.length - 1)));
                    } catch (e) {
                        var id = (state.cell != null) ? state.cell.id : '?';
                        var detail = 'cell ' + id + ': ' + ((e != null && e.message != null) ? e.message : e);
                        DrawioStencils.report(null, null, 'inline:' + id, 'inline-stencil-invalid');
                        if (typeof DrawioHeadless !== 'undefined' && DrawioHeadless != null
                                && typeof DrawioHeadless.report === 'function') {
                            DrawioHeadless.report('stencil:inline', detail);
                        }
                        return null;
                    }
                }
            }

            return mxCellRendererCreateShape.apply(this, arguments);
        };
    }

    scope.DrawioStencils = DrawioStencils;
})(this);
