/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.HumoyPin;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

/**
 * Asks for one of Humoy's PINs (4 digits; or a fingerprint), or creates it (entered twice). On success
 * runs the callback and closes; the callback usually presents the screen the PIN guards.
 */
public class HumoyPinActivity extends BaseFragment {

    public static final int MODE_CHECK = 0;
    public static final int MODE_CREATE = 1;

    private static final int LENGTH = 4;

    private final int scope;
    private final int mode;
    private final Runnable onSuccess;
    private final StringBuilder entered = new StringBuilder();
    private String firstEntry;

    private TextView titleView;
    private TextView subtitleView;
    private DotsView dotsView;

    public HumoyPinActivity(int mode, Runnable onSuccess) {
        this(HumoyPin.SCOPE_ACCOUNTS, mode, onSuccess);
    }

    public HumoyPinActivity(int scope, int mode, Runnable onSuccess) {
        this.scope = scope;
        this.mode = mode;
        this.onSuccess = onSuccess;
    }

    private String scopeKey() {
        return scope == HumoyPin.SCOPE_LOCKED ? "Locked" : scope == HumoyPin.SCOPE_HIDDEN ? "Hidden" : "";
    }

    private boolean canUseFingerprint() {
        if (mode != MODE_CHECK || !HumoyPin.useFingerprint(scope) || android.os.Build.VERSION.SDK_INT < 23) {
            return false;
        }
        try {
            return BiometricManager.from(ApplicationLoader.applicationContext).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable e) {
            return false;
        }
    }

