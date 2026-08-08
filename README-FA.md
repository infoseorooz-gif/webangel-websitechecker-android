# WebAngel SEO Analyzer — Android v1.0.0

این پروژه Shell اندروید WebsiteChecker است و URL اصلی زیر را به‌صورت امن و App-like اجرا می‌کند:

`https://webangel.marketing/websitechecker`

## مشخصات

- Application ID: `marketing.webangel.websitechecker`
- Version: `1.0.0` / versionCode 1
- minSdk: 26 (Android 8)
- compileSdk / targetSdk: 36 (Android 16)
- Java: 17
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13

## قابلیت‌های Native

- WebView فقط برای `https://webangel.marketing/websitechecker/*`
- لینک‌های خارجی در مرورگر/اپ مربوط باز می‌شوند.
- Back اندروید با History وب هماهنگ است.
- نوار Native پایین: خانه، بررسی‌ها، ابزارها، حساب، اشتراک.
- هدر و فوتر وب در App حذف می‌شوند تا UI دوباره تکرار نشود.
- Session و Cookie حساب کاربری حفظ می‌شود.
- Mixed Content غیرفعال و Cleartext HTTP ممنوع است.
- SSL Error هرگز bypass نمی‌شود.
- File chooser برای input[type=file].
- Download عادی HTTPS با Save As سیستم.
- Blob/Data download برای XML/JSON/PDFهای ساخته‌شده در مرورگر.
- Share بومی Android.
- چاپ Native برای `window.print()` و گزارش‌ها.
- Offline state و Retry.
- Deep Link برای مسیرهای `/websitechecker/*`.
- User-Agent: `WebAngelSEOAndroid/1.0.0`.

## Build در Android Studio

1. Android Studio جدید را نصب کنید.
2. Android SDK Platform 36 را از SDK Manager نصب کنید.
3. این پوشه را با Open Project باز کنید.
4. اگر Android Studio درباره Gradle پرسید، Gradle 8.13 را انتخاب/دانلود کنید.
5. Build > Build APK(s).
6. خروجی Debug در `app/build/outputs/apk/debug/app-debug.apk` قرار می‌گیرد.

## Build با GitHub Actions

Workflow آماده در `.github/workflows/build-android.yml` وجود دارد. پروژه را در یک Repository قرار دهید و Action با نام **Build Android APK** را Run کنید. APK Debug به‌عنوان Artifact تحویل می‌شود.

## انتشار در Google Play

برای انتشار نهایی این موارد هنوز باید انجام شوند:

1. ساخت Release Keystore اختصاصی و نگهداری امن آن.
2. Build امضاشده Release/AAB.
3. ایجاد `assetlinks.json` با SHA-256 گواهی Release برای App Links تأییدشده.
4. تصمیم نهایی درباره خرید اشتراک داخل App و الزامات Google Play Billing.
5. Data Safety و Privacy Policy در Play Console.
6. تست واقعی حداقل روی Android 8، Android 12، Android 15 و Android 16.

**Keystore تولیدی داخل این بسته قرار داده نشده است**؛ کلید انتشار باید در مالکیت صاحب اپ باقی بماند.
