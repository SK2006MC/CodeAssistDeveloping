package com.tyron.code.ui.editor.impl.image;

import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.tyron.fileeditor.api.FileEditor;

import java.io.File;
import java.util.Objects;

/**
 * ImageEditor implementation supporting both local File and SAF Uri.
 */
public class ImageEditor implements FileEditor {

    private final File mFile;
    private final Uri mUri;
    private final ImageEditorProvider mProvider;
    private ImageEditorFragment mFragment;

    // Constructor for local File
    public ImageEditor(@NonNull File file, ImageEditorProvider provider) {
        mFile = file;
        mUri = null;
        mProvider = provider;
        mFragment = createFragment(file, null);
    }

    // Constructor for SAF Uri
    public ImageEditor(@NonNull Uri uri, ImageEditorProvider provider) {
        mFile = null;
        mUri = uri;
        mProvider = provider;
        mFragment = createFragment(null, uri);
    }

    protected ImageEditorFragment createFragment(File file, Uri uri) {
        if (file != null) {
            return ImageEditorFragment.newInstance(file);
        }
        if (uri != null) {
            return ImageEditorFragment.newInstance(uri);
        }
        throw new IllegalArgumentException("Both file and uri are null");
    }

    @Override
    public Fragment getFragment() {
        return mFragment;
    }

    @Override
    public View getView() {
        // If you have a custom view inside fragment and want to expose it
        return mFragment != null ? mFragment.getView() : null;
    }

    @Override
    public View getPreferredFocusedView() {
        // Focus on fragment's view if exists
        return mFragment != null ? mFragment.getView() : null;
    }

    @NonNull
    @Override
    public String getName() {
        return "Image Editor";
    }

    @Override
    public boolean isModified() {
        // For images, typically editing is not supported, so always false
        return false;
    }

    @Override
    public boolean isValid() {
        // Valid if either file or uri is not null
        return mFile != null || mUri != null;
    }

    @Override
    public File getFile() {
        return mFile;
    }

    // SAF support: getUri if using SAF
    public Uri getUri() {
        return mUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageEditor that = (ImageEditor) o;
        // Compare file or uri depending on usage
        return Objects.equals(mFile, that.mFile) && Objects.equals(mUri, that.mUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFile, mUri);
    }
}
