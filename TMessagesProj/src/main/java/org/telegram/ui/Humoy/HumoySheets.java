/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Humoy's server sheets: an announcement, and a new version that downloads and installs in the app. */
public class HumoySheets {

    private static LinearLayout sheetLayout(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(16));
        return layout;
    }

    private static TextView button(Context context, String text, boolean primary) {
        TextView b = new TextView(context);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        b.setTypeface(AndroidUtilities.bold());
        if (primary) {
            b.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            b.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 10));
        } else {
            b.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            b.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        }
        return b;
    }

    /** "- point" lines become rows with a check mark; other lines stay text. */
    private static void addText(Context context, LinearLayout layout, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        StringBuilder plain = new StringBuilder();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith("- ") || t.startsWith("• ")) {
                if (plain.length() > 0) {
                    layout.addView(body(context, plain.toString().trim()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
                    plain.setLength(0);
                }
                LinearLayout row = new LinearLayout(context);
                row.setGravity(Gravity.TOP);
                ImageView icon = new ImageView(context);
                icon.setImageResource(R.drawable.msg_select);
                icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton));
                row.addView(icon, LayoutHelper.createLinear(22, 22, 0, 0, 10, 0));
                row.addView(body(context, t.substring(2).trim()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                layout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
            } else {
                plain.append(line).append('\n');
            }
        }
        if (plain.toString().trim().length() > 0) {
            layout.addView(body(context, plain.toString().trim()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
    }

    private static TextView body(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private static TextView title(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    // ---- announcement

    public static boolean showAnnouncement(BaseFragment fragment, JSONObject announcement) {
        Context context = fragment.getParentActivity();
        LinearLayout layout = sheetLayout(context);

        FrameLayout art = new FrameLayout(context);
        art.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), 0.12f)));
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.logo_middle);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.addView(logo, LayoutHelper.createFrame(76, 76, Gravity.CENTER));
        layout.addView(art, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 120, 0, 4, 0, 16));

        layout.addView(title(context, announcement.optString("title")), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        addText(context, layout, announcement.optString("text"));

        BottomSheet[] sheet = new BottomSheet[1];
        String buttonText = announcement.optString("button");
        String link = announcement.optString("link").trim();
        boolean hasLink = !TextUtils.isEmpty(buttonText) && !TextUtils.isEmpty(link);
        TextView main = button(context, hasLink ? buttonText : HumoyStrings.get("AnnounceOk"), true);
        main.setOnClickListener(v -> {
            if (hasLink) {
                Browser.openUrl(context, link.contains("://") ? link : "https://" + link);
            }
            sheet[0].dismiss();
        });
        layout.addView(main, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));
        if (hasLink) {
            TextView close = button(context, HumoyStrings.get("AnnounceClose"), false);
            close.setOnClickListener(v -> sheet[0].dismiss());
            layout.addView(close, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 4, 0, 0));
        }
        sheet[0] = new BottomSheet.Builder(context).setCustomView(layout).create();
        return fragment.showDialog(sheet[0]) != null;
    }

    // ---- update

    public static BottomSheet showUpdate(BaseFragment fragment, String version, String changelog, String url, boolean forced, Runnable onLater) {
        Activity activity = fragment.getParentActivity();
        LinearLayout layout = sheetLayout(activity);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.logo_middle);
        head.addView(logo, LayoutHelper.createLinear(56, 56, 0, 0, 14, 0));
        LinearLayout names = new LinearLayout(activity);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(activity);
        name.setText("Humoy " + version);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        names.addView(name);
        TextView sub = new TextView(activity);
        sub.setText(HumoyStrings.get(forced ? "UpdateRequiredShort" : "UpdateNewVersion"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sub.setTextColor(Theme.getColor(forced ? Theme.key_text_RedRegular : Theme.key_dialogTextGray3));
        names.addView(sub);
        head.addView(names);
        layout.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 16));

        if (forced) {
            layout.addView(body(activity, HumoyStrings.get("UpdateRequiredText")), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        }
        addText(activity, layout, changelog);

        View track = new View(activity);
        View bar = new View(activity);
        FrameLayout progress = new FrameLayout(activity);
        progress.setBackground(Theme.createRoundRectDrawable(dp(3), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.08f)));
        bar.setBackground(Theme.createRoundRectDrawable(dp(3), Theme.getColor(Theme.key_featuredStickers_addButton)));
        bar.setPivotX(0);
        bar.setScaleX(0);
        progress.addView(bar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        progress.setVisibility(View.GONE);
        layout.addView(progress, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6, 0, 6, 0, 4));
        TextView status = new TextView(activity);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        status.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        status.setVisibility(View.GONE);
        layout.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView install = button(activity, HumoyStrings.get("UpdateInstall"), true);
        layout.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));

        BottomSheet sheet = new BottomSheet.Builder(activity).setCustomView(layout).create();
        if (!forced) {
            TextView later = button(activity, HumoyStrings.get("UpdateLater"), false);
            later.setOnClickListener(v -> {
                if (onLater != null) {
                    onLater.run();
                }
                sheet.dismiss();
            });
            layout.addView(later, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 4, 0, 0));
        } else {
            sheet.setCanDismissWithSwipe(false);
            sheet.setCancelable(false);
        }

        File file = new File(new File(ApplicationLoader.applicationContext.getFilesDir(), "cache"), "humoy-update-" + version + ".apk");
        boolean[] busy = new boolean[1];
        install.setOnClickListener(v -> {
            if (busy[0]) {
                return;
            }
            if (org.telegram.messenger.ApplicationLoader.isPlayStoreBuild() || url.contains("play.google.com") || url.startsWith("market://")) {
                // a Play Market build is updated by the store, never by downloading an APK
                try {
                    String id = url.contains("play.google.com") ? Uri.parse(url).getQueryParameter("id") : activity.getPackageName();
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(id != null ? "market://details?id=" + id : url)));
                } catch (Exception e) {
                    Browser.openUrl(activity, url);
                }
                return;
            }
            if (file.exists() && file.length() > 0) {
                installApk(activity, file);
                return;
            }
            busy[0] = true;
            progress.setVisibility(View.VISIBLE);
            status.setVisibility(View.VISIBLE);
            status.setText(HumoyStrings.get("UpdateDownloading"));
            install.setAlpha(0.6f);
            Utilities.globalQueue.postRunnable(() -> {
                boolean ok = download(url, file, (done, total) -> AndroidUtilities.runOnUIThread(() -> {
                    float p = total > 0 ? done / (float) total : 0;
                    bar.setScaleX(p);
                    status.setText(String.format(HumoyStrings.get("UpdateDownloadingPercent"), Math.round(p * 100), AndroidUtilities.formatFileSize(total)));
                }));
                AndroidUtilities.runOnUIThread(() -> {
                    busy[0] = false;
                    install.setAlpha(1f);
                    if (ok) {
                        bar.setScaleX(1);
                        status.setText(HumoyStrings.get("UpdateReady"));
                        installApk(activity, file);
                    } else {
                        status.setText(HumoyStrings.get("UpdateFailed"));
                        // let the browser try
                        Browser.openUrl(activity, url);
                    }
                });
            });
        });
        if (forced) {
            return fragment.showDialog(sheet) != null ? sheet : null;
        }
        // put away in any way (swipe, back, outside): it waits a day, like "Later"
        return fragment.showDialog(sheet, d -> {
            if (onLater != null) {
                onLater.run();
            }
        }) != null ? sheet : null;
    }

    private interface Progress {
        void onProgress(long done, long total);
    }

    private static boolean download(String url, File file, Progress progress) {
        HttpURLConnection connection = null;
        File part = new File(file.getPath() + ".part");
        try {
            file.getParentFile().mkdirs();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            if (connection.getResponseCode() != 200) {
                return false;
            }
            long total = connection.getContentLengthLong();
            long done = 0;
            long lastReport = 0;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    done += n;
                    if (done - lastReport > 256 * 1024 || done == total) {
                        lastReport = done;
                        progress.onProgress(done, total);
                    }
                }
            }
            return part.renameTo(file);
        } catch (Exception e) {
            FileLog.e(e);
            part.delete();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void installApk(Activity activity, File file) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                // the phone asks once to allow installing from Humoy; then "Install" again
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
