package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.models.GroupRecentChatCache;
import aoharureverie.ocaacrclient.oldchat.util.ClipboardUtil;
import aoharureverie.ocaacrclient.oldchat.util.MyUidStore;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;
import aoharureverie.ocaacrclient.oldchat.util.TypingStatusManager;

abstract class GroupChatActivityLifecycleA extends GroupChatActivityLifecycleB {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);
        groupId = getIntent().getStringExtra("group_id");
        groupName = getIntent().getStringExtra("group_name");
        groupAvatar = getIntent().getStringExtra("group_avatar");
        myRole = getIntent().getIntExtra("group_role", 0);
        if (groupId != null) {
            groupId = groupId.trim().toUpperCase();
        }
        GroupRecentChatCache.touchGroup(this, groupId, groupName, groupAvatar, myRole);

        tvTitle = (TextView) findViewByIdCompat(R.id.tvGroupTitle);
        if (tvTitle != null) {
            tvTitle.setText(groupName == null ? "" : groupName);
        }
        View btnManage = (View) findViewByIdCompat(R.id.btnGroupManage);
        if (btnManage != null) {
            btnManage.setVisibility(View.VISIBLE);
            btnManage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openManage();
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
        setupMentionInput();
        typingStatusManager = TypingStatusManager.getInstance();
        btnSend = findViewByIdCompat(R.id.btnSend);
        pbMessagesLoading = findViewByIdCompat(R.id.pbMessagesLoading);
        backDot = findViewByIdCompat(R.id.vBackDot);

        View btnBack = (View) findViewByIdCompat(R.id.btnGroupChatBack);
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
        btnQuoteClose = findViewByIdCompat(R.id.btnQuoteClose);
        announcementBanner = findViewByIdCompat(R.id.group_announcement_banner);
        tvAnnouncementBanner = findViewByIdCompat(R.id.tvGroupAnnouncementBanner);
        if (announcementBanner != null) {
            announcementBanner.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (announcementMode == ANNOUNCEMENT_MODE_OPTIONAL) {
                        markAnnouncementRead();
                        hideAnnouncementBanner();
                    }
                }
            });
        }
        if (btnQuoteClose != null) {
            btnQuoteClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearQuoteDraft();
                }
            });
        }

        View btnToggleVoice = (View) findViewByIdCompat(R.id.btnToggleVoice);
        View btnPlus = (View) findViewByIdCompat(R.id.btnPlus);
        View btnHoldToTalk = (View) findViewByIdCompat(R.id.btnHoldToTalk);
        View btnActionImage = (View) findViewByIdCompat(R.id.btnActionImage);
        View btnActionVideo = (View) findViewByIdCompat(R.id.btnActionVideo);
        View btnActionEmoji = (View) findViewByIdCompat(R.id.btnActionEmoji);
        View btnActionRedPacket = (View) findViewByIdCompat(R.id.btnActionRedPacket);
        View actionPanel = (View) findViewByIdCompat(R.id.action_panel);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        myUID = prefs.getString("my_uid", "");
        lastDraft = ChatDraftHelper.restoreDraft(this, etInput, getDraftKey());
        setupTypingWatcher();
        setupLoadMoreHeader();

        adapter = new GroupMessageAdapter(this, messageList, myUID);
        adapter.setMyRole(myRole);
        adapter.setActionListener(new GroupMessageAdapter.GroupMessageActionListener() {
            @Override
            public void onQuote(GroupMessage message, String displayName) {
                quoteMessage(message, displayName);
            }

            @Override
            public void onMention(GroupMessage message, String displayName) {
                mentionUser(message, displayName);
            }

            @Override
            public void onCopy(String text) {
                ClipboardUtil.copyText(GroupChatActivityLifecycleA.this, text);
            }

            @Override
            public void onRecall(GroupMessage message) {
                recallMessage(message);
            }

            
            public void onReEdit(GroupMessage message) {
                reEditRecalledMessage(message);
            }
        });
        lvMessages.setAdapter(adapter);

        btnNewMessage = findViewByIdCompat(R.id.btnNewMessage);
        listHelper = new GroupChatListHelper(this, lvMessages, adapter, messageList, messageIds,
                btnLoadMore, btnNewMessage, pbMessagesLoading, groupId, groupName, myUID);
        adapter.setQuoteClickListener(new GroupMessageAdapter.QuoteClickListener() {
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

        messageSender = new GroupMessageSender(this, listHelper, btnLoadMore, etInput, token, groupId, groupName,
                new GroupMessageSender.SendStateListener() {
                    @Override
                    public void onSendState(boolean sending) {
                        setSending(sending);
                    }
                },
                new GroupMessageSender.QuoteClearListener() {
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
        lvMessages.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (listHelper != null) {
                    boolean atBottom = firstVisibleItem + visibleItemCount >= totalItemCount - 1;
                    listHelper.onUserScroll(atBottom);
                }
            }
        });

        wsListener = new GroupChatWsListener(asGroupChatActivity(), groupId, messageIds, messageList, adapter, lvMessages,
                new Runnable() {
                    @Override
                    public void run() {
                        if (listHelper != null) {
                            listHelper.onNewMessageReceived();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (listHelper != null) {
                            listHelper.markRead(token);
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
        if (btnActionRedPacket != null) {
            btnActionRedPacket.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openRedPacketSend();
                }
            });
        }

        chatBackground = findViewByIdCompat(R.id.chat_background);
        backgroundHelper = new ChatBackgroundHelper(this, chatBackground, groupId, true);
        applyChatBackground();
        // 进入群聊仅同步成员名称/称号/角色，用于消息区显示用户名；不预取全员头像。
        manageApi = new GroupManageApi();
        GroupMemberLoader.loadMemberNames(this, token, groupId, adapter, nameMap, titleMap, roleMap,
                mentionMembers, new Runnable() {
                    @Override
                    public void run() {
                        if (mentionAdapter != null) {
                            mentionAdapter.setMembers(mentionMembers);
                        }
                    }
                });
        applyFontScale();
        String jumpMessageId = getIntent().getStringExtra("jump_message_id");
        if (jumpMessageId != null && !jumpMessageId.isEmpty()) {
            listHelper.jumpToMessageId(token, jumpMessageId);
        } else {
            listHelper.loadMessages(token, false, 0, false);
        }
        refreshGroupAnnouncement();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WSManager.getInstance().start(this);
        WSManager.getInstance().addListener(wsListener);
        WSManager.getInstance().addListener(backDotListener);
        if (groupId != null && !groupId.isEmpty()) {
            GroupRecentChatCache.clearUnread(this, groupId);
        }
        updateBackDot();
        refreshMyUID();
        applyFontScale();
        applyChatBackground();
        refreshGroupAnnouncement();
        if (needsRefreshOnResume) {
            boolean keepPosition = listHelper != null && !listHelper.isAtBottom();
            listHelper.loadMessages(token, false, 0, true, keepPosition);
            needsRefreshOnResume = false;
        }
        boolean typingEnabled = SettingsPrefs.isTypingIndicatorEnabled(this);
        boolean allowPolling = !WSManager.getInstance().isConnected();
        if (typingStatusManager != null && groupId != null && !groupId.isEmpty()) {
            typingStatusManager.unregisterListener(groupId);
            typingStatusManager.registerListener(groupId, new TypingStatusManager.TypingListener() {
                @Override
                public void onTypingStatusChanged(String uid, boolean typing) {
                    if (uid == null || uid.isEmpty()) {
                        return;
                    }
                    if (!SettingsPrefs.isTypingIndicatorEnabled(GroupChatActivityLifecycleA.this)) {
                        clearTypingState();
                        return;
                    }
                    if (MyUidStore.isMyUid(GroupChatActivityLifecycleA.this, uid, myUID)) {
                        return;
                    }
                    if (typing) {
                        typingUsers.remove(uid);
                        typingUsers.add(uid);
                    } else {
                        typingUsers.remove(uid);
                    }
                    updateTypingIndicator();
                }
            });
            if (typingEnabled && allowPolling) {
                typingStatusManager.startCheckingTyping(this, token, groupId, true);
            } else {
                typingStatusManager.stopCheckingTyping(groupId);
                clearTypingState();
            }
        }
    }
}
