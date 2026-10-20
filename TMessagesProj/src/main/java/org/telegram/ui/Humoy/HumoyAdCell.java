/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HumoyAds;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

/**
 * A Humoy ad in the chat list, in one of two looks: "top" sits under the pinned chats like a chat row
 * with an "Ad" label; "inline" sits among the chats on a soft background, with an "AD" mark, an open
 * button and an (i) to hide it.
 */
public class HumoyAdCell extends FrameLayout {

    private final String style;
    private final BackupImageView avatar;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final TextView title;
    private final TextView text;
    private final int account;
    private TextView openButton;
    private int adId;
    private String link;
    private String name = "";

    public HumoyAdCell(Context context, int account, String style) {
        super(context);
        this.account = account;
        this.style = style;
        boolean inline = HumoyAds.STYLE_INLINE.equals(style);
        setWillNotDraw(false);
        if (inline) {
            setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.035f));
            setForeground(Theme.getSelectorDrawable(false));
        } else {
            setBackground(Theme.getSelectorDrawable(false));
        }

        avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(28));
        addView(avatar, LayoutHelper.createFrame(56, 56, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_chats_name));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 13, inline ? 104 : 84, 0));

        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        text.setTextColor(Theme.getColor(Theme.key_chats_message));
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 39, inline ? 104 : 16, 0));

        if (inline) {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            openButton = new TextView(context);
            openButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            openButton.setTypeface(AndroidUtilities.bold());
            openButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            openButton.setGravity(Gravity.CENTER);
            openButton.setSingleLine(true);
            openButton.setPadding(dp(14), 0, dp(14), 0);
            openButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), accent, Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_buttonText), 0.2f)));
            openButton.setOnClickListener(v -> open());
            addView(openButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        } else {
            TextView tag = new TextView(context);
            tag.setText(HumoyStrings.get("AdLabel"));
            tag.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            int gray = Theme.getColor(Theme.key_chats_message);
            tag.setTextColor(gray);
            tag.setPadding(dp(6), dp(1), dp(6), dp(1));
            tag.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(gray, 0.15f)));
            addView(tag, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 14, 12, 0));
        }
    }

    public void open() {
        if (!TextUtils.isEmpty(link)) {
            HumoyAds.onClick(account, adId, style);
            Browser.openUrl(LaunchActivity.instance != null ? LaunchActivity.instance : getContext(), link);
        }
    }

    /** Swiped away in the list. */
    public void dismiss() {
        HumoyAds.onClose(adId, account, style);
    }

    public void showOptions(View anchor) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return;
        }
        ItemOptions.makeOptions(fragment, anchor)
                .add(R.drawable.msg_cancel, HumoyStrings.get("AdHide"), () -> HumoyAds.onClose(adId, account, style))
                .setGravity(Gravity.RIGHT)
                .show();
    }

    public void set(JSONObject ad) {
        if (ad == null) {
            return;
        }
        adId = ad.optInt("id");
        link = ad.optString("link").trim();
        if (!link.isEmpty() && !link.contains("://")) {
            link = "https://" + link;
        }
        name = ad.optString("title");
        if (openButton != null) {
            // a quiet "AD ·" before the name, in the color of the message line
            SpannableStringBuilder sb = new SpannableStringBuilder("AD · " + name);
            sb.setSpan(new android.text.style.ForegroundColorSpan(Theme.multAlpha(Theme.getColor(Theme.key_chats_message), 0.75f)), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.75f), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setText(sb);
            String buttonText = ad.optString("button");
            openButton.setText((TextUtils.isEmpty(buttonText) ? HumoyStrings.get("AdOpen") : buttonText).toUpperCase());
        } else {
            title.setText(name);
        }
        text.setText(ad.optString("text"));
        avatarDrawable.setInfo(adId, name, null);
        String image = ad.optString("image");
        if (org.telegram.messenger.HumoyServer.isImageUrl(image)) {
            avatar.setImage(image, "56_56", avatarDrawable);
        } else {
            avatar.setImageDrawable(avatarDrawable);
        }
        HumoyAds.onShown(account, adId);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(72), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (openButton == null) {
            canvas.drawLine(dp(78), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
