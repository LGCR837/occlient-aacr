package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.support.v7.app.AppCompatDelegate;
import android.view.View;
import android.support.v7.widget.SwitchCompat;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.data.ChatBackgroundStore;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;

public class UiSettingsActivity extends BaseActivity {
    private static final int REQ_PICK_GLOBAL_CHAT_BG = 4101;
    private static final int REQ_PICK_GLOBAL_GROUP_BG = 4102;
    private android.widget.TextView tvChatBgValue;
    private android.widget.TextView tvGroupChatBgValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui_settings);

        View btnBack = (View) findViewByIdCompat(R.id.btnUiSettingsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        final SwitchCompat switchDarkMode = (SwitchCompat) findViewByIdCompat(R.id.switchDarkMode);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(SettingsPrefs.isDarkModeEnabled(this));
            switchDarkMode.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    SettingsPrefs.setDarkModeEnabled(UiSettingsActivity.this, isChecked);
                    AppCompatDelegate.setDefaultNightMode(isChecked
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO);
                    restartSelf();
                }
            });
        }

        final View rowFontSize = (View) findViewByIdCompat(R.id.rowFontSize);
        final android.widget.TextView tvFontSizeValue = findViewByIdCompat(R.id.tvFontSizeValue);
        updateFontSizeLabel(tvFontSizeValue);
        if (rowFontSize != null) {
            rowFontSize.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFontSizeDialog(tvFontSizeValue);
                }
            });
        }

        View rowChatBg = (View) findViewByIdCompat(R.id.rowChatBackgroundGlobal);
        View rowGroupChatBg = (View) findViewByIdCompat(R.id.rowGroupChatBackgroundGlobal);
        tvChatBgValue = findViewByIdCompat(R.id.tvChatBackgroundGlobalValue);
        tvGroupChatBgValue = findViewByIdCompat(R.id.tvGroupChatBackgroundGlobalValue);
        updateChatBackgroundLabels();
        if (rowChatBg != null) {
            rowChatBg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showGlobalBackgroundDialog(false);
                }
            });
        }
        if (rowGroupChatBg != null) {
            rowGroupChatBg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showGlobalBackgroundDialog(true);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateChatBackgroundLabels();
    }

    private void showFontSizeDialog(final android.widget.TextView valueView) {
        final String[] labels = new String[] { "小", "标准", "大", "特大" };
        int currentIndex = SettingsPrefs.getFontSizeIndex(this);
        if (currentIndex < 0 || currentIndex >= labels.length) {
            currentIndex = 1;
        }
        new AlertDialog.Builder(this)
                .setTitle("字体大小")
                .setSingleChoiceItems(labels, currentIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SettingsPrefs.setFontSizeIndex(UiSettingsActivity.this, which);
                        updateFontSizeLabel(valueView);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateFontSizeLabel(android.widget.TextView valueView) {
        if (valueView == null) {
            return;
        }
        int index = SettingsPrefs.getFontSizeIndex(this);
        String label;
        switch (index) {
            case 0:
                label = "小";
                break;
            case 2:
                label = "大";
                break;
            case 3:
                label = "特大";
                break;
            default:
                label = "标准";
                break;
        }
        valueView.setText(label);
    }

    private void showGlobalBackgroundDialog(final boolean isGroup) {
        boolean hasBg = ChatBackgroundStore.hasGlobalBackground(this, isGroup);
        String[] items;
        if (hasBg) {
            items = new String[]{
                getString(R.string.chat_background_set),
                getString(R.string.chat_background_clear)
            };
        } else {
            items = new String[]{getString(R.string.chat_background_set)};
        }
        int titleRes = isGroup ? R.string.chat_background_global_group_title : R.string.chat_background_global_title;
        new AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        pickGlobalBackground(isGroup);
                    } else {
                        ChatBackgroundStore.clearGlobalBackground(UiSettingsActivity.this, isGroup);
                        updateChatBackgroundLabels();
                        android.widget.Toast.makeText(UiSettingsActivity.this,
                                R.string.chat_background_clear_success,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .show();
    }

    private void pickGlobalBackground(boolean isGroup) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.chat_background_set)),
                    isGroup ? REQ_PICK_GLOBAL_GROUP_BG : REQ_PICK_GLOBAL_CHAT_BG);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, R.string.error_pick_image, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChatBackgroundLabels() {
        if (tvChatBgValue != null) {
            tvChatBgValue.setText(ChatBackgroundStore.hasGlobalBackground(this, false)
                    ? R.string.chat_background_status_set
                    : R.string.chat_background_status_empty);
        }
        if (tvGroupChatBgValue != null) {
            tvGroupChatBgValue.setText(ChatBackgroundStore.hasGlobalBackground(this, true)
                    ? R.string.chat_background_status_set
                    : R.string.chat_background_status_empty);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        boolean handled = false;
        if (requestCode == REQ_PICK_GLOBAL_CHAT_BG) {
            handled = ChatBackgroundStore.saveGlobalBackground(this, false, uri);
        } else if (requestCode == REQ_PICK_GLOBAL_GROUP_BG) {
            handled = ChatBackgroundStore.saveGlobalBackground(this, true, uri);
        }
        if (handled) {
            android.widget.Toast.makeText(this, R.string.chat_background_set_success, android.widget.Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQ_PICK_GLOBAL_CHAT_BG || requestCode == REQ_PICK_GLOBAL_GROUP_BG) {
            android.widget.Toast.makeText(this, R.string.error_save_image, android.widget.Toast.LENGTH_SHORT).show();
        }
        updateChatBackgroundLabels();
    }

    private void restartSelf() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }
}
