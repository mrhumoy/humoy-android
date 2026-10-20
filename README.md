# Humoy for Android

Humoy is an unofficial Telegram client for Android, built on the open source code of
[Telegram for Android](https://github.com/DrKLO/Telegram). It adds features such as ghost mode,
kept deleted and edited messages, several accounts with hidden accounts, locked chats, a hidden
section, contact change history, folder tabs and more, while staying close to Telegram's own design.

This repository holds the source code of the Humoy app, published under the GNU GPL v2 as Telegram's
license requires.

## Building

1. Get your own `api_id` and `api_hash` at [my.telegram.org](https://my.telegram.org) and put them
   into `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`.
2. Create your own Firebase project and replace the `google-services.json` files (needed for push
   notifications).
3. Humoy's server features (announcements, the Humoy chat, update prompts) need a server of your own:
   set its address and key in `TMessagesProj/src/main/java/org/telegram/messenger/HumoyServer.java`.
   Without one, the app works as a Telegram client and those features stay empty.
4. Release builds are signed with your own key. Put it into your user `gradle.properties`
   (`HUMOY_STORE_FILE`, `HUMOY_STORE_PASSWORD`, `HUMOY_KEY_ALIAS`, `HUMOY_KEY_PASSWORD`); without it
   the sample key from Telegram's repository is used.
5. Build with Android Studio, or: `./gradlew :TMessagesProj_App:assembleAfatRelease`.

## Name and logo

The source code is free under the GPL, the Humoy name and logo are not. If you publish an app built
from this code, give it a different name and icon, so that people don't mistake it for Humoy.

## License

GNU General Public License v2.0 — see [LICENSE](LICENSE). Telegram is a trademark of Telegram FZ-LLC;
Humoy is not affiliated with Telegram.

---

# Humoy Android uchun

Humoy — [Telegram for Android](https://github.com/DrKLO/Telegram) ochiq manba kodi asosida yaratilgan
norasmiy Telegram mijozi. Bu repozitoriyda Humoy ilovasining manba kodi GNU GPL v2 litsenziyasi
asosida e'lon qilingan.

Kod GPL asosida erkin, lekin Humoy nomi va logotipi emas: shu koddan ilova chiqarsangiz, unga boshqa
nom va belgi bering.

Yangiliklar: [@humoymessenger](https://t.me/humoymessenger)
