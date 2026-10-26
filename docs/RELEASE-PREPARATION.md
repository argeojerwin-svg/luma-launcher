# Production release preparation

The public `v1.0.0-rc1` APK is debug-signed. The repository can now create an **unsigned** release AAB for inspection, or sign it when the four `LUMA_RELEASE_*` environment variables are supplied. Signing credentials and keystores must remain outside the repository.

```sh
./gradlew :app:bundleRelease :app:testDebugUnitTest :app:lintRelease
```

For a signed bundle, first create and securely back up a private upload key, then set `LUMA_RELEASE_STORE_FILE`, `LUMA_RELEASE_STORE_PASSWORD`, `LUMA_RELEASE_KEY_ALIAS`, and `LUMA_RELEASE_KEY_PASSWORD` in your local build environment. Run the same build command. Do not paste passwords into source files, shell history, GitHub issues, or CI logs. Verify the AAB signature before uploading. Use Play App Signing when publishing through Google Play.

Before declaring a stable release:

- Test the signed build on at least one additional Android TV model, including remote Menu behavior. The P6-specific Mac bridge is not part of the Play app and is not required on compatible TVs.
- Implement and review Google Play's prominent in-app disclosure and affirmative consent for the optional Accessibility service. Complete the Accessibility declaration and accurate Data safety form in Play Console.
- Review all third-party marks and provider artwork in the Play listing. Use Luma-owned artwork for store graphics where possible.
- Increment `versionCode` and set the final `versionName` only after the candidate is ready. Retain a rollback copy of the RC APK.
- Plan migration from the debug-signed RC. A production-signed APK cannot update it in place. Existing testers must back up any important preferences and uninstall the debug build before installing the production build; uninstalling clears local Luma data. Do not present this as a seamless upgrade.
- Keep the original AUMI launcher installed and enabled. Do not change the default Home app without the device owner's choice.

Do not publish a stable release or upload the final AAB until the signing key, policy review, and device validation are complete.
