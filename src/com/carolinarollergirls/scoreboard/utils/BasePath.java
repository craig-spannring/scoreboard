package com.carolinarollergirls.scoreboard.utils;

import java.io.File;

/** 
 * Top level directory for the scoreboard. 
 * <p>By default, this is the current working directory.</p>
 */
public class BasePath {
    /** Top level directory for the scoreboard. */
    public static File get() { 
        return basePath; 
    }
    
    /**
     * Change location used as top level directory
     * <p>
     * <em>Note: This is primarily intended for unit tests.  Changing 
     *           this after startup will like result in pain and 
     *           suffering.</em>
     * </p>
     */
    public static void set(File newBaseDirectory) { 
        basePath = newBaseDirectory; 
    } 

    private static File basePath = new File(".");
}