    private void askFingerprint() {
        if (!canUseFingerprint() || LaunchActivity.instance == null || isFinishing()) {
            return;
        }
        try {
            BiometricPrompt prompt = new BiometricPrompt(LaunchActivity.instance, ContextCompat.getMainExecutor(LaunchActivity.instance), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    done();
                }
            });
            prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(HumoyStrings.get("PinEnter"))
                    .setNegativeButtonText(HumoyStrings.get("PinUseCode"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && !backward) {
            askFingerprint();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(HumoyStrings.get(scope == HumoyPin.SCOPE_LOCKED ? "LockedChats" : scope == HumoyPin.SCOPE_HIDDEN ? "HiddenSection" : "HiddenAccounts"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        fragmentView = layout;

        FrameLayout lockFrame = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF5592D6, 0xFF3576C0});
        circle.setShape(GradientDrawable.OVAL);
        lockFrame.setBackground(circle);
        ImageView lock = new ImageView(context);
        lock.setImageResource(R.drawable.msg_secret);
        lock.setColorFilter(0xFFFFFFFF);
        lock.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lock.setPadding(dp(14), dp(14), dp(14), dp(14));
        lockFrame.addView(lock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        layout.addView(lockFrame, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 18, 24, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setLineSpacing(dp(2), 1f);
        layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 6, 32, 0));

        dotsView = new DotsView(context);
        layout.addView(dotsView, LayoutHelper.createLinear(LENGTH * 28, 20, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        View spacer = new View(context);
        layout.addView(spacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        GridLayout keypad = new GridLayout(context);
        keypad.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<"};
        String[] letters = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            View button;
            if (i == 9 && canUseFingerprint()) {
                button = createFingerprintKey(context);
            } else {
                button = key.isEmpty() ? new View(context) : createKey(context, key, letters[i]);
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(60);
            params.height = dp(60);
            params.setMargins(dp(14), dp(8), dp(14), dp(8));
            keypad.addView(button, params);
        }
        layout.addView(keypad, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 36));

        updateTexts(false);
        return fragmentView;
    }

    private View createFingerprintKey(Context context) {
        FrameLayout button = new FrameLayout(context);
        int base = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0, Theme.multAlpha(base, 0.16f)));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.fingerprint);
        icon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        button.addView(icon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        button.setOnClickListener(v -> askFingerprint());
        return button;
    }

    private View createKey(Context context, String key, String letters) {
        FrameLayout button = new FrameLayout(context);
        int base = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), Theme.multAlpha(base, 0.06f), Theme.multAlpha(base, 0.16f)));
        if ("<".equals(key)) {
            ImageView back = new ImageView(context);
            back.setImageResource(R.drawable.msg_clear_input);
            back.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            back.setScaleType(ImageView.ScaleType.CENTER);
            button.addView(back, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            button.setOnClickListener(v -> {
                if (entered.length() > 0) {
                    entered.setLength(entered.length() - 1);
                    dotsView.invalidate();
                }
            });
            button.setOnLongClickListener(v -> {
                entered.setLength(0);
                dotsView.invalidate();
                return true;
            });
            return button;
        }
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER);
        TextView digit = new TextView(context);
        digit.setText(key);
        digit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        digit.setTextColor(base);
        digit.setGravity(Gravity.CENTER);
        texts.addView(digit, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        if (!letters.isEmpty()) {
            TextView small = new TextView(context);
            small.setText(letters);
            small.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            small.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            small.setGravity(Gravity.CENTER);
            texts.addView(small, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, -4, 0, 0));
        }
        button.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        button.setOnClickListener(v -> onDigit(key));
        return button;
    }

    private void onDigit(String digit) {
        if (entered.length() >= LENGTH) {
            return;
        }
        entered.append(digit);
        dotsView.invalidate();
        if (entered.length() == LENGTH) {
            AndroidUtilities.runOnUIThread(this::onComplete, 120);
        }
    }

    private void onComplete() {
        String pin = entered.toString();
        if (mode == MODE_CHECK) {
            if (HumoyPin.checkPin(scope, pin)) {
                done();
            } else {
                wrong(HumoyStrings.get("PinWrong"));
            }
        } else if (firstEntry == null) {
            firstEntry = pin;
            entered.setLength(0);
            dotsView.invalidate();
            updateTexts(true);
        } else if (firstEntry.equals(pin)) {
            HumoyPin.setPin(scope, pin);
            done();
        } else {
            firstEntry = null;
            updateTexts(true);
            wrong(HumoyStrings.get("PinMismatch"));
        }
    }

    private boolean doneCalled;
    private BaseFragment next;

    /** The screen shown in place of this one once the PIN is right. */
    public HumoyPinActivity setNext(BaseFragment next) {
        this.next = next;
        return this;
    }

    private void done() {
        if (doneCalled) {
            return;
        }
        doneCalled = true;
        AndroidUtilities.hideKeyboard(fragmentView);
        if (next != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            presentFragment(next, true);
            return;
        }
        finishFragment();
        if (onSuccess != null) {
            AndroidUtilities.runOnUIThread(onSuccess, 200);
        }
    }

    private void wrong(String message) {
        entered.setLength(0);
        dotsView.invalidate();
        subtitleView.setText(message);
        subtitleView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        try {
            dotsView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
        AndroidUtilities.shakeViewSpring(dotsView, 6);
    }

    private void updateTexts(boolean animated) {
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        if (mode == MODE_CHECK) {
            titleView.setText(HumoyStrings.get("PinEnter"));
            subtitleView.setText(HumoyStrings.get("PinEnterInfo" + scopeKey()));
        } else if (firstEntry == null) {
            titleView.setText(HumoyStrings.get("PinCreate"));
            subtitleView.setText(HumoyStrings.get("PinCreateInfo" + scopeKey()));
        } else {
            titleView.setText(HumoyStrings.get("PinRepeat"));
            subtitleView.setText(HumoyStrings.get("PinRepeatInfo"));
        }
    }

    private class DotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DotsView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float step = getMeasuredWidth() / (float) LENGTH;
            float cy = getMeasuredHeight() / 2f;
            for (int i = 0; i < LENGTH; i++) {
                float cx = step * i + step / 2f;
                boolean filled = i < entered.length();
                paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.5f));
                paint.setColor(filled ? getThemedColor(Theme.key_featuredStickers_addButton) : getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                canvas.drawCircle(cx, cy, dp(filled ? 6 : 5.5f), paint);
            }
        }
    }
}
