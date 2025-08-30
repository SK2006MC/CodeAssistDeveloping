package com.tyron.code.ui.editor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tyron.completion.java.JavaCompletionProvider
import com.tyron.completion.model.CompletionList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class EditorViewModel : ViewModel() {

    // وضعیت تحلیل کد (برای نمایش لودینگ یا پردازش)
    private val mAnalyzeState = MutableLiveData(false)
    val analyzeState: LiveData<Boolean>
        get() = mAnalyzeState

    fun setAnalyzeState(analyzing: Boolean) {
        mAnalyzeState.value = analyzing
    }

    // لیست Completion (تکمیل کد) برای ادیتور
    private val _completionList = MutableLiveData<CompletionList?>()
    val completionList: LiveData<CompletionList?>
        get() = _completionList

    // گرفتن Completion از JavaCompletionProvider به صورت Flow
    fun getCompletions(code: String): Flow<CompletionList> = flow {
        val provider = JavaCompletionProvider()
        val completions = provider.provideCompletions(code)
        emit(completions)
    }

    // گرفتن Completion و آپدیت LiveData به صورت Async
    fun fetchCompletions(code: String) {
        viewModelScope.launch {
            val provider = JavaCompletionProvider()
            val completions = provider.provideCompletions(code)
            _completionList.postValue(completions)
        }
    }

    // قابلیت ریست کردن Completion
    fun clearCompletions() {
        _completionList.value = null
    }
}
