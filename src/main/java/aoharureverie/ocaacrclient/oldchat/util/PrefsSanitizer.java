package aoharureverie.ocaacrclient.oldchat.util;

import android.content.Context;
import android.util.Xml;
import java.io.File;
import java.io.FileInputStream;
import org.xmlpull.v1.XmlPullParser;

public final class PrefsSanitizer {
    private PrefsSanitizer() {
    }

    public static void sanitize(Context context) {
        if (context == null) {
            return;
        }
        File dir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name == null || !name.endsWith(".xml")) {
                continue;
            }
            if (file.length() == 0) {
                safeDelete(file);
                continue;
            }
            if (!isWellFormedXml(file)) {
                File broken = new File(file.getParent(), name + ".broken");
                try {
                    file.renameTo(broken);
                } catch (Exception e) {
                }
                safeDelete(file);
            }
        }
    }

    private static boolean isWellFormedXml(File file) {
        FileInputStream in = null;
        try {
            XmlPullParser parser = Xml.newPullParser();
            in = new FileInputStream(file);
            parser.setInput(in, "UTF-8");
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                // just iterate to validate
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private static void safeDelete(File file) {
        try {
            file.delete();
        } catch (Exception e) {
        }
    }
}
