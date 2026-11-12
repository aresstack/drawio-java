package com.aresstack.drawio;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;

/**
 * Reject pages with more cells than allowed before any JavaScript runs.
 *
 * <p>The page XML was already validated by the parser, so counting cell
 * start tags is a cheap, conservative upper bound.
 */
final class CellBudget {

    private CellBudget() {
    }

    static void check(DiagramPage page, int maxCells) throws RenderException {
        String xml = page.getGraphModelXml();
        int count = 0;
        int index = 0;
        while ((index = xml.indexOf("<mxCell", index)) >= 0) {
            index += 7;
            char next = index < xml.length() ? xml.charAt(index) : ' ';
            if (next == ' ' || next == '>' || next == '/' || next == '\t'
                    || next == '\n' || next == '\r') {
                if (++count > maxCells) {
                    throw new RenderException(RenderErrorCode.LIMIT_EXCEEDED,
                            "Diagram page has more cells than the configured limit");
                }
            }
        }
    }
}
