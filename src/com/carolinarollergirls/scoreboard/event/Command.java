package com.carolinarollergirls.scoreboard.event;

import java.util.Collection;

/**
 * A command that can be executed on an object.
 * <p>
 * Commands are properties that can be executed.  Technically
 * speaking, commands have a boolean value, but it does not appear the
 * value is not important.  The command is passed to implementations
 * of ScoreBoardEventProvider.execute().
 * </p>
 */
public class Command extends Property<Boolean> {
    public Command(String jsonName, Collection<Property<?>> propsToAddTo) {
        super(Boolean.class, jsonName, propsToAddTo);
    }
}
