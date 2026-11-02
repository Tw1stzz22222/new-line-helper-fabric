package com.new_line_helper;

/** 自动化总开关。 */
public final class ModState {

    private ModState() {
    }

    public static volatile boolean running = false;

    public static void start() {
        running = true;
    }

    public static void terminate() {
        running = false;
    }
}
