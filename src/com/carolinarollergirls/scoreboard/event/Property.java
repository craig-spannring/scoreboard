package com.carolinarollergirls.scoreboard.event;

import java.util.Collection;

/**
 * The name of a property and the data type associated with it.
 * <p>
 * Each Property consists of a JSON name (the name used to access it
 * from the frontend) and a type (the class of the value it
 * represents).  
 * </p>
 */
public abstract class Property<T> {
    public Property(Class<T> type, String jsonName, Collection<Property<?>> propsToAddTo) {
        this.type = type;
        this.jsonName = jsonName;
        if (propsToAddTo != null) { propsToAddTo.add(this); }
    }

    @Override
    public String toString() {
        return jsonName;
    }

    public Class<T> getType() { return type; }
    public String getJsonName() { return jsonName; }

    private Class<T> type;
    private String jsonName;
}
