package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.PopupMenu;
import android.view.View;
import android.widget.Toast;
import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.Message;
import aoharureverie.ocaacrclient.oldchat.util.DownloadUtil;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;
import aoharureverie.ocaacrclient.oldchat.util.SettingsPrefs;

public class MessageActionBinder {
    public interface RecallChecker {
        boolean canRecall(Message msg);
    }

    public static void bind(final Context context,
                            View bubble,
                            View text,
                            View image,
                            View voiceRow,
                            final Message msg,
                            final MessagePayload payload,
                            final boolean isMine,
                            final MessageAdapter.MessageActionListener actionListener,
                            final RecallChecker recallChecker) {
        if (actionListener == null) {
            return;
        }
        View.OnLongClickListener listener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ContextThemeWrapper themeWrapper = new ContextThemeWrapper(
                        context,
                        SettingsPrefs.isDarkModeEnabled(context)
                                ? R.style.PopupMenuOverlayDark
                                : R.style.PopupMenuOverlayLight);
                PopupMenu menu = new PopupMenu(themeWrapper, v);

                final String msgType = msg.msg_type == null ? "text" : msg.msg_type.toLowerCase();
                if ("text".equals(msgType)) {
                    menu.getMenu().add("复制");
                }
                menu.getMenu().add("引用");
                if (!isMine) {
                    menu.getMenu().add("举报");
                }

                boolean isEmoji = payload != null && "emoji".equals(payload.mediaKind);
                boolean isImage = "image".equals(msgType);
                boolean canFavoriteMedia = ("image".equals(msgType) || "voice".equals(msgType) || "video".equals(msgType))
                        && msg.media_url != null && msg.media_url.length() > 0;
                if ((isImage || isEmoji) && msg.media_url != null && msg.media_url.length() > 0) {
                    menu.getMenu().add("保存到下载");
                }
                if (!isMine && isEmoji && msg.media_url != null && msg.media_url.length() > 0) {
                    menu.getMenu().add("保存表情");
                }
                if (canFavoriteMedia) {
                    menu.getMenu().add("收藏");
                }

                if (isMine && recallChecker != null && recallChecker.canRecall(msg)) {
                    menu.getMenu().add("撤回");
                }

                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(android.view.MenuItem item) {
                        String title = item.getTitle().toString();
                        if ("复制".equals(title)) {
                            actionListener.onCopy(payload != null ? payload.text : msg.body);
                        } else if ("引用".equals(title)) {
                            actionListener.onQuote(msg);
                        } else if ("举报".equals(title)) {
                            MessageReportHelper.reportDirectMessage(context, msg);
                        } else if ("保存到下载".equals(title)) {
                            saveMediaToDownloads(context, msg, isEmoji);
                        } else if ("保存表情".equals(title)) {
                            saveEmojiFromMessage(context, msg);
                        } else if ("收藏".equals(title)) {
                            FavoriteHelper.addChatMediaFavorite(context,
                                    msgType,
                                    msg.id,
                                    msg.from_uid,
                                    msg.media_url,
                                    "direct");
                        } else if ("撤回".equals(title)) {
                            actionListener.onRecall(msg);
                        }
                        return true;
                    }
                });

                menu.show();
                return true;
            }
        };

        bubble.setOnLongClickListener(listener);
        text.setOnLongClickListener(listener);
        image.setOnLongClickListener(listener);
        voiceRow.setOnLongClickListener(listener);
    }

    private static void saveEmojiFromMessage(final Context context, Message msg) {
        if (msg == null || msg.media_url == null || msg.media_url.length() == 0) {
            return;
        }
        boolean isGif = isGifUrl(msg.media_url);
        EmojiStore.saveFromUrlAsync(context, msg.media_url, isGif, new EmojiStore.SaveCallback() {
            @Override
            public void onResult(boolean success, String message) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void saveMediaToDownloads(final Context context, Message msg, boolean isEmoji) {
        if (msg == null || msg.media_url == null || msg.media_url.length() == 0) {
            return;
        }
        String fallbackExt = isEmoji && isGifUrl(msg.media_url) ? ".gif" : ".jpg";
        DownloadUtil.saveUrlToDownloadsAsync(context, msg.media_url, "oldchat_img_", fallbackExt,
                new DownloadUtil.Callback() {
                    @Override
                    public void onResult(boolean success, String message, java.io.File file) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static boolean isGifUrl(String url) {
        return url != null && url.toLowerCase().contains(".gif");
    }
}
