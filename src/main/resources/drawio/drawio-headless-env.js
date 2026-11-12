/*
 * Headless environment for the original mxGraph core inside GraalJS.
 *
 * Load this before drawio-graph.js. It provides only what the pinned mxClient
 * core reads while loading and while computing the view headlessly:
 *
 * - window: the global object, so mxCodec can resolve allowlisted constructors.
 * - navigator: an empty user agent; no browser feature is claimed.
 * - document: element factories returning inert elements. Shapes and labels
 *   create SVG nodes while being initialised; these nodes record nothing and
 *   paint nothing. Real painting happens later through mxImageExport into the
 *   Java2D canvas bridge. There is no layout, no networking, no script loading,
 *   no timers and no events.
 *
 * Every property read on an inert element that is not part of this small
 * vocabulary is recorded in DrawioHeadlessEnv.probes, so unsupported DOM
 * dependencies become visible in diagnostics instead of silently mattering.
 */
var window = this;

var DrawioHeadlessEnv = (function (global) {
    'use strict';

    var probes = {};
    var SVG_NS = 'http://www.w3.org/2000/svg';

    function noop() {
    }

    function inertElement(namespace, name) {
        var nodeName = namespace === SVG_NS ? String(name) : String(name).toUpperCase();
        var element = {
            nodeType: 1,
            nodeName: nodeName,
            tagName: nodeName,
            namespaceURI: namespace,
            style: {},
            childNodes: [],
            firstChild: null,
            lastChild: null,
            nextSibling: null,
            previousSibling: null,
            parentNode: null,
            ownerSVGElement: null,
            ownerDocument: null,
            setAttribute: noop,
            setAttributeNS: noop,
            removeAttribute: noop,
            getAttribute: function () {
                return null;
            },
            hasAttribute: function () {
                return false;
            },
            appendChild: function (child) {
                return child;
            },
            insertBefore: function (child) {
                return child;
            },
            removeChild: function (child) {
                return child;
            },
            addEventListener: noop,
            removeEventListener: noop,
            getElementsByTagName: function () {
                return [];
            },
            toString: function () {
                return '[object DrawioInertElement]';
            }
        };
        element.ownerDocument = document;
        if (typeof Proxy !== 'function') {
            return element;
        }
        return new Proxy(element, {
            get: function (target, property) {
                if (typeof property === 'string' && !(property in target)) {
                    var key = nodeName + '.' + property;
                    probes[key] = (probes[key] || 0) + 1;
                }
                return target[property];
            }
        });
    }

    var document = {
        nodeType: 9,
        location: Object.freeze({href: 'about:drawio-headless'}),
        createElement: function (name) {
            return inertElement(null, name);
        },
        createElementNS: function (namespace, name) {
            return inertElement(namespace, name);
        },
        createTextNode: function () {
            return inertElement(null, '#text');
        },
        getElementsByTagName: function () {
            return [];
        },
        getElementById: function () {
            return null;
        }
    };
    document.documentElement = inertElement(null, 'html');
    document.body = inertElement(null, 'body');

    global.document = document;
    // mxUtils.clone compares constructors with Element; no object is one.
    global.Element = function Element() {
        throw new Error('DOM Element construction is not available headless');
    };
    global.navigator = Object.freeze({
        userAgent: '',
        vendor: '',
        appVersion: '',
        platform: '',
        appName: 'Netscape',
        language: 'en',
        maxTouchPoints: 0
    });

    // mxClient loader switches: never load resources, stylesheets or scripts.
    global.mxLoadResources = false;
    global.mxLoadStylesheets = false;
    global.mxForceIncludes = false;
    global.mxResourceExtension = '.txt';
    // The pinned core is concatenated, so mxClient must not include its files.
    global.module = {exports: {}};

    return Object.freeze({
        probes: probes,
        createInertElement: function (name) {
            return inertElement(null, name);
        },
        probeReport: function () {
            return Object.keys(probes).sort();
        }
    });
})(this);
