package io.github.prabhath112.iggroupguard;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Covers Instagram's "Send to new group chat (N)" share-sheet button with a grey,
 * tap-swallowing overlay. "Send separately" stays usable.
 *
 * Smoothness: a separate window cannot follow another app's animation without visible steps
 * (measured on CPH2707: ~300 ms sheet slide, button position readable only every 30-40 ms).
 * So the service remembers where the button comes to rest ("home") and, while the sheet slides
 * towards it, shows the grey banner standing still at home in one go. The window underneath
 * stretches down to wherever the button currently is, transparent, so it is never tappable.
 */
public class GroupGuardService extends AccessibilityService {

    static final String TAG = "GroupGuard";
    static final String INSTAGRAM = "com.instagram.android";

    // Verified on Instagram 455.0.0.0.47 (uiautomator dump, 2026-11-05).
    static final String GROUP_BUTTON_ID = INSTAGRAM + ":id/direct_send_to_group_button_vertical";
    // ponytail: English-only fallback in case the view id changes; add locales if needed.
    static final String GROUP_BUTTON_TEXT = "Send to new group chat";

    private static final long RECHECK_MS = 500;
    private static final long FADE_MS = 80;
    private static final long SETTLE_MS = 100; // unchanged this long = stopped

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recheck = new Runnable() {
        @Override public void run() { refresh(); }
    };
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback follow = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) { refresh(); }
    };

    private WindowManager wm;
    private FrameLayout window;   // tap-swallowing window, may be taller than the banner while sliding
    private TextView banner;      // the visible grey bar, always at the top of the window
    private boolean fading;
    private long movedAt;            // uptime of the last position change
    private final Rect shown = new Rect();       // current window bounds
    private final Rect lastButton = new Rect();  // button bounds at the previous check
    // ponytail: in memory only (privacy policy: only the consent flag is stored). First open after
    // the service starts measures it; every later open is instant.
    private Rect home;

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Log.i(TAG, "connected sdk=" + Build.VERSION.SDK_INT);
        refresh(); // share sheet may already be open when the service is turned on
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        refresh();
    }

    private void refresh() {
        handler.removeCallbacks(recheck);
        choreographer.removeFrameCallback(follow);
        // Play policy: no use of the Accessibility API before the in-app disclosure is accepted.
        Rect button = MainActivity.hasConsent(this) ? findGroupButton() : null;
        if (button == null) {
            hide();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastButton.isEmpty() || !button.equals(lastButton)) movedAt = now;
        lastButton.set(button);
        // Instagram's position updates arrive every 30-40 ms but we check every 16 ms frame, so two
        // equal readings in a row do not mean it stopped. Treating them as "stopped" faded the banner
        // in mid-slide and could record a mid-slide spot as home (stutter on first use).
        boolean moving = now - movedAt < SETTLE_MS;
        if (moving) {
            choreographer.postFrameCallback(follow); // check again next frame until it stops
        } else {
            if (home == null || !home.equals(button)) {
                home = new Rect(button);
                Log.i(TAG, "home " + home.flattenToString());
            }
        }

        // Sliding up into the remembered spot: banner stands still at home, window reaches down
        // to the button so the moving button is covered too.
        // Height is not compared: the button first shows up clipped by the screen edge (measured:
        // 42 px, then 95 px, then full 131 px), and waiting for full height cost ~100 ms.
        boolean towardsHome = moving && home != null && button.left == home.left
                && button.right == home.right && button.top >= home.top;
        Rect bounds = button;
        if (towardsHome) {
            // Only ever grow during the slide: resizing the window every frame made it flicker.
            int bottom = Math.max(home.bottom, button.bottom);
            if (window != null && shown.top == home.top) bottom = Math.max(bottom, shown.bottom);
            bounds = new Rect(home.left, home.top, home.right, bottom);
        }
        int bannerHeight = towardsHome ? home.height() : button.height();
        place(bounds, bannerHeight);
        if (banner == null) return; // window could not be added (service shutting down)

        if (towardsHome) {
            showNow(); // known final spot: appear at once, standing still; the button slides in under it
        } else {
            // Standing still: fade in. Moving with no known spot yet (first open after the service
            // starts): stay invisible but keep blocking taps, so the user never sees it step.
            setVisible(!moving);
        }

        // ponytail: Android 8 sends no event when the button goes GONE, so the overlay would stay
        // and cover whatever slides into its place ("Send separately"). Re-check only while shown.
        handler.postDelayed(recheck, RECHECK_MS);
    }

    private void showNow() {
        if (banner.getAlpha() == 1f && !fading) return;
        banner.animate().cancel();
        banner.setAlpha(1f);
        fading = false;
        Log.i(TAG, "overlay visible");
    }

    private void setVisible(boolean visible) {
        if (visible) {
            if (!fading && banner.getAlpha() < 1f) {
                fading = true;
                banner.animate().alpha(1f).setDuration(FADE_MS).start();
                Log.i(TAG, "overlay visible");
            }
        } else if (banner.getAlpha() > 0f || fading) {
            fading = false;
            banner.animate().cancel();
            banner.setAlpha(0f);
        }
    }

    /** Bounds of the group button in any on-screen Instagram window, or null. */
    private Rect findGroupButton() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) return findIn(getRootInActiveWindow());
        for (AccessibilityWindowInfo w : windows) {
            Rect r = findIn(w.getRoot());
            if (r != null) return r;
        }
        return null;
    }

    private static Rect findIn(AccessibilityNodeInfo root) {
        if (root == null || !INSTAGRAM.contentEquals(root.getPackageName())) return null;
        AccessibilityNodeInfo node = first(root.findAccessibilityNodeInfosByViewId(GROUP_BUTTON_ID));
        if (node == null) {
            node = first(root.findAccessibilityNodeInfosByText(GROUP_BUTTON_TEXT));
            while (node != null && !node.isClickable()) node = node.getParent();
        }
        if (node == null || !node.isVisibleToUser()) return null;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        return r.isEmpty() ? null : r;
    }

    private static AccessibilityNodeInfo first(List<AccessibilityNodeInfo> nodes) {
        return nodes == null || nodes.isEmpty() ? null : nodes.get(0);
    }

    private void place(Rect r, int bannerHeight) {
        if (window != null && r.equals(shown)
                && banner.getLayoutParams().height == bannerHeight) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                r.width(), r.height(), r.left, r.top,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.windowAnimations = 0; // no system animation when the window is added or resized
        if (Build.VERSION.SDK_INT >= 28) { // field does not exist on Android 8.x
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        if (window == null) {
            banner = new TextView(this);
            banner.setText("Group send disabled");
            banner.setTextColor(0xFFC4C4C4); // ~6:1 on #3A3A3A (WCAG AA)
            banner.setGravity(Gravity.CENTER);
            banner.setBackgroundColor(0xFF3A3A3A); // opaque, or Instagram's label bleeds through
            banner.setAlpha(0f); // refresh() decides when it shows; taps are blocked regardless
            window = new FrameLayout(this);
            window.setContentDescription("Send to new group chat, disabled by IG Group Guard");
            window.addView(banner, new FrameLayout.LayoutParams(-1, bannerHeight, Gravity.TOP));
            window.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        Log.i(TAG, "blocked tap");
                        Toast.makeText(GroupGuardService.this,
                                "Blocked: this would create a group with everyone selected. Use \"Send separately\".",
                                Toast.LENGTH_SHORT).show();
                    }
                    return true; // swallow every tap anywhere in the window
                }
            });
            try {
                wm.addView(window, lp);
            } catch (WindowManager.BadTokenException e) {
                // Service is being turned off while an event is still in flight: token is dead.
                Log.w(TAG, "overlay not added: " + e.getMessage());
                window = null;
                banner = null;
                return;
            }
        } else {
            banner.getLayoutParams().height = bannerHeight;
            banner.requestLayout();
            try {
                wm.updateViewLayout(window, lp);
            } catch (IllegalArgumentException e) { // view detached underneath us; re-add next time
                window = null;
                banner = null;
                return;
            }
        }
        shown.set(r);
        Log.i(TAG, "overlay " + r.flattenToString());
    }

    private void hide() {
        handler.removeCallbacks(recheck);
        choreographer.removeFrameCallback(follow);
        lastButton.setEmpty();
        if (window == null) return;
        try {
            wm.removeView(window);
        } catch (IllegalArgumentException e) {
            // Already detached (window token died with the service connection); nothing to remove.
        }
        window = null;
        banner = null;
        fading = false;
        shown.setEmpty();
        Log.i(TAG, "overlay hidden");
    }

    @Override
    public void onInterrupt() {
        hide();
    }

    @Override
    public void onDestroy() {
        hide();
        super.onDestroy();
    }
}
