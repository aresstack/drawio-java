/*
 * Minimal XML node model for the original mxGraph codecs running headless.
 *
 * Java parses and validates untrusted XML with StrictXml (no DTDs, no external
 * entities, bounded size) and hands the resulting tree to JavaScript as JSON.
 * This file turns that JSON into plain JavaScript nodes that implement only the
 * DOM Core members used by mxCodec, mxObjectCodec, mxCellCodec, mxModelCodec,
 * mxStylesheetCodec and mxStencil. It never parses markup itself, never loads
 * anything and has no access to Java.
 *
 * JSON format (produced by com.aresstack.drawio.graal.XmlTreeJson):
 *   element: {"n": "name", "a": [["attr", "value"], ...], "c": [child, ...]}
 *   text:    "character data"
 */
(function (scope) {
    'use strict';

    var ELEMENT_NODE = 1;
    var TEXT_NODE = 3;
    var DOCUMENT_NODE = 9;

    function DrawioXmlNode() {
    }

    function defineSiblingAccessors(proto) {
        Object.defineProperty(proto, 'firstChild', {
            get: function () {
                return this.childNodes.length > 0 ? this.childNodes[0] : null;
            }
        });
        Object.defineProperty(proto, 'lastChild', {
            get: function () {
                var n = this.childNodes.length;
                return n > 0 ? this.childNodes[n - 1] : null;
            }
        });
        Object.defineProperty(proto, 'nextSibling', {
            get: function () {
                return siblingAt(this, 1);
            }
        });
        Object.defineProperty(proto, 'previousSibling', {
            get: function () {
                return siblingAt(this, -1);
            }
        });
    }

    function siblingAt(node, offset) {
        var parent = node.parentNode;
        if (parent == null) {
            return null;
        }
        // The codecs walk children with firstChild/nextSibling; a verified
        // position hint keeps that walk linear for wide elements.
        var siblings = parent.childNodes;
        var position = node.siblingIndex;
        if (typeof position !== 'number' || siblings[position] !== node) {
            position = siblings.indexOf(node);
            node.siblingIndex = position;
        }
        var index = position + offset;
        if (index < 0 || index >= siblings.length) {
            return null;
        }
        siblings[index].siblingIndex = index;
        return siblings[index];
    }

    defineSiblingAccessors(DrawioXmlNode.prototype);

    DrawioXmlNode.prototype.appendChild = function (child) {
        if (child.parentNode != null) {
            child.parentNode.removeChild(child);
        }
        child.parentNode = this;
        child.ownerDocument = this.nodeType === DOCUMENT_NODE ? this : this.ownerDocument;
        this.childNodes.push(child);
        if (this.nodeType === DOCUMENT_NODE && child.nodeType === ELEMENT_NODE) {
            this.documentElement = child;
        }
        return child;
    };

    DrawioXmlNode.prototype.insertBefore = function (child, reference) {
        if (reference == null) {
            return this.appendChild(child);
        }
        if (child.parentNode != null) {
            child.parentNode.removeChild(child);
        }
        var index = this.childNodes.indexOf(reference);
        if (index < 0) {
            throw new Error('DrawioXml: reference node is not a child');
        }
        child.parentNode = this;
        child.ownerDocument = this.ownerDocument;
        this.childNodes.splice(index, 0, child);
        return child;
    };

    DrawioXmlNode.prototype.removeChild = function (child) {
        var index = this.childNodes.indexOf(child);
        if (index < 0) {
            throw new Error('DrawioXml: node is not a child');
        }
        this.childNodes.splice(index, 1);
        child.parentNode = null;
        if (this.documentElement === child) {
            this.documentElement = null;
        }
        return child;
    };

    DrawioXmlNode.prototype.hasChildNodes = function () {
        return this.childNodes.length > 0;
    };

    Object.defineProperty(DrawioXmlNode.prototype, 'textContent', {
        get: function () {
            if (this.nodeType === TEXT_NODE) {
                return this.nodeValue;
            }
            var parts = [];
            collectText(this, parts);
            return parts.join('');
        }
    });

    function collectText(node, parts) {
        for (var i = 0; i < node.childNodes.length; i++) {
            var child = node.childNodes[i];
            if (child.nodeType === TEXT_NODE) {
                parts.push(child.nodeValue);
            } else {
                collectText(child, parts);
            }
        }
    }

    function DrawioXmlText(value, ownerDocument) {
        this.nodeType = TEXT_NODE;
        this.nodeName = '#text';
        this.nodeValue = String(value);
        this.data = this.nodeValue;
        this.childNodes = [];
        this.parentNode = null;
        this.ownerDocument = ownerDocument;
    }

    DrawioXmlText.prototype = Object.create(DrawioXmlNode.prototype);
    DrawioXmlText.prototype.constructor = DrawioXmlText;

    DrawioXmlText.prototype.cloneNode = function () {
        return new DrawioXmlText(this.nodeValue, this.ownerDocument);
    };

    function DrawioXmlAttr(name, value) {
        this.name = name;
        this.nodeName = name;
        this.localName = name;
        this.value = value;
        this.nodeValue = value;
        this.namespaceURI = null;
    }

    function DrawioXmlElement(name, ownerDocument) {
        this.nodeType = ELEMENT_NODE;
        this.nodeName = String(name);
        this.tagName = this.nodeName;
        this.localName = this.nodeName;
        this.namespaceURI = null;
        this.attributes = [];
        this.childNodes = [];
        this.parentNode = null;
        this.ownerDocument = ownerDocument;
    }

    DrawioXmlElement.prototype = Object.create(DrawioXmlNode.prototype);
    DrawioXmlElement.prototype.constructor = DrawioXmlElement;

    function findAttribute(element, name) {
        for (var i = 0; i < element.attributes.length; i++) {
            if (element.attributes[i].name === name) {
                return i;
            }
        }
        return -1;
    }

    DrawioXmlElement.prototype.getAttribute = function (name) {
        var index = findAttribute(this, String(name));
        return index >= 0 ? this.attributes[index].value : null;
    };

    DrawioXmlElement.prototype.hasAttribute = function (name) {
        return findAttribute(this, String(name)) >= 0;
    };

    DrawioXmlElement.prototype.setAttribute = function (name, value) {
        name = String(name);
        value = String(value);
        var index = findAttribute(this, name);
        if (index >= 0) {
            this.attributes[index] = new DrawioXmlAttr(name, value);
        } else {
            this.attributes.push(new DrawioXmlAttr(name, value));
        }
    };

    DrawioXmlElement.prototype.setAttributeNS = function (namespace, name, value) {
        this.setAttribute(name, value);
    };

    DrawioXmlElement.prototype.removeAttribute = function (name) {
        var index = findAttribute(this, String(name));
        if (index >= 0) {
            this.attributes.splice(index, 1);
        }
    };

    DrawioXmlElement.prototype.getElementsByTagName = function (name) {
        var result = [];
        collectElements(this, String(name), result);
        return result;
    };

    function collectElements(node, name, result) {
        for (var i = 0; i < node.childNodes.length; i++) {
            var child = node.childNodes[i];
            if (child.nodeType === ELEMENT_NODE) {
                if (name === '*' || child.nodeName === name) {
                    result.push(child);
                }
                collectElements(child, name, result);
            }
        }
    }

    DrawioXmlElement.prototype.cloneNode = function (deep) {
        var clone = new DrawioXmlElement(this.nodeName, this.ownerDocument);
        for (var i = 0; i < this.attributes.length; i++) {
            clone.attributes.push(new DrawioXmlAttr(this.attributes[i].name, this.attributes[i].value));
        }
        if (deep) {
            for (var j = 0; j < this.childNodes.length; j++) {
                clone.appendChild(this.childNodes[j].cloneNode(true));
            }
        }
        return clone;
    };

    function DrawioXmlDocument() {
        this.nodeType = DOCUMENT_NODE;
        this.nodeName = '#document';
        this.childNodes = [];
        this.parentNode = null;
        this.ownerDocument = null;
        this.documentElement = null;
    }

    DrawioXmlDocument.prototype = Object.create(DrawioXmlNode.prototype);
    DrawioXmlDocument.prototype.constructor = DrawioXmlDocument;

    DrawioXmlDocument.prototype.createElement = function (name) {
        return new DrawioXmlElement(name, this);
    };

    DrawioXmlDocument.prototype.createElementNS = function (namespace, name) {
        return new DrawioXmlElement(name, this);
    };

    DrawioXmlDocument.prototype.createTextNode = function (value) {
        return new DrawioXmlText(value, this);
    };

    DrawioXmlDocument.prototype.getElementsByTagName = function (name) {
        return DrawioXmlElement.prototype.getElementsByTagName.call(this, name);
    };

    DrawioXmlDocument.prototype.importNode = function (node, deep) {
        var copy = node.cloneNode(deep);
        reown(copy, this);
        return copy;
    };

    function reown(node, document) {
        node.ownerDocument = document;
        for (var i = 0; i < node.childNodes.length; i++) {
            reown(node.childNodes[i], document);
        }
    }

    function build(json, document, depth) {
        if (depth > 512) {
            throw new Error('DrawioXml: element nesting too deep');
        }
        if (typeof json === 'string') {
            return new DrawioXmlText(json, document);
        }
        if (json == null || typeof json.n !== 'string') {
            throw new Error('DrawioXml: malformed node');
        }
        var element = new DrawioXmlElement(json.n, document);
        var attributes = json.a || [];
        for (var i = 0; i < attributes.length; i++) {
            element.attributes.push(new DrawioXmlAttr(String(attributes[i][0]), String(attributes[i][1])));
        }
        var children = json.c || [];
        for (var j = 0; j < children.length; j++) {
            var child = build(children[j], document, depth + 1);
            child.parentNode = element;
            child.siblingIndex = element.childNodes.length;
            element.childNodes.push(child);
        }
        return element;
    }

    scope.DrawioXml = Object.freeze({
        ELEMENT_NODE: ELEMENT_NODE,
        TEXT_NODE: TEXT_NODE,
        createDocument: function () {
            return new DrawioXmlDocument();
        },
        fromJson: function (text) {
            var document = new DrawioXmlDocument();
            document.appendChild(build(JSON.parse(String(text)), document, 0));
            return document;
        }
    });
})(this);
