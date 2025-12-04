package io.github.wiiznokes.gitnote.data

import android.content.Context
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.manager.PreferencesManager
import io.github.wiiznokes.gitnote.provider.ProviderType
import io.github.wiiznokes.gitnote.ui.model.Cred
import io.github.wiiznokes.gitnote.ui.model.CredType
import io.github.wiiznokes.gitnote.ui.model.NoteMinWidth
import io.github.wiiznokes.gitnote.ui.model.SortOrder
import io.github.wiiznokes.gitnote.ui.model.StorageConfiguration
import io.github.wiiznokes.gitnote.ui.theme.Theme
import kotlinx.coroutines.runBlocking
import kotlin.io.path.pathString

class AppPreferences(
    context: Context
) : PreferencesManager(context, "settings") {

    companion object {
        val appStorageRepoPath =
            MyApp.appModule.context.filesDir.toPath().resolve("repo").pathString
        const val DEFAULT_USERNAME = "gitnote"
        const val DEFAULT_COMMIT_MESSAGE_BEFORE_CHANGE = "commit from gitnote, before doing a change"
        const val DEFAULT_COMMIT_MESSAGE_UPDATE_REPO =
            "commit from gitnote to update the repo of the app"
        const val DEFAULT_COMMIT_MESSAGE_NOTE_UPDATE = "gitnote changed {path}"
        const val DEFAULT_COMMIT_MESSAGE_NOTE_CREATE = "gitnote created {path}"
        const val DEFAULT_COMMIT_MESSAGE_NOTE_DELETE = "gitnote deleted {path}"
        const val DEFAULT_COMMIT_MESSAGE_NOTES_DELETE = "gitnote deleted {count} notes"
        const val DEFAULT_COMMIT_MESSAGE_FOLDER_CREATE = "gitnote created folder {path}"
        const val DEFAULT_COMMIT_MESSAGE_FOLDER_DELETE = "gitnote deleted folder {path}"
    }

    val dynamicColor = booleanPreference("dynamicColor", true)
    val theme = enumPreference("theme", Theme.SYSTEM)

    val isInit = booleanPreference("isInit", false)
    val databaseCommit = stringPreference("")

    private val repoPath = stringPreference("repoPath")

    suspend fun repoPath(): String {
        if (!isInit.get()) {
            throw Exception("calling repoPath function with no repo initialized")
        }

        return when (storageConfig.get()) {
            StorageConfig.App -> appStorageRepoPath
            StorageConfig.Device -> repoPath.get()
        }
    }

    fun repoPathBlocking(): String = runBlocking { repoPath() }


    fun repoPathSafely(): String {
        return try {
            repoPathBlocking()
        } catch (e: Exception) {
            ""
        }
    }

    val remoteUrl = stringPreference("remoteUrl", "")

    val credType = enumPreference("credType", CredType.None)

    val username = stringPreference("username", "")

    suspend fun usernameOrDefault(): String =
        username.get().let { it.ifEmpty { DEFAULT_USERNAME } }

    val userPassUsername = stringPreference("userPassUsername", "")
    val userPassPassword = stringPreference("userPassPassword", "")

    val commitMessageBeforeChange =
        stringPreference("commitMessageBeforeChange", DEFAULT_COMMIT_MESSAGE_BEFORE_CHANGE)
    val commitMessageUpdateRepo =
        stringPreference("commitMessageUpdateRepo", DEFAULT_COMMIT_MESSAGE_UPDATE_REPO)
    val commitMessageNoteUpdate =
        stringPreference("commitMessageNoteUpdate", DEFAULT_COMMIT_MESSAGE_NOTE_UPDATE)
    val commitMessageNoteCreate =
        stringPreference("commitMessageNoteCreate", DEFAULT_COMMIT_MESSAGE_NOTE_CREATE)
    val commitMessageNoteDelete =
        stringPreference("commitMessageNoteDelete", DEFAULT_COMMIT_MESSAGE_NOTE_DELETE)
    val commitMessageNotesDelete =
        stringPreference("commitMessageNotesDelete", DEFAULT_COMMIT_MESSAGE_NOTES_DELETE)
    val commitMessageFolderCreate =
        stringPreference("commitMessageFolderCreate", DEFAULT_COMMIT_MESSAGE_FOLDER_CREATE)
    val commitMessageFolderDelete =
        stringPreference("commitMessageFolderDelete", DEFAULT_COMMIT_MESSAGE_FOLDER_DELETE)

    private fun format(
        template: String,
        defaultValue: String,
        replacements: Map<String, String>
    ): String {
        var res = template.ifEmpty { defaultValue }
        replacements.forEach { (k, v) ->
            res = res.replace("{$k}", v)
        }
        return res
    }

    suspend fun commitMessageBeforeChangeValue(): String =
        commitMessageBeforeChange.get().ifEmpty { DEFAULT_COMMIT_MESSAGE_BEFORE_CHANGE }

    suspend fun commitMessageUpdateRepoValue(): String =
        commitMessageUpdateRepo.get().ifEmpty { DEFAULT_COMMIT_MESSAGE_UPDATE_REPO }

    suspend fun commitMessageNoteUpdateMessage(relativePath: String): String =
        format(
            commitMessageNoteUpdate.get(),
            DEFAULT_COMMIT_MESSAGE_NOTE_UPDATE,
            mapOf("path" to relativePath)
        )

    suspend fun commitMessageNoteCreateMessage(relativePath: String): String =
        format(
            commitMessageNoteCreate.get(),
            DEFAULT_COMMIT_MESSAGE_NOTE_CREATE,
            mapOf("path" to relativePath)
        )

    suspend fun commitMessageNoteDeleteMessage(relativePath: String): String =
        format(
            commitMessageNoteDelete.get(),
            DEFAULT_COMMIT_MESSAGE_NOTE_DELETE,
            mapOf("path" to relativePath)
        )

    suspend fun commitMessageNotesDeleteMessage(count: Int): String =
        format(
            commitMessageNotesDelete.get(),
            DEFAULT_COMMIT_MESSAGE_NOTES_DELETE,
            mapOf("count" to count.toString())
        )

    suspend fun commitMessageFolderCreateMessage(relativePath: String): String =
        format(
            commitMessageFolderCreate.get(),
            DEFAULT_COMMIT_MESSAGE_FOLDER_CREATE,
            mapOf("path" to relativePath)
        )

    suspend fun commitMessageFolderDeleteMessage(relativePath: String): String =
        format(
            commitMessageFolderDelete.get(),
            DEFAULT_COMMIT_MESSAGE_FOLDER_DELETE,
            mapOf("path" to relativePath)
        )

    val sshUsername = stringPreference("sshUsername", "")
    val publicKey = stringPreference("publicKey", "")
    val privateKey = stringPreference("privateKey", "")
    val passphrase = stringPreference("passphrase", "")

    val appAuthToken = stringPreference("appAuthToken", "")

    suspend fun cred(): Cred? {
        return when (credType.get()) {
            CredType.None -> null
            CredType.UserPassPlainText -> {
                Cred.UserPassPlainText(
                    username = userPassUsername.get(),
                    password = userPassPassword.get()
                )
            }

            CredType.Ssh -> Cred.Ssh(
                username = this.sshUsername.get(),
                publicKey = this.publicKey.get(),
                privateKey = this.privateKey.get(),
                passphrase = this.passphrase.get().ifEmpty { null }
            )
        }
    }

    suspend fun updateCred(cred: Cred?) {
        when (cred) {
            is Cred.Ssh -> {
                credType.update(CredType.Ssh)
                sshUsername.update(cred.username)
                publicKey.update(cred.publicKey)
                privateKey.update(cred.privateKey)
                passphrase.update(cred.passphrase ?: "")
            }

            is Cred.UserPassPlainText -> {
                credType.update(CredType.UserPassPlainText)
                userPassUsername.update(cred.username)
                userPassPassword.update(cred.password)
            }

            null -> credType.update(CredType.None)
        }
    }

    val provider = enumPreference("provider", ProviderType.GitHub)

    val defaultPathForNewNote = stringPreference("defaultPathForNewNote", "")
    val sortOrder = enumPreference("sortOrder", SortOrder.MostRecent)
    val sortOrderFolder = enumPreference("sortOrderFolder", SortOrder.AZ)

    val noteMinWidth = enumPreference("noteMinWidth", NoteMinWidth.Default)
    val showFullNoteHeight = booleanPreference("showFullNoteHeight", false)

    val rememberLastOpenedFolder = booleanPreference("rememberLastOpenedFolder", false)
    val lastOpenedFolder = stringPreference("lastOpenedFolder", "")

    val showFullPathOfNotes = booleanPreference("showFullPathOfNotes", false)

    val defaultExtension = stringPreference("defaultExtension", "md")
    val showLinesNumber = booleanPreference("showLinesNumber", false)

    val folderFilters = setPreference(
        "folderFilters", setOf(
            ".*"
        )
    )

    val storageConfig = enumPreference("storageConfig", StorageConfig.App)

    suspend fun initRepo(storageConfig: StorageConfiguration) {
        databaseCommit.update("")
        isInit.update(true)
        remoteUrl.reset()

        when (storageConfig) {
            StorageConfiguration.App -> {
                this.storageConfig.update(StorageConfig.App)
            }

            is StorageConfiguration.Device -> {
                this.storageConfig.update(StorageConfig.Device)
                repoPath.update(storageConfig.path)
            }
        }
        lastOpenedFolder.update("")
    }

    suspend fun closeRepo() {
        isInit.update(false)
    }

    val isReadOnlyModeActive = booleanPreference("isReadOnlyModeActive", false)

}


enum class StorageConfig {
    App,
    Device
}
