package com.aresstack.drawio.api;

import java.util.Objects;

/**
 * Identify the page that was rendered and how many pages the document has.
 */
public final class RenderedPage {

    private final int index;
    private final String id;
    private final String name;
    private final int pageCount;

    public RenderedPage(int index, String id, String name, int pageCount) {
        this.index = index;
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.pageCount = pageCount;
    }

    public static RenderedPage of(DiagramPage page, int pageCount) {
        return new RenderedPage(page.getIndex(), page.getId(), page.getName(), pageCount);
    }

    public int getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPageCount() {
        return pageCount;
    }
}
