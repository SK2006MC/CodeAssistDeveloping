package com.tyron.code.ui.editor.impl.text.rosemoe;

import static io.github.rosemoe.sora2.text.EditorUtil.getDefaultColorScheme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ForwardingListener;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.tyron.actions.ActionManager;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.DataContext;
import com.tyron.actions.util.DataContextUtils;
import com.tyron.builder.log.LogViewModel;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.FileManager;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.listener.FileListener;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.R;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.language.LanguageManager;
import com.tyron.code.language.java.JavaLanguage;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.ui.editor.CodeAssistCompletionAdapter;
import com.tyron.code.ui.editor.CodeAssistCompletionLayout;
import com.tyron.code.ui.editor.EditorViewModel;
import com.tyron.code.ui.editor.Savable;
import com.tyron.code.ui.editor.impl.FileEditorManagerImpl;
import com.tyron.code.ui.editor.scheme.CompiledEditorScheme;
import com.tyron.code.ui.editor.shortcuts.ShortcutAction;
import com.tyron.code.ui.editor.shortcuts.ShortcutItem;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.ui.settings.EditorSettingsFragment;
import com.tyron.code.ui.theme.ThemeRepository;
import com.tyron.code.util.CoordinatePopupMenu;
import com.tyron.code.util.PopupMenuHelper;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.logging.IdeLog;
import com.tyron.common.util.AndroidUtilities;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.java.util.JavaDataContextUtil;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.editor.CharPosition;

import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import io.github.rosemoe.sora.event.ClickEvent;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.DirectAccessProps;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora2.text.EditorUtil;

