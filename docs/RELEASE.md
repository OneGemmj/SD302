# Release Workflow

Use Git tags and GitHub Releases so source code and APK files stay tied to the same version.

## 1. Update App Version

Edit `gradle.properties`:

```properties
APP_VERSION_CODE=11
APP_VERSION_NAME=1.1.9
```

Rules:

- `APP_VERSION_CODE` must be a higher integer than the previous release.
- `APP_VERSION_NAME` should match the release tag without the leading `v`.

## 2. Verify Locally

```powershell
.\gradlew.bat test assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 3. Local APK Backup (required)

Every generation must be copied to `releases/apk/` with the versioned name:

```powershell
$ver = (Select-String -Path gradle.properties -Pattern '^APP_VERSION_NAME=(.+)$').Matches.Groups[1].Value
New-Item -ItemType Directory -Force -Path releases\apk | Out-Null
Copy-Item app\build\outputs\apk\debug\app-debug.apk "releases\apk\Seedream-302-v$ver-debug.apk" -Force
```

Example filename:

```text
Seedream-302-v1.1.8-debug.apk
```

APK files under `releases/apk/` are local backups and should not be committed to the source repository. Publish distributable APKs through GitHub Releases.

## 4. Commit and Tag

```powershell
git add .
git commit -m "Release v1.1.8"
git tag v1.1.8
git push
git push origin v1.1.8
```

## 5. GitHub Release

Pushing a `v*` tag triggers the GitHub Actions workflow in `.github/workflows/android-release.yml`.

The workflow:

- runs unit tests,
- builds the debug APK,
- renames it to include the version,
- attaches it to a GitHub Release.

## Troubleshooting

If a release APK does not appear on GitHub:

1. Confirm the tag was pushed:

```powershell
git push origin v1.1.8
```

2. If the tag already exists on GitHub but was created before the workflow file existed, recreate it:

```powershell
git push origin :refs/tags/v1.1.8
git tag -d v1.1.8
git tag v1.1.8
git push origin v1.1.8
```

3. To upload a locally built APK manually:

```powershell
gh release upload v1.1.8 app\build\outputs\apk\debug\app-debug.apk --repo OneGemmj/302AISeedreamAPP --clobber
```
