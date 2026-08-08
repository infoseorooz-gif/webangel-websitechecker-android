# Android QA Checklist

## نصب و شروع
- [ ] Splash بدون کشیدگی لوگو
- [ ] شروع در صفحه اصلی WebsiteChecker
- [ ] Status bar / navigation bar بدون هم‌پوشانی
- [ ] چرخش Portrait/Landscape بدون Log out

## حساب
- [ ] Register
- [ ] Login
- [ ] Forgot password
- [ ] Session بعد از بستن و بازکردن App
- [ ] Logout

## تحلیل‌ها
- [ ] تحلیل سریع
- [ ] Compare
- [ ] Site Audit
- [ ] Lighthouse/CWV
- [ ] Report pages
- [ ] PDF export

## ابزارها
- [ ] Sitemap Builder + XML download
- [ ] Schema Builder + JSON download
- [ ] Redirect Checker + CSV/download if present
- [ ] Robots Builder + text download
- [ ] DNS Checker + JSON download

## Native
- [ ] Back button
- [ ] Home / Analyze / Tools / Account / Share
- [ ] External links open outside WebView
- [ ] target=_blank links open outside WebView
- [ ] Offline screen + Retry
- [ ] Blob downloads
- [ ] HTTP(S) downloads
- [ ] File upload if a future page uses it
- [ ] Deep link into /websitechecker/...

## امنیت
- [ ] HTTP cleartext blocked
- [ ] SSL error blocked
- [ ] WebView cannot navigate arbitrary external sites internally
- [ ] No storage/location/camera/microphone permission requested by default
- [ ] JavaScript bridge exposed only while internal pages are allowed in WebView
