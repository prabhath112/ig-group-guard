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

    // While the banner is shown, re-check this often. Needed because Android 8 sends no event when a
    // view goes GONE, and because the service only listens to Instagram, so leaving Instagram is
    // noticed by this re-check (worst case ~300 ms of banner over the next app).
    private static final long RECHECK_MS = 300;
    private static final long FADE_MS = 80;
    private static final long SETTLE_MS = 70; // unchanged this long = stopped (> 30-40 ms update gap)

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recheck = new Runnable() {
        @Override public void run() { refresh(); }
    };
    // Instagram fires accessibility events in bursts (video playback, list thumbnails loading). Each
    // refresh() does a full getWindows() + node-tree walk, so running one per event saturates the main
    // thread: CPU spikes and the refresh that should place the banner backs up behind the burst (banner
    // arrives late). Coalesce instead -- a burst collapses to one scan on the next loop turn.
    private boolean refreshScheduled;
    private final Runnable refreshNow = new Runnable() {
        @Override public void run() { refreshScheduled = false; refresh(); }
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
    private boolean opening;          // true from the button's first sighting until it first settles
    private int openingFromTop;       // where the button was first seen in this opening
    private long graceUntil;          // keep re-checking until then after the button vanished
    // GRACE_MS is also how long we keep fast-polling (GRACE_STEP_MS) after the group button vanishes,
    // which is exactly when the keyboard opens on the share sheet. Long enough to span typing a message,
    // so closing the keyboard re-covers the button within ~50 ms. Scoped to a share sheet that was just
    // open (grace only arms after the button was tracked), so general scrolling/comments pay nothing.
    private static final long GRACE_MS = 6000, GRACE_STEP_MS = 50, WATCH_STEP_MS = 120;

    // Set while the service is bound, so the Quick Settings tile and the app can pause it.
    private static volatile GroupGuardService running;

    static boolean isRunning() { return running != null; }

    /** Turns the service off (as in Settings), e.g. so a banking app that refuses to run while an
     *  accessibility service is on can open. Turned back on from Settings or the volume-key shortcut. */
    static boolean pause() {
        GroupGuardService s = running;
        if (s == null) return false;
        Log.i(TAG, "paused by user");
        s.hide();
        s.disableSelf();
        return true;
    }

    private FrameLayout confirm;      // our own "Create a group chat?" popup (Ask me first mode)
    private boolean allowedThisSheet; // user said "Create group": leave the button alone until the sheet closes
    private static final Pattern COUNT = Pattern.compile("\\((\\d+)\\)"); // "... (3)"

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        running = this;
        home = MainActivity.savedHome(this); // remembered across restarts (e.g. Pause for banking)
        GuardTile.refresh(this);
        Log.i(TAG, "connected sdk=" + Build.VERSION.SDK_INT);
        refresh(); // share sheet may already be open when the service is turned on
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (refreshScheduled) return; // a burst of events collapses to one refresh (see refreshNow)
        refreshScheduled = true;
        handler.post(refreshNow);
    }

    private void refresh() {
        handler.removeCallbacks(recheck);
        choreographer.removeFrameCallback(follow);
        // Active only while Instagram is the foreground app. The moment it is not, tear everything
        // down and sleep: no overlay, no timers, no polling, until Instagram comes back. The service
        // stays enabled (it is not hiding from anything); it just does no work outside Instagram.
        Integer fg = foregroundIsInstagram();
        if (fg != null && fg == 0) {
            if (window != null || !lastButton.isEmpty()) Log.i(TAG, "hibernate (left Instagram)");
            graceUntil = 0;
            hide();
            return;
        }
        // Play policy: no use of the Accessibility API before the in-app disclosure is accepted.
        Rect button = MainActivity.hasConsent(this) ? findGroupButton() : null;
        if (button == null) {
            if (window != null || !lastButton.isEmpty()) {
                // The button can vanish for a moment while the keyboard closes. Instagram is still in
                // front (checked above), but may send nothing more, so keep looking for a short while.
                graceUntil = SystemClock.uptimeMillis() + GRACE_MS;
            }
            hide();
            if (SystemClock.uptimeMillis() < graceUntil) {
                handler.postDelayed(recheck, GRACE_STEP_MS);
            } else if (fg != null && fg == 1) {
                // Instagram is foreground but the share sheet is not up yet. Being event-driven, we
                // would otherwise wait for Instagram's next event (~0.9 s on a fresh open, measured on
                // CPH2707) and the real group button stays tappable that whole time. Poll gently while
                // Instagram is in front so the sheet is covered within WATCH_STEP_MS. Stops the moment
                // Instagram is not foreground (hibernate above), so it is bounded, not a global poll.
                handler.postDelayed(recheck, WATCH_STEP_MS);
            }
            return;
        }
        graceUntil = 0;
        if (allowedThisSheet) {
            // Confirmed for this sheet: keep Instagram's button uncovered; keep checking so the
            // permission ends as soon as the sheet (and the button) goes away.
            removeWindow();
            handler.postDelayed(recheck, RECHECK_MS);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastButton.isEmpty()) { // first sighting: maybe the share sheet sliding in
            opening = true;
            openingFromTop = button.top;
        }
        int prevTop = lastButton.isEmpty() ? button.top : lastButton.top;
        if (lastButton.isEmpty() || !button.equals(lastButton)) movedAt = now;
        lastButton.set(button);
        // Instagram's position updates arrive every 30-40 ms but we check every 16 ms frame, so two
        // equal readings in a row do not mean it stopped. Treating them as "stopped" faded the banner
        // in mid-slide and could record a mid-slide spot as home (stutter on first use).
        boolean moving = now - movedAt < SETTLE_MS;
        if (moving) {
            choreographer.postFrameCallback(follow); // check again next frame until it stops
        } else if (opening) {
            // Home is where the button rests when the sheet has just opened. Only the opening slide
            // may set it: keyboard-up positions overwrote it before, and when the keyboard closed the
            // banner sat at that stale spot for ~1.2 s (screen recording, CPH2707, 2026-11-06).
            opening = false;
            // Learn home only from a real slide-in (button rose from below), not from a sheet that was
            // already open (service restarted) or one with the keyboard up: that stored the keyboard-up
            // spot and the next opening showed the banner there (log, CPH2707, 2026-11-06 15:55).
            boolean slidIn = openingFromTop - button.top > button.height() / 2;
            if (slidIn && (home == null || !home.equals(button))) {
                home = new Rect(button);
                MainActivity.saveHome(this, home);
                Log.i(TAG, "home " + home.flattenToString());
            }
        }

        // Sliding up into the remembered spot (opening slide only): banner stands still at home,
        // window reaches down to the button so the moving button is covered too. Any other movement
        // (keyboard, scrolling) takes the invisible-follow path below and fades in once it stops.
        // Height is not compared: the button first shows up clipped by the screen edge (measured:
        // 42 px, then 95 px, then full 131 px), and waiting for full height cost ~100 ms.
        boolean sameColumn = home != null && button.left == home.left && button.right == home.right;
        boolean towardsHome = opening && moving && sameColumn && button.top >= home.top;
        // Keyboard closing: the button comes back DOWN to home. Home is reliable now (only learned from
        // the opening slide), so show the banner there at once instead of waiting for it to settle.
        boolean backDownHome = !opening && moving && sameColumn && button.top < home.top
                && button.top >= prevTop;
        Rect bounds = button;
        boolean bannerAtBottom = false;
        if (towardsHome) {
            // Only ever grow during the slide: resizing the window every frame made it flicker.
            int bottom = Math.max(home.bottom, button.bottom);
            if (window != null && shown.top == home.top) bottom = Math.max(bottom, shown.bottom);
            bounds = new Rect(home.left, home.top, home.right, bottom);
        } else if (backDownHome) {
            // Window from the moving button down to home; banner sits at its bottom (= home).
            int top = button.top;
            if (window != null && shown.bottom == home.bottom) top = Math.min(top, shown.top);
            bounds = new Rect(home.left, top, home.right, home.bottom);
            bannerAtBottom = true;
        }
        int bannerHeight = towardsHome || backDownHome ? home.height() : button.height();
        place(bounds, bannerHeight, bannerAtBottom);
        if (banner == null) return; // window could not be added (service shutting down)
        String label = getString(MainActivity.askFirst(this) ? R.string.banner_ask : R.string.banner_block);
        if (!label.contentEquals(banner.getText())) banner.setText(label);

        if (towardsHome || backDownHome) {
            showNow(); // known final spot: appear at once, standing still; the button moves into it
        } else if (!moving && !opening) {
            showNow(); // settled after a keyboard/scroll move: no fade, appear at once
        } else {
            // Standing still after the opening slide: fade in. Moving with no known spot (first open
            // ever): stay invisible but keep blocking taps, so the user never sees it step.
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

    /**
     * 1 = Instagram is the active app, 0 = a known other app is, null = unknown (keep current state).
     * The overlay is a window of ours, so it is ignored. Null on uncertainty so a brief blank frame
     * never tears down a working overlay (the keyboard-close case keeps its grace path).
     */
    private Integer foregroundIsInstagram() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return null;
        if (INSTAGRAM.contentEquals(pkg)) return 1;
        if (getPackageName().contentEquals(pkg)) return null; // our own overlay window; not a signal
        return 0;
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
        // Cheap path first: the share sheet is a modal in Instagram's own active window, so the button
        // is almost always in getRootInActiveWindow() (one tree to walk). getWindows() builds and walks
        // EVERY interactive window and is much heavier (worse with a video-call PiP up): only pay it as
        // a fallback when the button is not in the active window. This is the main CPU/latency saver.
        AccessibilityNodeInfo n = findIn(getRootInActiveWindow());
        if (n != null) return n;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;
        for (AccessibilityWindowInfo w : windows) {
            n = findIn(w != null ? w.getRoot() : null);
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
        place(r, bannerHeight, false);
    }

    private void place(Rect r, int bannerHeight, boolean bannerAtBottom) {
        int gravity = bannerAtBottom ? Gravity.BOTTOM : Gravity.TOP;
        if (window != null && r.equals(shown) && banner.getLayoutParams().height == bannerHeight
                && ((FrameLayout.LayoutParams) banner.getLayoutParams()).gravity == gravity) return;
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
            window.addView(banner, new FrameLayout.LayoutParams(-1, bannerHeight, gravity));
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
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) banner.getLayoutParams();
            blp.height = bannerHeight;
            blp.gravity = gravity;
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
    public boolean onUnbind(android.content.Intent intent) {
        running = null;
        GuardTile.refresh(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        running = null;
        graceUntil = 0;
        hide();
        super.onDestroy();
    }
}
