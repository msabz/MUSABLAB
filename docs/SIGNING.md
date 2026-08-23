# Stable signing requirement

Safe self-update and rollback require every MUSABLAB APK to be signed by the same private signing key.

Do not commit a keystore to this public repository. Configure a repository secret based release-signing workflow before enabling automatic self-update. Until that is done, CI produces a debug APK for installation/testing, but the debug artifact is not treated as an authenticated self-update channel.
