package com.dianba.pos.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.demo.trade.config.Configs;
import com.alipay.demo.trade.model.builder.AlipayTradePayContentBuilder;
import com.alipay.demo.trade.model.result.AlipayF2FPayResult;
import com.alipay.demo.trade.service.AlipayTradeService;
import com.alipay.demo.trade.service.impl.AlipayTradeServiceImpl;
import com.dianba.pos.base.exception.PosAccessDeniedException;
import com.dianba.pos.order.service.LifeOrderManager;
import com.dianba.pos.passport.po.Passport;
import com.dianba.pos.passport.service.PassportManager;
import com.dianba.pos.payment.pojo.BarcodePayResponse;
import com.dianba.pos.payment.service.AliPayManager;
import com.xlibao.metadata.order.OrderEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;

@Service
public class DefaultAliPayManager implements AliPayManager {

    private static Logger logger = LogManager.getLogger(DefaultAliPayManager.class);

    @Autowired
    private LifeOrderManager orderManager;
    @Autowired
    private PassportManager passportManager;

    private static final String ALIPAY_PROPERTIES = "properties" + File.separator + "alipay.properties";

    static {
        Configs.init(ALIPAY_PROPERTIES);
    }

    @Override
    public BarcodePayResponse barcodePayment(Long passportId, Long orderId, String authCode) throws Exception {
        BarcodePayResponse response = null;
        OrderEntry order = orderManager.getOrder(orderId);
        if (order == null) {
            response = BarcodePayResponse.FAILURE;
            response.setMsg("参数错误");
            logger.warn("无法根据订单id:{}获取订单", orderId);
            return response;
        }
        if ("1".equals(order.getPaymentType())) {
            response = BarcodePayResponse.FAILURE;
            response.setCode(1001);
            response.setMsg("订单已付款");
            logger.info("订单id:{}已付款,不需重复付款", orderId);
            return response;
        }
        Passport merchantPassport = passportManager.getPassportInfoByCashierId(passportId);
        AlipayTradeService tradeService = new AlipayTradeServiceImpl.ClientBuilder().build();
        String outTradeNo = order.getSequenceNumber();
        // (必填) 订单总金额，单位为元，不能超过1亿元
        // 如果同时传入了【打折金额】,【不可打折金额】,【订单总金额】三者,则必须满足如下条件:【订单总金额】=【打折金额】+【不可打折金额】
        BigDecimal totalAmount = BigDecimal.valueOf(order.getTotalPrice());
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BarcodePayResponse.SUCCESS;
        }
        totalAmount = totalAmount.divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        if (totalAmount.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new PosAccessDeniedException("交易超限！支付宝单笔交易限额1000！");
        }
        String undiscountableAmount = "0.0";
        // 卖家支付宝账号ID，用于支持一个签约账号下支持打款到不同的收款账号，(打款到sellerId对应的支付宝账号)
        // 如果该字段为空，则默认为与支付宝签约的商户的PID，也就是appid对应的PID
        String sellerId = "";
        // 订单描述，可以对交易或商品进行一个详细地描述，比如填写"购买商品2件共15.00元"
        String subject = "1号生活--" + merchantPassport.getShowName();
        String body = "本次消费共" + totalAmount + "元";
        // 商户操作员编号，添加此参数可以为商户操作员做销售统计
        String operatorId = String.valueOf(merchantPassport.getId());
        // (必填) 商户门店编号，通过门店号和商家后台可以配置精准到门店的折扣信息，详询支付宝技术支持
        String storeId = String.valueOf(merchantPassport.getId());
        String timeExpress = "5m";
        // 创建请求builder，设置请求参数
        AlipayTradePayContentBuilder builder = new AlipayTradePayContentBuilder()
                .setOutTradeNo(outTradeNo)
                .setSubject(subject)
                .setAuthCode(authCode)
                .setTotalAmount(totalAmount + "")
                .setStoreId(storeId)
                .setUndiscountableAmount(undiscountableAmount)
                .setBody(body)
                .setOperatorId(operatorId)
                .setSellerId(sellerId)
                .setTimeExpress(timeExpress);
        // 调用tradePay方法获取当面付应答
        AlipayF2FPayResult result = tradeService.tradePay(builder);
        logger.info("超市订单{}扫条形码支付宝付款,付款结果:{}", orderId, JSON.toJSONString(result.getResponse()));
        switch (result.getTradeStatus()) {
            case SUCCESS:
                logger.info("支付宝付款成功, 订单号:{}, out_trade_no:{} )", orderId, outTradeNo);
                response = BarcodePayResponse.SUCCESS;
                break;
            case FAILED:
                logger.error("支付宝付款失败!!!, 订单号:{}, out_trade_no:{} )", orderId, outTradeNo);
                response = BarcodePayResponse.FAILURE;
                response.setMsg(result.getResponse().getBody());
                break;
            case UNKNOWN:
                logger.error("支付宝付款系统异常, 订单状态未知!!!, 订单号:{}, out_trade_no:{} )", orderId, outTradeNo);
                response = BarcodePayResponse.FAILURE;
                response.setMsg("系统异常");
                break;
            default:
                response = BarcodePayResponse.FAILURE;
                response.setMsg("不支持的交易状态");
                logger.error("不支持的交易状态，交易返回异常!!!");
                break;
        }
        return response;
    }
}
