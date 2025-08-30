package com.tyron.code.ui.editor.impl;

import android.content.Context;
import android.net.Uri;

import com.tyron.code.ui.editor.impl.text.rosemoe.ContentWrapper;
import com.tyron.editor.Content;
import com.tyron.fileeditor.api.impl.FileDocumentContentProvider;

import org.apache.commons.vfs2.FileObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Provides content from a text, local file, or SAF Uri for the editor.
 * Works properly on Android 11+ for public folders using SAF.
 */
public class DefaultFileDocumentContentProvider implements FileDocumentContentProvider {

    private final Context context;

    /**
     * Pass application or activity context for SAF operations.
     * @param context Context for ContentResolver and SAF access
     */
    public DefaultFileDocumentContentProvider(Context context) {
        this.context = context;
    }

    /**
     * Creates content from a CharSequence, FileObject (local file), or Uri (SAF/public file).
     *
     * @param text the direct text (if available)
     * @param file the local file (VFS)
     * @param uri  the SAF Uri (for public storage)
     * @return Content for the editor
     */
    public Content createContent(CharSequence text, FileObject file, Uri uri) {
        // 1. If direct text is provided, use it
        if (text != null) {
            return new ContentWrapper(text);
        }

        // 2. Try reading from FileObject (local file)
        if (file != null) {
            try (InputStream inputStream = file.getContent().getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return new ContentWrapper(sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
                return new ContentWrapper("");
            }
        }

        // 3. Try reading from SAF Uri (public file)
        if (uri != null && context != null) {
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return new ContentWrapper(sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
                return new ContentWrapper("");
            }
        }

        // 4. If nothing is available, return empty content
        return new ContentWrapper("");
    }

    /**
     * Legacy method for backward compatibility (only text and FileObject).
     * For new usage, call the overloaded method with Uri support.
     */
    @Override
    public Content createContent(CharSequence text, FileObject file) {
        return createContent(text, file, null);
    }
}
