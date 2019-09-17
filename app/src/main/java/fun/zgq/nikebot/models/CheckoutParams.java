package fun.zgq.nikebot.models;

public class CheckoutParams {
    private String skuId;
    private String paymentToken; //paymentPreview接口的jobId
    private String priceChecksum; //checkoutPreview接口返回
    private Address shippingAddress;

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

    public String getPriceChecksum() {
        return priceChecksum;
    }

    public void setPriceChecksum(String priceChecksum) {
        this.priceChecksum = priceChecksum;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }
}
