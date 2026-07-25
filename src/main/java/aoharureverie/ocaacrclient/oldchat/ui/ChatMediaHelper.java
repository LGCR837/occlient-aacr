package aoharureverie.ocaacrclient.oldchat.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;

import java.io.File;

public class ChatMediaHelper extends ChatMediaHelperSupport {
    private static final float VOICE_CANCEL_DISTANCE_DP = 48f;
    private static final float VOICE_CANCEL_MARGIN_DP = 16f;

    private boolean recordCancelPending = false;
    private boolean pendingRecordAfterPermission = false;
    private float downRawY = 0f;

    public interface MediaCallback {
        void onMediaReady(String type, String url, String thumbUrl, int durationMs);
    }

    public interface SendStateListener {
        void onSendState(boolean sending);
    }

    public ChatMediaHelper(Activity activity, String token, EditText etInput, Button btnSend,
                           View btnToggleVoice, View btnPlus, View btnHoldToTalk, View actionPanel,
                           View btnActionImage, View btnActionVideo, View videoProgressContainer,
                           ProgressBar videoProgressBar, TextView videoProgressText, MediaCallback callback,
                           SendStateListener sendStateListener) {
        super(activity, token, etInput, btnSend, btnToggleVoice, btnPlus, btnHoldToTalk, actionPanel,
                btnActionImage, btnActionVideo, videoProgressContainer, videoProgressBar,
                videoProgressText, callback, sendStateListener);
    }

