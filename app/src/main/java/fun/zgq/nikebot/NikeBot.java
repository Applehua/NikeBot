package fun.zgq.nikebot;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import fun.zgq.nikebot.models.Address;
import fun.zgq.nikebot.models.ApiResult;
import fun.zgq.nikebot.models.CheckoutParams;
import fun.zgq.nikebot.models.CheckoutPreviewParams;
import fun.zgq.nikebot.models.PaymentParams;
import fun.zgq.nikebot.models.PaymentPreviewParams;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static fun.zgq.nikebot.DeviceInfo.randomScreenSize;
import static fun.zgq.nikebot.HttpHelper.createSSLSocketFactory;
import static fun.zgq.nikebot.Utils.md5;
import static fun.zgq.nikebot.Utils.simpleJSON;
import static fun.zgq.nikebot.Utils.simpleJSONArray;
import static fun.zgq.nikebot.Utils.simulateTouchEvent;

public class NikeBot {
    private static final boolean DEBUG = true;

    private static ReentrantLock loginLock = new ReentrantLock();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    CookieHandler cookieHandler = new CookieManager();
    OkHttpClient client = new OkHttpClient.Builder()
            .protocols(Collections.unmodifiableList(Arrays.asList(Protocol.HTTP_1_1, Protocol.HTTP_2)))
            .sslSocketFactory(createSSLSocketFactory(), new HttpHelper.TrustAllCerts())
            .hostnameVerifier(new HttpHelper.TrustAllHostnameVerifier())
            .cookieJar(new JavaNetCookieJar(cookieHandler))
            .build();
    Context context;
    Handler uiHandler;

    private String relicId;
    private String vistor;

    //模拟手机信息
    DeviceInfo mDeviceInfo;

    //账号信息
    private String username;
    private String password;
    private String accessToken;
    private String userId;
    private JSONObject userInfo;

    //app信息
    private static final String clientId = "qG9fJbnMcBPAMGibPRGI72Zr89l8CD4R";
    private static final String uxId = "com.nike.commerce.snkrs.droid";
    private static final String appVersion = "630";
    private static final String experienceVersion = "528";
    private static final String locale = "zh_CN";

    SecretKey aesKey = null;
    SecretKey hmacKey = null;
    String aesKeyEncrypted = null;
    String hamcKeyEncryped = null;

    public NikeBot(Context context, String username, String password) {
        this.context = context;
        uiHandler = new Handler(context.getMainLooper());
        this.username = username;
        this.password = password;
        relicId = Base64.encodeToString(md5("relicid" + username).substring(0, 16).getBytes(), 1);
        vistor = UUID.randomUUID().toString();
        mDeviceInfo = new DeviceInfo(username);
    }

