package fun.zgq.nikebot.models;

import com.alibaba.fastjson.JSONObject;

public class ApiResult {
    private int status;//0失败 ，1成功
    private String errMsg;
    private JSONObject data;//原始数据

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public static ApiResult parseResult(JSONObject data) {
        ApiResult apiResult = new ApiResult();
        if (data.getJSONObject("error") != null) {
            apiResult.setStatus(0);
            apiResult.setErrMsg(data.getJSONObject("error").get("errors").toString());
        } else {
            apiResult.setStatus(1);
        }
        apiResult.setData(data);
        return apiResult;
    }
}