    public void bind() {
        if (btnToggleVoice != null) {
            btnToggleVoice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleVoiceMode();
                }
            });
        }
        if (btnPlus != null) {
            btnPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleActionPanel();
                }
            });
        }
        if (btnActionImage != null) {
            btnActionImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickImage();
                }
            });
        }
        if (btnActionVideo != null) {
            btnActionVideo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickVideo();
                }
            });
        }
        if (btnHoldToTalk != null) {
            btnHoldToTalk.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    int action = event.getAction();
                    if (action == MotionEvent.ACTION_DOWN) {
                        pendingRecordAfterPermission = true;
                        recordCancelPending = false;
                        downRawY = event.getRawY();
                        boolean granted = ensureAudioPermission();
                        if (granted) {
                            setHoldToTalkPressed(true);
                            startRecording();
                        } else {
                            setHoldToTalkPressed(false);
                        }
                        return true;
                    }
                    if (action == MotionEvent.ACTION_MOVE) {
                        if (recorder != null) {
                            updateRecordCancelState(v, event);
                        }
                        return true;
                    }
                    if (action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        pendingRecordAfterPermission = false;
                        boolean cancelByGesture = recorder != null && (action == MotionEvent.ACTION_CANCEL || recordCancelPending);
                        boolean showCancelToast = recorder != null && recordCancelPending;
                        setHoldToTalkPressed(false);
                        stopRecording(!cancelByGesture);
                        resetRecordGestureState();
                        if (showCancelToast) {
                            Toast.makeText(activity, R.string.chat_voice_send_canceled, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                    return true;
                }
            });
        }
    }

    public void onPause() {
        if (recorder != null) {
            stopRecording(false);
        }
    }

    public void hideActionPanel() {
        actionsVisible = false;
        if (actionPanel != null) {
            actionPanel.setVisibility(View.GONE);
        }
    }

    public boolean handlePermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingRecordAfterPermission) {
                    setHoldToTalkPressed(true);
                    startRecording();
                }
            } else {
                setHoldToTalkPressed(false);
                Toast.makeText(activity, "未授权录音权限", Toast.LENGTH_SHORT).show();
            }
            pendingRecordAfterPermission = false;
            return true;
        }
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingPick == PICK_VIDEO) {
                    pickVideo();
                } else {
                    pickImage();
                }
            } else {
                Toast.makeText(activity, "未授权读取存储", Toast.LENGTH_SHORT).show();
            }
            pendingPick = PICK_NONE;
            return true;
        }
        return false;
    }

    public boolean handleActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQ_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                uploadImage(uri);
            }
            return true;
        }
        if (requestCode == REQ_PICK_VIDEO && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                uploadVideo(uri);
            }
            return true;
        }
        return false;
    }

    private void toggleVoiceMode() {
        isVoiceMode = !isVoiceMode;
        if (isVoiceMode) {
            etInput.setVisibility(View.GONE);
            btnSend.setVisibility(View.GONE);
            btnHoldToTalk.setVisibility(View.VISIBLE);
            if (btnToggleVoice instanceof ImageView) {
                ((ImageView) btnToggleVoice).setImageResource(R.drawable.chatting_setmode_keyboard_btn_normal);
            }
            if (actionsVisible) {
                actionsVisible = false;
                if (actionPanel != null) {
                    actionPanel.setVisibility(View.GONE);
                }
            }
        } else {
            etInput.setVisibility(View.VISIBLE);
            btnSend.setVisibility(View.VISIBLE);
            btnHoldToTalk.setVisibility(View.GONE);
            if (btnToggleVoice instanceof ImageView) {
                ((ImageView) btnToggleVoice).setImageResource(R.drawable.chatting_setmode_voice_btn_normal);
            }
        }
    }

    private void toggleActionPanel() {
        boolean nextVisible = !actionsVisible;
        actionsVisible = nextVisible;
        if (nextVisible) {
            hideKeyboard();
            if (etInput != null) {
                etInput.clearFocus();
            }
        } else {
            hideKeyboard();
            if (etInput != null) {
                etInput.clearFocus();
            }
        }
        if (actionPanel != null) {
            actionPanel.setVisibility(nextVisible ? View.VISIBLE : View.GONE);
        }
    }

    private void hideKeyboard() {
        if (activity == null) {
            return;
        }
        View anchor = etInput != null ? etInput : (actionPanel != null ? actionPanel : btnPlus);
        if (anchor == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(anchor.getWindowToken(), 0);
        }
    }

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        return false;
    }

    private void setHoldToTalkPressed(boolean pressed) {
        if (btnHoldToTalk == null) {
            return;
        }
        btnHoldToTalk.setPressed(pressed);
        ViewCompat.setAlpha(btnHoldToTalk, pressed ? 0.68f : 1.0f);
    }

    private void pickImage() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        activity.startActivityForResult(android.content.Intent.createChooser(intent, "选择图片"), REQ_PICK_IMAGE);
    }

    private void pickVideo() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        activity.startActivityForResult(android.content.Intent.createChooser(intent, "选择视频"), REQ_PICK_VIDEO);
    }

    private void startRecording() {
        if (recorder != null) {
            return;
        }
        try {
            recordCancelPending = false;
            File dir = new File(activity.getCacheDir(), "voice");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            recordingFile = new File(dir, "voice_" + System.currentTimeMillis() + ".3gp");
            recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recordStartMs = System.currentTimeMillis();
            recordLimitReached = false;
            scheduleRecordTimeout();
            if (btnHoldToTalk instanceof TextView) {
                ((TextView) btnHoldToTalk).setText(R.string.chat_hold_release);
            }
        } catch (Exception e) {
            recorder = null;
            recordCancelPending = false;
            setHoldToTalkPressed(false);
            Toast.makeText(activity, "录音失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void stopRecording(boolean send) {
        if (recorder == null) {
            setHoldToTalkPressed(false);
            recordCancelPending = false;
            if (btnHoldToTalk instanceof TextView) {
                ((TextView) btnHoldToTalk).setText(R.string.chat_hold_to_talk);
            }
            return;
        }
        try {
            recorder.stop();
        } catch (Exception e) {
        }
        recorder.release();
        recorder = null;
        cancelRecordTimeout();
        setHoldToTalkPressed(false);
        recordCancelPending = false;
        if (btnHoldToTalk instanceof TextView) {
            ((TextView) btnHoldToTalk).setText(R.string.chat_hold_to_talk);
        }
        long duration = System.currentTimeMillis() - recordStartMs;
        if (duration > MAX_RECORD_MS) {
            duration = MAX_RECORD_MS;
        }
        if (!send) {
            if (recordingFile != null && recordingFile.exists()) {
                recordingFile.delete();
            }
            return;
        }
        if (duration < 800) {
            if (recordingFile != null && recordingFile.exists()) {
                recordingFile.delete();
            }
            Toast.makeText(activity, "录音时间太短", Toast.LENGTH_SHORT).show();
            return;
        }
        if (recordingFile != null && recordingFile.exists()) {
            uploadVoice(recordingFile, duration);
        }
        if (recordLimitReached) {
            Toast.makeText(activity, "已达到60秒，已自动发送", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRecordCancelState(View touchView, MotionEvent event) {
        if (touchView == null || event == null) {
            return;
        }
        float cancelDistancePx = dpToPx(VOICE_CANCEL_DISTANCE_DP);
        float moveUp = downRawY - event.getRawY();
        float cancelMarginPx = dpToPx(VOICE_CANCEL_MARGIN_DP);
        float x = event.getX();
        float y = event.getY();
        boolean movedOut = x < -cancelMarginPx
                || x > touchView.getWidth() + cancelMarginPx
                || y < -cancelMarginPx
                || y > touchView.getHeight() + cancelMarginPx;
        boolean nextCancel = moveUp >= cancelDistancePx || movedOut;
        if (nextCancel == recordCancelPending) {
            return;
        }
        recordCancelPending = nextCancel;
        if (btnHoldToTalk instanceof TextView) {
            ((TextView) btnHoldToTalk).setText(nextCancel ? R.string.chat_hold_cancel : R.string.chat_hold_release);
        }
        if (btnHoldToTalk != null) {
            ViewCompat.setAlpha(btnHoldToTalk, nextCancel ? 0.52f : 0.68f);
        }
    }

    private void resetRecordGestureState() {
        recordCancelPending = false;
        downRawY = 0f;
    }

    private float dpToPx(float dp) {
        if (activity == null) {
            return dp;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        if (density <= 0f) {
            density = 1f;
        }
        return dp * density;
    }
}
