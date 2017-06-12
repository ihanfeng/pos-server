package com.dianba.pos.payment.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.dianba.pos.base.BasicResult;
import com.dianba.pos.base.exception.PosAccessDeniedException;
import com.dianba.pos.base.exception.PosRuntimeException;
import com.dianba.pos.order.service.LifeOrderManager;
import com.dianba.pos.order.vo.LifeOrderVo;
import com.dianba.pos.passport.po.Passport;
import com.dianba.pos.passport.service.PassportManager;
import com.dianba.pos.payment.service.CreditLoanManager;
import com.dianba.pos.payment.service.PaymentManager;
import com.dianba.pos.payment.service.TransLoggerManager;
import com.dianba.pos.payment.support.PaymentRemoteService;
import com.dianba.pos.payment.vo.CreditLoanQuotaVo;
import com.xlibao.common.constant.payment.PaymentTypeEnum;
import com.xlibao.common.constant.payment.TransTypeEnum;
import com.xlibao.metadata.order.OrderEntry;
import com.xlibao.metadata.order.OrderItemSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class DefaultCreditLoanManager extends PaymentRemoteService implements CreditLoanManager {

    @Autowired
    private PassportManager passportManager;
    @Autowired
    private LifeOrderManager orderManager;
    @Autowired
    private PaymentManager paymentManager;
    @Autowired
    private TransLoggerManager transLoggerManager;

    @Override
    public BasicResult isHaveCreditLoanQuota(Long passportId) throws Exception {
        CreditLoanQuotaVo creditLoanQuotaVo = getQuota(passportId);
        if (creditLoanQuotaVo != null) {
            if (creditLoanQuotaVo.getSurplusQuota().compareTo(BigDecimal.ZERO) <= 0) {
                return BasicResult.createFailResult("商家信用卡可用额度不足！");
            }
            BasicResult basicResult = BasicResult.createSuccessResult();
            basicResult.setResponse(JSONObject.parseObject(JSONObject.toJSON(creditLoanQuotaVo).toString()));
            return basicResult;
        } else {
            return BasicResult.createFailResult("商家信用卡信息不存在！");
        }
    }

    @Override
    public CreditLoanQuotaVo getQuota(Long passportId) throws Exception {
        Passport passport = passportManager.getPassportInfoByCashierId(passportId);
        JSONObject result = postCreditLoan(GET_QUOTA, passport.getId(), new HashMap<>());
        JSONObject jsonObject = result.getJSONObject("response");
        if (jsonObject == null || jsonObject.size() == 0) {
            return null;
        }
        CreditLoanQuotaVo creditLoanQuotaVo = new CreditLoanQuotaVo();
        creditLoanQuotaVo.setNowQuota(jsonObject.getBigDecimal("now_quota"));
        creditLoanQuotaVo.setSurplusQuota(jsonObject.getBigDecimal("surplus_quota"));
        creditLoanQuotaVo.setAccountPeriodDays(jsonObject.getInteger("account_period_days"));
        creditLoanQuotaVo.setCardName(jsonObject.getString("card_name"));
        creditLoanQuotaVo.setBusType(jsonObject.getString("bus_type"));
        return creditLoanQuotaVo;
    }

    @Override
    public BasicResult submitOrder(Long passportId, Long orderId, String paymentPassword) throws Exception {
        BasicResult basicResult = paymentManager.checkPayPasswordKey(passportId, paymentPassword);
        if (!basicResult.isSuccess()) {
            return basicResult;
        }
        CreditLoanQuotaVo creditLoanQuota = getQuota(passportId);
        if (creditLoanQuota == null) {
            throw new PosAccessDeniedException("商家信用卡信息不存在！");
        }
        Passport passport = passportManager.getPassportInfoByCashierId(passportId);
        OrderEntry orderEntry = orderManager.getOrder(orderId);
        Map<String, String> params = new HashMap<>();
        params.put("order_type", creditLoanQuota.getBusType());
        params.put("business_ordernum", orderEntry.getSequenceNumber());
        BigDecimal totalAmount = BigDecimal.valueOf(orderEntry.getTotalPrice());
        totalAmount = totalAmount.divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        params.put("amount", totalAmount + "");
        params.put("commodity_title", "1号生活715超市--" + passport.getShowName());
        String itemDeatils = "";
        for (OrderItemSnapshot itemSnapshot : orderEntry.getItemSnapshots()) {
            itemDeatils = itemDeatils + itemSnapshot.getItemName() + "*" + itemSnapshot.getNormalQuantity() + "，";
        }
        if (itemDeatils.equals("")) {
            itemDeatils = passport.getShowName();
        } else {
            itemDeatils = itemDeatils.substring(0, itemDeatils.length() - 1);
        }
        params.put("commodity_detail", itemDeatils);
        JSONObject result = postCreditLoan(SUBMIT_ORDER, passport.getId(), params);
        JSONObject jsonObject = result.getJSONObject("response");
        if (jsonObject == null || jsonObject.size() == 0) {
            String msg = result.getString("msg");
            throw new PosRuntimeException(msg);
        }
        String orderNum = jsonObject.getString("platform_ordernum");
        String surplusQuota = jsonObject.getString("surplus_quota");
        PaymentTypeEnum paymentTypeEnum = PaymentTypeEnum.UNKNOWN;
        //保存支付信息
        transLoggerManager.saveTransLog(orderEntry.getSequenceNumber()
                , passportId, "", paymentTypeEnum, TransTypeEnum.SUPPLYCHAIN_INCOME
                , orderEntry.getTotalPrice());
        //通知订单系统，订单已经支付
        basicResult = orderManager.paymentOrder(orderEntry.getId(), paymentTypeEnum);
        if (basicResult.isSuccess()) {
            //返回订单详情-加商品列表
            LifeOrderVo lifeOrderVo = orderManager.getLifeOrder(orderEntry.getId());
            basicResult = BasicResult.createSuccessResult();
            basicResult.setResponse(JSONObject.parseObject(JSONObject.toJSON(lifeOrderVo).toString()));
        }
        return basicResult;
    }
}