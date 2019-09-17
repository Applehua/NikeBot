package fun.zgq.nikebot;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.alibaba.fastjson.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import fun.zgq.nikebot.models.ApiResult;
import fun.zgq.nikebot.models.CheckoutParams;
import fun.zgq.nikebot.models.CheckoutPreviewParams;
import fun.zgq.nikebot.models.PaymentParams;
import fun.zgq.nikebot.models.PaymentPreviewParams;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("fun.zgq.nikebot", appContext.getPackageName());
        Resources resources = appContext.getResources();
        if (resources != null) {
            Configuration configuration = resources.getConfiguration();
            if (configuration != null && configuration.locale != null) {
                String cty = configuration.locale.getISO3Country();
                System.out.println(cty);
            }
        }
    }

    @Test
    public void testMutiAccount() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        String[] accounts = new String[]{"xxx,xxx", "xxxx,xxxx"};
        final CountDownLatch mDoneSignal = new CountDownLatch(accounts.length);
        for (String account : accounts) {
            final String username = account.split(",")[0];
            final String password = account.split(",")[1];
            new Thread(new Runnable() {
                @Override
                public void run() {

                    String skuId = "db211c3c-ff72-532a-a3c8-b206c11055e5";
                    String productId = "4a121c7e-0ca2-5328-8fbc-d6104975b81c";
                    NikeBot nb = new NikeBot(appContext, username, password);
                    ApiResult apiResult = null;
                    try {
                        apiResult = autoOrder(nb, skuId, productId);
                        System.out.println("autoorder##" + username + ",下单成功");
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("autoorder##" + username + ",下单失败");
                    }
                    mDoneSignal.countDown();
                }
            }).start();
            ;
        }
        mDoneSignal.await();
    }

    @Test
    public void testAutoOrder() throws Exception {
        //自动下单完整流程
        String skuId = "db211c3c-ff72-532a-a3c8-b206c11055e5";
        String productId = "4a121c7e-0ca2-5328-8fbc-d6104975b81c";
        final Context appContext = InstrumentationRegistry.getTargetContext();
        NikeBot nb = new NikeBot(appContext, "your username", "password");
        autoOrder(nb, skuId, productId);
    }

    public ApiResult autoOrder(NikeBot nb, String skuId, String productId) throws IOException {
        //自动下单完整流程


//        JSONObject userinfo = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("loginresult.json"))));
//        nb.setUserInfo(userinfo);

        //checkoutPreview
        CheckoutPreviewParams param = new CheckoutPreviewParams();
        param.setSkuId(skuId);
        ApiResult checkoutPreviewResult = nb.checkoutPreview(param);
        System.out.println("checkoutPreview result:" + checkoutPreviewResult.getData());

        //paymentPreview
        PaymentPreviewParams paymentPreviewParam = new PaymentPreviewParams();
        paymentPreviewParam.setPayType(PaymentPreviewParams.PAYTYPE_ALIPAY);
        paymentPreviewParam.setCheckoutId(checkoutPreviewResult.getData().getJSONObject("response").getString("id"));
        paymentPreviewParam.setProductId(productId);
        paymentPreviewParam.setTotal(checkoutPreviewResult.getData().getJSONObject("response").getJSONObject("totals").getString("total"));
        ApiResult paymentPreviewResult = nb.paymnetPreview(paymentPreviewParam);
        System.out.println("paymentPreview result:" + paymentPreviewResult.getData());

        //checkout
        CheckoutParams checkoutParam = new CheckoutParams();
        checkoutParam.setSkuId(skuId);
        checkoutParam.setPaymentToken(paymentPreviewResult.getData().getJSONObject("response").getString("id"));
        checkoutParam.setPriceChecksum(checkoutPreviewResult.getData().getJSONObject("response").getString("priceChecksum"));
        ApiResult checkoutResult = nb.checkout(checkoutParam);
        System.out.println("checkout result:" + checkoutResult.getData());

        //payment
        PaymentParams paymentParam = new PaymentParams();
        paymentParam.setApprovalId(checkoutResult.getData().getJSONObject("response").getString("paymentApprovalId"));
        paymentParam.setOrderNumber(checkoutResult.getData().getJSONObject("response").getString("orderId"));
        ApiResult paymentResult = nb.payment(paymentParam);
        System.out.println("checkout result:" + paymentResult.getData());
        return paymentResult;
    }

    @Test
    public void testLogin() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        NikeBot nb = new NikeBot(appContext, "xxxxx", "xxxxx");
        try {
            JSONObject result = nb.login();
            assert result != null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testcheckoutPreview() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        NikeBot nb = new NikeBot(appContext, "xxxx", "xxxx");
        try {
            JSONObject userinfo = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("loginresult.json"))));
            nb.setUserInfo(userinfo);
            CheckoutPreviewParams param = new CheckoutPreviewParams();
            param.setSkuId("db211c3c-ff72-532a-a3c8-b206c11055e5");
            ApiResult result = nb.checkoutPreview(param);
            System.out.println(result.getData());
            assert result.getStatus() == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testpaymentPreview() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        NikeBot nb = new NikeBot(appContext, "xxxx", "xxxx");
        try {
            JSONObject userinfo = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("loginresult.json"))));
            nb.setUserInfo(userinfo);

            JSONObject checkoutPreviewResult = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("checkoutPreviewresult.json"))));

            PaymentPreviewParams param = new PaymentPreviewParams();
            param.setPayType(PaymentPreviewParams.PAYTYPE_ALIPAY);
            param.setCheckoutId(checkoutPreviewResult.getJSONObject("response").getString("id"));
            param.setProductId("4a121c7e-0ca2-5328-8fbc-d6104975b81c");
            param.setTotal(checkoutPreviewResult.getJSONObject("response").getJSONObject("totals").getString("total"));
            ApiResult result = nb.paymnetPreview(param);
            System.out.println(result.getData());
            assert result.getStatus() == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCheckout() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        NikeBot nb = new NikeBot(appContext, "xxxx", "xxxx");
        try {
            JSONObject userinfo = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("loginresult.json"))));
            nb.setUserInfo(userinfo);

            JSONObject checkoutPreviewResult = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("checkoutPreviewresult.json"))));
            JSONObject paymentPreviewResult = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("paymentPreviewresult.json"))));

            CheckoutParams param = new CheckoutParams();
            param.setSkuId("db211c3c-ff72-532a-a3c8-b206c11055e5");
            param.setPaymentToken(paymentPreviewResult.getJSONObject("response").getString("id"));
            param.setPriceChecksum(checkoutPreviewResult.getJSONObject("response").getString("priceChecksum"));
            ApiResult result = nb.checkout(param);
            System.out.println(result.getData());
            assert result.getStatus() == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPayment() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        NikeBot nb = new NikeBot(appContext, "xxxx", "xxxx");
        try {
            JSONObject userinfo = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("loginresult.json"))));
            nb.setUserInfo(userinfo);

            JSONObject checkoutPreviewResult = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("checkoutPreviewresult.json"))));
            JSONObject paymentPreviewResult = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("paymentPreviewresult.json"))));
            JSONObject checkoutResult = JSONObject.parseObject(new String(Utils.consumeInputStream(appContext.getAssets().open("checkoutresult.json"))));

            PaymentParams param = new PaymentParams();
            param.setApprovalId(checkoutResult.getJSONObject("response").getString("paymentApprovalId"));
            param.setOrderNumber(checkoutResult.getJSONObject("response").getString("orderId"));
            ApiResult result = nb.payment(param);
            System.out.println(result.getData());
            assert result.getStatus() == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDeviceInfo() {
        DeviceInfo df = new DeviceInfo("xxxx");
        System.out.println(df.getClientInfo());
    }
}
