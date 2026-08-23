# One-time GitHub OAuth setup

MUSABLAB uses GitHub OAuth Device Flow so a new phone can be provisioned as:

`Install APK -> grant Shizuku -> Connect GitHub -> approve in browser -> READY`

No token is typed on the phone.

One GitHub OAuth App must be created once for the project:

1. GitHub Settings -> Developer settings -> OAuth Apps -> New OAuth App.
2. Name: `MUSABLAB`.
3. Homepage: `https://github.com/msabz/MUSABLAB`.
4. Enable **Device Flow**.
5. Copy the OAuth **Client ID**. It is public and is not a secret.
6. Add repository variable `MUSABLAB_GITHUB_CLIENT_ID` with that value.

The access token returned by GitHub is encrypted at rest with Android Keystore. The app currently requests `repo` scope because the control/test-result repository may be private and the agent must read/write contents and download Actions artifacts.