@SuppressWarnings("FieldCanBeLocal")
public class CodeEditorFragment extends Fragment implements Savable,
        SharedPreferences.OnSharedPreferenceChangeListener, FileListener,
        ProjectManager.OnProjectOpenListener {

    private static final Logger LOG = IdeLog.getCurrentLogger(CodeEditorFragment.class);

    public static final String KEY_LINE = "line";
    public static final String KEY_COLUMN = "column";
    public static final String KEY_PATH = "path";
    public static final String KEY_URI = "uri";

    // Constructors for File and for SAF/Uri support
    public static CodeEditorFragment newInstance(File file) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putString(KEY_PATH, file.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    public static CodeEditorFragment newInstance(File file, int line, int column) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_LINE, line);
        args.putInt(KEY_COLUMN, column);
        args.putString(KEY_PATH, file.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    public static CodeEditorFragment newInstance(Uri uri) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putParcelable(KEY_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }

    public static CodeEditorFragment newInstance(Uri uri, int line, int column) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_LINE, line);
        args.putInt(KEY_COLUMN, column);
        args.putParcelable(KEY_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }

    private static final String EDITOR_LEFT_LINE_KEY = "line";
    private static final String EDITOR_LEFT_COLUMN_KEY = "column";
    private static final String EDITOR_RIGHT_LINE_KEY = "rightLine";
    private static final String EDITOR_RIGHT_COLUMN_KEY = "rightColumn";

    private CodeEditorView mEditor;
    private Language mLanguage;
    private File mCurrentFile = null;
    private Uri mCurrentUri = null;
    private MainViewModel mMainViewModel;
    private Bundle mSavedInstanceState;
    private boolean mCanSave = false;
    private boolean mReading = false;
    private View.OnTouchListener mDragToOpenListener;

    public CodeEditorFragment() {
        super(R.layout.code_editor_fragment);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(KEY_PATH)) {
                mCurrentFile = new File(args.getString(KEY_PATH, ""));
                mCurrentUri = null;
            } else if (args.containsKey(KEY_URI)) {
                mCurrentUri = args.getParcelable(KEY_URI);
                mCurrentFile = null;
            }
        }
        mMainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mSavedInstanceState = savedInstanceState;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Cursor cursor = mEditor.getCursor();
        outState.putInt(EDITOR_LEFT_LINE_KEY, cursor.getLeftLine());
        outState.putInt(EDITOR_LEFT_COLUMN_KEY, cursor.getLeftColumn());
        outState.putInt(EDITOR_RIGHT_LINE_KEY, cursor.getRightLine());
        outState.putInt(EDITOR_RIGHT_COLUMN_KEY, cursor.getRightColumn());
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void hideEditorWindows() {
        mEditor.hideAutoCompleteWindow();
    }

    @NonNull
    private ListenableFuture<TextMateColorScheme> getScheme(@Nullable String path) {
        if (path != null && new File(path).exists()) {
            return EditorSettingsFragment.getColorScheme(new File(path));
        } else {
            return Futures.immediateFailedFuture(new Throwable());
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCanSave = false;

        mEditor = view.findViewById(R.id.code_editor);
        mEditor.setEditable(false);
        configureEditor(mEditor);

        View topView = view.findViewById(R.id.top_view);
        EditorViewModel viewModel =
                new ViewModelProvider((ViewModelStoreOwner) requireParentFragment()).get(
                        EditorViewModel.class);
        viewModel.getAnalyzeState().observe(getViewLifecycleOwner(), analyzing -> {
            if (analyzing) {
                topView.setVisibility(View.VISIBLE);
            } else {
                topView.setVisibility(View.GONE);
            }
        });
        mEditor.setViewModel(viewModel);
        ApplicationLoader.getDefaultPreferences().registerOnSharedPreferenceChangeListener(this);

        postConfigureEditor();

        String schemeValue = ApplicationLoader.getDefaultPreferences()
                .getString(SharedPreferenceKeys.SCHEME, null);
        if (schemeValue != null &&
            new File(schemeValue).exists() &&
            ThemeRepository.getColorScheme(schemeValue) != null) {
            TextMateColorScheme scheme = ThemeRepository.getColorScheme(schemeValue);
            if (scheme != null) {
                mEditor.setColorScheme(scheme);
                initializeLanguage();
                openCurrentFile();
                readOrWait();
            }
        } else {
            ListenableFuture<TextMateColorScheme> scheme = getScheme(schemeValue);
            Futures.addCallback(scheme, new FutureCallback<TextMateColorScheme>() {
                @Override
                public void onSuccess(@Nullable TextMateColorScheme result) {
                    if (getContext() == null) {
                        return;
                    }
                    assert result != null;
                    ThemeRepository.putColorScheme(schemeValue, result);
                    mEditor.setColorScheme(result);

                    initializeLanguage();
                    openCurrentFile();
                    readOrWait();
                }

                @Override
                public void onFailure(@NonNull Throwable t) {
                    if (getContext() == null) {
                        return;
                    }
                    String key = EditorUtil.isDarkMode(
                            requireContext()) ? ThemeRepository.DEFAULT_NIGHT :
                            ThemeRepository.DEFAULT_LIGHT;
                    TextMateColorScheme scheme = ThemeRepository.getColorScheme(key);
                    if (scheme == null) {
                        scheme = getDefaultColorScheme(requireContext());
                        ThemeRepository.putColorScheme(key, scheme);
                    }
                    mEditor.setColorScheme(scheme);
                    initializeLanguage();
                    openCurrentFile();
                    readOrWait();
                }
            }, ContextCompat.getMainExecutor(requireContext()));
        }
    }

    private void initializeLanguage() {
        if (mCurrentFile != null) {
            mLanguage = LanguageManager.getInstance().get(mEditor, mCurrentFile);
        } else if (mCurrentUri != null) {
            mLanguage = LanguageManager.getInstance().get(mEditor, mCurrentUri);
        }
        if (mLanguage == null) {
            mLanguage = new EmptyTextMateLanguage();
        }
        mEditor.setEditorLanguage(mLanguage);
    }

    private void configureEditor(@NonNull CodeEditorView editor) {
        editor.setEditable(false);
        editor.setColorScheme(new CompiledEditorScheme(requireContext()));
        editor.setBackgroundAnalysisEnabled(false);
        editor.setTypefaceText(
                ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono_regular));
        editor.getComponent(EditorAutoCompletion.class).setLayout(new CodeAssistCompletionLayout());
        editor.setLigatureEnabled(true);
        editor.setHighlightCurrentBlock(true);
        editor.setEdgeEffectColor(Color.TRANSPARENT);
        openCurrentFile();
        editor.setAutoCompletionItemAdapter(new CodeAssistCompletionAdapter());
        editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        editor.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                            EditorInfo.TYPE_CLASS_TEXT |
                            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE |
                            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        SharedPreferences pref = ApplicationLoader.getDefaultPreferences();
        editor.setWordwrap(pref.getBoolean(SharedPreferenceKeys.EDITOR_WORDWRAP, false));
        editor.setTextSize(Integer.parseInt(pref.getString(SharedPreferenceKeys.FONT_SIZE, "12")));

        DirectAccessProps props = editor.getProps();
        props.overScrollEnabled = false;
        props.allowFullscreen = false;
        props.deleteEmptyLineFast = pref.getBoolean(SharedPreferenceKeys.DELETE_WHITESPACES, false);
    }

    private void openCurrentFile() {
        if (mEditor == null) return;
        if (mCurrentFile != null) {
            mEditor.openFile(mCurrentFile);
        } else if (mCurrentUri != null) {
            mEditor.openFile(mCurrentUri);
        }
    }

    private void postConfigureEditor() {
        mEditor.setOnTouchListener((view12, motionEvent) -> {
            if (mDragToOpenListener instanceof ForwardingListener) {
                PopupMenuHelper.setForwarding((ForwardingListener) mDragToOpenListener);
                mDragToOpenListener.onTouch(view12, motionEvent);
            }
            return false;
        });
        mEditor.subscribeEvent(LongPressEvent.class, (event, unsubscribe) -> {
            event.intercept();

            updateFile(mEditor.getText());
            Cursor cursor = mEditor.getCursor();
            if (cursor.isSelected()) {
                int index = mEditor.getCharIndex(event.getLine(), event.getColumn());
                int cursorLeft = cursor.getLeft();
                int cursorRight = cursor.getRight();
                char c = mEditor.getText().charAt(index);
                if (Character.isWhitespace(c)) {
                    mEditor.setSelection(event.getLine(), event.getColumn());
                } else if (index < cursorLeft || index > cursorRight) {
                    EditorUtil.selectWord(mEditor, event.getLine(), event.getColumn());
                }
            } else {
                char c = mEditor.getText().charAt(event.getIndex());
                if (!Character.isWhitespace(c)) {
                    EditorUtil.selectWord(mEditor, event.getLine(), event.getColumn());
                } else {
                    mEditor.setSelection(event.getLine(), event.getColumn());
                }
            }

            ProgressManager.getInstance().runLater(() -> {
                showPopupMenu(event);
            });
        });
        mEditor.subscribeEvent(ClickEvent.class, (event, unsubscribe) -> {
            Cursor cursor = mEditor.getCursor();
            if (mEditor.getCursor().isSelected()) {
                int index = mEditor.getCharIndex(event.getLine(), event.getColumn());
                int cursorLeft = cursor.getLeft();
                int cursorRight = cursor.getRight();
                if (!EditorUtil.isWhitespace(mEditor.getText().charAt(index) + "") &&
                    index >= cursorLeft &&
                    index <= cursorRight) {
                    mEditor.showSoftInput();
                    event.intercept();
                }
            }
        });
        mEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                return;
            }
            updateFile(event.getEditor().getText());
        });

        LogViewModel logViewModel =
                new ViewModelProvider(requireActivity()).get(LogViewModel.class);
        mEditor.setDiagnosticsListener(diagnostics -> {
            for (DiagnosticWrapper diagnostic : diagnostics) {
                DiagnosticUtil.setLineAndColumn(diagnostic, mEditor);
            }
            ProgressManager.getInstance()
                    .runLater(() -> logViewModel.updateLogs(LogViewModel.DEBUG, diagnostics));
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
        if (mEditor == null) {
            return;
        }
        switch (key) {
            case SharedPreferenceKeys.FONT_SIZE:
                mEditor.setTextSize(Integer.parseInt(pref.getString(key, "14")));
                break;
            case SharedPreferenceKeys.EDITOR_WORDWRAP:
                mEditor.setWordwrap(pref.getBoolean(key, false));
                break;
            case SharedPreferenceKeys.DELETE_WHITESPACES:
                mEditor.getProps().deleteEmptyLineFast =
                        pref.getBoolean(SharedPreferenceKeys.DELETE_WHITESPACES, false);
                break;
            case SharedPreferenceKeys.SCHEME:
                ListenableFuture<TextMateColorScheme> scheme =
                        getScheme(pref.getString(SharedPreferenceKeys.SCHEME, null));
                Futures.addCallback(scheme, new FutureCallback<TextMateColorScheme>() {
                    @Override
                    public void onSuccess(@Nullable TextMateColorScheme result) {
                        if (getContext() == null) {
                            return;
                        }
                        assert result != null;
                        mEditor.setColorScheme(result);
                        if (mLanguage.getAnalyzeManager() instanceof BaseTextmateAnalyzer) {
                            ((BaseTextmateAnalyzer) mLanguage.getAnalyzeManager()).updateTheme(
                                    result.getRawTheme());
                            mLanguage.getAnalyzeManager().rerun();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        if (getContext() == null) {
                            return;
                        }
                        mEditor.setColorScheme(getDefaultColorScheme(requireContext()));
                        mLanguage.getAnalyzeManager().rerun();
                    }
                }, ContextCompat.getMainExecutor(requireContext()));
                break;
        }
    }

    private void showPopupMenu(LongPressEvent event) {
        MotionEvent e = event.getCausingEvent();
        CoordinatePopupMenu popupMenu =
                new CoordinatePopupMenu(requireContext(), mEditor, Gravity.BOTTOM);
        DataContext dataContext = createDataContext();
        ActionManager.getInstance()
                .fillMenu(dataContext, popupMenu.getMenu(), ActionPlaces.EDITOR, true, false);
        popupMenu.show((int) e.getX(), ((int) e.getY()) - AndroidUtilities.dp(24));
        ProgressManager.getInstance().runLater(() -> {
            popupMenu.setOnDismissListener(d -> mDragToOpenListener = null);
            mDragToOpenListener = popupMenu.getDragToOpenListener();
        }, 300);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Project currentProject = ProjectManager.getInstance().getCurrentProject();
        if (currentProject != null && mCurrentFile != null) {
            Module module = currentProject.getModule(mCurrentFile);
            if (module != null) {
                module.getFileManager().removeSnapshotListener(this);
            }
        }
        ProjectManager.getInstance().removeOnProjectOpenListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ProjectManager.getInstance().getCurrentProject() != null && mCanSave) {
            if (mCurrentFile != null) {
                ProgressManager.getInstance().runNonCancelableAsync(
                        () -> ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile)
                                .getFileManager().closeFileForSnapshot(mCurrentFile));
            }
        }
        ApplicationLoader.getDefaultPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        hideEditorWindows();
        save(true);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mEditor.setBackgroundAnalysisEnabled(false);
    }

    @Override
    public void onSnapshotChanged(File file, CharSequence contents) {
        if (mCurrentFile != null && mCurrentFile.equals(file)) {
            if (mEditor != null) {
                if (!mEditor.getText().toString().contentEquals(contents)) {
                    Cursor cursor = mEditor.getCursor();
                    int left = cursor.getLeft();
                    mEditor.setText(contents);
                    if (left > contents.length()) {
                        left = contents.length();
                    }
                    CharPosition position = mEditor.getCharPosition(left);
                    mEditor.setSelection(position.getLine(), position.getColumn());
                }
            }
        }
    }

    @Override
    public boolean canSave() {
        return mCanSave && !mReading;
    }

    @Override
    public void save(boolean toDisk) {
        if (!mCanSave || mReading) {
            return;
        }
        if (mCurrentFile != null) {
            if (!mCurrentFile.exists()) {
                return;
            }
            if (ProjectManager.getInstance().getCurrentProject() != null && !toDisk) {
                ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile)
                        .getFileManager()
                        .setSnapshotContent(mCurrentFile, mEditor.getText().toString(), false);
            } else {
                ProgressManager.getInstance().runNonCancelableAsync(() -> {
                    try {
                        FileUtils.writeStringToFile(mCurrentFile, mEditor.getText().toString(),
                                StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOG.severe("Unable to save file: " +
                                   mCurrentFile.getAbsolutePath() +
                                   "\n" +
                                   "Reason: " +
                                   e.getMessage());
                    }
                });
            }
        } else if (mCurrentUri != null) {
            // SAF/Uri: save file using ContentResolver
            ProgressManager.getInstance().runNonCancelableAsync(() -> {
                try {
                    OutputStream outputStream = requireContext().getContentResolver()
                            .openOutputStream(mCurrentUri, "wt");
                    if (outputStream != null) {
                        outputStream.write(mEditor.getText().toString().getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        outputStream.close();
                    }
                } catch (Exception e) {
                    LOG.severe("Unable to save file: " + mCurrentUri + "\nReason: " + e.getMessage());
                }
            });
        }
    }

    @Override
    public void onProjectOpen(Project project) {
        ProgressManager.getInstance().runLater(() -> readFile(project, mSavedInstanceState));
    }

    private void readOrWait() {
        if (ProjectManager.getInstance().getCurrentProject() != null) {
            readFile(ProjectManager.getInstance().getCurrentProject(), mSavedInstanceState);
        } else {
            ProjectManager.getInstance().addOnProjectOpenListener(this);
        }
    }

    private ListenableFuture<String> readFile() {
        if (mCurrentFile != null) {
            return Futures.submitAsync(() -> {
                FileSystemManager manager = VFS.getManager();
                FileObject fileObject = manager.resolveFile(mCurrentFile.toURI());
                FileContent content = fileObject.getContent();
                return Futures.immediateFuture(content.getString(StandardCharsets.UTF_8));
            }, Executors.newSingleThreadExecutor());
        } else if (mCurrentUri != null) {
            return Futures.submitAsync(() -> {
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        requireContext().getContentResolver().openInputStream(mCurrentUri), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line).append('\n');
                    }
                }
                return Futures.immediateFuture(builder.toString());
            }, Executors.newSingleThreadExecutor());
        } else {
            return Futures.immediateFailedFuture(new Throwable("No file or uri to read"));
        }
    }

    private void readFile(@NonNull Project currentProject, @Nullable Bundle savedInstanceState) {
        mCanSave = false;
        Module module = mCurrentFile != null ? currentProject.getModule(mCurrentFile) : null;
        FileManager fileManager = module != null ? module.getFileManager() : null;
        if (fileManager != null) {
            fileManager.addSnapshotListener(this);
            if (fileManager.isOpened(mCurrentFile)) {
                Optional<CharSequence> contents = fileManager.getFileContent(mCurrentFile);
                if (contents.isPresent()) {
                    mEditor.setText(contents.get());
                    return;
                }
            }
        }

        mReading = true;
        mEditor.setBackgroundAnalysisEnabled(false);
        ListenableFuture<String> future = readFile();
        Futures.addCallback(future, new FutureCallback<String>() {
            @Override
            public void onSuccess(@Nullable String result) {
                mReading = false;
                if (getContext() == null) {
                    mCanSave = false;
                    return;
                }
                if (mLanguage == null) {
                    return;
                }
                mCanSave = true;
                mEditor.setBackgroundAnalysisEnabled(true);
                mEditor.setEditable(true);
                if (fileManager != null && mCurrentFile != null) {
                    fileManager.openFileForSnapshot(mCurrentFile, result);
                }

                Bundle bundle = new Bundle();
                bundle.putBoolean("loaded", true);
                bundle.putBoolean("bg", true);
                mEditor.setText(result, bundle);

                if (savedInstanceState != null) {
                    restoreState(savedInstanceState);
                } else {
                    int line = requireArguments().getInt(KEY_LINE, 0);
                    int column = requireArguments().getInt(KEY_COLUMN, 0);
                    Content text = mEditor.getText();
                    if (line < text.getLineCount() && column < text.getColumnCount(line)) {
                        setCursorPosition(line, column);
                    }
                }
                checkCanSave();
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                mCanSave = false;
                mReading = false;
                if (getContext() != null) {
                    checkCanSave();
                }

                LOG.severe("Unable to read current file: " +
                           (mCurrentFile != null ? mCurrentFile : mCurrentUri) +
                           "\nReason: " +
                           t.getMessage());
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void checkCanSave() {
        if (!mCanSave) {
            Snackbar snackbar =
                    Snackbar.make(mEditor, R.string.editor_error_file, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.menu_close, v -> {
                                if (mCurrentFile != null) {
                                    FileEditorManagerImpl.getInstance().closeFile(mCurrentFile);
                                } else if (mCurrentUri != null) {
                                    FileEditorManagerImpl.getInstance().closeFile(mCurrentUri);
                                }
                            });
            ViewGroup snackbarView = (ViewGroup) snackbar.getView();
            AndroidUtilities.setMargins(snackbarView, 0, 0, 0, 50);
            snackbar.show();
        }
    }

    private void restoreState(@NonNull Bundle savedInstanceState) {
        int leftLine = savedInstanceState.getInt(EDITOR_LEFT_LINE_KEY, 0);
        int leftColumn = savedInstanceState.getInt(EDITOR_LEFT_COLUMN_KEY, 0);
        int rightLine = savedInstanceState.getInt(EDITOR_RIGHT_LINE_KEY, 0);
        int rightColumn = savedInstanceState.getInt(EDITOR_RIGHT_COLUMN_KEY, 0);

        Content text = mEditor.getText();
        if (leftLine > text.getLineCount() || rightLine > text.getLineCount()) {
            return;
        }
        if (leftLine != rightLine && leftColumn != rightColumn) {
            mEditor.setSelectionRegion(leftLine, leftColumn, rightLine, rightColumn, true);
        } else {
            mEditor.setSelection(leftLine, leftColumn);
        }
    }

    private void updateFile(CharSequence contents) {
        Project project = ProjectManager.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        if (mCurrentFile != null) {
            Module module = project.getModule(mCurrentFile);
            if (module != null) {
                if (!module.getFileManager().isOpened(mCurrentFile)) {
                    return;
                }
                module.getFileManager().setSnapshotContent(mCurrentFile, contents.toString(), this);
            }
        } // For SAF/Uri (snapshot logic can be extended here if needed)
    }

    public CodeEditorView getEditor() {
        return mEditor;
    }

    public void undo() {
        if (mEditor == null) {
            return;
        }
        if (mEditor.canUndo()) {
            mEditor.undo();
        }
    }

    public void redo() {
        if (mEditor == null) {
            return;
        }
        if (mEditor.canRedo()) {
            mEditor.redo();
        }
    }

    public void setCursorPosition(int line, int column) {
        if (mEditor != null) {
            mEditor.getCursor().set(line, column);
        }
    }

    public void performShortcut(ShortcutItem item) {
        if (mEditor == null) {
            return;
        }
        for (ShortcutAction action : item.actions) {
            action.apply(mEditor, item);
        }
    }

    public void format() {
        if (mEditor != null) {
            mEditor.formatCodeAsync();
        }
    }

    public void analyze() {
        if (mEditor != null && !mReading) {
            mEditor.rerunAnalysis();
        }
    }

    private DataContext createDataContext() {
        Project currentProject = ProjectManager.getInstance().getCurrentProject();

        DataContext dataContext = DataContextUtils.getDataContext(mEditor);
        dataContext.putData(CommonDataKeys.PROJECT, currentProject);
        dataContext.putData(CommonDataKeys.ACTIVITY, requireActivity());
        dataContext.putData(CommonDataKeys.FILE_EDITOR_KEY, mMainViewModel.getCurrentFileEditor());
        if (mCurrentFile != null) {
            dataContext.putData(CommonDataKeys.FILE, mCurrentFile);
        } else if (mCurrentUri != null) {
            dataContext.putData(CommonDataKeys.FILE_URI, mCurrentUri);
        }
        dataContext.putData(CommonDataKeys.EDITOR, mEditor);

        if (currentProject != null && mLanguage instanceof JavaLanguage && mCurrentFile != null) {
            JavaDataContextUtil.addEditorKeys(dataContext, currentProject, mCurrentFile,
                    mEditor.getCursor().getLeft());
        }
        return dataContext;
    }
}
