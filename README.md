# Local File Manager & Web Share

Ung dung Android Native (Kotlin + Jetpack Compose + Material 3 + NanoHTTPD).

Du an nay duoc chuan bi de **lam viec truc tiep tren GitHub**: sua code tren web, de GitHub Actions build file APK.

## Tao repo GitHub (khong can Android Studio)

### Cach 1 — Upload thu muc len GitHub.com

1. Vao https://github.com/new
2. Dat ten repo, vi du `LocalFileManagerWebShare`
3. Chon Public hoac Private, **khong** tick "Add a README" (da co san)
4. Bam Create repository
5. Chon uploading an existing file
6. Keo toan bo noi dung trong file zip vao (giu dung cau truc, `README.md` nam o goc repo)
7. Bam Commit changes

### Cach 2 — Dung Git (may tinh hoac GitHub Codespaces)

```bash
git init
git add .
git commit -m "Initial commit: Local File Manager & Web Share"
git branch -M main
git remote add origin https://github.com/TEN_BAN/LocalFileManagerWebShare.git
git push -u origin main
```

## Sua code tren GitHub

- Mo file trong repo tren github.com
- Bam bieu tuong but (Edit)
- File chinh:
  - `app/src/main/java/com/localfm/app/MainActivity.kt` — giao dien
  - `app/src/main/java/com/localfm/app/LocalWebServer.kt` — HTTP server
  - `app/src/main/java/com/localfm/app/NetworkUtils.kt` — lay IP Wi-Fi
  - `app/src/main/AndroidManifest.xml` — quyen
  - `app/build.gradle.kts` — thu vien

Khong can Android Studio de sua. Chi can APK do Actions build khi muon cai vao dien thoai.

## Lay file APK tu GitHub Actions

1. Vao tab Actions cua repo
2. Chon workflow Build Debug APK
3. Bam Run workflow (hoac doi workflow chay sau moi lan push)
4. Khi xong, mo job, muc Artifacts, tai `app-debug`
5. Giai nen, copy `app-debug.apk` sang dien thoai va cai

Lan dau cai APK debug: tren dien thoai can bat Install unknown apps.

## Sau khi cai APK

1. Mo app, cap quyen All files access
2. Dien thoai va may tinh cung Wi-Fi
3. Bat cong tac Web Share
4. Tren may tinh mo `http://IP:8080`

## Cau truc

```
LocalFileManagerWebShare/
  .github/workflows/android.yml
  app/src/main/java/com/localfm/app/MainActivity.kt
  app/src/main/java/com/localfm/app/LocalWebServer.kt
  app/src/main/java/com/localfm/app/NetworkUtils.kt
  app/src/main/AndroidManifest.xml
  app/build.gradle.kts
  gradle/wrapper/
  gradlew
  README.md
```

## Quyen

- INTERNET, ACCESS_WIFI_STATE
- MANAGE_EXTERNAL_STORAGE (Android 11+)

Icon launcher lay tu anh bieu tuong folder + Android ban gui.
