package com.tyron.code.ui.editor.impl.image;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import com.tyron.fileeditor.api.FileEditor;
import com.tyron.fileeditor.api.FileEditorProvider;

import java.io.File;

/**
 * ImageEditorProvider supporting both local File and SAF Uri images.
 */
public class ImageEditorProvider implements FileEditorProvider {

    private static final String TYPE_ID = "image-editor";

    @Override
    public boolean accept(@NonNull File file) {
        if (file.isDirectory()) {
            return false;
        }
        String name = file.getName();
        return isImageFile(name);
    }

    /**
     * SAF support: Accepts image files by Uri.
     */
    public boolean accept(@NonNull Context context, @NonNull Uri uri) {
        String name = getFileNameFromUri(context, uri);
        return name != null && isImageFile(name);
    }

    @NonNull
    @Override
    public FileEditor createEditor(@NonNull Context context, @NonNull File file) {
        return new ImageEditor(file, this);
    }

    /**
     * SAF support: Create editor for Uri.
     */
    @NonNull
    public FileEditor createEditor(@NonNull Context context, @NonNull Uri uri) {
        return new ImageEditor(uri, this);
    }

    @NonNull
    @Override
    public String getEditorTypeId() {
        return TYPE_ID;
    }

    /** Helper to check valid image extensions */
    private boolean isImageFile(String name) {
        if (name == null || !name.contains(".")) {
            return false;
        }
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "png":
            case "jpg":
            case "jpeg":
            case "bmp":
            case "webp":
            case "gif":
                return true;
        }
        return false;
    }

    /** Helper to get file name from Uri (for SAF files) */
    private String getFileNameFromUri(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                // Ignore, fallback below
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
}
