package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogManager {
    private static final String TAG = "OldChat";
    private static final String LOG_DIR = "logs";
    private static final int MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_LOGS = 5;
    private static LogManager instance;
    private final Queue<String> logQueue = new ConcurrentLinkedQueue<>();
    private final Context context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private final SimpleDateFormat fileFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private File currentLogFile;
    private BufferedWriter writer;

    private LogManager(Context context) {
        this.context = context.getApplicationContext();
        initLogFile();
    }

    public static synchronized LogManager getInstance(Context context) {
        if (instance == null) {
            instance = new LogManager(context);
        }
        return instance;
    }

    private void initLogFile() {
        try {
            File logDir = new File(context.getFilesDir(), LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String fileName = "log_" + fileFormat.format(new Date()) + ".txt";
            currentLogFile = new File(logDir, fileName);

            // 检查文件大小，如果超过限制则创建新文件
            if (currentLogFile.exists() && currentLogFile.length() > MAX_LOG_SIZE) {
                rotateLogFile(logDir);
            }

            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            Log.e(TAG, "Failed to init log file", e);
        }
    }

    private void rotateLogFile(File logDir) {
        try {
            if (writer != null) {
                writer.close();
            }

            File[] files = logDir.listFiles(new java.io.FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("log_") && name.endsWith(".txt");
                }
            });
            if (files != null && files.length >= MAX_LOGS) {
                // 删除最旧的文件
                File oldest = files[0];
                for (File f : files) {
                    if (f.lastModified() < oldest.lastModified()) {
                        oldest = f;
                    }
                }
                oldest.delete();
            }

            String fileName = "log_" + fileFormat.format(new Date()) + "_" + System.currentTimeMillis() + ".txt";
            currentLogFile = new File(logDir, fileName);
            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            Log.e(TAG, "Failed to rotate log file", e);
        }
    }

    public void d(String tag, String msg) {
        log("D", tag, msg, null);
    }

    public void i(String tag, String msg) {
        log("I", tag, msg, null);
    }

    public void w(String tag, String msg) {
        log("W", tag, msg, null);
    }

    public void e(String tag, String msg, Throwable tr) {
        log("E", tag, msg, tr);
    }

    public void e(String tag, String msg) {
        log("E", tag, msg, null);
    }

    private void log(String level, String tag, String msg, Throwable tr) {
        String timestamp = dateFormat.format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(" [").append(level).append("] ");
        sb.append(tag).append(": ").append(msg);

        if (tr != null) {
            sb.append("\n").append(getStackTrace(tr));
        }

        String logMsg = sb.toString();
        logQueue.offer(logMsg);

        // 同时输出到Logcat
        switch (level) {
            case "D":
                Log.d(tag, msg);
                break;
            case "I":
                Log.i(tag, msg);
                break;
            case "W":
                Log.w(tag, msg);
                break;
            case "E":
                Log.e(tag, msg, tr);
                break;
        }

        // 异步写入文件
        writeLogAsync(logMsg);
    }

    private void writeLogAsync(final String logMsg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (writer != null) {
                        writer.write(logMsg);
                        writer.newLine();
                        writer.flush();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write log", e);
                }
            }
        }).start();
    }

    private String getStackTrace(Throwable tr) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = tr.getStackTrace();
        for (StackTraceElement element : elements) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    public File getLogFile() {
        return currentLogFile;
    }

    public File getLogDir() {
        return new File(context.getFilesDir(), LOG_DIR);
    }

    public void clearLogs() {
        try {
            if (writer != null) {
                writer.close();
            }
            File logDir = getLogDir();
            if (logDir.exists()) {
                File[] files = logDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
            initLogFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to clear logs", e);
        }
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close log writer", e);
        }
    }
}
