package io.github.wiiznokes.gitnote.manager

import android.util.Log
import androidx.annotation.Keep
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.ui.model.Cred
import io.github.wiiznokes.gitnote.ui.model.GitAuthor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success


enum class GitExceptionType {
    InitLib,
    RepoAlreadyInit,
    RepoNotInit,
    Other
}

class GitException(
    val type: GitExceptionType,
    message: String?
) : Exception(getMessage(type, message)) {

    companion object {
        private fun getMessage(
            type: GitExceptionType,
            message: String?
        ): String? =
            message ?: if (type != GitExceptionType.Other) type.name else null

    }

    constructor(message: String) : this(GitExceptionType.Other, message)
    constructor(type: GitExceptionType) : this(type, null)
}

class GitManager {

    companion object {
        private const val TAG = "GitManager"

        init {
            Log.d(TAG, "init")
            System.loadLibrary("git_wrapper")
        }
    }

    private val uiHelper = MyApp.appModule.uiHelper

    private val locker = Mutex()
    var isRepoInitialized = false
        private set
    private var isLibInitialized = false

    private suspend fun <T> safelyAccessLibGit2(
        operation: String,
        f: suspend () -> T
    ): Result<T> = locker.withLock {
        Log.d(TAG, "safelyAccessLibGit2: $operation (isRepoInitialized=$isRepoInitialized, isLibInitialized=$isLibInitialized)")
        try {
            if (!isLibInitialized) {
                val res = initLib()
                Log.d(TAG, "res on init = $res")
                if (res < 0) {
                    throw GitException(GitExceptionType.InitLib)
                }
                isLibInitialized = true
            }
            success(f())
        } catch (e: Exception) {
            Log.w(TAG, "safelyAccessLibGit2: operation '$operation' failed", e)
            failure(e)
        }
    }


