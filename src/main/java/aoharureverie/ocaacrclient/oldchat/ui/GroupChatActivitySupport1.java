package aoharureverie.ocaacrclient.oldchat.ui;

import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.GroupMember;
import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

abstract class GroupChatActivitySupport1 extends GroupChatActivitySupport0 {
    protected void mentionUser(GroupMessage msg, String displayName) {
        suppressMentionTrigger = true;
        GroupChatUiActions.insertMention(etInput, msg, displayName);
        suppressMentionTrigger = false;
        if (msg != null) {
            addMentionDraft(msg.from_uid, displayName);
        }
    }

    protected void setupMentionInput() {
        if (etInput == null) {
            return;
        }
        etInput.addTextChangedListener(new TextWatcher() {
            private int lastStart = 0;
            private int lastCount = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lastStart = start;
                lastCount = count;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressMentionTrigger) {
                    return;
                }
                String content = s == null ? "" : s.toString();
                if (content.length() == 0) {
                    mentionDrafts.clear();
                    return;
                }
                pruneMentions(content);
                if (lastCount <= 0) {
                    return;
                }
                int end = Math.min(lastStart + lastCount, content.length());
                for (int i = lastStart; i < end; i++) {
                    if (content.charAt(i) == '@' && (i == 0 || Character.isWhitespace(content.charAt(i - 1)))) {
                        showMentionPicker();
                        break;
                    }
                }
            }
        });
    }

    private void rebuildMentionMembersFromSession() {
        HashSet<String> seen = new HashSet<String>();
        boolean allowSessionMerge = mentionMembers.isEmpty();
        for (int i = 0; i < mentionMembers.size(); i++) {
            GroupMember existing = mentionMembers.get(i);
            if (existing == null || existing.uid == null || existing.uid.isEmpty()) {
                continue;
            }
            seen.add(existing.uid);
            if (existing.display_name == null || existing.display_name.isEmpty()) {
                existing.display_name = resolveDisplayName(existing.uid);
            }
            if (existing.avatar_url == null || existing.avatar_url.isEmpty()) {
                existing.avatar_url = avatarMap.get(existing.uid);
            }
            Integer existingRole = roleMap.get(existing.uid);
            if (existingRole != null) {
                existing.role = existingRole.intValue();
            }
        }
        if (!allowSessionMerge) {
            return;
        }
        for (int i = messageList.size() - 1; i >= 0; i--) {
            GroupMessage msg = messageList.get(i);
            if (msg == null || msg.from_uid == null || msg.from_uid.isEmpty()) {
                continue;
            }
            String uid = msg.from_uid;
            if (seen.contains(uid)) {
                continue;
            }
            seen.add(uid);
            GroupMember member = new GroupMember();
            member.uid = uid;
            member.display_name = resolveDisplayName(uid);
            member.avatar_url = avatarMap.get(uid);
            Integer role = roleMap.get(uid);
            member.role = role == null ? 0 : role.intValue();
            mentionMembers.add(member);
        }
    }

    private void showMentionPicker() {
        if (mentionDialog != null && mentionDialog.isShowing()) {
            return;
        }
        rebuildMentionMembersFromSession();
        View view = getLayoutInflater().inflate(R.layout.dialog_group_mention, null);
        final EditText etSearch = (EditText) view.findViewById(R.id.etMentionSearch);
        final ListView lvMembers = (ListView) view.findViewById(R.id.lvMentionList);
        mentionAdapter = new GroupMentionAdapter(this, mentionMembers, myUID);
        mentionAdapter.setMyRole(myRole);
        lvMembers.setAdapter(mentionAdapter);
        lvMembers.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                GroupMember member = mentionAdapter.getItem(position);
                insertMention(member);
                if (mentionDialog != null) {
                    mentionDialog.dismiss();
                }
            }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mentionAdapter != null) {
                    mentionAdapter.filter(s == null ? "" : s.toString());
                }
            }
        });
        mentionDialog = new AlertDialog.Builder(this, R.style.AppDialogTheme)
                .setTitle("选择成员")
                .setView(view)
                .setNegativeButton("取消", null)
                .create();
        mentionDialog.show();
    }

    private void insertMention(GroupMember member) {
        if (member == null || etInput == null) {
            return;
        }
        String name = resolveMemberName(member);
        if (name == null || name.isEmpty()) {
            return;
        }
        addMentionDraft(member.uid, name);
        Editable text = etInput.getText();
        if (text == null) {
            return;
        }
        int cursor = etInput.getSelectionStart();
        if (cursor < 0 || cursor > text.length()) {
            cursor = text.length();
        }
        int start = findMentionStart(text, cursor);
        suppressMentionTrigger = true;
        text.replace(start, cursor, "@" + name + " ");
        suppressMentionTrigger = false;
    }

    private int findMentionStart(CharSequence text, int cursor) {
        if (text == null || cursor <= 0) {
            return cursor;
        }
        for (int i = cursor - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '@' && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                return i;
            }
            if (Character.isWhitespace(c)) {
                break;
            }
        }
        return cursor;
    }

    private void addMentionDraft(String uid, String name) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        String display = name == null || name.isEmpty() ? uid : name;
        for (MessagePayload.Mention mention : mentionDrafts) {
            if (mention != null && uid.equals(mention.uid)) {
                mention.name = display;
                return;
            }
        }
        MessagePayload.Mention mention = new MessagePayload.Mention();
        mention.uid = uid;
        mention.name = display;
        mentionDrafts.add(mention);
    }

    private void pruneMentions(String content) {
        if (mentionDrafts.isEmpty()) {
            return;
        }
        String text = content == null ? "" : content;
        for (int i = mentionDrafts.size() - 1; i >= 0; i--) {
            MessagePayload.Mention mention = mentionDrafts.get(i);
            if (mention == null || mention.uid == null || mention.uid.isEmpty()) {
                mentionDrafts.remove(i);
                continue;
            }
            if (!text.contains(buildMentionTag(mention))) {
                mentionDrafts.remove(i);
            }
        }
    }

    private String buildMentionTag(MessagePayload.Mention mention) {
        if (mention == null) {
            return "";
        }
        String name = mention.name == null || mention.name.isEmpty() ? mention.uid : mention.name;
        return "@" + (name == null ? "" : name);
    }

    @Override
    protected List<MessagePayload.Mention> collectMentions(String content) {
        if (content == null || content.isEmpty() || mentionDrafts.isEmpty()) {
            return null;
        }
        List<MessagePayload.Mention> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (MessagePayload.Mention mention : mentionDrafts) {
            if (mention == null || mention.uid == null || mention.uid.isEmpty() || seen.contains(mention.uid)) {
                continue;
            }
            if (content.contains(buildMentionTag(mention))) {
                result.add(mention);
                seen.add(mention.uid);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String resolveMemberName(GroupMember member) {
        if (member == null) {
            return "";
        }
        if (member.display_name != null && !member.display_name.isEmpty()) {
            return member.display_name;
        }
        if (member.username != null && !member.username.isEmpty()) {
            return member.username;
        }
        return member.uid == null ? "" : member.uid;
    }
}
