package aoharureverie.ocaacrclient.oldchat.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageCompressUtil {
    private ImageCompressUtil() {
    }

    public static byte[] compressToBytes(ContentResolver resolver, Uri uri, int maxSizePx, int maxBytes) throws Exception {
        Bitmap bitmap = decodeSampledBitmap(resolver, uri, maxSizePx, maxSizePx);
        if (bitmap == null) {
            throw new Exception("decode failed");
        }
        return compressBitmap(bitmap, maxBytes);
    }

    private static byte[] compressBitmap(Bitmap bitmap, int maxBytes) {
        Bitmap working = bitmap;
        int quality = 96;
        byte[] data = compressOnce(working, quality);
        for (int i = 0; i < 8 && data.length > maxBytes && quality > 82; i++) {
            quality -= 3;
            data = compressOnce(working, quality);
        }

        int scaleAttempts = 0;
        while (data.length > maxBytes && scaleAttempts < 8) {
            int w = Math.max(1, (int) (working.getWidth() * 0.90f));
            int h = Math.max(1, (int) (working.getHeight() * 0.90f));
            Bitmap scaled = Bitmap.createScaledBitmap(working, w, h, true);
            if (working != bitmap) {
                working.recycle();
            }
            working = scaled;
            quality = 94;
            data = compressOnce(working, quality);
            for (int i = 0; i < 8 && data.length > maxBytes && quality > 82; i++) {
                quality -= 3;
                data = compressOnce(working, quality);
            }
            scaleAttempts++;
        }

        if (working != bitmap) {
            working.recycle();
        }
        bitmap.recycle();
        return data;
    }

    private static byte[] compressOnce(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }

    private static Bitmap decodeSampledBitmap(ContentResolver resolver, Uri uri, int reqWidth, int reqHeight) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream is = resolver.openInputStream(uri);
        BitmapFactory.decodeStream(is, null, options);
        if (is != null) {
            is.close();
        }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        InputStream is2 = resolver.openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(is2, null, options);
        if (is2 != null) {
            is2.close();
        }
        return bitmap;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
