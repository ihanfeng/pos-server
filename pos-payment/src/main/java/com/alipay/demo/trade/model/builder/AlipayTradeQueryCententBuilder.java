package com.alipay.demo.trade.model.builder;

import com.alibaba.fastjson.annotation.JSONField;
import org.apache.commons.lang.StringUtils;

public class AlipayTradeQueryCententBuilder {

    @JSONField(name = "trade_no")
    private String tradeNo;
    @JSONField(name = "out_trade_no")
    private String outTradeNo;

    public boolean validate() {
        if ((StringUtils.isEmpty(this.tradeNo))
                && (StringUtils.isEmpty(this.outTradeNo))) {
            throw new IllegalStateException("tradeNo and outTradeNo can not both be NULL!");
        }
        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("AlipayTradeQueryCententBuilder{");
        sb.append("tradeNo='").append(this.tradeNo).append('\'');
        sb.append(", outTradeNo='").append(this.outTradeNo).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getTradeNo() {
        return this.tradeNo;
    }

    public AlipayTradeQueryCententBuilder setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
        return this;
    }

    public String getOutTradeNo() {
        return this.outTradeNo;
    }

    public AlipayTradeQueryCententBuilder setOutTradeNo(String outTradeNo) {
        this.outTradeNo = outTradeNo;
        return this;
    }
}
