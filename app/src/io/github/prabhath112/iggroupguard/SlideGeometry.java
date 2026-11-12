package io.github.prabhath112.iggroupguard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure decision logic for the share-sheet overlay, split out of {@link GroupGuardService} so it can be
 * unit-tested on a plain JVM (no Android classes). Everything here is a function of ints/booleans.
 * See test/SlideGeometryTest.java.
 */
final class SlideGeometry {

    private SlideGeometry() {}

    private static final Pattern COUNT = Pattern.compile("\\((\\d+)\\)"); // "... (3)"

    /** The group button rose from well below: a real share-sheet slide-in, not a sheet already open. */
    static boolean slidIn(int openingFromTop, int buttonTop, int buttonHeight) {
        return openingFromTop - buttonTop > buttonHeight / 2;
    }

    /** Button occupies the same horizontal span as the learned home spot. */
    static boolean sameColumn(int buttonLeft, int buttonRight, int homeLeft, int homeRight) {
        return buttonLeft == homeLeft && buttonRight == homeRight;
    }

    /** Opening slide rising into home: show the banner standing still at home at once. */
    static boolean towardsHome(boolean opening, boolean moving, boolean sameColumn, int buttonTop, int homeTop) {
        return opening && moving && sameColumn && buttonTop >= homeTop;
    }

    /** Keyboard closing: the button comes back DOWN to the reliable home spot. */
    static boolean backDownHome(boolean opening, boolean moving, boolean sameColumn,
                                int buttonTop, int homeTop, int prevTop) {
        return !opening && moving && sameColumn && buttonTop < homeTop && buttonTop >= prevTop;
    }

    /** Number of selected people from the button label "Send to new group chat (N)", or -1. */
    static int parseCount(CharSequence text) {
        if (text == null) return -1;
        Matcher m = COUNT.matcher(text);
        if (!m.find()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1; // a digit run longer than int overflows; treat as unknown
        }
    }
}
