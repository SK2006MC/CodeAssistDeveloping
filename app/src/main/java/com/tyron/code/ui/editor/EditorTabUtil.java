package com.tyron.code.ui.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ListUpdateCallback;

import com.google.android.material.tabs.TabLayout;
import com.tyron.code.ui.editor.adapter.PageAdapter;
import com.tyron.fileeditor.api.FileEditor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;

public class EditorTabUtil {

    public static void updateTabLayout(@NonNull TabLayout mTabLayout, List<FileEditor> oldList, List<FileEditor> files) {
        PageAdapter.getDiff(oldList, files, new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                FileEditor editor = files.get(position);
                TabLayout.Tab tab = getTabLayout(editor);
                mTabLayout.addTab(tab, position, false);
                mTabLayout.selectTab(tab, true);
            }

            @Override
            public void onRemoved(int position, int count) {
                for (int i = 0; i < count; i++) {
                    mTabLayout.removeTabAt(position);
                }
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {

            }

            @Override
            public void onChanged(int position, int count, @Nullable Object payload) {

            }

            private TabLayout.Tab getTabLayout(FileEditor editor) {
                TabLayout.Tab tab = mTabLayout.newTab();
                String filename = null;

                File file = editor.getFile();
                if (file != null) {
                    filename = file.getName();
                } else if (editor.getUri() != null) {
                    // اگر FileEditor با Uri کار می‌کند، نام فایل را از DocumentFile بگیر
                    Uri fileUri = editor.getUri();
                    DocumentFile docFile = DocumentFile.fromSingleUri(mTabLayout.getContext(), fileUri);
                    if (docFile != null) {
                        filename = docFile.getName();
                    } else {
                        filename = "Unknown";
                    }
                } else {
                    filename = "Unknown";
                }

                tab.setText(filename);
                return tab;
            }
        });
    }
}
