package com.carolinarollergirls.scoreboard.event;

/**
 * Class containing an ID (of type string) and a value (also of type string).
  * <p><em>Note:</em> In some implementations, ID and value are the same.</p>
 */
public interface ValueWithId {
    /**
     * Id used to identify this element.
     * </p>
     * Id to be used in order to identify this element amongst all
     * elements of its type. Used when the element is referenced by
     * elements other than its parent.  (Typically a UUID.)
     * </p>
     */
    public String getId();
        
    /**
     * Value of the element
     * <p>
     * Implementations can use this field for any purpose.  For
     * example, implementations of ScoreBoardEventProvider this should
     * usually be the same as getId() Other implementations may have
     * have a comma-separated list of values.
     * </p>
     */
    public String getValue();
}
