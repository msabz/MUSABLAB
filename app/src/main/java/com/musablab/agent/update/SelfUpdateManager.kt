package com.musablab.agent.update

/**
 * Self-update plumbing is intentionally isolated from the normal artifact installer.
 * Production activation requires a stable release signing certificate. A debug build
 * must never be treated as an authenticated self-update channel.
 */
object SelfUpdateManager {
    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_DEV = "dev"
}
