package cn.edu.shmtu.terminal.android.data.about

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A git contributor extracted at build time from `git log`.
 *
 * @param name  Display name from git config (`user.name`)
 * @param email Email address from git config (`user.email`)
 */
@Serializable
data class GitContributor(
    val name: String,
    val email: String,
) {
    /**
     * Best-effort GitHub username derived from the email local-part.
     * Used to fetch the avatar from `avatars.githubusercontent.com`.
     */
    val githubUsername: String get() = email.substringBefore("@")
}
