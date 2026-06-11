package cn.edu.shmtu.terminal.android.data.about

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Reads the list of git contributors from the build-time generated
 * `assets/git_contributors.json`.
 */
object GitContributorsProvider {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load contributors from assets. Returns an empty list if the file
     * is missing or cannot be parsed (e.g. non-git build).
     */
    fun load(context: Context): List<GitContributor> {
        return try {
            val raw = context.assets.open("git_contributors.json")
                .bufferedReader()
                .use { it.readText() }
            json.decodeFromString<List<GitContributor>>(raw)
        } catch (_: IOException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
