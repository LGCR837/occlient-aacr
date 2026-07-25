package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.util.ClipboardUtil;

class GroupManageActivityLifecycle extends GroupManageActivitySupport2 {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_manage);
        ivGroupAvatar = findViewByIdCompat(R.id.ivGroupAvatar);
        tvGroupName = findViewByIdCompat(R.id.tvGroupName);
        tvGroupId = findViewByIdCompat(R.id.tvGroupId);
        btnChangeAvatar = findViewByIdCompat(R.id.btnChangeAvatar);
        switchJoinApproval = findViewByIdCompat(R.id.switchJoinApproval);
        switchGlobalMute = findViewByIdCompat(R.id.switchGlobalMute);
        switchMuteNotify = findViewByIdCompat(R.id.switchMuteNotify);
        btnInviteMember = findViewByIdCompat(R.id.btnInviteMember);
        btnJoinRequests = findViewByIdCompat(R.id.btnJoinRequests);
        btnChatBackground = findViewByIdCompat(R.id.btnChatBackground);
        btnGroupAnnouncement = findViewByIdCompat(R.id.btnGroupAnnouncement);
        btnLeaveGroup = findViewByIdCompat(R.id.btnLeaveGroup);
        btnReportGroup = findViewByIdCompat(R.id.btnReportGroup);
        tvLeaveOrDissolve = findViewByIdCompat(R.id.tvLeaveOrDissolve);
        lvMembers = findViewByIdCompat(R.id.lvMembers);
        tvGroupAnnouncement = findViewByIdCompat(R.id.tvGroupAnnouncement);

        View btnBack = (View) findViewByIdCompat(R.id.btnGroupManageBack);
        final View btnCopyGroupId = (View) findViewByIdCompat(R.id.btnCopyGroupId);
        final View btnOpenMembersPage = (View) findViewByIdCompat(R.id.btnOpenMembersPage);
        if (btnBack instanceof ImageView) {
            ((ImageView) btnBack).setColorFilter(getResources().getColor(R.color.color_text_primary));
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnCopyGroupId != null) {
            btnCopyGroupId.setOnClickListener(new View.OnClickListener() {
                
                public void onClick(View v) {
                    String targetId = groupId;
                    if (targetId == null || targetId.length() == 0) {
                        CharSequence text = tvGroupId == null ? null : tvGroupId.getText();
                        targetId = text == null ? "" : text.toString().trim();
                    }
                    if (targetId == null || targetId.length() == 0) {
                        Toast.makeText(GroupManageActivityLifecycle.this, "群号为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ClipboardUtil.copyText(GroupManageActivityLifecycle.this, targetId);
                }
            });
        }

        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        groupId = getIntent().getStringExtra("group_id");
        groupName = getIntent().getStringExtra("group_name");
        myRole = getIntent().getIntExtra("group_role", 0);
        avatarManager = new GroupAvatarManager(this, ivGroupAvatar, token, groupId);
        backgroundHelper = new ChatBackgroundHelper(this, null, groupId, true);

        tvGroupName.setText(groupName == null ? "" : groupName);
        tvGroupId.setText(groupId == null ? "" : groupId);
        tvGroupName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myRole >= 1) {
                    showGroupNameEditor();
                } else {
                    Toast.makeText(GroupManageActivityLifecycle.this, R.string.group_manage_admin_only, Toast.LENGTH_SHORT).show();
                }
            }
        });

        adapter = new GroupMemberAdapter(this, members, myRole, new GroupMemberAdapter.ActionListener() {
            @Override
            public void onKick(aoharureverie.ocaacrclient.oldchat.models.GroupMember member) {
                kickMember(member);
            }

            @Override
            public void onToggleAdmin(aoharureverie.ocaacrclient.oldchat.models.GroupMember member, boolean makeAdmin) {
                setAdmin(member, makeAdmin);
            }
        });
        lvMembers.setAdapter(adapter);
        lvMembers.post(new Runnable() {
            @Override
            public void run() {
                updateMemberListHeight();
            }
        });

        btnChangeAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (avatarManager != null) {
                    avatarManager.pickAvatar(REQ_PICK_AVATAR, REQ_STORAGE);
                }
            }
        });
        btnInviteMember.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInvite();
            }
        });
        btnJoinRequests.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openRequests();
            }
        });

        if (btnGroupAnnouncement != null) {
            btnGroupAnnouncement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openAnnouncementPage();
                }
            });
        }

        if (btnOpenMembersPage != null) {
            btnOpenMembersPage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(GroupManageActivityLifecycle.this, GroupMembersActivity.class);
                    intent.putExtra("group_id", groupId);
                    intent.putExtra("group_name", groupName);
                    intent.putExtra("group_role", myRole);
                    startActivity(intent);
                }
            });
        }

        switchJoinApproval.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (!loadingSettings) {
                    updateSettings();
                }
            }
        });
        switchGlobalMute.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (!loadingSettings) {
                    updateSettings();
                }
            }
        });
        switchMuteNotify.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (!loadingSettings) {
                    aoharureverie.ocaacrclient.oldchat.data.SettingsStore.setConversationMuted(GroupManageActivityLifecycle.this, groupId, true, isChecked);
                    Toast.makeText(GroupManageActivityLifecycle.this, isChecked ? R.string.chat_muted : R.string.chat_unmuted, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnChatBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (backgroundHelper != null) {
                    backgroundHelper.showBackgroundDialog(REQ_PICK_CHAT_BG);
                }
            }
        });
        if (btnReportGroup != null) {
            btnReportGroup.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showGroupReportDialog();
                }
            });
        }
        View btnSearchMessages = (View) findViewByIdCompat(R.id.btnSearchMessages);
        if (btnSearchMessages != null) {
            btnSearchMessages.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSearchMessages();
                }
            });
        }
        btnLeaveGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myRole == 2) {
                    exitHelper.confirmDissolveGroup(GroupManageActivityLifecycle.this, groupId, token, myRole);
                } else {
                    exitHelper.confirmLeaveGroup(GroupManageActivityLifecycle.this, groupId, token);
                }
            }
        });

        applyRoleVisibility();
        loadGroupInfo();
        loadMembers();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (avatarManager != null
                && avatarManager.handlePermissionResult(requestCode, grantResults, REQ_STORAGE, REQ_PICK_AVATAR)) {
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (avatarManager != null && avatarManager.handleActivityResult(requestCode, resultCode, data, REQ_PICK_AVATAR)) {
            return;
        }
        if (requestCode == REQ_EDIT_ANNOUNCEMENT && resultCode == Activity.RESULT_OK && data != null) {
            announcementText = data.getStringExtra(GroupAnnouncementActivity.EXTRA_ANNOUNCEMENT);
            announcementMode = data.getIntExtra(GroupAnnouncementActivity.EXTRA_ANNOUNCEMENT_MODE, announcementMode);
            if (announcementText == null) {
                announcementText = "";
            }
            updateAnnouncementUI();
            return;
        }
        if (requestCode == REQ_PICK_CHAT_BG && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (backgroundHelper != null) {
                backgroundHelper.handlePickResult(uri);
            }
        }
    }
}
