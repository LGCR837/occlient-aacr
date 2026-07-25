package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import aoharureverie.ocaacrclient.oldchat.BaseActivity;
import android.view.View;
import android.widget.Toast;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.util.EmojiBackupManager;
import aoharureverie.ocaacrclient.oldchat.util.ImageLoader;
import aoharureverie.ocaacrclient.oldchat.util.LogManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DataSettingsActivity extends BaseActivity {
    private static final int REQ_PICK_EMOJI_ZIP = 5201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_settings);

        View btnBack = (View) findViewByIdCompat(R.id.btnDataSettingsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnCacheSettings = (View) findViewByIdCompat(R.id.btnCacheSettings);
        if (btnCacheSettings != null) {
            btnCacheSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(DataSettingsActivity.this, CacheSettingsActivity.class));
                }
            });
        }

        View btnClearAvatarCache = (View) findViewByIdCompat(R.id.btnClearAvatarCache);
        if (btnClearAvatarCache != null) {
            btnClearAvatarCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ImageLoader.clearDiskCache(DataSettingsActivity.this);
                    Toast.makeText(DataSettingsActivity.this, "已清理头像缓存", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnExportEmojis = (View) findViewByIdCompat(R.id.btnExportEmojis);
        if (btnExportEmojis != null) {
            btnExportEmojis.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exportEmojis();
                }
            });
        }

        View btnImportEmojis = (View) findViewByIdCompat(R.id.btnImportEmojis);
        if (btnImportEmojis != null) {
            btnImportEmojis.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    importEmojis();
                }
            });
        }

        View btnExportLogs = (View) findViewByIdCompat(R.id.btnExportLogs);
        if (btnExportLogs != null) {
            btnExportLogs.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exportLogs();
                }
            });
        }
    }

    private void exportLogs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ZipOutputStream zos = null;
                try {
                    File logDir = LogManager.getInstance(DataSettingsActivity.this).getLogDir();
                    if (!logDir.exists() || logDir.listFiles() == null || logDir.listFiles().length == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(DataSettingsActivity.this, "没有日志文件", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
                    final File zipFile = new File(getExternalFilesDir(null), "logs_" + timestamp + ".zip");

                    zos = new ZipOutputStream(new FileOutputStream(zipFile));
                    File[] files = logDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                ZipEntry entry = new ZipEntry(file.getName());
                                zos.putNextEntry(entry);

                                FileInputStream fis = null;
                                try {
                                    fis = new FileInputStream(file);
                                    byte[] buffer = new byte[1024];
                                    int len;
                                    while ((len = fis.read(buffer)) > 0) {
                                        zos.write(buffer, 0, len);
                                    }
                                } finally {
                                    if (fis != null) {
                                        try {
                                            fis.close();
                                        } catch (Exception e) {
                                        }
                                    }
                                }
                                zos.closeEntry();
                            }
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DataSettingsActivity.this, "日志已导出到: " + zipFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DataSettingsActivity.this, "导出日志失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    if (zos != null) {
                        try {
                            zos.close();
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }).start();
    }

    private void exportEmojis() {
        Toast.makeText(this, "正在导出表情包...", Toast.LENGTH_SHORT).show();
        EmojiBackupManager.exportEmojis(this, new EmojiBackupManager.BackupCallback() {
            @Override
            public void onSuccess(String message, File file) {
                Toast.makeText(DataSettingsActivity.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DataSettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void importEmojis() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择表情包 ZIP 文件"), REQ_PICK_EMOJI_ZIP);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_EMOJI_ZIP && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Toast.makeText(this, "正在导入表情包...", Toast.LENGTH_SHORT).show();
                EmojiBackupManager.importEmojis(this, uri, new EmojiBackupManager.RestoreCallback() {
                    @Override
                    public void onSuccess(String message, int count) {
                        Toast.makeText(DataSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(DataSettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}
