/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import java.util.HashMap;

/**
 * Texts for Humoy-only screens. Telegram's language packs come from its servers and know nothing
 * about these keys, so Humoy keeps its own small dictionary per app language.
 */
public class HumoyStrings {

    private static final HashMap<String, String> UZ = new HashMap<>();
    private static final HashMap<String, String> RU = new HashMap<>();
    private static final HashMap<String, String> EN = new HashMap<>();

    private static void put(String key, String uz, String ru, String en) {
        UZ.put(key, uz);
        RU.put(key, ru);
        EN.put(key, en);
    }

    static {
        put("AppName", "Humoy", "Humoy", "Humoy");
        put("HumoySettings", "Humoy sozlamalari", "Настройки Humoy", "Humoy Settings");
        put("HumoySettingsInfo", "Ghost rejimi va boshqa imkoniyatlar", "Режим призрака и другие функции", "Ghost mode and more");
        put("HumoySections", "Bo'limlar", "Разделы", "Sections");
        put("HumoyAbout", "Telegram asosidagi mustaqil mijoz: ghost rejimi, o'chirilgan xabarlar va o'chib ketuvchi media'ni saqlash.",
                "Независимый клиент на основе Telegram: режим призрака, сохранение удалённых сообщений и исчезающих медиа.",
                "An independent Telegram-based client: ghost mode, keeping deleted messages and self-destructing media.");
        put("HumoyBasedOn", "Telegram %s asosida", "На основе Telegram %s", "Based on Telegram %s");
        put("ContactChanges", "Kontaktlar o'zgarishlari", "Изменения контактов", "Contact changes");
        put("CcSwitchView", "Ko'rinishni almashtirish", "Сменить вид", "Switch view");
        put("CcFilter", "Filtr", "Фильтр", "Filter");
        put("CcAll", "Barcha o'zgarishlar", "Все изменения", "All changes");
        put("CcPhoto", "Rasmini o'zgartirdi", "Сменил фото", "Changed photo");
        put("CcPhotoRemoved", "Rasmini o'chirdi", "Удалил фото", "Removed photo");
        put("CcPhone", "Telefon raqamini o'zgartirdi", "Сменил номер", "Changed phone number");
        put("CcName", "Ismini o'zgartirdi", "Сменил имя", "Changed name");
        put("CcUsername", "Username'ini o'zgartirdi", "Сменил имя пользователя", "Changed username");
        put("CcBlocked", "Bloklandi", "Заблокирован", "Blocked");
        put("CcUnblocked", "Blokdan chiqarildi", "Разблокирован", "Unblocked");
        put("CcPhotoNew", "Yangi rasm qo'ydi", "Поставил новое фото", "Set a new photo");
        put("CcPhotoRemovedInfo", "Profil rasmini olib tashladi", "Убрал фото профиля", "Took the profile photo down");
        put("CcPhotoOldSaved", "Eski rasm saqlandi, ko'rish uchun bosing", "Старое фото сохранено, нажмите, чтобы посмотреть", "The old photo was kept, tap to see it");
        put("CcBlockedInfo", "Siz uni bloklagansiz", "Вы его заблокировали", "You blocked them");
        put("CcUnblockedInfo", "Siz uni blokdan chiqardingiz", "Вы его разблокировали", "You unblocked them");
        put("CcNoUsername", "(username yo'q)", "(нет имени пользователя)", "(no username)");
        put("CcCount", "O'zgarishlar: %d", "Изменений: %d", "Changes: %d");
        put("CcEmpty", "O'zgargan kontaktlar yo'q", "Изменений пока нет", "No contact changes yet");
        put("CcEmptyInfo", "Kimdir ismini, username'ini, raqamini yoki rasmini o'zgartirsa, shu yerda ko'rinadi. Kuzatish Humoy o'rnatilgandan keyin boshlanadi.", "Когда кто-то сменит имя, имя пользователя, номер или фото, это появится здесь. Отслеживание начинается после установки Humoy.", "When someone changes their name, username, number or photo, it shows up here. Watching starts once Humoy is installed.");
        put("CcEmptyOff", "Kontakt o'zgarishlarini saqlash o'chirilgan. Uni ⋮ → Sozlamalar → Kontakt turi orqali yoqing.", "Сохранение изменений контактов выключено. Включите его в ⋮ → Настройки → Тип контактов.", "Keeping contact changes is off. Turn it on in ⋮ → Settings → Contact type.");
        put("CcClearAll", "Barcha o'zgarishlarni o'chirish", "Удалить все изменения", "Delete all changes");
        put("CcClearAllConfirm", "Barcha kontaktlar o'zgarishlari tarixi telefoningizdan o'chirilsinmi?", "Удалить с телефона всю историю изменений контактов?", "Delete the whole contact change history from your phone?");
        put("CcClearPerson", "Bu odam tarixini o'chirish", "Удалить историю этого человека", "Delete this person's history");
        put("CcClearPersonConfirm", "Bu odamning o'zgarishlar tarixi o'chirilsinmi?", "Удалить историю изменений этого человека?", "Delete this person's change history?");
        put("CcSettings", "Kontakt o'zgarishlari sozlamalari", "Настройки изменений контактов", "Contact changes settings");
        put("CcSettingsHeader", "Sozlamalar", "Настройки", "Settings");
        put("CcUnseenFirst", "Ko'rilmaganlar birinchi", "Непросмотренные сверху", "Unseen first");
        put("CcDefaultView", "Ochilganda ko'rsatish", "Показывать при открытии", "Show when opened");
        put("CcViewPeople", "O'zgargan odamlar", "Изменившиеся люди", "People who changed");
        put("CcViewAll", "Barcha o'zgarishlar", "Все изменения", "All changes");
        put("CcSettingsInfo", "Ko'rilmagan o'zgarishi bor odamlar ro'yxat tepasida turadi. Sahifa odamlar ro'yxati yoki barcha o'zgarishlar bilan ochiladi.", "Люди с непросмотренными изменениями стоят наверху. Страница открывается списком людей или всеми изменениями.", "People with unseen changes are on top. The page opens on the people list or on all changes.");
        put("CcTypesHeader", "Saqlanadigan o'zgarishlar", "Что сохранять", "What to keep");
        put("CcTypesInfo", "Faqat belgilangan o'zgarishlar saqlanadi.", "Сохраняются только отмеченные изменения.", "Only the ticked kinds of changes are kept.");
        put("CcScopeHeader", "Kimlar kuzatiladi", "Чьи изменения", "Whose changes");
        put("CcScope", "Kontakt turi", "Тип контактов", "Contact type");
        put("CcScopeAll", "Barcha foydalanuvchilar", "Все пользователи", "All users");
        put("CcScopeContacts", "Qurilmadagi barcha kontaktlar", "Все контакты на устройстве", "All contacts on this device");
        put("CcScopeMutual", "O'zaro kontaktlar", "Взаимные контакты", "Mutual contacts");
        put("CcScopeCustom", "Maxsus kontaktlar", "Выбранные контакты", "Chosen contacts");
        put("CcScopeNone", "Kontakt o'zgarishlarini saqlamaslik", "Не сохранять изменения", "Don't keep contact changes");
        put("CcCustomPeople", "Tanlangan odamlar", "Выбранные люди", "Chosen people");
        put("CcScopeInfo", "Faqat shu turdagi odamlarning o'zgarishlari saqlanadi. Oxirgi variant tanlansa, hech narsa saqlanmaydi. Hammasi faqat telefoningizda turadi.", "Сохраняются изменения только этих людей. Если выбрать последний вариант, ничего не сохраняется. Всё хранится только на телефоне.", "Only these people's changes are kept. With the last option nothing is kept. Everything stays on your phone.");
        put("OnlineContacts", "Onlayn kontaktlar", "Контакты в сети", "Online contacts");
        put("OnlineNowHeader", "Hozir onlayn · %d", "Сейчас в сети · %d", "Online now · %d");
        put("OnlineRecentHeader", "Yaqinda bo'lganlar (oxirgi 1 soat)", "Были недавно (за последний час)", "Recently online (last hour)");
        put("OnlineSubtitle", "Onlayn: %d", "В сети: %d", "Online: %d");
        put("OnlineEmpty", "Hozir hech kim onlayn emas", "Сейчас никого нет в сети", "No one is online right now");
        put("OnlineEmptyInfo", "Kontaktlaringizdan kimdir Telegram'ga kirsa, shu yerda paydo bo'ladi. «Oxirgi marta ko'rilgan» vaqtini yashirgan odamlar ko'rinmaydi.", "Когда кто-то из контактов зайдёт в Telegram, он появится здесь. Те, кто скрыл время последнего визита, не видны.", "When one of your contacts opens Telegram, they show up here. People who hide their last seen time don't.");
        put("IconClassic", "Klassik", "Классика", "Classic");
        put("IconOcean", "Okean", "Океан", "Ocean");
        put("IconNight", "Tun", "Ночь", "Night");
        put("IconDark", "Qorong'u", "Тёмная", "Dark");
        put("IconPremium", "Premium", "Премиум", "Premium");
        put("IconGold", "Oltin", "Золото", "Gold");
        put("IconNeon", "Neon", "Неон", "Neon");
        put("IconMaterial", "Material", "Material", "Material");
        put("GhostBeforeStory", "Story'ni ko'rishdan oldin **ghost rejimi** yoqilsinmi? Yoqilsa, muallif story'ni ko'rganingizni bilmaydi.", "Включить **режим призрака** перед просмотром истории? Тогда автор не узнает, что вы её смотрели.", "Turn on **ghost mode** before viewing the story? Then the author won't know you saw it.");
        put("GhostBeforeStoryYes", "Ha, yoqilsin", "Да, включить", "Yes");
        put("GhostBeforeStoryNo", "Yo'q", "Нет", "No");
        put("AskGhostBeforeStory", "Story oldidan so'rash", "Спрашивать перед историей", "Ask before stories");
        put("AskGhostBeforeStoryInfo", "Ghost rejimi o'chiq paytda birovning story'sini ochsangiz, avval ghost rejimini yoqish-yoqmasligi so'raladi.", "Если режим призрака выключен, перед открытием чужой истории появится вопрос, включить ли его.", "With ghost mode off, opening someone's story first asks whether to turn it on.");
        put("KeepLocally", "Telefonda saqlab qolish", "Оставить на телефоне", "Keep on this phone");
        put("ManageAccounts", "Akkauntlarni boshqarish", "Управление аккаунтами", "Manage accounts");
        put("ManageAccountsInfo", "Tartibini o'zgartirish uchun akkauntni bosib turib suring. ⚙️ orqali akkauntni asosiy qilish, nomini o'zgartirish, yashirish yoki undan chiqish mumkin. Yashirilgan akkauntlar ro'yxatlarda ko'rinmaydi, xabarlari esa kelib turadi; ularni ochish uchun «Hisob qo'shish»ni bosib turing.", "Чтобы изменить порядок, зажмите аккаунт и перетащите. Через ⚙️ аккаунт можно сделать основным, переименовать, скрыть или выйти из него. Скрытый аккаунт не показывается в списках переключения, но сообщения приходят.", "Long-press and drag an account to reorder. With ⚙️ you can make it main, rename it, hide it or log out. A hidden account isn't listed in account switchers, but its messages still arrive.");
        put("AccountMakeMain", "Asosiy akkaunt qilish", "Сделать основным", "Make main account");
        put("AccountRename", "Akkaunt nomini o'zgartirish", "Переименовать аккаунт", "Rename account");
        put("AccountRenameInfo", "Bu nom faqat sizning telefoningizdagi akkauntlar ro'yxatida ko'rinadi. Bo'sh qoldirsangiz, akkauntning o'z nomi chiqadi.", "Это имя видно только в списке аккаунтов на вашем телефоне. Оставьте пустым, чтобы показывать имя аккаунта.", "This name shows only in the account lists on your phone. Leave it empty to show the account's own name.");
        put("AccountHide", "Yashirish", "Скрыть", "Hide");
        put("AccountUnhide", "Ko'rsatish", "Показать", "Show");
        put("AccountLogout", "Chiqish", "Выйти", "Log out");
        put("AccountLogoutConfirm", "«%s» akkauntidan chiqilsinmi?", "Выйти из аккаунта «%s»?", "Log out of “%s”?");
        put("AccountCurrent", "hozirgi", "текущий", "current");
        put("AccountHidden", "yashirilgan", "скрыт", "hidden");
        put("AccountAdd", "Hisob qo'shish", "Добавить аккаунт", "Add account");
        put("KeepStoryViewers", "Story ko'rganlarini saqlash", "Сохранять зрителей историй", "Keep story viewers");
        put("KeepStoryViewersInfo", "Telegram story ko'rganlarining to'liq ro'yxatini 2 kundan keyin faqat Premium'ga ko'rsatadi. Humoy ro'yxatni shu muddat ichida telefoningizga yozib boradi va keyin ham ko'rsatadi.", "Полный список зрителей истории Telegram показывает через 2 дня только с Premium. Humoy записывает список на телефон в это время и показывает его и потом.", "After 2 days Telegram shows the full list of a story's viewers only with Premium. Humoy writes the list down on your phone meanwhile and shows it afterwards too.");
        put("HiddenAccounts", "Yashirilgan akkauntlar", "Скрытые аккаунты", "Hidden accounts");
        put("HiddenAccountsInfo", "Bu akkauntlar ro'yxatlarda ko'rinmaydi. Ularga faqat «Hisob qo'shish»ni bosib turib, PIN-kod bilan kiriladi.", "Эти аккаунты не видны в списках. Открыть их можно, зажав «Добавить аккаунт» и введя PIN-код.", "These accounts don't show in the lists. Open them by long-pressing Add account and entering the PIN.");
        put("HiddenAccountsEmpty", "Yashirilgan akkaunt yo'q", "Скрытых аккаунтов нет", "No hidden accounts");
        put("HiddenAccountsNone", "Yashirilgan akkaunt yo'q. Akkauntni «Akkauntlarni boshqarish» → ⚙️ → «Yashirish» orqali yashirish mumkin.", "Скрытых аккаунтов нет. Скрыть аккаунт можно в «Управление аккаунтами» → ⚙️ → «Скрыть».", "No hidden accounts. Hide one in Manage accounts → ⚙️ → Hide.");
        put("AccountHiddenDone", "Akkaunt yashirildi. Ko'rish uchun «Hisob qo'shish»ni bosib turing.", "Аккаунт скрыт. Чтобы увидеть, зажмите «Добавить аккаунт».", "Account hidden. Long-press Add account to see it.");
        put("AccountOpen", "Akkauntga kirish", "Открыть аккаунт", "Open account");
        put("PinCreate", "PIN-kod yarating", "Придумайте PIN-код", "Create a PIN");
        put("PinCreateInfo", "Yashirilgan akkauntlarni ochish uchun 4 xonali PIN-kod. Uni eslab qoling.", "4-значный PIN-код для открытия скрытых аккаунтов. Запомните его.", "A 4-digit PIN to open the hidden accounts. Remember it.");
        put("PinRepeat", "PIN-kodni takrorlang", "Повторите PIN-код", "Repeat the PIN");
        put("PinRepeatInfo", "Tasdiqlash uchun yana bir marta kiriting.", "Введите ещё раз для подтверждения.", "Enter it once more to confirm.");
        put("PinEnter", "PIN-kodni kiriting", "Введите PIN-код", "Enter the PIN");
        put("PinEnterInfo", "Yashirilgan akkauntlarni ochish uchun.", "Чтобы открыть скрытые аккаунты.", "To open the hidden accounts.");
        put("PinWrong", "PIN-kod noto'g'ri", "Неверный PIN-код", "Wrong PIN");
        put("PinMismatch", "PIN-kodlar mos kelmadi. Qaytadan yarating.", "PIN-коды не совпали. Придумайте заново.", "The PINs didn't match. Create it again.");
        put("PinChange", "PIN-kodni o'zgartirish", "Сменить PIN-код", "Change PIN");
        put("PinChanged", "PIN-kod o'zgartirildi", "PIN-код изменён", "PIN changed");
        put("HumoyPrivacy", "Maxfiylik", "Приватность", "Privacy");
        put("LockedChats", "Qulflangan chatlar", "Закрытые чаты", "Locked chats");
        put("HiddenSection", "Yashirin bo'lim", "Скрытый раздел", "Hidden section");
        put("HiddenSectionOpen", "Yashirin bo'limni ochish", "Открыть скрытый раздел", "Open the hidden section");
        put("HiddenAccountsOpen", "Yashirilgan akkauntlarni ochish", "Открыть скрытые аккаунты", "Open hidden accounts");
        put("PinSet", "PIN-kod o'rnatish", "Установить PIN-код", "Set a PIN");
        put("PinUseCode", "PIN-kod", "PIN-код", "Use PIN");
        put("UseFingerprint", "Barmoq izi bilan ochish", "Открывать отпечатком пальца", "Unlock with fingerprint");
        put("HiddenNotify", "Bildirishnomalarni ko'rsatish", "Показывать уведомления", "Show notifications");
        put("TurnOff", "O'chirish", "Отключить", "Turn off");
        put("LockedChatsOff", "Qulflangan chatlarni o'chirish", "Отключить закрытые чаты", "Turn off locked chats");
        put("LockedChatsOffConfirm", "Barcha chatlar qulfdan chiqariladi va PIN-kod o'chiriladi.", "Все чаты будут открыты, PIN-код удалён.", "All chats will be unlocked and the PIN removed.");
        put("HiddenSectionOff", "Yashirin bo'limni o'chirish", "Отключить скрытый раздел", "Turn off the hidden section");
        put("HiddenSectionOffConfirm", "Barcha yashirin chatlar asosiy ro'yxatga qaytadi va PIN-kod o'chiriladi.", "Все скрытые чаты вернутся в общий список, PIN-код будет удалён.", "All hidden chats return to the main list and the PIN is removed.");
        put("PinEnterInfoLocked", "Chatni ochish uchun.", "Чтобы открыть чат.", "To open the chat.");
        put("PinEnterInfoHidden", "Yashirin bo'limni ochish uchun.", "Чтобы открыть скрытый раздел.", "To open the hidden section.");
        put("PinCreateInfoLocked", "Qulflangan chatlarni ochish uchun 4 xonali PIN-kod. Uni eslab qoling.", "4-значный PIN-код для закрытых чатов. Запомните его.", "A 4-digit PIN to open locked chats. Remember it.");
        put("PinCreateInfoHidden", "Yashirin bo'limni ochish uchun 4 xonali PIN-kod. Uni eslab qoling.", "4-значный PIN-код для скрытого раздела. Запомните его.", "A 4-digit PIN to open the hidden section. Remember it.");
        put("ChatLock", "Chatni qulflash", "Закрыть чат", "Lock chat");
        put("ChatUnlock", "Qulfni olib tashlash", "Снять блокировку", "Unlock chat");
        put("ChatLocked", "Chat qulflandi: endi u PIN-kod bilan ochiladi", "Чат закрыт: теперь он открывается по PIN-коду", "Chat locked: it now opens with the PIN");
        put("ChatUnlocked", "Chat qulfi olib tashlandi", "Блокировка снята", "Chat unlocked");
        put("ChatHide", "Yashirish", "Скрыть", "Hide");
        put("ChatUnhide", "Yashirin bo'limdan chiqarish", "Вернуть из скрытых", "Unhide");
        put("ChatHiddenDone", "Yashirildi. Ko'rish uchun yangi xabar tugmasini bosib turing.", "Скрыто. Чтобы увидеть, зажмите кнопку нового сообщения.", "Hidden. Long-press the new message button to see it.");
        put("ChatUnhiddenDone", "Asosiy ro'yxatga qaytarildi", "Возвращено в общий список", "Back in the main list");
        put("ContactsFilter", "Kontaktlar filtri", "Фильтр контактов", "Contacts filter");
        put("ContactsFilterAll", "Barcha", "Все", "All");
        put("ContactsFilterMutual", "O'zaro kontaktlar", "Взаимные контакты", "Mutual contacts");
        put("ContactsFilterNotMutual", "O'zaro bo'lmagan kontaktlar", "Невзаимные контакты", "Non-mutual contacts");
        put("ShowMutualContacts", "O'zaro kontakt belgisi", "Значок взаимного контакта", "Mutual contact sign");
        put("ShowMutualContactsInfo", "Sizni ham kontaktiga qo'shgan odamlar yonida belgi ko'rsatiladi.", "Значок рядом с теми, у кого вы тоже в контактах.", "A sign next to people who have you in their contacts too.");
        put("PinEnterCode", "Kirish kodingizni kiriting", "Введите код доступа", "Enter your passcode");
        put("MembersFilter", "A'zolar filtri", "Фильтр участников", "Members filter");
        put("MembersFilterAdmins", "Administratorlar", "Администраторы", "Administrators");
        put("MembersFilterBots", "Botlar", "Боты", "Bots");
        put("MembersFilterContacts", "Kontaktlar", "Контакты", "Contacts");
        put("MembersFilterPremium", "Premium a'zolar", "Premium участники", "Premium members");
        put("HumoyVersion", "Android uchun Humoy %1$s", "Humoy для Android %1$s", "Humoy for Android %1$s");
        put("AdLabel", "Reklama", "Реклама", "Ad");
        put("UpdateTitle", "Humoy %1$s chiqdi", "Вышел Humoy %1$s", "Humoy %1$s is out");
        put("UpdateRequiredTitle", "Humoy %1$s ga yangilang", "Обновите Humoy до %1$s", "Update to Humoy %1$s");
        put("UpdateRequiredText", "Bu versiya endi ishlamaydi. Davom etish uchun Humoy'ni yangilang.", "Эта версия больше не работает. Обновите Humoy, чтобы продолжить.", "This version no longer works. Update Humoy to continue.");
        put("UpdateNow", "Yangilash", "Обновить", "Update");
        put("UpdateLater", "Keyinroq", "Позже", "Later");
        put("AdHide", "Reklamani yashirish", "Скрыть рекламу", "Hide this ad");
        put("AdOpen", "Ochish", "Открыть", "Open");
        put("HumoyChatSubtitle", "rasmiy xabarlar", "официальные сообщения", "official messages");
        put("HumoyChatEmpty", "Hali xabar yo'q", "Сообщений пока нет", "No messages yet");
        put("AnnounceOk", "Tushunarli", "Понятно", "Got it");
        put("AnnounceClose", "Yopish", "Закрыть", "Close");
        put("UpdateNewVersion", "Yangi versiya", "Новая версия", "New version");
        put("UpdateRequiredShort", "Yangilash shart", "Требуется обновление", "Update required");
        put("UpdateInstall", "O'rnatish", "Установить", "Install");
        put("UpdateDownloading", "Yuklanmoqda…", "Загрузка…", "Downloading…");
        put("UpdateDownloadingPercent", "Yuklanmoqda · %1$d%% · %2$s", "Загрузка · %1$d%% · %2$s", "Downloading · %1$d%% · %2$s");
        put("UpdateReady", "Yuklandi. O'rnatish oynasi ochilmoqda", "Загружено. Открывается установка", "Downloaded. Opening the installer");
        put("UpdateFailed", "Yuklab bo'lmadi, brauzer orqali ochilmoqda", "Не удалось загрузить, открываем в браузере", "Couldn't download, opening in the browser");
        put("HumoyChatClear", "Tarixni tozalash", "Очистить историю", "Clear history");
        put("HumoyChatClearConfirm", "Humoy chatidagi barcha xabarlar shu telefondan o'chiriladi.", "Все сообщения чата Humoy будут удалены с этого телефона.", "All Humoy chat messages will be deleted from this phone.");
        put("HumoyChatDelete", "Chatni o'chirish", "Удалить чат", "Delete chat");
        put("HumoyChatDeleteConfirm", "Humoy chatini o'chirmoqchimisiz? Yangi xabar kelganda u qaytadan paydo bo'ladi.", "Удалить чат Humoy? Он вернётся, когда придёт новое сообщение.", "Delete the Humoy chat? It comes back with the next new message.");
        put("HumoyPrivacyPolicy", "Maxfiylik siyosati", "Политика конфиденциальности", "Privacy policy");
        put("CacheCleaner", "Keshni tozalash vositasi", "Очистка кэша", "Cache cleaner");
        put("HumoyChannelRow", "Humoy kanali", "Канал Humoy", "Humoy channel");
        put("PhoneFinder", "Raqam qidiruvchisi", "Поиск по номеру", "Phone finder");
        put("IdFinder", "ID qidiruvchisi", "Поиск по ID", "ID finder");
        put("PhoneFinderTitle", "Raqam orqali topish", "Найти по номеру", "Find by phone number");
        put("PhoneFinderInfo", "Telefon raqamini kiriting: unga bog'langan Telegram akkaunt topiladi. Egasi raqami orqali topishni yopib qo'ygan bo'lsa, topilmaydi.",
                "Введите номер телефона, и найдётся привязанный к нему аккаунт Telegram. Если владелец запретил находить себя по номеру, он не найдётся.",
                "Enter a phone number to find the Telegram account linked to it. Accounts whose owners don't allow finding them by number won't show up.");
        put("PhoneFinderField", "Telefon raqami", "Номер телефона", "Phone number");
        put("PhoneFinderShort", "Raqam to'liq emas. Davlat kodi bilan birga kiriting, masalan 998 90 123 45 67.", "Номер неполный. Введите его с кодом страны, например 998 90 123 45 67.", "The number is incomplete. Enter it with the country code, e.g. 998 90 123 45 67.");
        put("PhoneFinderNotFound", "Bu raqam bilan Telegram akkaunt topilmadi. Raqam noto'g'ri, ro'yxatdan o'tmagan yoki egasi raqami orqali topishni yopib qo'ygan.",
                "Аккаунт Telegram с этим номером не найден. Номер неверный, не зарегистрирован или владелец запретил находить себя по номеру.",
                "No Telegram account found for this number. It's wrong, not registered, or its owner doesn't allow finding them by number.");
        put("IdFinderTitle", "ID yoki username orqali topish", "Найти по ID или имени пользователя", "Find by ID or username");
        put("IdFinderInfo", "Raqamli ID, @username yoki t.me havolasini kiriting. Odamlar, guruhlar, kanallar va botlar topiladi.",
                "Введите числовой ID, @имя или ссылку t.me. Находит людей, группы, каналы и ботов.",
                "Enter a numeric ID, @username or t.me link. Finds people, groups, channels and bots.");
        put("IdFinderField", "ID, @username yoki havola", "ID, @имя или ссылка", "ID, @username or link");
        put("IdFinderBadInput", "Raqamli ID (masalan 1794830140), @username yoki t.me havolasini kiriting.", "Введите числовой ID (например 1794830140), @имя или ссылку t.me.", "Enter a numeric ID (e.g. 1794830140), an @username or a t.me link.");
        put("IdFinderNotFound", "Bu ID topilmadi. Telegram begona odamni faqat ID orqali topishga ruxsat bermaydi: telefoningiz avval ko'rgan odamlar (kontaktlar, umumiy guruh a'zolari, yozishganlar) topiladi. Uning @username'i bo'lsa, shuni kiriting.",
                "ID не найден. Telegram не позволяет находить незнакомых людей только по ID: находятся те, кого телефон уже видел (контакты, участники общих групп, собеседники). Если у человека есть @имя, введите его.",
                "This ID wasn't found. Telegram doesn't let you find a stranger by ID alone: only people your phone has already seen (contacts, members of shared groups, people you've chatted with) are found. If they have an @username, enter that.");
        put("IdFinderUsernameNotFound", "Bunday username topilmadi.", "Такое имя пользователя не найдено.", "No such username.");
        put("FinderSearch", "Qidirish", "Найти", "Search");
        put("FinderFlood", "Juda ko'p qidirdingiz. Birozdan keyin qayta urinib ko'ring.", "Слишком много запросов. Попробуйте немного позже.", "Too many searches. Try again a bit later.");
        put("FinderMyId", "Sizning ID'ingiz", "Ваш ID", "Your ID");
        put("FinderIdCopied", "ID nusxalandi", "ID скопирован", "ID copied");
        put("FinderUsername", "Username", "Имя пользователя", "Username");
        put("FinderPhone", "Telefon", "Телефон", "Phone");
        put("FinderId", "ID · nusxalash uchun bosing", "ID · нажмите, чтобы скопировать", "ID · tap to copy");
        put("FinderBot", "bot", "бот", "bot");
        put("FinderChannel", "kanal", "канал", "channel");
        put("FinderGroup", "guruh", "группа", "group");
        put("FinderWrite", "Xabar yozish", "Написать", "Message");
        put("FinderOpen", "Ochish", "Открыть", "Open");
        put("FinderProfile", "Profil", "Профиль", "Profile");
        put("SectionSaving", "Saqlash", "Сохранение", "Saving");
        put("SectionSending", "Tasdiqlashlar", "Подтверждения", "Confirmations");
        put("HumoyLinks", "Havolalar", "Ссылки", "Links");
        put("HumoyChannel", "Kanal", "Канал", "Channel");
        put("HumoySourceCode", "Manba kodi", "Исходный код", "Source code");
        put("GhostMode", "Ghost rejimi", "Режим призрака", "Ghost Mode");
        put("GhostModeHeader", "Ghost asoslari", "Основное", "Ghost essentials");
        put("GhostDontReadMessages", "Xabarlarni o'qilgan qilmaslik", "Не читать сообщения", "Don't read messages");
        put("GhostDontReadStories", "Storylarni ko'rilgan qilmaslik", "Не просматривать истории", "Don't read stories");
        put("GhostDontSendOnline", "Online ko'rinmaslik", "Не показывать «в сети»", "Don't send online");
        put("GhostDontSendTyping", "«Yozmoqda...» ni ko'rsatmaslik", "Не показывать «печатает...»", "Don't send typing");
        put("GhostGoOffline", "Avtomatik offline bo'lish", "Автоматически выходить из сети", "Go offline automatically");
        put("GhostModeInfo", "Ghost rejimi yoqilganda tanlangan narsalar boshqalarga yuborilmaydi. Ro'yxatni ochish uchun nom ustiga bosing.",
                "Когда режим призрака включён, выбранное не отправляется другим. Нажмите на название, чтобы раскрыть список.",
                "When ghost mode is on, the selected things aren't sent to others. Tap the title to expand the list.");
        put("GhostReadOnInteract", "Javob yozganda o'qish", "Читать при ответе", "Read on interact");
        put("GhostReadOnInteractInfo", "Chatga xabar yozsangiz yoki reaksiya bossangiz, o'sha chat o'qilgan bo'ladi.",
                "Если вы напишете в чат или поставите реакцию, этот чат будет отмечен прочитанным.",
                "Writing to a chat or reacting in it marks that chat as read.");
        put("FolderAll", "Barcha chatlar", "Все чаты", "All chats");
        put("FolderPersonal", "Shaxsiy", "Личные", "Personal");
        put("FolderGroups", "Guruhlar", "Группы", "Groups");
        put("FolderChannels", "Kanallar", "Каналы", "Channels");
        put("FolderBots", "Botlar", "Боты", "Bots");
        put("FolderTabs", "Chat jildlari", "Папки чатов", "Chat folders");
        put("FolderTabsHeader", "Ko'rinadigan jildlar", "Показываемые папки", "Shown folders");
        put("FolderTabsInfo", "Bu jildlar faqat shu akkauntda va faqat Humoy'da. Tartibini o'zgartirish uchun jildni bosib turing va \"Tartibni o'zgartirish\" ni tanlang.",
                "Эти папки есть только в этом аккаунте и только в Humoy. Чтобы изменить порядок, зажмите папку и выберите «Изменить порядок».",
                "These folders exist only in this account and only in Humoy. To reorder, long-press a folder and choose Reorder.");
        put("FolderHide", "Jildni yashirish", "Скрыть папку", "Hide folder");
        put("ShowGhostButton", "Asosiy ekranda arvoh tugmasi", "Кнопка призрака на главном экране", "Ghost button on main screen");
        put("ShowGhostButtonInfo", "O'chirilsa, ghost rejimini faqat shu sahifadan yoqish mumkin bo'ladi.",
                "Если выключить, режим призрака можно будет включать только на этой странице.",
                "When off, ghost mode can be turned on only from this page.");
        put("ConfirmHeader", "Yuborishdan oldin tasdiqlash", "Подтверждение перед отправкой", "Confirm before sending");
        put("ConfirmSticker", "Stiker", "Стикер", "Sticker");
        put("ConfirmVoice", "Ovozli xabar", "Голосовое сообщение", "Voice message");
        put("ConfirmGif", "GIF", "GIF", "GIF");
        put("ConfirmInfo", "Stiker va GIF yuborishdan oldin so'raladi. Ovozli xabar esa yozib bo'lingach darhol ketmaydi: avval tinglab, keyin yuborasiz yoki o'chirasiz.",
                "Перед отправкой стикера и GIF будет вопрос. Голосовое сообщение не уйдёт сразу: сначала прослушайте, потом отправьте или удалите.",
                "You'll be asked before a sticker or GIF is sent. A voice message won't go right away: listen first, then send or delete it.");
        put("ConfirmSendSticker", "Stiker yuborilsinmi?", "Отправить стикер?", "Send this sticker?");
        put("ConfirmSendGif", "GIF yuborilsinmi?", "Отправить GIF?", "Send this GIF?");
        put("SavedMessagesHeader", "O'chirilgan va tahrirlangan xabarlar", "Удалённые и изменённые сообщения", "Deleted and edited messages");
        put("KeepDeletedMessages", "O'chirilgan xabarlarni saqlash", "Сохранять удалённые сообщения", "Keep deleted messages");
        put("SaveEditHistory", "Tahrirdan oldingi matnni saqlash", "Сохранять текст до изменения", "Keep text before edits");
        put("SavedMessagesInfo", "Xabar o'chirilsa, u chatda qoladi va yonida 🗑 belgisi chiqadi. Tahrirlangan xabarning eski matnini xabarni bosib turib, «Tahrir tarixi» orqali ko'rasiz. Faqat telefoningizga kelgan xabarlar saqlanadi, hech qanday serverga yuborilmaydi.",
                "Удалённое сообщение остаётся в чате со значком корзины. Прежний текст изменённого сообщения можно посмотреть, зажав сообщение и выбрав «История изменений». Сохраняются только сообщения, уже пришедшие на телефон; на серверы ничего не отправляется.",
                "A deleted message stays in the chat, marked with a trash icon. To see an edited message's earlier text, long-press it and choose Edit history. Only messages that already reached your phone are kept, and nothing is sent to any server.");
        put("EditHistory", "Tahrir tarixi", "История изменений", "Edit history");
        put("ViewDeleted", "O'chirilganlarni ko'rish", "Удалённые сообщения", "View deleted");
        put("ReadExclusion", "O'qish istisnosi", "Исключение для чтения", "Read exclusion");
        put("TypingExclusion", "Yozish istisnosi", "Исключение для набора", "Typing exclusion");
        put("ReadExclusionTitle", "Bu chatda o'qilganlik", "Прочтение в этом чате", "Read receipts in this chat");
        put("TypingExclusionTitle", "Bu chatda «yozmoqda...»", "«Печатает...» в этом чате", "Typing in this chat");
        put("ExclusionDefault", "Umumiy sozlama (ghost rejimi)", "Как в режиме призрака", "Default (ghost mode)");
        put("ReadNever", "Hech qachon o'qilgan qilmaslik", "Никогда не отмечать прочитанным", "Never read");
        put("ReadAlways", "Doim o'qilgan qilish", "Всегда отмечать прочитанным", "Always read");
        put("TypingNever", "Hech qachon ko'rsatmaslik", "Никогда не показывать", "Never type");
        put("TypingAlways", "Doim ko'rsatish", "Всегда показывать", "Always type");
        put("ClearDeleted", "O'chirilganlarni tozalash", "Очистить удалённые", "Clear deleted");
        put("ClearDeletedConfirm", "Shu chatda saqlangan %d ta o'chirilgan xabar telefondan butunlay o'chiriladi.",
                "%d сохранённых удалённых сообщений этого чата будут стёрты с телефона.",
                "%d kept deleted messages of this chat will be erased from this phone.");
        put("NoDeletedMessages", "Bu chatda saqlangan o'chirilgan xabar yo'q", "В этом чате нет сохранённых удалённых сообщений", "No kept deleted messages in this chat");
        put("StoriesHeader", "Storylar", "Истории", "Stories");
        put("SaveAnyStory", "Har qanday storyni saqlash", "Сохранять любые истории", "Save any story");
        put("SaveAnyStoryInfo", "Story menyusida «Galereyaga saqlash» doim chiqadi: muallif saqlashni cheklagan va Premium bo'lmagan holatda ham.",
                "В меню истории всегда есть «Сохранить в галерею» — даже если автор запретил сохранение и без Premium.",
                "Story menus always have Save to gallery, even when the author restricted saving and without Premium.");
        put("StoryDownloading", "Story yuklanmoqda, tugagach galereyaga saqlanadi", "История загружается и сохранится в галерею", "Downloading the story, it will be saved to the gallery");
        put("SelfDestructHeader", "O'chib ketuvchi media", "Исчезающие медиа", "Self-destructing media");
        put("SaveSelfDestructMedia", "O'chib ketuvchi rasm va videolarni saqlash", "Сохранять исчезающие фото и видео", "Save self-destructing photos and videos");
        put("ViewSelfDestructSilently", "Taymerni ishga tushirmay ko'rish", "Смотреть, не запуская таймер", "View without starting the timer");
        put("SelfDestructInfo", "Bir marta ko'riladigan va taymerli rasm va videolar kelishi bilan yuklab olinib, telefoningizda saqlanadi. Taymersiz ko'rish yoqilsa, ular oddiy rasm kabi ochiladi: sizda taymer boshlanmaydi va media chatda qoladi. Taymerni o'zingiz boshlash uchun xabarni bosib turib «Ko'rilgan deb belgilash» ni tanlang.",
                "Одноразовые и фото и видео с таймером скачиваются сразу при получении и хранятся на телефоне. Если включён просмотр без таймера, они открываются как обычные фото: у вас таймер не запускается, и медиа остаётся в чате. Чтобы запустить таймер самому, зажмите сообщение и выберите «Отметить просмотренным».",
                "View-once and timed photos and videos are downloaded as soon as they arrive and kept on your phone. With viewing without the timer on, they open like ordinary photos: no timer starts on your side and the media stays in the chat. To start the timer yourself, long-press the message and choose Mark as viewed.");
        put("AutoMarkSelfDestructViewed", "Ochganda yuboruvchiga «ko'rildi» deb bildirish", "При открытии сообщать отправителю о просмотре", "Tell the sender when you open it");
        put("AutoMarkSelfDestructViewedInfo", "Rasm yoki videoni ochsangiz, yuboruvchi uni ko'rganingizni biladi va taymer uning tomonida odatdagidek tugab, media o'chib ketadi. Sizda esa media va uning nusxasi qoladi. Ghost rejimida «ko'rildi» yuborilmaydi.",
                "Когда вы открываете фото или видео, отправитель узнаёт о просмотре, и у него таймер идёт как обычно, после чего медиа исчезает. У вас медиа и его копия остаются. В режиме призрака отметка о просмотре не отправляется.",
                "When you open the photo or video, the sender learns you viewed it, and on their side the timer runs out as usual and the media disappears. You keep the media and its copy. Nothing is sent while ghost mode is on.");
        put("SavedSelfDestructMedia", "Saqlangan o'chib ketuvchi media", "Сохранённые исчезающие медиа", "Saved self-destructing media");
        put("SavedSelfDestructEmpty", "Hali hech narsa saqlanmagan. Sizga bir marta ko'riladigan yoki taymerli rasm yoki video kelsa, u shu yerda paydo bo'ladi.",
                "Пока ничего не сохранено. Когда вам придёт одноразовое фото или видео либо медиа с таймером, оно появится здесь.",
                "Nothing saved yet. When someone sends you a view-once or timed photo or video, it will show up here.");
        put("MarkSelfDestructViewed", "Ko'rilgan deb belgilash", "Отметить просмотренным", "Mark as viewed");
        put("MarkSelfDestructViewedInfo", "Taymer boshlanadi va yuboruvchi media ochilganini ko'radi. Saqlangan nusxa telefoningizda qoladi.",
                "Запустится таймер, и отправитель увидит, что медиа открыто. Сохранённая копия останется на телефоне.",
                "The timer starts and the sender sees the media was opened. The saved copy stays on your phone.");
        put("SelfDestructTimer", "Taymer", "Таймер", "Timer");
        put("SelfDestructViewOnce", "bir marta ko'rish", "просмотр один раз", "view once");
        put("SelfDestructOpenChat", "Chatni ochish", "Открыть чат", "Open chat");
        put("SelfDestructDeleteCopy", "Nusxani o'chirish", "Удалить копию", "Delete copy");
        put("SelfDestructDeleteAll", "Hammasini o'chirish", "Удалить все", "Delete all");
        put("SelfDestructDeleteAllConfirm", "Barcha saqlangan o'chib ketuvchi media telefoningizdan o'chirilsinmi?", "Удалить с телефона все сохранённые исчезающие медиа?", "Delete all saved self-destructing media from your phone?");
        put("SelfDestructSaved", "O'chib ketuvchi media saqlandi", "Исчезающее медиа сохранено", "Self-destructing media saved");
        put("ProtectedHeader", "Himoyalangan kontent", "Защищённый контент", "Protected content");
        put("SaveProtectedContent", "Cheklangan kanallardan saqlash", "Сохранять из защищённых каналов", "Save from protected chats");
        put("SaveProtectedContentInfo", "Saqlash cheklangan kanal va guruhlarda ham rasm, video, ovozli xabar va fayllarni saqlash, nusxalash va uzatish mumkin bo'ladi. Uzatilganda xabar yuklab olinib, sizning nomingizdan yangi xabar sifatida yuboriladi.",
                "В каналах и группах с запретом сохранения можно сохранять, копировать и пересылать фото, видео, голосовые и файлы. При пересылке сообщение скачивается и отправляется заново от вашего имени.",
                "Photos, videos, voice messages and files in chats that restrict saving can be saved, copied and forwarded. Forwarding downloads the message and sends it again as your own.");
        put("ForwardingProtected", "Himoyalangan xabarlar yuklanib, qayta yuborilmoqda...", "Защищённые сообщения скачиваются и отправляются...", "Downloading and sending protected messages...");
        put("GhostStateOn", "Yoqilgan", "Вкл.", "On");
        put("GhostStateOff", "O'chiq", "Выкл.", "Off");
        put("GhostModeOn","Ghost rejimi yoqildi", "Режим призрака включён", "Ghost mode is on");
        put("GhostModeOff", "Ghost rejimi o'chirildi", "Режим призрака выключен", "Ghost mode is off");
    }

    public static String get(String key) {
        String lang = "en";
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null) {
                lang = info.getLangCode();
            }
        } catch (Exception ignore) {
        }
        HashMap<String, String> map = lang != null && lang.startsWith("uz") ? UZ : lang != null && lang.startsWith("ru") ? RU : EN;
        String value = map.get(key);
        return value != null ? value : EN.get(key);
    }
}
