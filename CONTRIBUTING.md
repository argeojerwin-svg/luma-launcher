# Working on Luma together

Keep each change small enough to describe, test and review on its own. Discuss larger changes to the app structure before implementing them.

## Local development

Use JDK 17, Android SDK platform 35, and the included Gradle 8.9 wrapper. See [Windows development](docs/WINDOWS-DEVELOPMENT.md) for PowerShell instructions. On macOS/Linux, run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Sharing changes

1. Start a branch from an up-to-date `main`, named for the change, such as `feature/weather-layout` or `fix/remote-focus`.
2. Make one focused change. Preserve existing remote navigation and device recovery.
3. Run the build, unit tests and lint checks above. For UI changes, test on a projector/TV and record its model, Android version and remote behavior.
4. Commit the related files together and open a pull request describing the problem, the resulting behavior and the checks performed. If you do not have write access, push your branch to your own GitHub fork and open the pull request against this repository.
5. Ask another contributor to review before merging. Use GitHub issues to track ideas and agreed follow-up work.

Never commit signing keys, credentials, personal device addresses, local SDK paths or generated APKs. Keep the original system launcher enabled during device testing. A debug build cannot replace a production-signed installation with a different signing key; agree on a testing/migration approach before removing an existing installation.
