# JavascriptInterface methods must remain callable from WebView JavaScript.
-keepclassmembers class marketing.webangel.websitechecker.WebAngelBridge {
    @android.webkit.JavascriptInterface <methods>;
}
