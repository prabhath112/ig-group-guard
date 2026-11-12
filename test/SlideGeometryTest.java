package io.github.prabhath112.iggroupguard;

/**
 * Dependency-free unit tests for {@link SlideGeometry}: the overlay decision logic that used to live
 * inline in GroupGuardService.refresh()/selectedCount(). Run by build.sh (javac + java), no JUnit.
 * Exits non-zero on any failure so a regression fails the build and CI.
 */
public final class SlideGeometryTest {

    private static int failures = 0;

    private static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("  ok   " + name);
        } else {
            System.out.println("  FAIL " + name);
            failures++;
        }
    }

    private static void eq(String name, int expected, int actual) {
        check(name + " (expected " + expected + ", got " + actual + ")", expected == actual);
    }

    public static void main(String[] args) {
        // slidIn: rose by more than half the button height = a real slide-in.
        check("slidIn: rose by full height", SlideGeometry.slidIn(2554, 1643, 131));
        check("slidIn: rose by just over half", SlideGeometry.slidIn(100, 34, 131)); // 66 > 65
        check("slidIn: rose by exactly half is NOT slidIn", !SlideGeometry.slidIn(100, 35, 130)); // 65 > 65 false
        check("slidIn: already open (no rise)", !SlideGeometry.slidIn(2554, 2554, 131));
        check("slidIn: came from above (keyboard-up spot)", !SlideGeometry.slidIn(1643, 2554, 131));

        // sameColumn: exact left/right match.
        check("sameColumn: exact match", SlideGeometry.sameColumn(48, 1224, 48, 1224));
        check("sameColumn: different left", !SlideGeometry.sameColumn(0, 1224, 48, 1224));
        check("sameColumn: different right (rotation/width change)", !SlideGeometry.sameColumn(48, 2000, 48, 1224));

        // towardsHome: opening slide rising into home (buttonTop >= homeTop).
        check("towardsHome: opening+moving+sameCol+at/below home", SlideGeometry.towardsHome(true, true, true, 2600, 2554));
        check("towardsHome: exactly at home", SlideGeometry.towardsHome(true, true, true, 2554, 2554));
        check("towardsHome: not opening", !SlideGeometry.towardsHome(false, true, true, 2600, 2554));
        check("towardsHome: not moving", !SlideGeometry.towardsHome(true, false, true, 2600, 2554));
        check("towardsHome: wrong column", !SlideGeometry.towardsHome(true, true, false, 2600, 2554));
        check("towardsHome: above home (keyboard spot)", !SlideGeometry.towardsHome(true, true, true, 1643, 2554));

        // backDownHome: keyboard closing, button moving back down to home (above home, not rising).
        check("backDownHome: below home, moving down", SlideGeometry.backDownHome(false, true, true, 1900, 2554, 1800));
        check("backDownHome: opening excludes it", !SlideGeometry.backDownHome(true, true, true, 1900, 2554, 1800));
        check("backDownHome: at/below home excluded", !SlideGeometry.backDownHome(false, true, true, 2554, 2554, 2500));
        check("backDownHome: moving UP (prevTop above) excluded", !SlideGeometry.backDownHome(false, true, true, 1900, 2554, 2000));
        check("backDownHome: wrong column", !SlideGeometry.backDownHome(false, true, false, 1900, 2554, 1800));

        // parseCount: "Send to new group chat (N)".
        eq("parseCount: (2)", 2, SlideGeometry.parseCount("Send to new group chat (2)"));
        eq("parseCount: (13)", 13, SlideGeometry.parseCount("Group chat (13) people"));
        eq("parseCount: no number", -1, SlideGeometry.parseCount("Send to new group chat"));
        eq("parseCount: null", -1, SlideGeometry.parseCount(null));
        eq("parseCount: first match wins", 3, SlideGeometry.parseCount("(3) and (4)"));
        eq("parseCount: overflow digit run -> -1", -1, SlideGeometry.parseCount("(99999999999999999999)"));

        System.out.println(failures == 0
                ? "SlideGeometryTest: ALL PASSED"
                : "SlideGeometryTest: " + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }
}
