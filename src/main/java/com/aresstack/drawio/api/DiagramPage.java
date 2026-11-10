package com.aresstack.drawio.api;

import java.util.Objects;

/**
 * Represent one normalized page of a draw.io document.
 */
public final class DiagramPage {

    private final int index;
    private final String id;
    private final String name;
    private final String graphModelXml;

    public DiagramPage(int index, String id, String name, String graphModelXml) {
        this.index = index;
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.graphModelXml = Objects.requireNonNull(graphModelXml, "graphModelXml");
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

    public String getGraphModelXml() {
        return graphModelXml;
    }
}
