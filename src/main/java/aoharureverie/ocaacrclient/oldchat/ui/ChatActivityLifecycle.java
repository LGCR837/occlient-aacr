package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.models.RecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.ClipboardUtil;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.util.TypingStatusManager;

abstract class ChatActivityLifecycle extends ChatActivityCallbacks {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        friendUID = getIntent().getStringExtra("friend_uid");
        if (friendUID == null || friendUID.isEmpty()) {
            friendUID = getIntent().getStringExtra("friend_id");
        }
        friendName = getIntent().getStringExtra("friend_name");
        friendAvatar = getIntent().getStringExtra("friend_avatar");
        if (friendUID != null && !friendUID.isEmpty()) {
            String name = friendName != null && !friendName.isEmpty() ? friendName : friendUID;
            RecentChatCache.touchRecentChat(this, friendUID, name, friendAvatar);
        }

        TextView tvTitle = (TextView) findViewByIdCompat(R.id.tvChatTitle);
        if (tvTitle != null) {
            String title = friendName;
            if (title == null || title.isEmpty()) {
                title = friendUID;
            }
            tvTitle.setText(title == null ? "" : title);
        }
        View header = (View) findViewByIdCompat(R.id.chat_header);
        if (header != null) {
            header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFriendSpace();
                }
            });
        }

        lvMessages = findViewByIdCompat(R.id.lvMessages);
        etInput = findViewByIdCompat(R.id.etInput);
        ChatInputMenuHelper.bind(etInput, new ChatInputMenuHelper.SendAction() {
            @Override
            public void onSend() {
                sendFromInput();
            }
        });
        registerForContextMenu(etInput);

        btnSend = findViewByIdCompat(R.id.btnSend);
        pbMessagesLoading = findViewByIdCompat(R.id.pbMessagesLoading);
        typingStatusManager = TypingStatusManager.getInstance();
        backDot = findViewByIdCompat(R.id.vBackDot);

        View btnBack = (View) findViewByIdCompat(R.id.btnChatBack);
        if (btnBack instanceof ImageView) {
            ((ImageView) btnBack).setColorFilter(getResources().getColor(R.color.colorTextPrimary));
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        quotePreview = findViewByIdCompat(R.id.quote_preview);
        tvQuotePreview = findViewByIdCompat(R.id.tvQuotePreview);
        View btnQuoteClose = findViewByIdCompat(R.id.btnQuoteClose);
        if (btnQuoteClose != null) {
            btnQuoteClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearQuoteDraft();
                }
            });
        }

        View btnActionImage = (View) findViewByIdCompat(R.id.btnActionImage);
        View btnActionVideo = (View) findViewByIdCompat(R.id.btnActionVideo);
        View btnActionEmoji = (View) findViewByIdCompat(R.id.btnActionEmoji);
        View btnActionRedPacket = (View) findViewByIdCompat(R.id.btnActionRedPacket);
        View btnToggleVoice = (View) findViewByIdCompat(R.id.btnToggleVoice);
        View btnPlus = (View) findViewByIdCompat(R.id.btnPlus);
        View btnHoldToTalk = (View) findViewByIdCompat(R.id.btnHoldToTalk);
        View actionPanel = (View) findViewByIdCompat(R.id.action_panel);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        myUID = prefs.getString("my_uid", "");
        lastDraft = ChatDraftHelper.restoreDraft(this, etInput, getDraftKey());

        setupTypingWatcher();
        setupLoadMoreHeader();
        adapter = new MessageAdapter(this, messageList, myUID);
        adapter.setActionListener(new MessageAdapter.MessageActionListener() {
            @Override
            public void onQuote(Message message) {
                quoteMessage(message);
            }

            @Override
            public void onCopy(String text) {
                ClipboardUtil.copyText(ChatActivityLifecycle.this, text);
            }

            @Override
            public void onRecall(Message message) {
                recallMessage(message);
            }

            
            public void onReEdit(Message message) {
                reEditRecalledMessage(message);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        lvMessages.setLayoutManager(layoutManager);
        lvMessages.setHasFixedSize(true);
        lvMessages.setItemViewCacheSize(20);
        lvMessages.setItemAnimator(null);
        lvMessages.setAdapter(adapter);

        btnNewMessage = findViewByIdCompat(R.id.btnNewMessage);
        listHelper = new DirectChatListHelper(this, lvMessages, adapter, messageList, messageIds,
                btnLoadMore, btnNewMessage, pbMessagesLoading,
                friendUID, friendName, friendAvatar, myUID);
        adapter.setQuoteClickListener(new MessageAdapter.QuoteClickListener() {
            @Override
            public void onQuoteClick(aoharureverie.ocaacrclient.oldchat.util.MessagePayload.Quote quote) {
                if (quote == null || quote.id == null || quote.id.isEmpty()) {
                    return;
                }
                if (listHelper != null) {
                    listHelper.jumpToMessageId(token, quote.id);
                }
            }
        });

        btnJumpToUnread = findViewByIdCompat(R.id.btnJumpToUnread);
        if (listHelper != null && btnJumpToUnread != null) {
            listHelper.setJumpToUnreadButton(btnJumpToUnread);
            int unreadCount = getIntent().getIntExtra("unread_count", 0);
            if (unreadCount > 0) {
                listHelper.setInitialUnreadCount(unreadCount);
            }
            btnJumpToUnread.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listHelper != null) {
                        listHelper.jumpToEarliestUnread(token);
                    }
                }
            });
        }

        messageSender = new DirectMessageSender(this, listHelper, btnLoadMore, etInput, token, friendUID,
                new DirectMessageSender.SendStateListener() {
                    @Override
                    public void onSendState(boolean sending) {
                        setSending(sending);
                    }
                },
                new DirectMessageSender.QuoteClearListener() {
                    @Override
                    public void onClearQuote() {
                        clearQuoteDraft();
                    }
                });
        if (btnNewMessage != null) {
            btnNewMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listHelper != null) {
                        listHelper.scrollToBottom();
                    }
                }
            });
        }
        lvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (listHelper != null) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null) {
                        int lastVisibleItem = lm.findLastVisibleItemPosition();
                        int totalItemCount = lm.getItemCount();
                        listHelper.onUserScroll(lastVisibleItem >= totalItemCount - 1);

                        int firstVisibleItem = lm.findFirstVisibleItemPosition();
                        if (dy < 0 && firstVisibleItem <= 1 && listHelper.canLoadMore()) {
                            long before = listHelper.getOldestTimestamp();
                            listHelper.loadMessages(token, true, before, true);
                        }
                    }
                }
            }
        });

        if (btnActionRedPacket != null) {
            btnActionRedPacket.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openRedPacketSend();
                }
            });
        }

        wsListener = new ChatWsListener(asChatActivity(), friendUID, myUID, messageIds, messageList, adapter, lvMessages,
                new Runnable() {
                    @Override
                    public void run() {
                        if (listHelper != null) {
                            listHelper.markRead(token);
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (listHelper != null) {
                            listHelper.onNewMessageReceived();
                        }
                    }
                });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFromInput();
            }
        });
        pbSend = findViewByIdCompat(R.id.pbSend);

        View videoProgressContainer = findViewByIdCompat(R.id.video_progress_container);
        ProgressBar pbVideoProgress = findViewByIdCompat(R.id.pbVideoProgress);
        TextView tvVideoProgress = findViewByIdCompat(R.id.tvVideoProgress);
        mediaHelper = new ChatMediaHelper(this, token, etInput, btnSend, btnToggleVoice, btnPlus,
                btnHoldToTalk, actionPanel, btnActionImage, btnActionVideo,
                videoProgressContainer, pbVideoProgress, tvVideoProgress,
                new ChatMediaHelper.MediaCallback() {
                    @Override
                    public void onMediaReady(String type, String url, String thumbUrl, int durationMs) {
                        if (messageSender != null) {
                            messageSender.sendMedia(type, url, thumbUrl, durationMs, quoteDraft);
                        }
                    }
                },
                new ChatMediaHelper.SendStateListener() {
                    @Override
                    public void onSendState(boolean sending) {
                        setSending(sending);
                    }
                });
        mediaHelper.bind();

        if (btnActionEmoji != null) {
            btnActionEmoji.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openEmojiPicker();
                }
            });
        }
        View btnChatMenu = (View) findViewByIdCompat(R.id.btnChatMenu);
        if (btnChatMenu instanceof ImageView) {
            ((ImageView) btnChatMenu).setColorFilter(getResources().getColor(R.color.colorTextPrimary));
        }
        if (btnChatMenu != null) {
            btnChatMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openChatSettings();
                }
            });
        }

        View chatBackground = findViewByIdCompat(R.id.chat_background);
        backgroundHelper = new ChatBackgroundHelper(this, chatBackground, friendUID, false);
        if (backgroundHelper != null) {
            backgroundHelper.applyBackground();
        }
        applyFontScale();
        String jumpMessageId = getIntent().getStringExtra("jump_message_id");
        if (jumpMessageId != null && !jumpMessageId.isEmpty()) {
            listHelper.jumpToMessageId(token, jumpMessageId);
        } else {
            listHelper.loadMessages(token, false, 0, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WSManager.getInstance().start(this);
        WSManager.getInstance().addListener(wsListener);
        WSManager.getInstance().addListener(backDotListener);
        if (friendUID != null && !friendUID.isEmpty()) {
            RecentChatCache.clearUnread(this, friendUID);
        }
        updateBackDot();
        refreshMyUID();
        applyFontScale();
        if (backgroundHelper != null) {
            backgroundHelper.applyBackground();
        }
        if (needsRefreshOnResume) {
            boolean keepPosition = listHelper != null && !listHelper.isAtBottom();
            listHelper.loadMessages(token, false, 0, true, keepPosition);
            needsRefreshOnResume = false;
        }
        boolean typingEnabled = SettingsPrefs.isTypingIndicatorEnabled(this);
        boolean allowPolling = !WSManager.getInstance().isConnected();
        if (typingStatusManager != null && friendUID != null && !friendUID.isEmpty()) {
            typingStatusManager.unregisterListener(friendUID);
            typingStatusManager.registerListener(friendUID, new TypingStatusManager.TypingListener() {
                @Override
                public void onTypingStatusChanged(String uid, boolean typing) {
                    if (uid == null || uid.isEmpty()) {
                        return;
                    }
                    if (!SettingsPrefs.isTypingIndicatorEnabled(ChatActivityLifecycle.this)) {
                        setTypingIndicatorVisible(false);
                        return;
                    }
                    if (MyUidStore.isMyUid(ChatActivityLifecycle.this, uid, myUID)) {
                        return;
                    }
                    if (friendUID != null && !friendUID.equals(uid)) {
                        return;
                    }
                    setTypingIndicatorVisible(typing);
                }
            });
            if (typingEnabled && allowPolling) {
                typingStatusManager.startCheckingTyping(this, token, friendUID, false);
            } else {
                typingStatusManager.stopCheckingTyping(friendUID);
                setTypingIndicatorVisible(false);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        lastDraft = ChatDraftHelper.saveDraftFromInput(this, etInput, getDraftKey(), lastDraft);
        updateRecentFromLastMessage();
        if (listHelper != null) {
            listHelper.recordScrollPosition();
        }
        if (mediaHelper != null) {
            mediaHelper.onPause();
        }
        if (adapter != null) {
            adapter.stopVoice();
        }
        WSManager.getInstance().removeListener(wsListener);
        WSManager.getInstance().removeListener(backDotListener);
        if (typingStatusManager != null && friendUID != null && !friendUID.isEmpty()) {
            if (isTyping) {
                typingStatusManager.stopTyping(this, token, friendUID, false);
                isTyping = false;
            }
            typingStatusManager.stopCheckingTyping(friendUID);
            typingStatusManager.unregisterListener(friendUID);
        }
        typingHandler.removeCallbacks(typingIdleRunnable);
        setTypingIndicatorVisible(false);
        needsRefreshOnResume = true;
    }

}
