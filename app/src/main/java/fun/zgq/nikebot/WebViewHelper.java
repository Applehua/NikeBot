package fun.zgq.nikebot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.alibaba.fastjson.JSONObject;

import org.jsoup.Jsoup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Headers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import static fun.zgq.nikebot.HttpHelper.createSSLSocketFactory;
import static fun.zgq.nikebot.Utils.consumeInputStream;

public class WebViewHelper {

    public static abstract class WaitUrlFinish {
        AtomicBoolean urlFinished = new AtomicBoolean(false);
        JSONObject result;

        public void checkUrl(WebView view, String url) {
            String uniteinfo = getJsonStringFromUrl(url);
            JSONObject uniteResponse = new JSONObject();
            if (!TextUtils.isEmpty(uniteinfo)) {
                uniteResponse = JSONObject.parseObject(uniteinfo);
            }
            if (isThisUrl(view, url, uniteResponse)) {
                urlFinished.set(true);
                result = uniteResponse;
                doSomeThingWhenFinished();
            }
        }

        public abstract boolean isThisUrl(WebView view, String url, JSONObject uniteResponse);

        public abstract void doSomeThingWhenFinished();

        public JSONObject waitResult() {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60000) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (urlFinished.get()) {
                    return result;
                }
            }
            return null;
        }

        protected String getJsonStringFromUrl(String url) {
            try {
                String str = new URL(url).getRef();
                if (str != null) {
                    return URLDecoder.decode(str, "utf-8");
                }
                return str;
            } catch (Exception e) {
                Log.e("nikebot", "Bad Url format:" + url);
            }
            return "";
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public static WebView getWebView(Context context, String userAgent, Map<String, String> extraHeaders, WaitUrlFinish mWaitUrlFinish) {
        WebView webView = new WebView(context);
        try {
            if (TextUtils.isEmpty(userAgent)) {
                userAgent = "";
            }
            UniteWebViewClient webviewclient = new UniteWebViewClient(mWaitUrlFinish, extraHeaders);
//            webView.setWillNotDraw(true);
//            webView.setVisibility(View.GONE);
//            webView.setWebChromeClient(webChromeClient);
            webView.setWebViewClient(webviewclient);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setAppCacheEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            if (!TextUtils.isEmpty(userAgent)) {
                settings.setUserAgentString(userAgent);
            }
            if (Build.VERSION.SDK_INT >= 19) {
                webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            if (Build.VERSION.SDK_INT <= 21) {
                webView.loadUrl("about:blank");
            }
            return webView;
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        return null;
    }

    private static class UniteWebViewClient extends WebViewClient {
        WaitUrlFinish mWaitUrlFinish;
        Map<String, String> extraHeaders;
        String interceptHeader;
        CookieHandler cookieHandler = new java.net.CookieManager();
        OkHttpClient client = new OkHttpClient.Builder()
                .protocols(Collections.unmodifiableList(Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2)))
                .sslSocketFactory(createSSLSocketFactory(), new HttpHelper.TrustAllCerts())
                .hostnameVerifier(new HttpHelper.TrustAllHostnameVerifier())
                .cookieJar(new JavaNetCookieJar(cookieHandler))
                .build();

        public UniteWebViewClient(WaitUrlFinish mWaitUrlFinish, Map<String, String> extraHeaders) {
            this.mWaitUrlFinish = mWaitUrlFinish;
            this.extraHeaders = extraHeaders;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String method = request.getMethod();
            Log.i("nikebot", "shouldInterceptRequest:" + request.getMethod() + ":" + request.getUrl().toString() + ", request:" + request);
            if (!request.getUrl().getHost().contains("nike") || request.getUrl().toString().matches("\\.js$")) {
                return super.shouldInterceptRequest(view, request);
            }
            if (method.equalsIgnoreCase("GET")
                    || method.equalsIgnoreCase("OPTIONS")
                    ) {
                Request.Builder okrequestbuild = new Request.Builder()
                        .url(request.getUrl().toString())
                        .method(method, null)
                        .headers(Headers.of(request.getRequestHeaders()));
                okrequestbuild.header("x-requested-with", "com.nike.snkrs");

                Request okrequest = okrequestbuild.build();

                try {
                    Response response = client.newCall(okrequest).execute();
                    WebResourceResponse res = new WebResourceResponse(
                            response.header("content-type", "text/html"),
                            response.header("content-encoding", "utf-8"),
                            response.body().byteStream()
                    );
                    Map<String, String> headers = okhttpheaderToWebResourceHeader(response.headers());

                    if (method.equalsIgnoreCase("GET")) {
                        res = injectIntercept(res, view.getContext());
                    }
                    if (method.equalsIgnoreCase("OPTIONS")) {
                        headers.put("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
                        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH,OPTIONS");
                        headers.remove("access-control-allow-origin");
                        headers.put("Access-Control-Allow-Origin", "https://s3.nikecdn.com");
                        System.out.println(headers);
                    }
                    res.setResponseHeaders(headers);
                    return res;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Log.i("nikebot", "shouldOverrideUrlLoading:" + request.getUrl().toString());
            view.loadUrl(request.getUrl().toString(), extraHeaders);
            return true;
        }

        public void onPageFinished(WebView webView, String url) {

            if (mWaitUrlFinish != null) {
                mWaitUrlFinish.checkUrl(webView, url);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("onPageFinished() - URL: ");
            stringBuilder.append(url);
            Log.i("nikebot", stringBuilder.toString());
        }

        private WebResourceResponse injectIntercept(WebResourceResponse response, Context
                context) {
            String encoding = response.getEncoding();
            String mime = response.getMimeType();
            InputStream responseData = response.getData();
            InputStream injectedResponseData = injectInterceptToStream(
                    context,
                    responseData,
                    mime,
                    encoding
            );
            return new WebResourceResponse(mime, encoding, injectedResponseData);
        }

        private InputStream injectInterceptToStream(
                Context context,
                InputStream is,
                String mime,
                String charset
        ) {
            try {
                byte[] pageContents = consumeInputStream(is);
                if (mime.equals("text/html")) {
                    pageContents = enableIntercept(context, pageContents)
                            .getBytes(charset);
                    Log.i("nikebot", "after inject:" + new String(pageContents));
                }
                return new ByteArrayInputStream(pageContents);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public String enableIntercept(Context context, byte[] data) throws IOException {
            if (interceptHeader == null) {
                interceptHeader = new String(
                        consumeInputStream(context.getAssets().open("interceptheader.html"))
                );
            }

            org.jsoup.nodes.Document doc = Jsoup.parse(new String(data));
            doc.outputSettings().prettyPrint(true);

            // Prefix every script to capture submits
            // Make sure our interception is the first element in the
            // header
            org.jsoup.select.Elements element = doc.getElementsByTag("head");
            if (element.size() > 0) {
                element.get(0).prepend(interceptHeader);
            }

            String pageContents = doc.toString();
            return pageContents;
        }

        private Map<String, String> okhttpheaderToWebResourceHeader(Headers headers) {
            Map<String, String> newherder = new HashMap<>();
            if (headers != null) {
                for (String key : headers.names()) {
                    newherder.put(key, headers.get(key));
                }
            }
            return newherder;
        }

    }
}