package fun.zgq.nikebot.models;

public class PaymentPreviewParams {

    public static final String PAYTYPE_ALIPAY = "Alipay";
    public static final String PAYTYPE_WECHAT = "WeChat";

    private String checkoutId;
    private String productId;
    private String total;
    private String payType; //Alipay,WeChat
    private Address shippingAddress;

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getCheckoutId() {
        return checkoutId;
    }

    public void setCheckoutId(String checkoutId) {
        this.checkoutId = checkoutId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getTotal() {
        return total;
    }

    public void setTotal(String total) {
        this.total = total;
    }
}
