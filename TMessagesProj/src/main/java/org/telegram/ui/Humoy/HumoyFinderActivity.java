/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/**
 * Finds a Telegram account by phone number, or by numeric ID / username / t.me link,
 * and shows it as a card with buttons to write to it or open its profile.
 */
public class HumoyFinderActivity extends BaseFragment {

    public static final int MODE_PHONE = 0;
    public static final int MODE_ID = 1;

    private final int mode;

    private EditTextBoldCursor editText;
    private ButtonWithCounterView searchButton;
    private TextView errorView;
    private LinearLayout resultCard;
    private int requestId;
    private String lastQuery;

    public HumoyFinderActivity(int mode) {
        this.mode = mode;
    }

    @Override
    public void onFragmentDestroy() {
        if (requestId != 0) {
            getConnectionsManager().cancelRequest(requestId, true);
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(HumoyStrings.get(mode == MODE_PHONE ? "PhoneFinder" : "IdFinder"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = scrollView;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // icon, title and what this does
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(Theme.createCircleDrawable(dp(72), getThemedColor(Theme.key_featuredStickers_addButton)));
        ImageView icon = new ImageView(context);
        icon.setImageResource(mode == MODE_PHONE ? R.drawable.msg_calls : R.drawable.msg_mention);
        icon.setColorFilter(0xffffffff);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        iconFrame.addView(icon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        content.addView(iconFrame, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(context);
        title.setText(HumoyStrings.get(mode == MODE_PHONE ? "PhoneFinderTitle" : "IdFinderTitle"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        TextView hint = new TextView(context);
        hint.setText(HumoyStrings.get(mode == MODE_PHONE ? "PhoneFinderInfo" : "IdFinderInfo"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(2), 1f);
        content.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 6, 8, 0));

        if (mode == MODE_ID) {
            content.addView(createMyIdCard(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));
        }

        // input and search button, on a card
        LinearLayout inputCard = new LinearLayout(context);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        inputCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        content.addView(inputCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));

        OutlineTextContainerView outline = new OutlineTextContainerView(context);
        outline.setText(HumoyStrings.get(mode == MODE_PHONE ? "PhoneFinderField" : "IdFinderField"));
        FrameLayout fieldRow = new FrameLayout(context);
        TextView prefix = null;
        if (mode == MODE_PHONE) {
            prefix = new TextView(context);
            prefix.setText("+");
            prefix.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            prefix.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            fieldRow.addView(prefix, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));
        }
        editText = new EditTextBoldCursor(context);
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackground(null);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setMaxLines(1);
        editText.setSingleLine(true);
        editText.setTypeface(Typeface.DEFAULT);
        editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        if (mode == MODE_PHONE) {
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setText("998 ");
            editText.setPadding(dp(30), dp(16), dp(16), dp(16));
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setPadding(dp(16), dp(16), dp(16), dp(16));
        }
        editText.setSelection(editText.length());
        editText.setOnFocusChangeListener((v, hasFocus) -> outline.animateSelection(hasFocus ? 1f : 0f));
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                errorView.setVisibility(View.GONE);
            }
        });
        fieldRow.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        outline.addView(fieldRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        outline.attachEditText(editText);
        inputCard.addView(outline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        errorView = new TextView(context);
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        errorView.setLineSpacing(dp(2), 1f);
        errorView.setVisibility(View.GONE);
        inputCard.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 10, 4, 0));

        searchButton = new ButtonWithCounterView(context, getResourceProvider());
        searchButton.setText(HumoyStrings.get("FinderSearch"), false);
        searchButton.setOnClickListener(v -> search());
        inputCard.addView(searchButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 14, 0, 0));

        resultCard = new LinearLayout(context);
        resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        resultCard.setVisibility(View.GONE);
        content.addView(resultCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }
    }

