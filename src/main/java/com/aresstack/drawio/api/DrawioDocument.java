package com.aresstack.drawio.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Keep all pages of a parsed draw.io document without discarding page metadata.
 */
public final class DrawioDocument {

    private final List<DiagramPage> pages;

    public DrawioDocument(List<DiagramPage> pages) {
        Objects.requireNonNull(pages, "pages");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("A draw.io document requires a page");
        }
        this.pages = Collections.unmodifiableList(new ArrayList<DiagramPage>(pages));
    }

    public List<DiagramPage> getPages() {
        return pages;
    }

    public DiagramPage firstPage() {
        return pages.get(0);
    }

    public DiagramPage pageByIndex(int index) throws DiagramFormatException {
        if (index < 0 || index >= pages.size()) {
            throw new DiagramFormatException(DiagramFormatException.Reason.PAGE_NOT_FOUND,
                    "Diagram page index does not exist");
        }
        return pages.get(index);
    }

    public DiagramPage pageByName(String name) throws DiagramFormatException {
        Objects.requireNonNull(name, "name");
        DiagramPage match = null;
        for (DiagramPage page : pages) {
            if (name.equals(page.getName())) {
                if (match != null) {
                    throw new DiagramFormatException(DiagramFormatException.Reason.AMBIGUOUS_PAGE,
                            "More than one diagram page has the requested name");
                }
                match = page;
            }
        }
        if (match == null) {
            throw new DiagramFormatException(DiagramFormatException.Reason.PAGE_NOT_FOUND,
                    "Diagram page name does not exist");
        }
        return match;
    }
}
