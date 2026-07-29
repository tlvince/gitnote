package io.github.wiiznokes.gitnote.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.data.AppPreferences
import io.github.wiiznokes.gitnote.data.StorageConfig
import io.github.wiiznokes.gitnote.data.platform.NodeFs
import io.github.wiiznokes.gitnote.helper.StoragePermissionHelper
import io.github.wiiznokes.gitnote.helper.UiHelper
import io.github.wiiznokes.gitnote.ui.model.StorageConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "MainViewModel"

class MainViewModel : ViewModel() {

    val prefs: AppPreferences = MyApp.appModule.appPreferences
    private val gitManager = MyApp.appModule.gitManager
    val uiHelper: UiHelper = MyApp.appModule.uiHelper

    private val storageManager = MyApp.appModule.storageManager


    suspend fun tryInit(): Boolean {

        Log.d(TAG, "tryInit: isInit=${prefs.isInit.get()}, storageConfig=${prefs.storageConfig.get()}")

        if (!prefs.isInit.get()) {
            Log.d(TAG, "tryInit: prefs not initialized yet")
            return false
        }

        val storageConfig = when (prefs.storageConfig.get()) {
            StorageConfig.App -> {
                Log.d(TAG, "tryInit: using App storage")
                StorageConfiguration.App
            }

            StorageConfig.Device -> {
                Log.d(TAG, "tryInit: using Device storage")
                if (!StoragePermissionHelper.isPermissionGranted()) {
                    Log.w(TAG, "tryInit: storage permission not granted")
                    return false
                }
                val repoPath = try {
                    prefs.repoPath()
                } catch (_: Exception) {
                    Log.w(TAG, "tryInit: failed to get repoPath from prefs")
                    return false
                }
                StorageConfiguration.Device(repoPath)
            }
        }

        val repoPath = storageConfig.repoPath()
        Log.d(TAG, "tryInit: repoPath=$repoPath")

        if (!NodeFs.Folder.fromPath(repoPath).exist()) {
            Log.w(TAG, "tryInit: repo folder does not exist at $repoPath")
            return false
        }

        Log.d(TAG, "tryInit: opening repo at $repoPath")
        gitManager.openRepo(repoPath).onFailure {
            Log.e(TAG, "tryInit: openRepo failed")
            return false
        }
        Log.d(TAG, "tryInit: openRepo succeeded, isRepoInitialized=${gitManager.isRepoInitialized}")

        val signature = gitManager.currentSignature()
        Log.d(TAG, "tryInit: currentSignature=$signature")
        prefs.applyGitAuthorDefaults(null, signature)

        Log.d(TAG, "tryInit: launching background sync")
        CoroutineScope(Dispatchers.IO).launch {
            storageManager.updateDatabaseAndRepo()
        }

        Log.d(TAG, "tryInit: returning true")
        return true
    }

}
