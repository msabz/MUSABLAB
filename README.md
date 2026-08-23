# MUSABLAB

MUSABLAB is a native Android physical-device CI test agent. It installs APK artifacts built by GitHub Actions, runs fast Shizuku-backed test plans on real phones, and publishes structured reports and raw evidence back to GitHub.

## Core design

- Native Kotlin Android app; no Termux/Python/`gh`/`rish` runtime.
- Persistent Shizuku UserService for privileged device operations.
- GitHub OAuth Device Flow for one-tap provisioning.
- Per-device GitHub inbox with ETag polling.
- Local typed test engine: conditions, retry and parallel branches.
- APK artifact download + update/fresh install.
- Raw evidence: logcat, UI XML, screenshots, meminfo, gfxinfo and package state.
- Results stored under `test-results/<session>/devices/<device-id>/`.
- Agents never connect directly to each other; test radios remain free for the app under test.

## Current milestone: native-agent v0.1

The first milestone establishes the end-to-end loop:

`GitHub -> MUSABLAB -> Shizuku -> install/test -> report -> GitHub`

See `docs/ARCHITECTURE.md` and `examples/smoke-command.json`.

## One-time setup

GitHub login requires an OAuth Client ID configured as repository variable `MUSABLAB_GITHUB_CLIENT_ID`. See `docs/GITHUB_OAUTH_SETUP.md`.

Safe self-update additionally requires stable release signing. See `docs/SIGNING.md`.
