package marketing.webangel.websitechecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.print.PrintAttributes;
import android.print.PrintManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String BASE_URL = "https://webangel.marketing/websitechecker";
    private static final String INTERNAL_HOST = "webangel.marketing";
    private static final String INTERNAL_PREFIX = "/websitechecker";
    private static final String APP_UA = " WebAngelSEOAndroid/1.0.0";

    private static final int REQ_FILE_CHOOSER = 4101;
    private static final int REQ_SAVE_REMOTE = 4102;
    private static final int REQ_SAVE_BLOB = 4103;
    private static final int MAX_BLOB_BYTES = 25 * 1024 * 1024;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout offlineView;
    private LinearLayout bottomNav;
    private ValueCallback<Uri[]> fileChooserCallback;

    private PendingRemoteDownload pendingRemoteDownload;
    private PendingBlobDownload pendingBlobDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
        configureWebView();
        registerBackHandler();

        String initialUrl = resolveLaunchUrl(getIntent());
        if (isOnline()) {
            hideOffline();
            webView.loadUrl(initialUrl);
        } else {
            showOffline();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        int navHeight = dp(62);

        webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        webParams.bottomMargin = navHeight;
        root.addView(webView, webParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF7618")));
            progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        }
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
                Gravity.TOP
        );
        root.addView(progressBar, progressParams);

        offlineView = buildOfflineView();
        FrameLayout.LayoutParams offlineParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        offlineParams.bottomMargin = navHeight;
        root.addView(offlineView, offlineParams);

        bottomNav = buildBottomNavigation();
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                navHeight,
                Gravity.BOTTOM
        );
        root.addView(bottomNav, navParams);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
    }

    private LinearLayout buildOfflineView() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        panel.setBackgroundColor(Color.parseColor("#F5F8FC"));
        panel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText(getString(R.string.offline_title));
        title.setTextColor(Color.parseColor("#10243D"));
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        panel.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.offline_body));
        body.setTextColor(Color.parseColor("#60758A"));
        body.setTextSize(14);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(10);
        panel.addView(body, bodyParams);

        Button retry = new Button(this);
        retry.setText(getString(R.string.retry));
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(14);
        retry.setAllCaps(false);
        GradientDrawable retryBg = new GradientDrawable();
        retryBg.setColor(Color.parseColor("#0B67D1"));
        retryBg.setCornerRadius(dp(12));
        retry.setBackground(retryBg);
        retry.setOnClickListener(v -> {
            if (isOnline()) {
                hideOffline();
                if (webView.getUrl() == null) webView.loadUrl(BASE_URL);
                else webView.reload();
            } else {
                Toast.makeText(this, "هنوز اتصال اینترنت برقرار نیست.", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(160), dp(50));
        retryParams.topMargin = dp(22);
        panel.addView(retry, retryParams);

        return panel;
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(5), dp(5), dp(5), dp(5));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(12));
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        addNavItem(nav, "خانه", () -> loadInternal(BASE_URL));
        addNavItem(nav, "بررسی‌ها", this::showAnalysisMenu);
        addNavItem(nav, "ابزارها", this::showToolsMenu);
        addNavItem(nav, "حساب", () -> loadInternal(BASE_URL + "/account"));
        addNavItem(nav, "اشتراک", this::shareCurrentPage);
        return nav;
    }

    private void addNavItem(LinearLayout nav, String label, Runnable action) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextSize(12);
        item.setTextColor(Color.parseColor("#20364F"));
        item.setGravity(Gravity.CENTER);
        item.setMinHeight(dp(48));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> action.run());
        item.setContentDescription(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        nav.addView(item, params);
    }

    private void showAnalysisMenu() {
        String[] items = {"شروع بررسی سئو", "مقایسه رقبا", "خزش چندصفحه‌ای سایت", "Lighthouse و Core Web Vitals"};
        String[] urls = {BASE_URL + "/#main-content", BASE_URL + "/compare", BASE_URL + "/site-audit", BASE_URL + "/advanced-audit"};
        new AlertDialog.Builder(this)
                .setTitle("بررسی‌های سئو")
                .setItems(items, (dialog, which) -> loadInternal(urls[which]))
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showToolsMenu() {
        String[] items = {"ساخت نقشه سایت XML", "ساخت اسکیما JSON-LD", "بررسی ریدایرکت", "ساخت و تست robots.txt", "بررسی DNS و دامنه"};
        String[] urls = {BASE_URL + "/sitemap-builder", BASE_URL + "/schema-builder", BASE_URL + "/redirect-checker", BASE_URL + "/seo-robots-builder", BASE_URL + "/dns-checker"};
        new AlertDialog.Builder(this)
                .setTitle("ابزارهای سئو")
                .setItems(items, (dialog, which) -> loadInternal(urls[which]))
                .setNegativeButton("بستن", null)
                .show();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + APP_UA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        WebView.setWebContentsDebuggingEnabled(isDebuggable());
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.addJavascriptInterface(new WebAngelBridge(this), "WebAngelAndroid");
        webView.setWebViewClient(new SecureWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());
        webView.setDownloadListener(new AppDownloadListener());
    }

    private final class SecureWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return handleNavigation(uri == null ? null : uri.toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(5);
            hideOffline();
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            applyNativePageMode();
            super.onPageCommitVisible(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            applyNativePageMode();
            progressBar.setProgress(100);
            progressBar.setVisibility(View.GONE);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showOffline();
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) {
                Toast.makeText(MainActivity.this, "پاسخ سرور موقتاً با خطا روبه‌رو شد.", Toast.LENGTH_SHORT).show();
            }
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            Toast.makeText(MainActivity.this, "اتصال امن این صفحه تأیید نشد.", Toast.LENGTH_LONG).show();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            Toast.makeText(MainActivity.this, "نمایشگر وب دوباره راه‌اندازی می‌شود.", Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        }
    }

    private final class AppChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = filePathCallback;

            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception ignored) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }

            try {
                startActivityForResult(intent, REQ_FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException e) {
                fileChooserCallback = null;
                Toast.makeText(MainActivity.this, "انتخاب فایل روی این دستگاه در دسترس نیست.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private final class AppDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            if (isBlank(url)) return;
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                captureBlobDownload(url, mimeType, URLUtil.guessFileName(url, contentDisposition, mimeType));
                return;
            }
            if (!url.startsWith("https://")) {
                Toast.makeText(MainActivity.this, "دانلود فقط از اتصال HTTPS انجام می‌شود.", Toast.LENGTH_SHORT).show();
                return;
            }
            requestRemoteDownload(url, userAgent, contentDisposition, mimeType);
        }
    }

    private boolean handleNavigation(String url) {
        if (isBlank(url)) return false;
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return true;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme) && isInternal(uri)) return false;
        if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme) || "sms".equals(scheme)) {
            openExternalUrl(url);
            return true;
        }
        if ("intent".equals(scheme)) {
            openIntentUrl(url);
            return true;
        }
        return true;
    }

    private boolean isInternal(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();
        return "https".equalsIgnoreCase(scheme)
                && INTERNAL_HOST.equalsIgnoreCase(host)
                && path != null
                && (path.equals(INTERNAL_PREFIX) || path.startsWith(INTERNAL_PREFIX + "/"));
    }

    private void loadInternal(String url) {
        if (!isOnline()) {
            showOffline();
            return;
        }
        Uri uri = Uri.parse(url);
        if (isInternal(uri)) {
            hideOffline();
            webView.loadUrl(url);
        }
    }

    void openExternalUrl(String url) {
        if (isBlank(url)) return;
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http") || scheme.equals("mailto") || scheme.equals("tel") || scheme.equals("sms"))) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "برنامه‌ای برای بازکردن این لینک پیدا نشد.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openIntentUrl(String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "این لینک روی دستگاه قابل بازکردن نیست.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyNativePageMode() {
        String js = "(function(){" +
                "try{" +
                "document.documentElement.setAttribute('data-wa-native-app','android');" +
                "var h=document.querySelector('.site-header');if(h)h.remove();" +
                "var f=document.querySelector('.site-footer');if(f)f.remove();" +
                "var s=document.querySelector('.skip-to-content');if(s)s.remove();" +
                "if(!window.__waAndroidBound){window.__waAndroidBound=true;" +
                "document.addEventListener('click',function(e){" +
                "var a=e.target&&e.target.closest?e.target.closest('a'):null;if(!a)return;" +
                "var href=a.href||'';" +
                "if(a.hasAttribute('download')&&(href.indexOf('blob:')===0||href.indexOf('data:')===0)){" +
                "e.preventDefault();e.stopImmediatePropagation();" +
                "var fn=a.getAttribute('download')||'download';" +
                "if(href.indexOf('data:')===0){var c=href.indexOf(',');var head=href.substring(5,c);var body=href.substring(c+1);var mime=head.split(';')[0]||'application/octet-stream';" +
                "if(head.indexOf(';base64')<0){body=btoa(unescape(encodeURIComponent(decodeURIComponent(body))));}WebAngelAndroid.saveBase64(body,mime,fn);return;}" +
                "fetch(href).then(function(r){return r.blob();}).then(function(b){var fr=new FileReader();fr.onloadend=function(){var x=String(fr.result||'');WebAngelAndroid.saveBase64(x.substring(x.indexOf(',')+1),b.type||'application/octet-stream',fn);};fr.readAsDataURL(b);});return;" +
                "}" +
                "if(a.getAttribute('target')==='_blank'&&href){e.preventDefault();WebAngelAndroid.openExternal(href);}" +
                "},true);" +
                "window.print=function(){WebAngelAndroid.printPage();};" +
                "}" +
                "}catch(ex){}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void captureBlobDownload(String url, String mimeType, String fileName) {
        String safeMime = isBlank(mimeType) ? "application/octet-stream" : mimeType;
        String safeName = sanitizeFileName(fileName, "download");
        String quotedUrl = org.json.JSONObject.quote(url);
        String quotedMime = org.json.JSONObject.quote(safeMime);
        String quotedName = org.json.JSONObject.quote(safeName);
        String js = "(function(){var u=" + quotedUrl + ";" +
                "if(u.indexOf('data:')===0){var c=u.indexOf(',');var h=u.substring(5,c);var b=u.substring(c+1);var m=h.split(';')[0]||" + quotedMime + ";if(h.indexOf(';base64')<0)b=btoa(unescape(encodeURIComponent(decodeURIComponent(b))));WebAngelAndroid.saveBase64(b,m," + quotedName + ");return;}" +
                "fetch(u).then(function(r){return r.blob();}).then(function(b){var f=new FileReader();f.onloadend=function(){var x=String(f.result||'');WebAngelAndroid.saveBase64(x.substring(x.indexOf(',')+1),b.type||" + quotedMime + "," + quotedName + ");};f.readAsDataURL(b);});})();";
        webView.evaluateJavascript(js, null);
    }

    void receiveBase64Download(String payload, String mimeType, String fileName) {
        if (isBlank(payload)) return;
        ioExecutor.execute(() -> {
            try {
                String base64 = payload;
                int comma = base64.indexOf(',');
                if (base64.startsWith("data:") && comma >= 0) base64 = base64.substring(comma + 1);
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (bytes.length > MAX_BLOB_BYTES) {
                    runOnUiThread(() -> Toast.makeText(this, "فایل برای انتقال مستقیم از WebView بزرگ است.", Toast.LENGTH_LONG).show());
                    return;
                }
                File temp = new File(getCacheDir(), "wa-download-" + System.currentTimeMillis());
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    out.write(bytes);
                }
                pendingBlobDownload = new PendingBlobDownload(temp, normalizeMime(mimeType), sanitizeFileName(fileName, "webangel-export"));
                runOnUiThread(() -> launchSaveDocument(REQ_SAVE_BLOB, pendingBlobDownload.mimeType, pendingBlobDownload.fileName));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "ذخیره فایل کامل نشد.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void requestRemoteDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        String name = sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType), "download");
        String effectiveUserAgent = isBlank(userAgent) ? webView.getSettings().getUserAgentString() : userAgent;
        String cookie = CookieManager.getInstance().getCookie(url);
        pendingRemoteDownload = new PendingRemoteDownload(url, effectiveUserAgent, cookie, normalizeMime(mimeType), name);
        launchSaveDocument(REQ_SAVE_REMOTE, pendingRemoteDownload.mimeType, pendingRemoteDownload.fileName);
    }

    private void launchSaveDocument(int requestCode, String mimeType, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(normalizeMime(mimeType));
        intent.putExtra(Intent.EXTRA_TITLE, sanitizeFileName(fileName, "download"));
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "ذخیره فایل روی این دستگاه در دسترس نیست.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveRemoteToUri(PendingRemoteDownload download, Uri destination) {
        ioExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(download.url);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(45000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", download.userAgent);
                if (!isBlank(download.cookie)) connection.setRequestProperty("Cookie", download.cookie);
                connection.connect();
                int code = connection.getResponseCode();
                if (code < 200 || code >= 400) throw new IllegalStateException("HTTP " + code);
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     OutputStream out = new BufferedOutputStream(getContentResolver().openOutputStream(destination, "w"))) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    out.flush();
                }
                runOnUiThread(() -> Toast.makeText(this, "فایل ذخیره شد.", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "دانلود فایل کامل نشد.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void saveBlobToUri(PendingBlobDownload blob, Uri destination) {
        ioExecutor.execute(() -> {
            try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(blob.tempFile));
                 OutputStream out = new BufferedOutputStream(getContentResolver().openOutputStream(destination, "w"))) {
                if (out == null) throw new IllegalStateException("No output stream");
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
                blob.tempFile.delete();
                runOnUiThread(() -> Toast.makeText(this, "فایل ذخیره شد.", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "ذخیره فایل کامل نشد.", Toast.LENGTH_LONG).show());
            }
        });
    }

    void printCurrentPage() {
        if (webView == null) return;
        try {
            PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (manager == null) {
                Toast.makeText(this, "سرویس چاپ روی این دستگاه در دسترس نیست.", Toast.LENGTH_SHORT).show();
                return;
            }
            String jobName = "WebAngel-SEO-" + System.currentTimeMillis();
            manager.print(jobName, webView.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
        } catch (Exception e) {
            Toast.makeText(this, "چاپ صفحه شروع نشد.", Toast.LENGTH_SHORT).show();
        }
    }

    void shareText(String text, String title) {
        String share = isBlank(text) ? (webView.getUrl() == null ? BASE_URL : webView.getUrl()) : text;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, share);
        if (!isBlank(title)) intent.putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"));
    }

    private void shareCurrentPage() {
        String title = webView.getTitle() == null ? "وب‌آنجل SEO Analyzer" : webView.getTitle();
        String url = webView.getUrl() == null ? BASE_URL : webView.getUrl();
        shareText(title + "\n" + url, title);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileChooserCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
            return;
        }

        if (requestCode == REQ_SAVE_REMOTE) {
            PendingRemoteDownload download = pendingRemoteDownload;
            pendingRemoteDownload = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && download != null) {
                saveRemoteToUri(download, data.getData());
            }
            return;
        }

        if (requestCode == REQ_SAVE_BLOB) {
            PendingBlobDownload blob = pendingBlobDownload;
            pendingBlobDownload = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && blob != null) {
                saveBlobToUri(blob, data.getData());
            } else if (blob != null) {
                blob.tempFile.delete();
            }
        }
    }

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) handleBack();
    }

    private void handleBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = resolveLaunchUrl(intent);
        if (url != null) loadInternal(url);
    }

    private String resolveLaunchUrl(Intent intent) {
        if (intent == null || intent.getData() == null) return BASE_URL;
        Uri data = intent.getData();
        if (isInternal(data)) return data.toString();
        if ("webangelseo".equalsIgnoreCase(data.getScheme()) && "open".equalsIgnoreCase(data.getHost())) {
            String path = data.getQueryParameter("path");
            if (path != null && path.startsWith("/") && !path.startsWith("//")) return BASE_URL + path;
        }
        return BASE_URL;
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOffline() {
        offlineView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideOffline() {
        offlineView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalizeMime(String mime) {
        if (isBlank(mime) || !mime.contains("/")) return "application/octet-stream";
        return mime.split(";", 2)[0].trim();
    }

    private static String sanitizeFileName(String value, String fallback) {
        String name = value == null ? "" : value.trim();
        if (isBlank(name)) name = fallback;
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "-");
        if (name.length() > 120) name = name.substring(0, 120);
        return name;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (!isOnline()) showOffline();
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("WebAngelAndroid");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class PendingRemoteDownload {
        final String url;
        final String userAgent;
        final String cookie;
        final String mimeType;
        final String fileName;

        PendingRemoteDownload(String url, String userAgent, String cookie, String mimeType, String fileName) {
            this.url = url;
            this.userAgent = userAgent;
            this.cookie = cookie;
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }

    private static final class PendingBlobDownload {
        final File tempFile;
        final String mimeType;
        final String fileName;

        PendingBlobDownload(File tempFile, String mimeType, String fileName) {
            this.tempFile = tempFile;
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }
}
