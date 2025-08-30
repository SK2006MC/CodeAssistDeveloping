package com.tyron.code.ui.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviderKt;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.transition.TransitionManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.transition.MaterialFadeThrough;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.DataContext;
import com.tyron.actions.menu.ActionPopupMenu;
import com.tyron.builder.project.Project;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.R;
import com.tyron.code.ui.editor.impl.FileEditorManagerImpl;
import com.tyron.code.ui.main.MainFragment;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.ui.main.action.project.SaveEvent;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.util.EventManagerUtilsKt;
import com.tyron.code.util.Listeners;
import com.tyron.code.util.UiUtilsKt;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.editor.Content;
import com.tyron.editor.event.ContentEvent;
import com.tyron.editor.event.ContentListener;
import com.tyron.editor.util.EditorUtil;
import com.tyron.fileeditor.api.FileDocumentManager;
import com.tyron.fileeditor.api.FileEditor;
import com.tyron.fileeditor.api.FileEditorManager;
import com.tyron.fileeditor.api.TextEditor;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import androidx.documentfile.provider.DocumentFile;

public class EditorContainerFragment extends Fragment implements
        ProjectManager.OnProjectOpenListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String SAVE_ALL_KEY = "saveAllEditors";
    public static final String PREVIEW_KEY = "previewEditor";
    public static final String FORMAT_KEY = "formatEditor";

    private TabLayout mTabLayout;
    private FrameLayout mContainer;
    private BottomSheetBehavior<View> mBehavior;

    private MainViewModel mMainViewModel;
    private EditorContainerViewModel mEditorContainerViewModel;

    private FileEditorManager mFileEditorManager;
    private SharedPreferences pref;
    private final List<FileEditor> mEditors = new ArrayList<>();

    private final OnBackPressedCallback mOnBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            mMainViewModel.setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    };

    private DataContext mDataContext;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = ApplicationLoader.getDefaultPreferences();
        mMainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mEditorContainerViewModel = new ViewModelProvider((ViewModelStoreOwner) this).get(EditorContainerViewModel.class);
        requireActivity().getOnBackPressedDispatcher().addCallback((LifecycleOwner) this, mOnBackPressedCallback);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("bottom_sheet_state", mBehavior.getState());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        DataContext dataContext = (DataContext) requireContext();
        dataContext.putData(CommonDataKeys.PROJECT,
                ProjectManager.getInstance().getCurrentProject());
        dataContext.putData(CommonDataKeys.FRAGMENT, EditorContainerFragment.this);
        dataContext.putData(MainFragment.MAIN_VIEW_MODEL_KEY, mMainViewModel);

        CoordinatorLayout root = (CoordinatorLayout) inflater.inflate(
                R.layout.editor_container_fragment,
                container,
                false
        );
        mContainer = root.findViewById(R.id.viewpager);
        ((FileEditorManagerImpl) FileEditorManagerImpl.getInstance())
                .attach(mMainViewModel, getChildFragmentManager());
        mTabLayout = root.findViewById(R.id.tablayout);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabUnselected(TabLayout.Tab p1) {
                FileEditor currentFileEditor = mMainViewModel.getCurrentFileEditor();
                if (currentFileEditor instanceof TextEditor) {
                    FileDocumentManager instance = FileDocumentManager.getInstance();
                    // --- تغییر برای پشتیبانی SAF ---
                    saveEditorContent(currentFileEditor);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab p1) {
                ActionPopupMenu.createAndShow(
                        p1.view,
                        (DataContext) requireContext(),
                        ActionPlaces.EDITOR_TAB
                );
            }

            @Override
            public void onTabSelected(TabLayout.Tab p1) {
                updateTab(p1.getPosition());
                mMainViewModel.setCurrentPosition(p1.getPosition(), true);

                ProgressManager.getInstance().runLater(() -> getParentFragmentManager()
                        .setFragmentResult(MainFragment.REFRESH_TOOLBAR_KEY, Bundle.EMPTY), 300);
            }
        });
        View persistentSheet = root.findViewById(R.id.persistent_sheet);
        mBehavior = BottomSheetBehavior.from(persistentSheet);
        mBehavior.setGestureInsetBottomIgnored(true);

        mBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View p1, int state) {
                mMainViewModel.setBottomSheetState(state);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (isAdded()) {
                    Bundle bundle = new Bundle();
                    bundle.putFloat("offset", slideOffset);
                    getChildFragmentManager().setFragmentResult(BottomEditorFragment.OFFSET_KEY, bundle);
                }
            }
        });
        mBehavior.setHalfExpandedRatio(0.3f);
        mBehavior.setFitToContents(false);

        ProjectManager.getInstance().addOnProjectOpenListener(this);

        if (savedInstanceState != null) {
            restoreViewState(savedInstanceState);
        }
        return root;
    }

    private void updateTabs() {
        FileEditor fileEditor = mMainViewModel.getCurrentFileEditor();
        int index = mEditors.indexOf(fileEditor);
        if (index != -1) {
            updateTab(index);
        }
    }

    private void updateTab(int pos) {
        TabLayout.Tab tab = mTabLayout.getTabAt(pos);
        if (tab == null) {
            return;
        }

        List<FileEditor> fileEditors = mMainViewModel.getFiles().getValue();
        if (fileEditors == null) {
            fileEditors = Collections.emptyList();
        }
        List<File> files = fileEditors.stream().map(FileEditor::getFile).collect(Collectors.toList());
        FileEditor currentEditor =
                Objects.requireNonNull(fileEditors).get(pos);
        File current = currentEditor.getFile();

        String text = current != null ?
                EditorUtil.getUniqueTabTitle(current, files)
                : "Unknown";
        if (currentEditor.isModified()) {
            text = "*" + text;
        }

        tab.setText(text);
    }

    @Override
    public void onProjectOpen(Project project) {

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ApplicationLoader.getDefaultPreferences().registerOnSharedPreferenceChangeListener(this);

        mMainViewModel.getFiles().observe(getViewLifecycleOwner(), files -> {
            List<FileEditor> oldList = new ArrayList<>(mEditors);
            mEditors.clear();
            mEditors.addAll(files);

            TransitionManager.beginDelayedTransition(mContainer, new MaterialFadeThrough());
            if (files.isEmpty()) {
                mContainer.removeAllViews();
                mTabLayout.removeAllTabs();
                mTabLayout.setVisibility(View.GONE);
                mMainViewModel.setCurrentPosition(-1);
            } else {
                mTabLayout.setVisibility(View.VISIBLE);
                EditorTabUtil.updateTabLayout(mTabLayout, oldList, files);
            }
        });

        mMainViewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> {
            mContainer.removeAllViews();

            FileEditor currentFileEditor = mMainViewModel.getCurrentFileEditor();
            if (position == -1 || currentFileEditor == null) {
                return;
            }

            if (mTabLayout.getSelectedTabPosition() != position) {
                mTabLayout.selectTab(mTabLayout.getTabAt(position), true);
            }
            MaterialFadeThrough transition = new MaterialFadeThrough();
            transition.setDuration(150L);
            TransitionManager.beginDelayedTransition(mContainer, transition);

            UiUtilsKt.removeFromParent(currentFileEditor.getView());
            mContainer.addView(currentFileEditor.getView());

            try {
                File file = currentFileEditor.getFile();
                FileObject fileObject = VFS.getManager().toFileObject(file);
                Content content = FileDocumentManager.getInstance().getContent(fileObject);

                if (content != null) {
                    Listeners.registerListener(new ContentListener() {
                        @Override
                        public void contentChanged(@NonNull ContentEvent event) {
                           updateTabs();
                        }
                    }, getViewLifecycleOwner(), content::addContentListener, content::removeContentListener);
                }
            } catch (FileSystemException e) {
                // safe to ignore here, just don't register the listener then
            }
        });


        EventManagerUtilsKt.subscribeEvent(
                ApplicationLoader.getInstance().getEventManager(),
                getViewLifecycleOwner(),
                SaveEvent.class,
                (event, unsubscribe) -> updateTabs()
        );

        mMainViewModel.getBottomSheetState().observe(getViewLifecycleOwner(), state -> {
            if (state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING) {
                return;
            }
            mBehavior.setState(state);
            mOnBackPressedCallback.setEnabled(state == BottomSheetBehavior.STATE_EXPANDED);
        });
    }

    private void restoreViewState(@NonNull Bundle state) {
        int behaviorState = state.getInt("bottom_sheet_state", BottomSheetBehavior.STATE_COLLAPSED);
        mMainViewModel.setBottomSheetState(behaviorState);
        Bundle floatOffset = new Bundle();
        floatOffset.putFloat("offset", behaviorState == BottomSheetBehavior.STATE_EXPANDED ? 1 : 0f);
        getChildFragmentManager().setFragmentResult(BottomEditorFragment.OFFSET_KEY, floatOffset);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case SharedPreferenceKeys.EDITOR_TAB_UNIQUE_FILE_NAME:
                for (int i = 0; i < mTabLayout.getTabCount(); i++) {
                    updateTab(i);
                }
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDataContext = null;
        ApplicationLoader.getDefaultPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Nullable
    @Override
    public Context getContext() {
        Context originalContext = super.getContext();
        if (originalContext == null) {
            return null;
        }

        if (mDataContext == null) {
            mDataContext = new DataContext(originalContext);
        }
        return mDataContext;
    }

    // ----------- این متد را اضافه کن برای ذخیره محتوا با SAF یا File -----------

    private void saveEditorContent(FileEditor fileEditor) {
        if (!(fileEditor instanceof TextEditor)) return;

        Content content = ((TextEditor) fileEditor).getContent();
        if (content == null) return;
        String code = content.getText();

        File file = fileEditor.getFile();
        if (file == null) return;

        // اگر پروژه با SAF ساخته شده (مسیر پروژه عمومی)، با SAF ذخیره کن
        String projectUriStr = ApplicationLoader.getDefaultPreferences()
                .getString("project_root_uri", null);
        if (projectUriStr != null && isPublicProject(file)) {
            Uri projectRootUri = Uri.parse(projectUriStr);
            DocumentFile rootDir = DocumentFile.fromTreeUri(requireContext(), projectRootUri);
            if (rootDir != null) {
                // پیدا کردن فایل مناسب
                DocumentFile targetFile = findDocumentFile(rootDir, file.getName());
                if (targetFile != null) {
                    try (OutputStream os = requireContext().getContentResolver().openOutputStream(targetFile.getUri(), "rwt")) {
                        os.write(code.getBytes());
                        os.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            // حالت قدیمی با File
            try (java.io.FileWriter writer = new java.io.FileWriter(file, false)) {
                writer.write(code);
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ----------- Utility برای پیدا کردن فایل مورد نظر در DocumentFile ها -----------

    private DocumentFile findDocumentFile(DocumentFile dir, String fileName) {
        for (DocumentFile file : dir.listFiles()) {
            if (file.getName() != null && file.getName().equals(fileName)) {
                return file;
            }
            if (file.isDirectory()) {
                DocumentFile found = findDocumentFile(file, fileName);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ----------- Utility برای تشخیص مسیر عمومی پروژه -----------

    private boolean isPublicProject(File file) {
        String path = file.getAbsolutePath();
        return path.contains("/storage/emulated/0/BalochScript");
    }
}
