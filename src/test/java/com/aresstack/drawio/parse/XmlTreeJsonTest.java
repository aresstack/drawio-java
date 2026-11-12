package com.aresstack.drawio.parse;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.DiagramFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlTreeJsonTest {

    @Test
    void convertsElementsAttributesAndTextWithEscaping() throws Exception {
        String json = XmlTreeJson.toJson("<a x=\"1&quot;&#10;\">\n  <b/>t\u2028\\<c>&lt;</c></a>");
        assertEquals("{\"n\":\"a\",\"a\":[[\"x\",\"1\\\"\\n\"]],\"c\":[{\"n\":\"b\"},\"t\\u2028\\\\\","
                + "{\"n\":\"c\",\"c\":[\"<\"]}]}", json);
    }

    @Test
    void rejectsDoctypesAndExternalEntities() {
        assertThrows(DiagramFormatException.class, () -> XmlTreeJson.toJson(
                "<!DOCTYPE a [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><a>&e;</a>"));
    }
}
