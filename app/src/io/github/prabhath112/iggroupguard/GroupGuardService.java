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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Covers Instagram's "Send to new group chat (N)" share-sheet button with a grey,
 * tap-swallowing overlay. "Send separately" stays usable.
 *
 * Two modes (MainActivity setting): "Block it" (default) swallows the tap; "Ask me first" opens
 * our own confirmation, and "Create group" uncovers Instagram's button for the current share sheet
 * only. The user then taps Instagram's button themselves; the app never taps it for them.
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

    private FrameLayout confirm;      // our own "Create a group chat?" popup (Ask me first mode)
    private boolean allowedThisSheet; // user said "Create group": leave the button alone until the sheet closes
    private static final Pattern COUNT = Pattern.compile("\\((\\d+)\\)"); // "... (3)"

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
        if (allowedThisSheet) {
            // Confirmed for this sheet: keep Instagram's button uncovered; keep checking so the
            // permission ends as soon as the sheet (and the button) goes away.
            removeWindow();
            handler.postDelayed(recheck, RECHECK_MS);
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
        String label = getString(MainActivity.askFirst(this) ? R.string.banner_ask : R.string.banner_block);
        if (!label.contentEquals(banner.getText())) banner.setText(label);

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
        AccessibilityNodeInfo node = findGroupNode();
        if (node == null) return null;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        return r.isEmpty() ? null : r;
    }

    private AccessibilityNodeInfo findGroupNode() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) return findIn(getRootInActiveWindow());
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo n = findIn(w.getRoot());
            if (n != null) return n;
        }
        return null;
    }

    private static AccessibilityNodeInfo findIn(AccessibilityNodeInfo root) {
        if (root == null || !INSTAGRAM.contentEquals(root.getPackageName())) return null;
        AccessibilityNodeInfo node = first(root.findAccessibilityNodeInfosByViewId(GROUP_BUTTON_ID));
        if (node == null) {
            node = first(root.findAccessibilityNodeInfosByText(GROUP_BUTTON_TEXT));
            while (node != null && !node.isClickable()) node = node.getParent();
        }
        return node != null && node.isVisibleToUser() ? node : null;
    }

    /** Number of selected people from the button label "Send to new group chat (N)", or -1. */
    private int selectedCount() {
        AccessibilityNodeInfo node = findGroupNode();
        if (node == null) return -1;
        StringBuilder text = new StringBuilder();
        collectText(node, text, 0);
        Matcher m = COUNT.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static void collectText(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 3) return;
        if (n.getText() != null) out.append(n.getText()).append(' ');
        for (int i = 0; i < n.getChildCount(); i++) collectText(n.getChild(i), out, depth + 1);
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
            banner.setText(MainActivity.askFirst(this) ? R.string.banner_ask : R.string.banner_block);
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
                        if (MainActivity.askFirst(GroupGuardService.this)) {
                            showConfirm(selectedCount());
                        } else {
                            Toast.makeText(GroupGuardService.this, R.string.toast_blocked,
                                    Toast.LENGTH_SHORT).show();
                        }
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
        dismissConfirm();
        if (allowedThisSheet) {
            allowedThisSheet = false; // the sheet closed: the next share asks again
            Log.i(TAG, "confirm reset");
        }
        removeWindow();
    }

    /** Removes the banner window only (keeps the "allowed for this sheet" state). */
    private void removeWindow() {
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

    // ---- "Ask me first": our own confirmation popup ----

    private void showConfirm(int count) {
        if (confirm != null) return;
        float dp = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * dp);

        TextView title = new TextView(this);
        title.setText(R.string.confirm_title);
        title.setTextSize(20);
        title.setTextColor(0xFF1B1F23);
        TextView body = new TextView(this);
        body.setText(count > 0 ? getString(R.string.confirm_body_count, count) : getString(R.string.confirm_body));
        body.setTextSize(16);
        body.setTextColor(0xFF3A3F45);
        body.setPadding(0, Math.round(8 * dp), 0, Math.round(16 * dp));

        final Button no = new Button(this);
        no.setText(R.string.confirm_no);
        no.setAllCaps(false);
        final Button yes = new Button(this);
        yes.setText(R.string.confirm_yes);
        yes.setAllCaps(false);
        no.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "confirm no");
                dismissConfirm();
            }
        });
        yes.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.i(TAG, "confirm yes");
                dismissConfirm();
                allowedThisSheet = true;
                removeWindow(); // Instagram's own button is now visible and tappable
                Toast.makeText(GroupGuardService.this, R.string.toast_allowed, Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.addView(no);
        buttons.addView(yes);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(pad, pad, pad, Math.round(12 * dp));
        card.setClickable(true); // taps on the card itself do not count as "outside"
        card.addView(title);
        card.addView(body);
        card.addView(buttons);

        confirm = new FrameLayout(this);
        confirm.setBackgroundColor(0x99000000); // dim Instagram behind the popup
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { // tap outside the card = cancel
                Log.i(TAG, "confirm no (outside)");
                dismissConfirm();
            }
        });
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cardLp.leftMargin = cardLp.rightMargin = Math.round(28 * dp);
        confirm.addView(card, cardLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(confirm, lp);
        } catch (WindowManager.BadTokenException e) {
            confirm = null;
            return;
        }
        final int c = count;
        // Logged for test/e2e.py, which taps the buttons by these screen bounds.
        confirm.post(new Runnable() {
            @Override public void run() {
                Log.i(TAG, "confirm shown count=" + (c > 0 ? String.valueOf(c) : "?")
                        + " yes=" + screenRect(yes).flattenToString()
                        + " no=" + screenRect(no).flattenToString());
            }
        });
    }

    private static Rect screenRect(View v) {
        int[] xy = new int[2];
        v.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + v.getWidth(), xy[1] + v.getHeight());
    }

    private void dismissConfirm() {
        if (confirm == null) return;
        try {
            wm.removeView(confirm);
        } catch (IllegalArgumentException e) {
            // already detached
        }
        confirm = null;
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
