# Releasing Kandroid

Kandroid has two complementary release paths:

- F-Droid builds and signs the application from a tagged source revision.
- GitHub Actions builds the same revision and publishes an APK signed with the Kandroid GitHub release key.

The signing keys are intentionally separate. Never commit a keystore, password, encoded key, or local signing configuration.

## One-time signing setup

Generate a dedicated key on a trusted offline machine. Keep the keystore for the lifetime of the application; losing it prevents compatible upgrades for users of GitHub-distributed APKs.

```sh
keytool -genkeypair -v \
  -keystore kandroid-release.jks \
  -alias kandroid \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Create at least two encrypted backups in separate secure locations. Record the alias and passwords in the same credential-management process, but do not store the only copy beside the keystore.

In the GitHub repository, create an environment named `release`. Add these environment secrets:

- `ANDROID_SIGNING_KEY_BASE64`: the complete keystore encoded as a single Base64 string.
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: the key alias, such as `kandroid`.
- `ANDROID_KEY_PASSWORD`: the private-key password.

Encode the keystore without modifying it. PowerShell example:

```powershell
[Convert]::ToBase64String(
    [IO.File]::ReadAllBytes((Resolve-Path .\kandroid-release.jks))
) | Set-Clipboard
```

Do not add a required reviewer to the `release` environment: valid tags are intended to publish automatically. Restrict environment deployment to tags matching `v*`.

## Repository protection setup

Configure a `main` branch ruleset with:

- pull requests required before merging;
- the `verify` status check required;
- conversations resolved before merging;
- branch deletion and force-pushes blocked;
- repository administrators as the deliberate bypass path.

Configure a tag ruleset for `v*` that restricts tag creation to maintainers and blocks tag updates and deletion. In the repository security settings, enable the dependency graph, Dependabot alerts and security updates, secret scanning with push protection, and CodeQL default setup for Java/Kotlin. Enable immutable releases in repository settings when offered.

Apply the branch rule only after the CI workflow has completed once on `main`, so GitHub knows the `verify` check name.

## Preparing a release

1. Update the literal `versionCode` and `versionName` values in `app/build.gradle.kts`. Never reuse a version code; keeping these values literal allows F-Droid to discover them.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. It must be non-empty and no longer than 500 characters.
3. Validate from a clean checkout, substituting the intended tag:

   ```sh
   ./gradlew :app:validateReleaseMetadata testDebugUnitTest lintDebug assembleRelease \
     -PreleaseTag=v1.0 \
     --no-daemon
   ```

   In PowerShell, quote the property argument as `"-PreleaseTag=v1.0"`.

   Without signing environment variables, this deliberately produces an unsigned APK suitable for source-build verification.

4. Merge the release changes into `main` through a pull request and wait for the required `verify` check.
5. From an up-to-date `main`, create and push an annotated tag:

   ```sh
   git tag -a v1.0 -m "Kandroid 1.0"
   git push origin v1.0
   ```

The release workflow rejects lightweight tags, tags not contained in `main`, version mismatches, invalid changelogs, incomplete signing configuration, and APKs that fail signature verification. Once all checks pass, it publishes `Kandroid-v<versionName>.apk` and its SHA-256 checksum using the Fastlane changelog as the GitHub Release notes.

If a release workflow fails, do not move or recreate a published tag. Fix the release commit, increment the version as appropriate, and create a new tag.