    /** "Your ID" with a copy button, shown above the search in ID mode. */
    private View createMyIdCard(Context context) {
        long myId = getUserConfig().getClientUserId();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        card.setPadding(dp(16), dp(12), dp(8), dp(12));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(context);
        label.setText(HumoyStrings.get("FinderMyId"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        texts.addView(label);
        TextView value = new TextView(context);
        value.setText(String.valueOf(myId));
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        value.setTypeface(AndroidUtilities.bold());
        value.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        texts.addView(value);
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView copy = new ImageView(context);
        copy.setImageResource(R.drawable.msg_copy);
        copy.setColorFilter(getThemedColor(Theme.key_featuredStickers_addButton));
        copy.setScaleType(ImageView.ScaleType.CENTER);
        copy.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        copy.setOnClickListener(v -> copyId(myId));
        card.addView(copy, LayoutHelper.createLinear(44, 44));
        return card;
    }

    private void copyId(long id) {
        if (AndroidUtilities.addToClipboard(String.valueOf(id))) {
            BulletinFactory.of(this).createCopyBulletin(HumoyStrings.get("FinderIdCopied")).show();
        }
    }

    // ---- searching

    private void search() {
        String text = editText.getText() == null ? "" : editText.getText().toString().trim();
        if (TextUtils.isEmpty(text) || searchButton.isLoading()) {
            return;
        }
        if (requestId != 0) {
            getConnectionsManager().cancelRequest(requestId, true);
            requestId = 0;
        }
        lastQuery = text;
        errorView.setVisibility(View.GONE);
        if (mode == MODE_PHONE) {
            searchPhone(text);
        } else {
            searchId(text);
        }
    }

    private void searchPhone(String text) {
        String phone = PhoneFormat.stripExceptNumbers(text);
        if (phone.length() < 7) {
            showError(HumoyStrings.get("PhoneFinderShort"));
            return;
        }
        // numbers already in contacts or in the cache need no request
        TLRPC.User known = findLocalByPhone(phone);
        if (known != null) {
            showResult(known, "+" + phone);
            return;
        }
        setLoading(true);
        TLRPC.TL_contacts_resolvePhone req = new TLRPC.TL_contacts_resolvePhone();
        req.phone = phone;
        String query = lastQuery;
        requestId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            requestId = 0;
            if (!TextUtils.equals(query, lastQuery)) {
                return;
            }
            setLoading(false);
            TLRPC.User user = null;
            if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                TLRPC.TL_contacts_resolvedPeer r = (TLRPC.TL_contacts_resolvedPeer) res;
                getMessagesController().putUsers(r.users, false);
                getMessagesController().putChats(r.chats, false);
                long did = DialogObject.getPeerDialogId(r.peer);
                if (did > 0) {
                    user = getMessagesController().getUser(did);
                }
            }
            if (user != null) {
                showResult(user, "+" + phone);
            } else if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT")) {
                showError(HumoyStrings.get("FinderFlood"));
            } else {
                showError(HumoyStrings.get("PhoneFinderNotFound"));
            }
        }));
    }

    private TLRPC.User findLocalByPhone(String phone) {
        TLRPC.TL_contact contact = getContactsController().contactsByPhone.get(phone);
        if (contact != null) {
            return getMessagesController().getUser(contact.user_id);
        }
        return null;
    }

    private void searchId(String text) {
        String username = usernameOf(text);
        if (username != null) {
            resolveUsername(username);
            return;
        }
        String digits = text.startsWith("-") ? text.substring(1) : text;
        if (!digits.matches("\\d{1,19}")) {
            showError(HumoyStrings.get("IdFinderBadInput"));
            return;
        }
        long id;
        try {
            id = Long.parseLong(text);
        } catch (NumberFormatException e) {
            showError(HumoyStrings.get("IdFinderBadInput"));
            return;
        }
        setLoading(true);
        String query = lastQuery;
        // look in memory, then in the device's cache: users, groups and channels the app has seen
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            TLObject found = findCached(id);
            AndroidUtilities.runOnUIThread(() -> {
                if (!TextUtils.equals(query, lastQuery)) {
                    return;
                }
                if (found != null) {
                    setLoading(false);
                    showResult(found, null);
                } else if (id > 0) {
                    askServer(id, query);
                } else {
                    setLoading(false);
                    showError(HumoyStrings.get("IdFinderNotFound"));
                }
            });
        });
    }

    /** Storage queue: a user, group or channel with this id that the device already knows. */
    private TLObject findCached(long id) {
        if (id > 0) {
            TLRPC.User user = getMessagesController().getUser(id);
            if (user == null) {
                user = getMessagesStorage().getUserSync(id);
                if (user != null) {
                    getMessagesController().putUser(user, true);
                }
            }
            if (user != null && !(user instanceof TLRPC.TL_userEmpty)) {
                return user;
            }
        }
        // groups and channels: "-100…" (as bots show them), "-…" for basic groups, or the bare id
        long chatId = Math.abs(id);
        String s = String.valueOf(id);
        if (s.startsWith("-100") && s.length() > 4) {
            chatId = Long.parseLong(s.substring(4));
        }
        TLRPC.Chat chat = getMessagesController().getChat(chatId);
        if (chat == null) {
            chat = getMessagesStorage().getChatSync(chatId);
            if (chat != null) {
                getMessagesController().putChat(chat, true);
            }
        }
        if (chat != null && !(chat instanceof TLRPC.TL_chatEmpty)) {
            return chat;
        }
        return null;
    }

    /** Last try for an id the device doesn't know: the server answers only for some accounts. */
    private void askServer(long id, String query) {
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = id;
        inputUser.access_hash = 0;
        req.id.add(inputUser);
        requestId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            requestId = 0;
            if (!TextUtils.equals(query, lastQuery)) {
                return;
            }
            setLoading(false);
            TLRPC.User user = null;
            if (res instanceof Vector) {
                for (Object object : ((Vector<?>) res).objects) {
                    if (object instanceof TLRPC.User && !(object instanceof TLRPC.TL_userEmpty) && ((TLRPC.User) object).id == id) {
                        user = (TLRPC.User) object;
                    }
                }
            }
            if (user != null) {
                getMessagesController().putUser(user, false);
                showResult(user, null);
            } else {
                showError(HumoyStrings.get("IdFinderNotFound"));
            }
        }));
    }

    /** "@name", "name", "t.me/name" or "https://t.me/name" → name; null if the text is an id. */
    private static String usernameOf(String text) {
        String s = text.trim();
        s = s.replaceFirst("^(https?://)?(www\\.)?(t|telegram)\\.(me|dog)/", "");
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int question = s.indexOf('?');
        if (question >= 0) {
            s = s.substring(0, question);
        }
        if (s.isEmpty() || Character.isDigit(s.charAt(0)) || s.charAt(0) == '-') {
            return null;
        }
        return s.matches("[A-Za-z][A-Za-z0-9_]{1,31}") ? s : null;
    }

    private void resolveUsername(String username) {
        TLObject known = getMessagesController().getUserOrChat(username);
        if (known != null) {
            showResult(known, null);
            return;
        }
        setLoading(true);
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        String query = lastQuery;
        requestId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            requestId = 0;
            if (!TextUtils.equals(query, lastQuery)) {
                return;
            }
            setLoading(false);
            TLObject found = null;
            if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                TLRPC.TL_contacts_resolvedPeer r = (TLRPC.TL_contacts_resolvedPeer) res;
                getMessagesController().putUsers(r.users, false);
                getMessagesController().putChats(r.chats, false);
                long did = DialogObject.getPeerDialogId(r.peer);
                found = did > 0 ? getMessagesController().getUser(did) : getMessagesController().getChat(-did);
            }
            if (found != null) {
                showResult(found, null);
            } else if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT")) {
                showError(HumoyStrings.get("FinderFlood"));
            } else {
                showError(HumoyStrings.get("IdFinderUsernameNotFound"));
            }
        }));
    }

    private void setLoading(boolean loading) {
        searchButton.setLoading(loading);
    }

    private void showError(String text) {
        resultCard.setVisibility(View.GONE);
        errorView.setText(text);
        errorView.setVisibility(View.VISIBLE);
    }

    // ---- result card

    private void showResult(TLObject object, String phone) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        AndroidUtilities.hideKeyboard(editText);
        errorView.setVisibility(View.GONE);
        resultCard.removeAllViews();

        TLRPC.User user = object instanceof TLRPC.User ? (TLRPC.User) object : null;
        TLRPC.Chat chat = object instanceof TLRPC.Chat ? (TLRPC.Chat) object : null;
        long id = user != null ? user.id : chat.id;
        long dialogId = user != null ? user.id : -chat.id;

        // avatar, name and status
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(12));
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(30));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(currentAccount, object);
        avatar.setForUserOrChat(object, avatarDrawable);
        top.addView(avatar, LayoutHelper.createLinear(60, 60));

        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(user != null ? UserObject.getUserName(user) : chat.title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name);
        TextView status = new TextView(context);
        if (user != null) {
            status.setText(user.bot ? HumoyStrings.get("FinderBot") : LocaleController.formatUserStatus(currentAccount, user));
        } else {
            status.setText(HumoyStrings.get(ChatObject.isChannelAndNotMegaGroup(chat) ? "FinderChannel" : "FinderGroup"));
        }
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        status.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setSingleLine(true);
        names.addView(status, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        top.addView(names, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 0, 0));
        resultCard.addView(top);

        // details
        String username = DialogObject.getPublicUsername(object);
        if (!TextUtils.isEmpty(username)) {
            addDetail(context, HumoyStrings.get("FinderUsername"), "@" + username, R.drawable.msg_mention);
        }
        String shownPhone = phone;
        if (shownPhone == null && user != null && !TextUtils.isEmpty(user.phone)) {
            shownPhone = "+" + user.phone;
        }
        if (shownPhone != null) {
            addDetail(context, HumoyStrings.get("FinderPhone"), formatPhone(shownPhone), R.drawable.msg_calls);
        }
        String shownId = chat != null && ChatObject.isChannel(chat) ? "-100" + id : chat != null ? "-" + id : String.valueOf(id);
        View idRow = addDetail(context, HumoyStrings.get("FinderId"), shownId, R.drawable.msg_copy);
        idRow.setOnClickListener(v -> {
            if (AndroidUtilities.addToClipboard(shownId)) {
                BulletinFactory.of(this).createCopyBulletin(HumoyStrings.get("FinderIdCopied")).show();
            }
        });

        // buttons
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(16), dp(8), dp(16), dp(16));
        ButtonWithCounterView write = new ButtonWithCounterView(context, getResourceProvider());
        write.setText(HumoyStrings.get(user != null ? "FinderWrite" : "FinderOpen"), false);
        write.setOnClickListener(v -> openChat(user, chat));
        buttons.addView(write, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 5, 0));
        ButtonWithCounterView profile = new ButtonWithCounterView(context, false, getResourceProvider());
        profile.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), .12f)));
        profile.setText(HumoyStrings.get("FinderProfile"), false);
        profile.setOnClickListener(v -> openProfile(user, chat));
        buttons.addView(profile, LayoutHelper.createLinear(0, 44, 1f, 5, 0, 0, 0));
        resultCard.addView(buttons);

        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(dp(12));
        resultCard.animate().alpha(1f).translationY(0).setDuration(220).start();
    }

    /** Uzbek numbers as +998 94 883 52 00; others as Telegram formats them. */
    private static String formatPhone(String phone) {
        String digits = PhoneFormat.stripExceptNumbers(phone);
        if (digits.startsWith("998") && digits.length() == 12) {
            return "+998 " + digits.substring(3, 5) + " " + digits.substring(5, 8) + " " + digits.substring(8, 10) + " " + digits.substring(10);
        }
        return PhoneFormat.getInstance().format("+" + digits);
    }

    private View addDetail(Context context, String label, String value, int iconRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setBackground(Theme.getSelectorDrawable(false));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        valueView.setTextIsSelectable(false);
        texts.addView(valueView);
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        labelView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        texts.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(icon, LayoutHelper.createLinear(28, 28));
        resultCard.addView(row);
        return row;
    }

    private void openChat(TLRPC.User user, TLRPC.Chat chat) {
        Bundle args = new Bundle();
        if (user != null) {
            args.putLong("user_id", user.id);
        } else {
            args.putLong("chat_id", chat.id);
        }
        if (getMessagesController().checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

    private void openProfile(TLRPC.User user, TLRPC.Chat chat) {
        Bundle args = new Bundle();
        if (user != null) {
            args.putLong("user_id", user.id);
        } else {
            args.putLong("chat_id", chat.id);
        }
        presentFragment(new ProfileActivity(args));
    }
}
