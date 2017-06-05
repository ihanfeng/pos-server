package com.dianba.pos.payment.controller;

import com.dianba.pos.base.BasicResult;
import com.dianba.pos.payment.config.PaymentURLConstant;
import com.dianba.pos.payment.service.PaymentManager;
import com.xlibao.common.BasicWebService;
import com.xlibao.common.constant.payment.PaymentTypeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(PaymentURLConstant.PAYMENT_ORDER)
public class PaymentController extends BasicWebService {

    private static Logger logger = LogManager.getLogger(PaymentController.class);

    @Autowired
    private PaymentManager paymentManager;

    /**
     * 支付订单
     *
     * @param passportId     通行证ID
     * @param orderId        订单ID
     * @param authCode       用户条形码
     * @param paymentTypeKey 支付类型Key
     * @return
     */
    @ResponseBody
    @RequestMapping("pay_order")
    public BasicResult payOrder(long passportId, long orderId, String paymentTypeKey
            , @RequestParam(required = false) String authCode
            , @RequestParam(required = false) String paymentPassword) throws Exception {
        if (PaymentTypeEnum.BALANCE.getKey().equals(paymentTypeKey)) {
            return paymentManager.balancePayment(passportId, orderId, paymentPassword);
        } else {
            return paymentManager.payOrder(passportId, orderId, paymentTypeKey, authCode);
        }
    }

    /**
     * 获取用户余额
     */
    @ResponseBody
    @RequestMapping("passport_currency")
    public BasicResult passportCurrency(long passportId) throws Exception {
        return paymentManager.passportCurrency(passportId);
    }
}