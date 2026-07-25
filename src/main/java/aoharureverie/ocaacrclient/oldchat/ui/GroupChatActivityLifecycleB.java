package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;

abstract class GroupChatActivityLifecycleB extends GroupChatActivitySupport2 {
    @Override
    protected void onPause() {
        super.onPause();
        lastDraft = ChatDraftHelper.saveDraftFromInput(this, etInput, getDraftKey(), lastDraft);
        updateRecentFromLastMessage();
        if (listHelper != null) {
            listHelper.recordScrollPosition();
        }
        if (mentionDialog != null && mentionDialog.isShowing()) {
            mentionDialog.dismiss();
        }
        if (mediaHelper != null) {
            mediaHelper.onPause();
        }
        if (adapter != null) {
            adapter.stopVoice();
        }
        aoharureverie.ocaacrclient.oldchat.api.WSManager.getInstance().removeListener(wsListener);
        aoharureverie.ocaacrclient.oldchat.api.WSManager.getInstance().removeListener(backDotListener);
        if (typingStatusManager != null && groupId != null && !groupId.isEmpty()) {
            if (isTyping) {
                typingStatusManager.stopTyping(this, token, groupId, true);
                isTyping = false;
            }
            typingStatusManager.stopCheckingTyping(groupId);
            typingStatusManager.unregisterListener(groupId);
        }
        typingHandler.removeCallbacks(typingIdleRunnable);
        clearTypingState();
        needsRefreshOnResume = true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v == etInput) {
            ChatInputMenuHelper.fillContextMenu(menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (ChatInputMenuHelper.handleContextItem(etInput, item)) {
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (mediaHelper != null && mediaHelper.handlePermissionsResult(requestCode, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GROUP_MANAGE && resultCode == RESULT_OK) {
            finish();
            return;
        }
        if (requestCode == REQ_GROUP_MANAGE && resultCode == RESULT_FIRST_USER) {
            if (data != null) {
                String name = data.getStringExtra("group_name");
                if (name != null && !name.isEmpty() && tvTitle != null) {
                    groupName = name;
                    tvTitle.setText(name);
                }
            }
            return;
        }
        if (requestCode == REQ_PICK_EMOJI && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra(EmojiPickerActivity.EXTRA_EMOJI_PATH);
            boolean isGif = data.getBooleanExtra(EmojiPickerActivity.EXTRA_EMOJI_IS_GIF, false);
            if (messageSender != null) {
                messageSender.sendEmojiFromPath(path, isGif, quoteDraft);
            }
            return;
        }
        if (requestCode == REQ_PICK_CHAT_BG && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (backgroundHelper != null) {
                backgroundHelper.handlePickResult(uri);
            }
            return;
        }
        if (requestCode == REQ_SEND_RED_PACKET && resultCode == RESULT_OK && data != null) {
            String payload = data.getStringExtra(RedPacketSendActivity.EXTRA_MESSAGE_JSON);
            if (payload != null && !payload.isEmpty() && listHelper != null) {
                GroupMessage sent = listHelper.parseMessageFromResponse(payload);
                if (sent != null) {
                    listHelper.appendMessage(sent, true);
                } else {
                    listHelper.loadMessages(token, false, 0, false);
                }
            }
            return;
        }
        if (mediaHelper != null && mediaHelper.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
    }
}
