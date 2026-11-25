package com.mrlauncher.app.game.platform.cursor;

import com.mrlauncher.app.game.platform.input.PlatformGrabListener;

/**
 * Platform cursor implementor. Receives cursor updates
 */
public interface PlatformCursorImplementor extends PlatformGrabListener {
    /**
     * Update cursor position on the screen
     */
    void onCursorPosition();

    /**
     * Update cursor drawable on the screen
     */
    void onCursorChanged();
}