    public NikeBot proxy(String hostName, int port) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostName, port));
        client = client.newBuilder().proxy(proxy).build();
        return this;
    }

    public JSONObject login() throws IOException {
        try {
            loginLock.lock();
            String mid = getRandomNumber(38);
            HttpUrl httpUrl = HttpUrl.parse("https://s3.nikecdn.com/unite/mobile.html").newBuilder()
                    .addQueryParameter("mid", mid)
                    .addQueryParameter("androidSDKVersion", "2.8.1")
                    .addQueryParameter("uxid", uxId)
                    .addQueryParameter("locale", locale)
                    .addQueryParameter("backendEnvironment", "identity")
                    .addQueryParameter("view", "login")
                    .addQueryParameter("clientId", clientId)
                    .build();
            final String baseurl = httpUrl.toString();
            final WebViewHelper.WaitUrlFinish mWaitUrlFinish = new WebViewHelper.WaitUrlFinish() {

                @Override
                public boolean isThisUrl(final WebView view, String url, JSONObject uniteResponse) {
                    Log.i("nikebot", "check url:" + url + "\nuniteResponse:" + uniteResponse);
                    String event = uniteResponse.getString("event");
                    if (TextUtils.equals(event, "loaded")) {
                        uiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                StringBuilder js = new StringBuilder();
                                js.append("javascript:");
                                js.append("var e = document.createEvent('MouseEvents');e.initEvent('mousedown', true, true);");
                                js.append("document.getElementsByName('verifyMobileNumber')[0].dispatchEvent(e);");
                                js.append("document.getElementsByName('verifyMobileNumber')[0].value=");
                                js.append("'" + username + "';");
                                js.append("document.getElementsByName('password')[0].dispatchEvent(e);");
                                js.append("document.getElementsByName('password')[0].value=");
                                js.append("'" + password + "';");
                                js.append("setTimeout(function(){document.getElementsByClassName('nike-unite-submit-button mobileLoginSubmit nike-unite-component')[0].children[0].click();},500);");
                                view.loadUrl(js.toString());
                                Log.i("nikebot", "loadjs:" + js.toString());
                            }
                        }, 500);
                        return false;
                    }
                    if (TextUtils.equals("success", event) || TextUtils.equals(event, "fail")) {
                        return true;
                    }
                    return false;
                }

                @Override
                public void doSomeThingWhenFinished() {

                }
            };
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Map<String, String> extraHeaders = new HashMap<String, String>();
                    extraHeaders.put("x-requested-with", "com.nike.snkrs");
                    WebView webview = WebViewHelper.getWebView(context, webUserAgent(), extraHeaders, mWaitUrlFinish);
                    webview.clearCache(true);
                    webview.loadUrl(baseurl, extraHeaders);
                }
            });
            JSONObject uniteResponse = mWaitUrlFinish.waitResult();
            Log.i("nikebot", "login finished:" + uniteResponse);
            if (uniteResponse != null) {
                setUserInfo(uniteResponse);
            }
            return uniteResponse;
        } finally {
            loginLock.unlock();
        }
    }


    public ApiResult checkoutPreview(CheckoutPreviewParams params) throws IOException {
        loginIfNeeded();

        String checkoutId = UUID.randomUUID().toString();
        String url = "https://api.nike.com/buy/checkout_previews/v2/" + checkoutId;
        String sensorData = getSensorData();
        System.out.println("sensorData:" + sensorData);
//        sensorData = "1,a,EqDC1zLvqucVr1p0c2EwrYgDst+CKKdOlvoiQN0i46er3k0XpWkP7hGu+uQO+XKqgd4PY2j6HU1mmXAC9+8RkCy0ZsCCjwawpG5uEx2u7vLpYkTdFZmSP+k8qgG+0AfFUOag6m/QEKZvWo3NNOrxnjgM4Qv9SZdgmFh1CUVQ/oc=,ix2eoaGqv1c8ZQdVkCD2jOgAk6IjLzlMzZn/JmNW7KSKkRjVSY8/4w3i+cQQFodcE1oLiRMeHatTUTOWnDCtEw21Xng0Pf+JvcMM8nP8vyy1bHq6L8+aDySQPbCRy1Zk+kfWpM5od3D3x5bKzthdgj0mTFz3UOn2u2KovO1VLdA=$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$0,0,0";
        Headers headers = Headers.of("authorization", "Bearer " + accessToken,
                "x-nike-ux-id", uxId,
                "accept", "application/json",
                "x-acf-sensor-data", sensorData,
                "user-agent", appUserAgent(),
                "x-newrelic-id", relicId
        );

        String deviceId = mDeviceInfo.getClientInfo();
        String currency = "CNY";

        if (params.getShippingAddress() == null) {
            Address add = getAccountAddress();
            if (add != null) {
                params.setShippingAddress(add);
            }
        }


        JSONObject body = new JSONObject();
        body.put("request", simpleJSON("channel", "SNKRS",
                "clientInfo", simpleJSON("client", uxId, "deviceId", deviceId),
                "country", userInfo.getJSONObject("location").getString("country"),
                "currency", currency,
                "email", userInfo.getJSONObject("emails").getJSONObject("primary").getString("email"),
                "invoiceInfo", new JSONArray(),
                "items", simpleJSONArray(simpleJSON("contactInfo", simpleJSON("email", userInfo.getJSONObject("emails").getJSONObject("primary").getString("email"),
                        "phoneNumber", userInfo.getJSONObject("address").getJSONObject("shipping").getJSONObject("phone").getString("primary")),
                        "id", UUID.randomUUID().toString(),
                        "quantity", 1,
                        "recipient", simpleJSON("altFirstName", "", "altLastName", "",
                                "firstName", userInfo.getJSONObject("name").getJSONObject("latin").getString("given"),
                                "lastName", userInfo.getJSONObject("name").getJSONObject("latin").getString("family")),
                        "shippingAddress", simpleJSON("address1", params.getShippingAddress().getAddress(),
                                "city", params.getShippingAddress().getCity(),
                                "country", params.getShippingAddress().getCountry(),
                                "county", params.getShippingAddress().getCounty(),
                                "state", params.getShippingAddress().getState()),
                        "shippingMethod", "GROUND_SERVICE",
                        "skuId", params.getSkuId(),
                        "valueAddedServices", new JSONArray()
                )),
                "locale", locale
        ));
        JSONObject resjson = doJob("PUT", url, headers, body.toString());
        return ApiResult.parseResult(resjson);
    }

    private Address getAccountAddress() {
        JSONObject shipping = userInfo.getJSONObject("address").getJSONObject("shipping");
        Address add = new Address();
        add.setState(shipping.getString("province"));
        add.setCounty(shipping.getString("zone"));
        add.setCountry(shipping.getString("country"));
        add.setAddress(shipping.getString("line1"));
        add.setCity(shipping.getString("locality"));
        return add;
    }

    public ApiResult paymnetPreview(PaymentPreviewParams params) throws IOException {
        loginIfNeeded();
        if (params.getShippingAddress() == null) {
            Address add = getAccountAddress();
            if (add != null) {
                params.setShippingAddress(add);
            }
        }

        String url = "https://api.nike.com/payment/preview/v2";
        String sensorData = getSensorData();
        System.out.println("sensorData:" + sensorData);
        Headers headers = Headers.of("authorization", "Bearer " + accessToken,
                "x-nike-ux-id", uxId,
                "accept", "application/json",
                "x-acf-sensor-data", sensorData,
                "user-agent", appUserAgent(),
                "x-newrelic-id", relicId
        );

        String currency = "CNY";

        JSONObject body = simpleJSON(
                "checkoutId", params.getCheckoutId(),
                "country", userInfo.getJSONObject("location").getString("country"),
                "currency", currency,
                "items", simpleJSONArray(simpleJSON(
                        "productId", params.getProductId(),
                        "shippingAddress", simpleJSON("address1", params.getShippingAddress().getAddress(),
                                "city", params.getShippingAddress().getCity(),
                                "country", params.getShippingAddress().getCountry(),
                                "county", params.getShippingAddress().getCounty(),
                                "state", params.getShippingAddress().getState())
                )),
                "paymentInfo", simpleJSONArray(simpleJSON(
                        "billingInfo", simpleJSON(
                                "address", simpleJSON("address1", params.getShippingAddress().getAddress(),
                                        "city", params.getShippingAddress().getCity(),
                                        "country", params.getShippingAddress().getCountry(),
                                        "county", params.getShippingAddress().getCounty(),
                                        "state", params.getShippingAddress().getState()),
                                "contactInfo", simpleJSON("email", userInfo.getJSONObject("emails").getJSONObject("primary").getString("email"),
                                        "phoneNumber", userInfo.getJSONObject("address").getJSONObject("shipping").getJSONObject("phone").getString("primary")),
                                "name", simpleJSON(
                                        "firstName", userInfo.getJSONObject("name").getJSONObject("latin").getString("given"),
                                        "lastName", userInfo.getJSONObject("name").getJSONObject("latin").getString("family"))
                        ),
                        "id", UUID.randomUUID().toString(),
                        "type", params.getPayType()
                )),
                "total", params.getTotal()
        );
        JSONObject resjson = doJob("POST", url, headers, body.toString());
        return ApiResult.parseResult(resjson);
    }

    public ApiResult checkout(CheckoutParams params) throws IOException {
        //生成订单
        loginIfNeeded();
        if (params.getShippingAddress() == null) {
            Address add = getAccountAddress();
            if (add != null) {
                params.setShippingAddress(add);
            }
        }

        String checkoutId = UUID.randomUUID().toString();
        String url = "https://api.nike.com/buy/checkouts/v2/" + checkoutId;
        String sensorData = getSensorData();
        System.out.println("sensorData:" + sensorData);
//        sensorData = "1,a,EqDC1zLvqucVr1p0c2EwrYgDst+CKKdOlvoiQN0i46er3k0XpWkP7hGu+uQO+XKqgd4PY2j6HU1mmXAC9+8RkCy0ZsCCjwawpG5uEx2u7vLpYkTdFZmSP+k8qgG+0AfFUOag6m/QEKZvWo3NNOrxnjgM4Qv9SZdgmFh1CUVQ/oc=,ix2eoaGqv1c8ZQdVkCD2jOgAk6IjLzlMzZn/JmNW7KSKkRjVSY8/4w3i+cQQFodcE1oLiRMeHatTUTOWnDCtEw21Xng0Pf+JvcMM8nP8vyy1bHq6L8+aDySQPbCRy1Zk+kfWpM5od3D3x5bKzthdgj0mTFz3UOn2u2KovO1VLdA=$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$0,0,0";
        Headers headers = Headers.of("authorization", "Bearer " + accessToken,
                "x-nike-ux-id", uxId,
                "accept", "application/json",
                "x-acf-sensor-data", sensorData,
                "user-agent", appUserAgent(),
                "x-newrelic-id", relicId
        );

        String deviceId = mDeviceInfo.getClientInfo();
        String currency = "CNY";

        JSONObject body = new JSONObject();
        body.put("request", simpleJSON("channel", "SNKRS",
                "clientInfo", simpleJSON("client", uxId, "deviceId", deviceId),
                "country", userInfo.getJSONObject("location").getString("country"),
                "currency", currency,
                "email", userInfo.getJSONObject("emails").getJSONObject("primary").getString("email"),
                "invoiceInfo", new JSONArray(),
                "items", simpleJSONArray(simpleJSON("contactInfo", simpleJSON("email", userInfo.getJSONObject("emails").getJSONObject("primary").getString("email"),
                        "phoneNumber", userInfo.getJSONObject("address").getJSONObject("shipping").getJSONObject("phone").getString("primary")),
                        "id", UUID.randomUUID().toString(),
                        "quantity", 1,
                        "recipient", simpleJSON("altFirstName", "", "altLastName", "",
                                "firstName", userInfo.getJSONObject("name").getJSONObject("latin").getString("given"),
                                "lastName", userInfo.getJSONObject("name").getJSONObject("latin").getString("family")),
                        "shippingAddress", simpleJSON("address1", params.getShippingAddress().getAddress(),
                                "city", params.getShippingAddress().getCity(),
                                "country", params.getShippingAddress().getCountry(),
                                "county", params.getShippingAddress().getCounty(),
                                "state", params.getShippingAddress().getState()),
                        "shippingMethod", "GROUND_SERVICE",
                        "skuId", params.getSkuId(),
                        "valueAddedServices", new JSONArray()
                )),
                "locale", locale,
                "paymentToken", params.getPaymentToken(),
                "priceChecksum", params.getPriceChecksum()
        ));
        JSONObject resjson = doJob("PUT", url, headers, body.toString());
        return ApiResult.parseResult(resjson);
    }

    public ApiResult payment(PaymentParams params) throws IOException {
        loginIfNeeded();

        String url = "https://api.nike.com/payment/deferred_payment_forms/v1";
        String sensorData = getSensorData();
        System.out.println("sensorData:" + sensorData);
//        sensorData = "1,a,EqDC1zLvqucVr1p0c2EwrYgDst+CKKdOlvoiQN0i46er3k0XpWkP7hGu+uQO+XKqgd4PY2j6HU1mmXAC9+8RkCy0ZsCCjwawpG5uEx2u7vLpYkTdFZmSP+k8qgG+0AfFUOag6m/QEKZvWo3NNOrxnjgM4Qv9SZdgmFh1CUVQ/oc=,ix2eoaGqv1c8ZQdVkCD2jOgAk6IjLzlMzZn/JmNW7KSKkRjVSY8/4w3i+cQQFodcE1oLiRMeHatTUTOWnDCtEw21Xng0Pf+JvcMM8nP8vyy1bHq6L8+aDySQPbCRy1Zk+kfWpM5od3D3x5bKzthdgj0mTFz3UOn2u2KovO1VLdA=$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$0,0,0";
        Headers headers = Headers.of("authorization", "Bearer " + accessToken,
                "x-nike-ux-id", uxId,
                "accept", "application/json",
                "x-acf-sensor-data", sensorData,
                "user-agent", appUserAgent(),
                "x-newrelic-id", relicId
        );


        JSONObject body = new JSONObject();
        body.put("request", simpleJSON(
                "approvalId", params.getApprovalId(),
                "experienceType", "APP",
                "orderNumber", params.getOrderNumber(),
                "returnURL", "http://orders.nike.com/" + params.getOrderNumber()
        ));
        JSONObject resjson = doJob("POST", url, headers, body.toString());
        return ApiResult.parseResult(resjson);
    }

    private void loginIfNeeded() throws IOException {
        if (!isLogin()) {
            login();
        }
    }

    private boolean isLogin() {
        return !TextUtils.isEmpty(accessToken);
    }

    private void logout() {
        accessToken = null;
        userId = null;
        userInfo = null;
    }

    private String webUserAgent() {
        //TODO 更随机
//        return "Dalvik/2.1.0 (Linux; U; Android 6.0.1; Le X820 Build/FEXCNFN6003009092S)";
        return "Mozilla/5.0 (Linux; Android 6.0.1; Huawei p20 Build/HHAA03009092S; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/49.0.2623.91 Mobile Safari/537.36";
    }

    private String appUserAgent() {
        return "SNKRS/2019071707 (Android; Android " + mDeviceInfo.androidVersion + "; Size/normal; Density/xxxhdpi; " + mDeviceInfo.model + ")";
    }

    public void setUserInfo(JSONObject json) {
        accessToken = json.getString("access_token");
        userId = json.getString("user_id");
        userInfo = json.getJSONObject("user");
    }

    private JSONObject doJob(String method, String url, Headers headers, String jsonbody) throws IOException {
        Response job = postOrPost(method, url, headers, jsonbody);
        JSONObject jobjson = JSONObject.parseObject(job.body().string());
        String jobResultUrl = "https://api.nike.com" + jobjson.getJSONObject("links").getJSONObject("self").getString("ref");
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(500l);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Response jobResult = get(jobResultUrl, headers);
            String content = jobResult.body().string();
            JSONObject json = JSONObject.parseObject(content);
            if (TextUtils.equals(json.getString("status"), "COMPLETED")) {
                return json;
            }
        }
        return null;
    }

    private Response postOrPost(String method, String url, Headers headers, String jsonbody) throws IOException {
        RequestBody body = RequestBody.create(jsonbody, JSON);
        Request request = new Request.Builder()
                .url(url)
                .method(method, body)
                .headers(headers)
                .build();
        Response response = client.newCall(request).execute();
        return response;
    }

    private Response get(String url, Headers headers) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .headers(headers)
                .build();
        Response response = client.newCall(request).execute();
        return response;
    }

    private void log(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }

    private String getRandomNumber(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(Math.max((int) (Math.random() * 10), 1));
        }
        return sb.toString();
    }

    public String getSensorData() {
        String model = mDeviceInfo.model;
        String androidVersion = mDeviceInfo.androidVersion;
        String brand = mDeviceInfo.brand;
        Random random = new Random();
        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append("2.2.1-1,2,-94,-100,")
                .append("-1,uaend,-1,")
                .append(randomScreenSize())
                .append(",1,100,1,zh,")
                .append(androidVersion).append(",0,")
                .append(brand)
                .append(",unknown,qcom,-1,com.nike.snkrs,-1,-1,")
                .append(UUID.randomUUID().toString())
                .append(",-1,0,1,REL,")
                .append(208).append(",23,")
                .append(brand).append(",").append(brand).append(",release-keys,user,sysop,")
                .append(model).append(",").append(brand).append(",").append(brand).append(",").append(brand).append(",").append(brand).append("/").append(brand).append("/").append(brand)
                .append(":").append(androidVersion).append("/").append(model).append("/").append(208)
                .append(":user/release-keys,")
                .append("builder02,").append(model);
        int length = chrplus(stringBuilder6.toString());
        stringBuilder6.append(",").append(length).append(",").append(Math.max(1, random.nextInt(9999))).append(",").append(System.currentTimeMillis() / 2)
                .append("-1,2,-94,-101,")
                .append("do_unr,dm_en,t_en")
                .append("-1,2,-94,-102,")
                .append("-1,2,-94,-108,")
                .append("-1,2,-94,-117,")
                .append("-1,2,-94,-111,")
                .append("488,251.16,36.5,-1.78,1;237,258.35,44.35,-13.11,1;197,266.15,47.71,-23.78,1;198,278.47,49.24,-40.3,1;196,290.74,48.53,-55.62,1;200,303.92,43.47,-73.12,1;196,319.91,45.4,-85.75,1;197,320.26,43.32,-87.43,1;234,316.89,44.01,-84.71,1;160,324.08,46.81,-88.62,1;")
                .append("-1,2,-94,-109,")
                .append("330,-0.09,-2.19,-2.98,-0.23,-5.95,-8.1,16.29,-4.44,1.62,1;157,-0.17,-1.17,-1.08,-0.54,-6.44,-7.61,52.57,-37,27.78,1;237,-0.64,-0.65,-0.08,-1.74,-6.67,-6.7,111.33,-104.75,38.67,1;197,-0.83,-0.74,1.39,-2.94,-7.65,-3.57,23.57,-13.33,63.47,1;198,-1.2,-0.56,0.01,-4.66,-8.1,-4.94,16.88,-110.1,17.13,1;197,-0.6,-0.01,0.72,-4.71,-7.57,-3.45,18.99,-90.13,41.01,1;200,-1.38,0.94,0.99,-7.25,-6.14,-1.01,9.08,-110.71,70.58,1;196,-1.03,0.96,1.31,-7.95,-5.15,0.64,19.63,-87.92,-29.64,1;196,0.7,-0.13,1.23,-5.52,-6.38,1.8,-21.57,-14.16,22.13,1;235,-0.3,0.16,0.53,-6.81,-5.93,1.62,-52.49,-9.56,-12.3,1;")
                .append("-1,2,-94,-144,")
                .append("2;160.00;488.00;2291790394;}O3GH2GNAGHGH3GHGH4G2HGH2GH2GH2GHGH8GHGH7G2H3GHG")
                .append("-1,2,-94,-142,")
                .append("2;251.16;350.86;3976058770;AEJQX`2jhlo3q2rt2vw3xy2z11{14|}4|z_HECGTm:2;26.95;63.23;2779607165;P]ced._.]ade3f2g2h2i4k11j19k2jeLAMo}{:2;-105.99;-1.78;1915754872;}vpf]SLKMJI4HGF2E3D2C6B26ABVotyugO")
                .append("-1,2,-94,-145,")
                .append("2;157.00;330.00;3702715838;}A.NONO2N.BNONO2N2ON2O3N2ONONMP2NO2NON2ON2ONO2NOMPNO2NO2N2ONONO")
                .append("-1,2,-94,-143,")
                .append("2;-1.38;0.82;4185390251;daUOEVAJy^nmkigf2gfghehkfghefge7f4g15fgfw}tyo:2;-2.19;1.08;1351463476;AS][^i2zflcf2g7hi4h13ihih16inx}wZK:2;-4.96;2.41;87824826;Q`htioqtsmiklj7i2hihikj30ioJAUj}:2;-7.95;-0.23;756849891;}zqg2ZFASIL2N4O3PQPRTS2T7S3R4STST14SU^fgsr:2;-8.11;-3.97;2797807175;`YUGAH]kZ`2ONM4L2KJK2H2GHIH10I11H7IJRev}aA:2;-10.25;3.34;48988008;JLP^X_iqvuprt3sr4s2rsrsu13t18u}YABJ]:2;-210.32;192.99;3109801715;bhpcbcac]X2a2`_7`_2`2a30`aUAWl}o:2;-110.71;232.15;2476552013;SMBRADADQRO2SU5TUVR3SUTS11TS17T]}{beUF:2;-29.64;70.58;2886463249;Scix.k}A_KAJ2PRQP2Q3PKT2RS2RT4S2R3Q6RS2RSRS2R4SV_FXgfi")
                .append("-1,2,-94,-115,")
                .append("0,0,8671479250,17701151970,26372631220,15386,0,0,64,64,18000,116000,1,699084045103471958,1568251393817,0")
                .append("-1,2,-94,-106,")
                .append("-1,0")
                .append("-1,2,-94,-120,")
                .append("-1,2,-94,-112,")
                //o.n
                .append("19,2175,59,1068,288800,2896,22100,220,7846")
                .append("-1,2,-94,-103,");
        //str10
        System.out.println("oriSensorData:" + stringBuilder6);
        String sensor = encryptSensor(stringBuilder6.toString());

        return sensor;
    }

    public static int chrplus(String paramString) {
        if (paramString != null && !paramString.trim().equalsIgnoreCase("")) {
            int b = 0;
            int c = 0;
            try {
                while (b < paramString.length()) {
                    char c2 = paramString.charAt(b);
                    if (c2 < 128)
                        c = c + c2;
                    b++;
                }
                return c;
            } catch (Exception e) {
                return -2;
            }
        }
        return -1;
    }

    public String encryptSensor(String str) {
        String result = null;
        try {
            initEncryptKey();

            long uptimeMillis = SystemClock.uptimeMillis();
            Cipher instance = Cipher.getInstance("AES/CBC/PKCS5Padding");
            instance.init(1, aesKey);
            byte[] doFinal = instance.doFinal(str.getBytes());
            long aesUptime = (SystemClock.uptimeMillis() - uptimeMillis) * 1000;
            byte[] iv = instance.getIV();
            byte[] obj = new byte[(doFinal.length + iv.length)];
            System.arraycopy(iv, 0, obj, 0, iv.length);
            System.arraycopy(doFinal, 0, obj, iv.length, doFinal.length);
            uptimeMillis = SystemClock.uptimeMillis();
            Key secretKeySpec = new SecretKeySpec(hmacKey.getEncoded(), "HmacSHA256");
            Mac instance2 = Mac.getInstance("HmacSHA256");
            instance2.init(secretKeySpec);
            iv = instance2.doFinal(obj);
            doFinal = new byte[(obj.length + iv.length)];
            long hmackUptime = (SystemClock.uptimeMillis() - uptimeMillis) * 1000;
            System.arraycopy(obj, 0, doFinal, 0, obj.length);
            System.arraycopy(iv, 0, doFinal, obj.length, iv.length);
            uptimeMillis = SystemClock.uptimeMillis();
            String encryptedData = Base64.encodeToString(doFinal, 2);
            long b64uptime = 1000 * (SystemClock.uptimeMillis() - uptimeMillis);

            StringBuilder sb = new StringBuilder();
            sb.append("1,a,");
            sb.append(aesKeyEncrypted);
            sb.append(",");
            sb.append(hamcKeyEncryped);
            sb.append("$");
            sb.append(encryptedData);
            sb.append("$");
            sb.append(aesUptime).append(",").append(hmackUptime).append(",").append(b64uptime);
            result = sb.toString();
        } catch (Exception e) {
        }
        return result;
    }

    private void initEncryptKey() {
        if (aesKey != null) {
            return;
        }
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            aesKey = keyGen.generateKey();

            KeyGenerator hmacKeyGen = KeyGenerator.getInstance("HmacSHA256");
            hmacKey = hmacKeyGen.generateKey();

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.decode("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC4sA7vA7N/t1SRBS8tugM2X4bByl0jaCZLqxPOql+qZ3sP4UFayqJTvXjd7eTjMwg1T70PnmPWyh1hfQr4s12oSVphTKAjPiWmEBvcpnPPMjr5fGgv0w6+KM9DLTxcktThPZAGoVcoyM/cTO/YsAMIxlmTzpXBaxddHRwi8S2NvwIDAQAB", 0));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey rsaKey = factory.generatePublic(keySpec);

            Cipher rsaInstance = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
            rsaInstance.init(1, rsaKey);
            aesKeyEncrypted = Base64.encodeToString(rsaInstance.doFinal(aesKey.getEncoded()), 2);
            hamcKeyEncryped = Base64.encodeToString(rsaInstance.doFinal(hmacKey.getEncoded()), 2);
        } catch (Exception e) {
        }
    }

    public static class BusinessException extends Exception {

    }

}
