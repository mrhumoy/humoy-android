package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    // Humoy: eight Humoy icons in different colours, all free (the aliases of Telegram's own icons now show these)
    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.humoy_icon_background, R.drawable.humoy_icon_foreground, "IconClassic"),
        AQUA("AquaIcon", R.drawable.humoy_icon_bg_ocean, R.drawable.humoy_icon_fg_ocean, "IconOcean"),
        NOX("NoxIcon", R.drawable.humoy_icon_bg_night, R.drawable.humoy_icon_fg_night, "IconNight"),
        VINTAGE("VintageIcon", R.drawable.humoy_icon_bg_dark, R.drawable.humoy_icon_fg_dark, "IconDark"),
        PREMIUM("PremiumIcon", R.drawable.humoy_icon_bg_premium, R.drawable.humoy_icon_fg_premium, "IconPremium"),
        TURBO("TurboIcon", R.drawable.humoy_icon_bg_gold, R.drawable.humoy_icon_fg_gold, "IconGold"),
        NEON("NeonIcon", R.drawable.humoy_icon_bg_neon, R.drawable.humoy_icon_fg_neon, "IconNeon"),
        MATERIAL("MaterialIcon", R.drawable.humoy_icon_bg_material, R.drawable.humoy_icon_fg_material, "IconMaterial");

        public final String key;
        public final int background;
        public final int foreground;
        public final String title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, String title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, String title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
