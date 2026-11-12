package com.aresstack.drawio.api;

import java.util.Objects;

/**
 * Select one page of a multi-page draw.io document.
 */
public final class PageSelector {

    private static final PageSelector FIRST = new PageSelector(Kind.INDEX, 0, null);

    private enum Kind { INDEX, NAME, ID }

    private final Kind kind;
    private final int index;
    private final String key;

    private PageSelector(Kind kind, int index, String key) {
        this.kind = kind;
        this.index = index;
        this.key = key;
    }

    public static PageSelector first() {
        return FIRST;
    }

    public static PageSelector index(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("page index must not be negative");
        }
        return new PageSelector(Kind.INDEX, index, null);
    }

    public static PageSelector name(String name) {
        return new PageSelector(Kind.NAME, -1, Objects.requireNonNull(name, "name"));
    }

    public static PageSelector id(String id) {
        return new PageSelector(Kind.ID, -1, Objects.requireNonNull(id, "id"));
    }

    /**
     * Resolve this selector against a parsed document.
     */
    public DiagramPage select(DrawioDocument document) throws DiagramFormatException {
        Objects.requireNonNull(document, "document");
        switch (kind) {
            case NAME:
                return document.pageByName(key);
            case ID:
                DiagramPage match = null;
                for (DiagramPage page : document.getPages()) {
                    if (key.equals(page.getId())) {
                        if (match != null) {
                            throw new DiagramFormatException(
                                    DiagramFormatException.Reason.AMBIGUOUS_PAGE,
                                    "More than one diagram page has the requested id");
                        }
                        match = page;
                    }
                }
                if (match == null) {
                    throw new DiagramFormatException(DiagramFormatException.Reason.PAGE_NOT_FOUND,
                            "Diagram page id does not exist");
                }
                return match;
            case INDEX:
            default:
                return document.pageByIndex(index);
        }
    }

    @Override
    public String toString() {
        // Page names can be user data; do not print them.
        return kind == Kind.INDEX ? "page[" + index + "]" : "page(" + kind.name().toLowerCase() + ")";
    }
}
