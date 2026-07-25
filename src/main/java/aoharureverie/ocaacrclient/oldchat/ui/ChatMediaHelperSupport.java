package aoharureverie.ocaacrclient.oldchat.ui;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.util.ImageCompressUtil;

import org.json.JSONObject;

import java.io.File;

class ChatMediaHelperSupport extends ChatMediaHelperVideoSupport {
    private static final int MAX_VOICE_DURATION_MS = 60000;

    ChatMediaHelperSupport(Activity activity, String token, EditText etInput, Button btnSend,
                           View btnToggleVoice, View btnPlus, View btnHoldToTalk, View actionPanel,
                           View btnActionImage, View btnActionVideo, View videoProgressContainer,
                           ProgressBar videoProgressBar, TextView videoProgressText,
                           ChatMediaHelper.MediaCallback callback,
                           ChatMediaHelper.SendStateListener sendStateListener) {
        super(activity, token, etInput, btnSend, btnToggleVoice, btnPlus, btnHoldToTalk, actionPanel,
                btnActionImage, btnActionVideo, videoProgressContainer, videoProgressBar,
                videoProgressText, callback, sendStateListener);
    }

    protected void scheduleRecordTimeout() {
        cancelRecordTimeout();
        recordTimeout = new Runnable() {
            @Override
            public void run() {
                if (recorder != null) {
                    recordLimitReached = true;
                    stopRecording(true);
                }
            }
        };
        recordHandler.postDelayed(recordTimeout, MAX_RECORD_MS);
    }

    protected void cancelRecordTimeout() {
        if (recordTimeout != null) {
            recordHandler.removeCallbacks(recordTimeout);
            recordTimeout = null;
        }
    }

    protected void uploadVoice(File file, long durationMs) {
        int safeDuration = (int) durationMs;
        if (safeDuration > MAX_VOICE_DURATION_MS) {
            safeDuration = MAX_VOICE_DURATION_MS;
        }
        if (safeDuration < 1) {
            safeDuration = 1;
        }
        final int durationFinal = safeDuration;
        try {
            notifySending(true);
            byte[] data = readAllBytes(file);
            HttpUtil.postMultipart("/media", data, "voice.3gp", "audio/3gpp", token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        String url = obj.optString("url");
                        if (callback != null) {
                            callback.onMediaReady("voice", url, "", durationFinal);
                        }
                    } catch (Exception e) {
                        Toast.makeText(activity, "发送语音失败", Toast.LENGTH_SHORT).show();
                    }
                    notifySending(false);
                }

                @Override
                public void onError(int code, String error) {
                    notifySending(false);
                    if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                        return;
                    }
                    String lower = error == null ? "" : error.toLowerCase();
                    if (code == 403) {
                        if (lower.contains("video_disabled")) {
                            Toast.makeText(activity, "服务器禁用了视频/3GP上传，请联系管理员", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (lower.contains("user_banned")) {
                            Toast.makeText(activity, "账号已被封禁，无法发送语音", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    Toast.makeText(activity, "发送语音失败: " + code, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(activity, "发送语音失败", Toast.LENGTH_SHORT).show();
            notifySending(false);
        }
    }

    protected void uploadImage(Uri uri) {
        try {
            notifySending(true);
            final Toast progressToast = Toast.makeText(activity, "上传中 0%", Toast.LENGTH_SHORT);
            progressToast.show();

            byte[] normal = ImageCompressUtil.compressToBytes(activity.getContentResolver(), uri, 1280, MAX_IMAGE_BYTES);
            byte[] thumb = ImageCompressUtil.compressToBytes(activity.getContentResolver(), uri, 320, MAX_IMAGE_BYTES);
            if (normal == null || normal.length == 0 || normal.length > MAX_IMAGE_UPLOAD_BYTES) {
                progressToast.cancel();
                Toast.makeText(activity, "图片不能超过3MB", Toast.LENGTH_SHORT).show();
                notifySending(false);
                return;
            }
            if (thumb != null && thumb.length > MAX_IMAGE_UPLOAD_BYTES) {
                progressToast.cancel();
                Toast.makeText(activity, "图片不能超过3MB", Toast.LENGTH_SHORT).show();
                notifySending(false);
                return;
            }

            final long[] lastProgressAt = new long[]{0L};
            final int[] lastProgressPercent = new int[]{-1};

            HttpUtil.postMultipartWithThumb("/media", normal, "image.jpg", "image/jpeg",
                    thumb, "thumb.jpg", "image/jpeg", token,
                    new HttpUtil.ProgressCallback() {
                        @Override
                        public void onProgress(long written, long total) {
                            if (total <= 0) {
                                return;
                            }
                            final int percent = (int) ((written * 100) / total);
                            long now = System.currentTimeMillis();
                            boolean percentChanged = percent != lastProgressPercent[0];
                            boolean dueByTime = now - lastProgressAt[0] >= 180;
                            if (!percentChanged && !dueByTime && percent < 100) {
                                return;
                            }
                            lastProgressAt[0] = now;
                            lastProgressPercent[0] = percent;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressToast.setText("上传中 " + percent + "%");
                                    progressToast.show();
                                }
                            });
                        }
                    },
                    new HttpUtil.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            try {
                                progressToast.cancel();
                                JSONObject obj = new JSONObject(response);
                                String url = obj.optString("url");
                                String thumbUrl = obj.optString("thumb_url");
                                if (callback != null) {
                                    callback.onMediaReady("image", url, thumbUrl, 0);
                                }
                            } catch (Exception e) {
                                Toast.makeText(activity, "发送图片失败", Toast.LENGTH_SHORT).show();
                            }
                            notifySending(false);
                        }

                        @Override
                        public void onError(int code, String error) {
                            progressToast.cancel();
                            notifySending(false);
                            if (HttpUtil.shouldSuppressAuthToast(code, error)) {
                                return;
                            }
                            if (code == 413 || (error != null && error.contains("image_too_large"))) {
                                Toast.makeText(activity, "图片不能超过3MB", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Toast.makeText(activity, "发送图片失败: " + code, Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(activity, "发送图片失败", Toast.LENGTH_SHORT).show();
            notifySending(false);
        }
    }
}
