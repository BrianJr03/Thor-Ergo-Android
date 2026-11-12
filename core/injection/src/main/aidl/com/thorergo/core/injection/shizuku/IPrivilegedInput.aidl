package com.thorergo.core.injection.shizuku;

/** Runs as the shell user through Shizuku; every method returns false when the platform refuses. */
interface IPrivilegedInput {
    /** Shizuku's reserved transaction for tearing down a user service. */
    void destroy() = 16777114;

    boolean injectKey(int action, int keyCode, int metaState) = 1;

    /** A mouse event on [displayId]; scroll values are in notches. */
    boolean injectMouse(int displayId, int action, float x, float y, int buttonState, int actionButton, float vScroll, float hScroll) = 2;

    /** A touchscreen tap at (x, y) on [displayId], for games that ignore mouse input. */
    boolean injectTouch(int displayId, int action, float x, float y) = 3;

    boolean setDisplayPower(int displayId, boolean on) = 4;
}
