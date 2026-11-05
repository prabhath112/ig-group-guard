package io.github.prabhath112.iggroupguard.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.instagram.android.R;

/**
 * Minimal copy of Instagram's multi-select share sheet footer.
 * Extra "mode": "id" (default, real view ids) or "textonly" (group button has no id,
 * simulates Instagram renaming it, so the service must fall back to the label text).
 * Every click is logged as "FIXTURE_CLICK <name>" for test/e2e.py.
 */
public class ShareSheetActivity extends Activity {

    private static final String TAG = "IgFixture";
    private View spacer;
    private LinearLayout group;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean textOnly = "textonly".equals(getIntent().getStringExtra("mode"));
        float dp = getResources().getDisplayMetrics().density;

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Large top/bottom padding keeps the controls clear of status/nav bars on edge-to-edge (API 35+).
        final int base = (int) (64 * dp);
        root.setPadding((int) (16 * dp), base, (int) (16 * dp), base);

        Button toggle = button("Toggle group button", R.id.fixture_toggle, "toggle");
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                log("toggle");
                group.setVisibility(group.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        Button move = button("Move buttons up", R.id.fixture_move, "move");
        move.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                log("move"); // like the keyboard opening: footer jumps up
                int bottom = root.getPaddingBottom() == base ? base * 5 : base;
                root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), bottom);
            }
        });
        root.addView(toggle);
        root.addView(move);

        spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button separate = button("Send separately", R.id.direct_send_button_multi_select, "separate");
        root.addView(separate);

        group = new LinearLayout(this);
        if (!textOnly) group.setId(R.id.direct_send_to_group_button_vertical);
        group.setClickable(true);
        group.setMinimumHeight((int) (56 * dp));
        group.setGravity(android.view.Gravity.CENTER);
        group.setBackgroundColor(0xFF222222);
        TextView label = new TextView(this);
        label.setId(R.id.button_text);
        label.setText("Send to new group chat (3)");
        label.setTextColor(0xFFFFFFFF);
        group.addView(label);
        group.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { log("group"); }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = (int) (12 * dp);
        root.addView(group, gp);

        setContentView(root);
        Log.i(TAG, "FIXTURE_READY mode=" + (textOnly ? "textonly" : "id"));
    }

    private Button button(String text, int id, final String name) {
        Button btn = new Button(this);
        btn.setId(id);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { log(name); }
        });
        return btn;
    }

    private static void log(String what) {
        Log.i(TAG, "FIXTURE_CLICK " + what);
    }
}
