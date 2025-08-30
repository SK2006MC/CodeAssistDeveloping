package com.tyron.code.ui.editor.impl;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tyron.code.R;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.fileeditor.api.FileEditor;
import com.tyron.fileeditor.api.FileEditorManager;
import com.tyron.fileeditor.api.FileEditorProvider;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Implementation of FileEditorManager supporting both local files and SAF Uri files.
 */
public class FileEditorManagerImpl extends FileEditorManager {

    private static volatile FileEditorManager sInstance = null;

    public static synchronized FileEditorManager getInstance() {
        if (sInstance == null) {
            sInstance = new FileEditorManagerImpl();
        }
        return sInstance;
    }

    private MainViewModel mViewModel;
    private FragmentManager mFragmentManager;

    FileEditorManagerImpl() {}

    public void attach(MainViewModel mainViewModel, FragmentManager fragmentManager) {
        mViewModel = mainViewModel;
        mFragmentManager = fragmentManager;
    }

    // ---------- Local File Support ----------

    @Override
    public void openFile(@NonNull Context context, File file, Consumer<FileEditor> callback) {
        checkAttached();
        FileEditor[] fileEditors = getFileEditors(context, file);
        openChooser(context, fileEditors, callback);
    }

    @NonNull
    @Override
    public FileEditor[] openFile(@NonNull Context context, @NonNull File file, boolean focus) {
        checkAttached();
        FileEditor[] editors = getFileEditors(context, file);
        openChooser(context, editors, this::openFileEditor);
        return editors;
    }

    @Override
    public FileEditor[] getFileEditors(Context context, @NonNull File file) {
        FileEditorProvider[] providers = FileEditorProviderManagerImpl.getInstance().getProviders(file);
        FileEditor[] editors = new FileEditor[providers.length];
        for (int i = 0; i < providers.length; i++) {
            FileEditor editor = providers[i].createEditor(context, file);
            editors[i] = editor;
        }
        return editors;
    }

    @Override
    public void openFileEditor(@NonNull FileEditor fileEditor) {
        mViewModel.openFile(fileEditor);
    }

    @Override
    public void closeFile(@NonNull File file) {
        mViewModel.removeFile(file);
    }

    // ---------- SAF Uri File Support ----------

    /**
     * Open SAF Uri file with callback.
     */
    public void openFile(@NonNull Context context, Uri uri, Consumer<FileEditor> callback) {
        checkAttached();
        FileEditor[] fileEditors = getFileEditors(context, uri);
        openChooser(context, fileEditors, callback);
    }

    /**
     * Open SAF Uri file and return editors.
     */
    @NonNull
    public FileEditor[] openFile(@NonNull Context context, @NonNull Uri uri, boolean focus) {
        checkAttached();
        FileEditor[] editors = getFileEditors(context, uri);
        openChooser(context, editors, this::openFileEditor);
        return editors;
    }

    /**
     * Get FileEditors for SAF Uri file.
     */
    public FileEditor[] getFileEditors(Context context, @NonNull Uri uri) {
        FileEditorProvider[] providers = FileEditorProviderManagerImpl.getInstance().getProviders(uri);
        FileEditor[] editors = new FileEditor[providers.length];
        for (int i = 0; i < providers.length; i++) {
            FileEditor editor = providers[i].createEditor(context, uri);
            editors[i] = editor;
        }
        return editors;
    }

    /**
     * Remove opened Uri file (if needed).
     */
    public void closeFile(@NonNull Uri uri) {
        mViewModel.removeFile(uri);
    }

    // ---------- Common ----------

    /**
     * Chooser dialog to select editor if multiple are available.
     */
    public void openChooser(Context context, FileEditor[] fileEditors, Consumer<FileEditor> callback) {
        if (fileEditors.length == 0) {
            return;
        }
        if (fileEditors.length > 1) {
            CharSequence[] items = Arrays.stream(fileEditors)
                    .map(FileEditor::getName)
                    .toArray(String[]::new);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.file_editor_selection_title)
                    .setItems(items, (__, which) ->
                            callback.accept(fileEditors[which]))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            callback.accept(fileEditors[0]);
        }
    }

    public FragmentManager getFragmentManager() {
        checkAttached();
        return this.mFragmentManager;
    }

    private void checkAttached() {
        if (mViewModel == null || mFragmentManager == null) {
            throw new IllegalStateException("File editor manager is not yet attached to a ViewModel");
        }
    }
}
