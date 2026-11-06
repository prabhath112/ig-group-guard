package io.github.prabhath112.iggroupguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Two screens in one activity:
 * 1. Prominent disclosure + consent (Google Play Accessibility API policy). Shown on every open
 *    until the user taps Agree. The service does nothing without consent.
 * 2. Status / setup screen: what happens on a tap (Block it / Ask me first), privacy policy link
 *    and a way to withdraw consent.
 */
public class MainActivity extends Activity {

    static final String PRIVACY_URL = "https://ig-group-guard.vercel.app/privacy-policy";
    private static final String PREFS = "groupgate";
    private static final String KEY_CONSENT = "consent";
    private static final String KEY_ASK_FIRST = "ask_first"; // false (default) = Block it

    static boolean hasConsent(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CONSENT, false);
    }

    private static final String KEY_HOME = "home"; // the group button's resting spot on screen

    /** Where the group button rests when the share sheet opens, or null (one screen rectangle). */
    static android.graphics.Rect savedHome(Context c) {
        String v = c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HOME, null);
        return v == null ? null : android.graphics.Rect.unflattenFromString(v);
    }

    static void saveHome(Context c, android.graphics.Rect r) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HOME, r.flattenToString()).apply();
    }

    /** "Ask me first" chosen; default is "Block it". */
    static boolean askFirst(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ASK_FIRST, false);
    }

    private void setAskFirst(boolean value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ASK_FIRST, value).apply();
    }

    private void setConsent(boolean value) {
        if (value) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, true).apply();
        } else {
            // "Withdraw and delete": consent, the Block/Ask choice and the button position are all it stores.
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasConsent(this)) showStatus(); else showDisclosure(false);
    }

    private void showDisclosure(boolean declined) {
        LinearLayout box = column();
        box.addView(text(R.string.disclosure_title, 22));
        box.addView(text(R.string.disclosure_body, 16));
        if (declined) box.addView(text(R.string.disclosure_declined, 16));
        box.addView(button(R.string.agree, new View.OnClickListener() {
            @Override public void onClick(View v) {
                setConsent(true);
                showStatus();
            }
        }));
        box.addView(button(R.string.decline, new View.OnClickListener() {
            @Override public void onClick(View v) { showDisclosure(true); }
        }));
        box.addView(link(R.string.privacy_policy, PRIVACY_URL));
        setContentView(scroll(box));
    }

    private void showStatus() {
        LinearLayout box = column();
        boolean running = isServiceEnabled();
        box.addView(text(running ? R.string.status_on : R.string.status_off, 22));
        if (!running) box.addView(text(R.string.status_off_hint, 16));
        box.addView(text(R.string.what_it_does, 16));
        box.addView(modeChooser());
        box.addView(text(R.string.how_to_enable, 16));
        box.addView(button(R.string.open_settings, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        box.addView(text(R.string.bank_title, 18));
        box.addView(text(R.string.bank_help, 14));
        if (running) {
            box.addView(button(R.string.pause_button, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (GroupGuardService.pause()) {
                        android.widget.Toast.makeText(MainActivity.this, R.string.paused_toast,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                    showStatus();
                }
            }));
        }
        box.addView(link(R.string.privacy_policy, PRIVACY_URL));
        box.addView(button(R.string.withdraw, new View.OnClickListener() {
            @Override public void onClick(View v) {
                setConsent(false);
                showDisclosure(false);
            }
        }));
        box.addView(text(R.string.not_affiliated, 13));
        setContentView(scroll(box));
    }

    /**
     * True only if the service is actually running. The Settings flag alone is not enough: after a
     * force-stop (or an OEM battery killer) Android 11 keeps the flag on but does not re-bind.
     */
    private boolean isServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info
                : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) return true;
        }
        return false;
    }

    /** "When I tap Send to new group chat": Block it (default) / Ask me first. */
    private View modeChooser() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(R.string.mode_title, 18);
        title.setPadding(0, 0, 0, 0);
        box.addView(title);
        RadioGroup group = new RadioGroup(this);
        RadioButton block = new RadioButton(this);
        block.setId(View.generateViewId());
        block.setText(R.string.mode_block);
        RadioButton ask = new RadioButton(this);
        ask.setId(View.generateViewId());
        ask.setText(R.string.mode_ask);
        group.addView(block);
        group.addView(ask);
        group.check(askFirst(this) ? ask.getId() : block.getId());
        final int askId = ask.getId();
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                setAskFirst(checkedId == askId);
            }
        });
        box.addView(group);
        box.addView(text(R.string.mode_help, 14));
        return box;
    }

    // ---- tiny view helpers (no layout XML needed for two plain screens) ----

    private LinearLayout column() {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad * 2, pad, pad);
        return box;
    }

    private ScrollView scroll(View child) {
        ScrollView s = new ScrollView(this);
        s.addView(child);
        return s;
    }

    private TextView text(int res, int sp) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(sp);
        t.setPadding(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        return t;
    }

    private Button button(int res, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(res);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private Button link(int res, final String url) {
        return button(res, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }
}
