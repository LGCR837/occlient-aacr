package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.widget.EditText;

final class ChatDraftHelper {
    private ChatDraftHelper() {
    }

    static String restoreDraft(Context context, EditText editText, String key) {
        if (context == null || editText == null || key == null || key.length() == 0) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences("drafts", Context.MODE_PRIVATE);
        String draft = prefs.getString(key, "");
        if (draft == null || draft.length() == 0) {
            return "";
        }
        Editable current = editText.getText();
        if (current != null && current.length() > 0) {
            return current.toString();
        }
        editText.setText(draft);
        editText.setSelection(draft.length());
        return draft;
    }

    static String saveDraft(Context context, String key, String text, String lastDraft) {
        if (context == null || key == null || key.length() == 0) {
            return lastDraft == null ? "" : lastDraft;
        }
        String value = text == null ? "" : text;
        String old = lastDraft == null ? "" : lastDraft;
        if (value.equals(old)) {
            return old;
        }
        SharedPreferences prefs = context.getSharedPreferences("drafts", Context.MODE_PRIVATE);
        if (value.trim().length() == 0) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, value).apply();
        }
        return value;
    }

    static String saveDraftFromInput(Context context, EditText editText, String key, String lastDraft) {
        if (editText == null) {
            return lastDraft == null ? "" : lastDraft;
        }
        Editable editable = editText.getText();
        String text = editable == null ? "" : editable.toString();
        return saveDraft(context, key, text, lastDraft);
    }
}
