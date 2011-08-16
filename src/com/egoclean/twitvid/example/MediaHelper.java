package com.egoclean.twitvid.example;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class MediaHelper {
    public static String getVideoName(Context context, Uri media) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(media, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
        if (c == null || (!c.moveToFirst())) {
            return null;
        }
        return c.getString(c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
    }

    public static String getVideoType(Context context, Uri media) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(media, new String[]{MediaStore.MediaColumns.MIME_TYPE}, null, null, null);
        if (c == null || (!c.moveToFirst())) {
            return null;
        }
        return c.getString(c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE));
    }
}