    suspend fun createRepo(repoPath: String): Result<Unit> = safelyAccessLibGit2("createRepo") {
        Log.d(TAG, "create repo: $repoPath")

        if (isRepoInitialized) {
            Log.w(TAG, "createRepo: repo already initialized, throwing RepoAlreadyInit")
            throw GitException(GitExceptionType.RepoAlreadyInit)
        }

        val res = createRepoLib(repoPath)
        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_create_repo, res.toString()))
        }
        Log.d(TAG, "createRepo: repo created successfully, setting isRepoInitialized=true")
        isRepoInitialized = true
    }


    suspend fun openRepo(repoPath: String): Result<Unit> = safelyAccessLibGit2("openRepo") {
        Log.d(TAG, "open repo: $repoPath")
        if (isRepoInitialized) {
            Log.d(TAG, "openRepo: already initialized, skipping")
            return@safelyAccessLibGit2
        }

        val res = openRepoLib(repoPath)
        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_open_repo, res))
        }
        Log.d(TAG, "openRepo: repo opened successfully, setting isRepoInitialized=true")
        isRepoInitialized = true
    }

    private var actualCb: ((Int) -> Boolean)? = null

    /**
     * This function is called from native code
     */
    @Keep
    fun progressCb(progress: Int): Boolean {
        return actualCb?.invoke(progress) != false
    }

    suspend fun cloneRepo(
        repoPath: String,
        repoUrl: String,
        cred: Cred?,
        progressCallback: (Int) -> Boolean
    ): Result<Unit> = safelyAccessLibGit2("cloneRepo") {
        Log.d(TAG, "clone repo: $repoPath, $repoUrl, $cred")

        if (isRepoInitialized) {
            Log.w(TAG, "cloneRepo: repo already initialized, throwing RepoAlreadyInit")
            throw GitException(GitExceptionType.RepoAlreadyInit)
        }

        actualCb = progressCallback

        val res = cloneRepoLib(
            repoPath = repoPath,
            remoteUrl = repoUrl,
            cred = cred,
            progressCallback = this
        )

        actualCb = null

        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_clone_repo, res))
        }

        Log.d(TAG, "cloneRepo: repo cloned successfully, setting isRepoInitialized=true")
        isRepoInitialized = true

    }

    // todo: update this shit
    suspend fun lastCommit(): String = safelyAccessLibGit2("lastCommit") {
        Log.d(TAG, "last commit")
        if (!isRepoInitialized) {
            Log.w(TAG, "lastCommit: isRepoInitialized is false, throwing RepoNotInit")
            throw GitException(GitExceptionType.RepoNotInit)
        }
        lastCommitLib()
    }.getOrDefault("") ?: ""

    suspend fun commitAll(author: GitAuthor, message: String): Result<Unit> = safelyAccessLibGit2("commitAll") {
        Log.d(TAG, "commit all: ${author.name}")
        if (!isRepoInitialized) {
            Log.w(TAG, "commitAll: isRepoInitialized is false, throwing RepoNotInit")
            throw GitException(GitExceptionType.RepoNotInit)
        }

        var res = isChangeLib()

        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_commit_file_change, res))
        }

        if (res == 0) {
            // nothing to commit
            Log.d(TAG, "nothing to commit")
            return@safelyAccessLibGit2
        }

        res = commitAllLib(author.name, author.email, message)
        if (res < 0) {
            throw GitException(uiHelper.getString(R.string.error_commit_repo, res.toString()))
        }

    }

    suspend fun currentSignature(): GitAuthor? = safelyAccessLibGit2("currentSignature") {
        Log.d(TAG, "currentSignature")
        if (!isRepoInitialized) {
            Log.w(TAG, "currentSignature: isRepoInitialized is false, throwing RepoNotInit")
            throw GitException(GitExceptionType.RepoNotInit)
        }

        currentSignatureLib()
    }.getOrNull()?.let { GitAuthor(name = it.first, email = it.second) }

    suspend fun push(cred: Cred?): Result<Unit> = safelyAccessLibGit2("push") {
        Log.d(TAG, "push: $cred")
        if (!isRepoInitialized) {
            Log.w(TAG, "push: isRepoInitialized is false, throwing RepoNotInit")
            throw GitException(GitExceptionType.RepoNotInit)
        }
        val res = pushLib(cred)

        if (res < 0) {
            Log.d(TAG, "push: $res")
            val msg = uiHelper.getString(R.string.error_push_repo, res.toString())
            Log.d(TAG, "push: $msg")

            throw Exception(uiHelper.getString(R.string.error_push_repo, res.toString()))
        }

    }

    suspend fun pull(cred: Cred?, author: GitAuthor): Result<Unit> = safelyAccessLibGit2("pull") {
        Log.d(TAG, "pull: $cred")
        if (!isRepoInitialized) {
            Log.w(TAG, "pull: isRepoInitialized is false, throwing RepoNotInit")
            throw GitException(GitExceptionType.RepoNotInit)
        }

        val res = pullLib(cred, author.name, author.email)

        if (res < 0) {
            throw Exception(uiHelper.getString(R.string.error_pull_repo, res.toString()))
        }
    }

    suspend fun getTimestamps(): Result<HashMap<String, Long>> = safelyAccessLibGit2("getTimestamps") {
        Log.d(TAG, "getTimestamps (isRepoInitialized=$isRepoInitialized)")
        if (!isRepoInitialized) {
            Log.w(TAG, "getTimestamps: isRepoInitialized is false, throwing RepoNotInit")
            throw GitException(GitExceptionType.RepoNotInit)
        }

        val h: HashMap<String, Long> = HashMap()

        val res = getTimestampsLib(h)

        if (res < 0) {
            throw Exception("getTimestampsLib error $res")
        }
        h
    }


    fun closeRepoWithoutLock() {
        Log.w(TAG, "closeRepoWithoutLock: isRepoInitialized=$isRepoInitialized", RuntimeException("stack trace"))
        if (isRepoInitialized) {
            closeRepoLib()
            Log.d(TAG, "closeRepoWithoutLock: called closeRepoLib")
        }
        isRepoInitialized = false
    }

    suspend fun closeRepo() = safelyAccessLibGit2("closeRepo") {
        Log.d(TAG, "closeRepo called")
        closeRepoWithoutLock()
    }

    suspend fun shutdown() = safelyAccessLibGit2("shutdown") {
        Log.d(TAG, "shutdown called")
        closeRepoWithoutLock()
        if (isLibInitialized) freeLib()
        isLibInitialized = false
    }

}

private external fun initLib(
    homePath: String = MyApp.appModule.context.filesDir.toPath().toString()
): Int

private external fun createRepoLib(repoPath: String): Int

private external fun openRepoLib(repoPath: String): Int

private external fun cloneRepoLib(
    repoPath: String,
    remoteUrl: String,
    cred: Cred?,
    progressCallback: GitManager
): Int


private external fun lastCommitLib(): String?

private external fun commitAllLib(name: String, email: String, message: String): Int
private external fun currentSignatureLib(): Pair<String, String>?
private external fun pushLib(cred: Cred?): Int
private external fun pullLib(cred: Cred?, name: String, email: String): Int

private external fun freeLib()


private external fun closeRepoLib()

private external fun isChangeLib(): Int

private external fun getTimestampsLib(timestamps: HashMap<String, Long>): Int

external fun generateSshKeysLib(): Pair<String, String>

// return true if url is ssh
external fun getUrlInfoLib(url: String): Boolean?

