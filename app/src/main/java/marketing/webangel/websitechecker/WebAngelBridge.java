package marketing.webangel.websitechecker;

import android.webkit.JavascriptInterface;

public final class WebAngelBridge {
    private final MainActivity activity;

    WebAngelBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void saveBase64(String payload, String mimeType, String fileName) {
        activity.receiveBase64Download(payload, mimeType, fileName);
    }

    @JavascriptInterface
    public void shareText(String text, String title) {
        activity.runOnUiThread(() -> activity.shareText(text, title));
    }

    @JavascriptInterface
    public void openExternal(String url) {
        activity.runOnUiThread(() -> activity.openExternalUrl(url));
    }

    @JavascriptInterface
    public void printPage() {
        activity.runOnUiThread(activity::printCurrentPage);
    }
}
