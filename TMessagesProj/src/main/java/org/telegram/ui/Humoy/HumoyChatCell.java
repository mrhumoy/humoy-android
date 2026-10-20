/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HumoyChat;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

/** The verified "Humoy" chat's row in the chat list. */
public class HumoyChatCell extends FrameLayout {

    private final TextView time;
    private final TextView text;
    private final TextView counter;
    private org.telegram.ui.Components.CheckBox2 checkBox;

    private final int account;

    public HumoyChatCell(Context context, int account) {
        super(context);
        this.account = account;
        setWillNotDraw(false);
        setBackground(Theme.getSelectorDrawable(false));

        ImageView avatar = new ImageView(context);
        avatar.setImageResource(R.drawable.logo_middle);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(avatar, LayoutHelper.createFrame(56, 56, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        name.setText("Humoy");
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_chats_name));
        nameRow.addView(name);
        if (Theme.dialogs_verifiedDrawable != null && Theme.dialogs_verifiedCheckDrawable != null) {
            ImageView verified = new ImageView(context);
            verified.setImageDrawable(new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable));
            nameRow.addView(verified, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 1, 0, 0));
        }
        addView(nameRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 12, 70, 0));

        time = new TextView(context);
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        time.setTextColor(Theme.getColor(Theme.key_chats_date));
        addView(time, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 14, 14, 0));

        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        text.setTextColor(Theme.getColor(Theme.key_chats_message));
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 39, 54, 0));

        counter = new TextView(context);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        counter.setTypeface(AndroidUtilities.bold());
        counter.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));
        counter.setGravity(Gravity.CENTER);
        counter.setMinWidth(dp(23));
        counter.setPadding(dp(7), 0, dp(7), 0);
        counter.setBackground(Theme.createRoundRectDrawable(dp(11.5f), Theme.getColor(Theme.key_chats_unreadCounter)));
        addView(counter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, Gravity.RIGHT | Gravity.TOP, 0, 39, 12, 0));

    }

    public void update() {
        int date = HumoyChat.lastDate(account);
        time.setText(date == 0 ? "" : LocaleController.stringForMessageListDate(date));
        text.setText(HumoyChat.preview(account));
        int unread = HumoyChat.unreadCount(account);
        counter.setText(String.valueOf(unread));
        counter.setVisibility(unread > 0 ? VISIBLE : GONE);
    }

    /** Selected in the chat list, with the same check on the avatar as other chats. */
    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null && !checked) {
            return;
        }
        if (checkBox == null) {
            checkBox = new org.telegram.ui.Components.CheckBox2(getContext(), 21);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.TOP, 44, 42, 0, 0));
        }
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(72), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawLine(dp(78), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }
}
