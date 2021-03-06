package com.dianba.pos.extended.service.impl;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dianba.pos.base.BasicResult;
import com.dianba.pos.common.util.DateUtil;
import com.dianba.pos.common.util.StringUtil;
import com.dianba.pos.extended.config.ExtendedConfig;
import com.dianba.pos.extended.mapper.Charge19eMapper;
import com.dianba.pos.extended.po.PosCharge19eOrder;
import com.dianba.pos.extended.po.PosPhoneInfo;
import com.dianba.pos.extended.repository.Charge19eJpaRepository;
import com.dianba.pos.extended.service.Charge19eManager;
import com.dianba.pos.extended.service.PosPhoneInfoManager;
import com.dianba.pos.extended.support.ExtendedRemoteService;
import com.dianba.pos.extended.vo.*;
import com.dianba.pos.item.mapper.PosItemMapper;
import com.dianba.pos.item.po.PosItem;
import com.dianba.pos.item.repository.PosItemJpaRepository;
import com.dianba.pos.item.vo.PosItemVo;
import com.dianba.pos.order.mapper.LifeOrderMapper;
import com.dianba.pos.extended.vo.Order19EDto;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
@SuppressWarnings("all")
@Service
public class DefaultCharge19eManager extends ExtendedRemoteService implements Charge19eManager {

    private static Logger logger = LogManager.getLogger(DefaultCharge19eManager.class);
    @Autowired
    private Charge19eJpaRepository charge19eJpaRepository;

    @Autowired
    private LifeOrderMapper orderMapper;

    @Autowired
    private Charge19eMapper charge19eMapper;

    @Autowired
    private PosPhoneInfoManager posPhoneInfoManager;

    @Autowired
    private PosItemMapper posItemMapper;

    @Autowired
    private PosItemJpaRepository posItemJpaRepository;

    @Autowired
    private ExtendedConfig extendedConfig;
    @Override
    public boolean hfCharge(Order19EDto or) {

        boolean flag = false;
        Charge19E charge19E = new Charge19E();
        charge19E.setChargeNumber(or.getMobile());
        String money = or.getMenuName().replace("元", "");
        charge19E.setChargeMoney(money);
        charge19E.setMerchantId(extendedConfig.getExtendedHfMerchantId());
        charge19E.setKey(extendedConfig.getExtendedHfKey());
        //默认生成的订单号
        String orderNum = DateUtil.getCurrDate("yyyyMMddHHmmss")
                + RandomStringUtils.random(4, "0123456789") + or.getOrderId();
        charge19E.setMerchantOrderId(orderNum);
        charge19E.setFillType("0");
        charge19E.setChargeType("0");
        charge19E.setSendNotifyUrl(extendedConfig.getExtendedHfNotifyUrl());
       // ChargeResult cr = HfCharge19EApi.hfCharge(HfCharge19EUtil.HF_CHARGE_19E_URL, charge19E);
        ChargeResult cr = hfCharge(charge19E);
        if (cr.getResultCode().equals("SUCCESS")) {
            //保存话费充值订单信息
            saveHfChargeTable(or, cr);
            logger.info("19e话费下单成功并保存订单信息成功！订单号：" + cr.getMerchantOrderId() + ",充值手机：" + or.getMobile()
                    + ",充值金额：" + or.getPrice() + ",第三方订单号：" + cr.getEhfOrderId());
            //修改订单信息状态 2 发货中
            charge19eMapper.editOrderInfoBy19e(2, orderNum);
            flag = true;
        }
        return flag;
    }

    @Override
    public void orderListHfCharge() {
        /**
         * 如果订单为为成功状态,并且未发货状态去发货
         */
        List<Order19EDto> list = charge19eMapper.getOrderListBy19EMenu(1, 0);
        for (Order19EDto od : list) {
            if (!StringUtil.isEmpty(od.getMobile())) {
                //查询此订单的充值次数
                Integer count = charge19eMapper.chargeCountByOrder(od.getOrderId());
                if (count < 3) { //没有充值3次
                    boolean flag = hfCharge(od);
                    if (flag) {
                        logger.info("话费充值下订单成功！" + "orderNum" + od.getOrderNum() + "phone" + od.getMobile()
                                + "money" + od.getPrice());
                    }
                }
            }
        }

    }


    @Override
    public boolean flowCharge(Order19EDto order19EDto) {

        boolean flag = false;
        ChargeFlow cf = new ChargeFlow();
        cf.setMerchantId(extendedConfig.getExtendedFlowMerchantId());
        //默认生成的订单号
        String orderNum = DateUtil.getCurrDate("yyyyMMddHHmmss")
                + RandomStringUtils.random(4, "0123456789") + order19EDto.getOrderId();

        cf.setMerOrderNo(orderNum);
        cf.setProductId(order19EDto.getMenuKey());
        cf.setMobile(order19EDto.getMobile());
        cf.setRemark("流量充值!");
        // ChargeFlowResult chargeFlowResult = FlowCharge19EApi.flowCharge(FlowCharge19EUtil.FLOW_CHARGE_URL, cf);
        ChargeFlowResult chargeFlowResult = flowCharge(cf);
        //保存流量充值订单信息
        saveFlowChargeTable(order19EDto, chargeFlowResult);
        if (chargeFlowResult.getResultCode().equals("00000")) {
            logger.info("19e流量下单成功并保存订单信息成功！订单号：" + chargeFlowResult.getMerOrderNo() + ",充值手机："
                    + chargeFlowResult.getMobile() + ",充值金额：" + order19EDto.getPrice() + ",第三方订单号："
                    + chargeFlowResult.getOrderNo());
            charge19eMapper.editOrderInfoBy19e(2, orderNum);
            flag = true;
        }
        return flag;
    }

    @Override
    public void orderListFlowCharge() {

        logger.info("获取查询订单集合，并进行充值业务!");
        /**
         * 如果订单为为成功状态都去充值
         */
        List<Order19EDto> list = charge19eMapper.getOrderListBy19EMenu(2, 0);
        for (Order19EDto od : list) {
            logger.info("要进行充值的订单号码为：" + od.getOrderNum() + ",商品订单号为" + od.getMenuKey());
            //查询此订单的充值次数
            Integer count = charge19eMapper.chargeCountByOrder(od.getOrderId());
            if (count < 3) { //没有充值3次

                boolean flag = flowCharge(od);
                if (flag) {
                    logger.info("流量充值下订单成功！" + "orderNum" + od.getOrderNum() + "phone" + od.getMobile()
                            + "money" + od.getPrice());
                }

            }

        }
    }

    @Override
    public void saveHfChargeTable(Order19EDto or, ChargeResult cr) {
        PosCharge19eOrder ct = new PosCharge19eOrder();
        ct.setChargeNumber(or.getPrice().toString());
        ct.setChargePhone(or.getMobile());
        ct.setCreateTime(DateUtil.getCurrDate("yyyyMMddHHmmss"));
        ct.setResultCode(cr.getResultCode());
        ct.setResultDesc(cr.getResultDesc());
        ct.setQueryResultUrl(cr.getQueryResultUrl());
        //第三方订单id

        ct.seteOrderId(cr.getEhfOrderId());
        ct.setMerchantOrderId(cr.getMerchantOrderId());

        //关联order表id
        ct.setOrderId(or.getOrderId());
        //此单为话费订单
        ct.setType(1);
        //新增订单信息
        charge19eJpaRepository.save(ct);
    }

    @Override
    public void saveFlowChargeTable(Order19EDto order19EDto, ChargeFlowResult chargeFlowResult) {
        PosCharge19eOrder ct = new PosCharge19eOrder();
        ct.setChargeNumber(order19EDto.getPrice().toString());
        ct.setChargePhone(order19EDto.getMobile());
        ct.setCreateTime(DateUtil.getCurrDate("yyyyMMddHHmmss"));
        ct.setResultCode(chargeFlowResult.getResultCode());
        ct.setResultDesc(chargeFlowResult.getResultDesc());
        ct.setQueryResultUrl("");
        //第三方订单id
        ct.seteOrderId(chargeFlowResult.getOrderNo());
        ct.setMerchantOrderId(chargeFlowResult.getMerOrderNo());
        ct.seteOrderId(chargeFlowResult.getOrderNo());
        //关联order表id
        ct.setOrderId(order19EDto.getOrderId());
        //此单为流量充值订单
        ct.setType(2);
        //新增订单信息
        charge19eJpaRepository.save(ct);
    }

    @Override
    public BasicResult chargeMenu(String type, String phone) {

        if (StringUtil.isEmpty(type) || StringUtil.isEmpty(phone)) {
            return BasicResult.createFailResult("参数输入有误");
        } else {
            Integer isFlash = Integer.parseInt(type);

            PosPhoneInfo phoneInfo = posPhoneInfoManager.findByMobileNumber(Long.parseLong(phone));
            if (phoneInfo == null) {
                return BasicResult.createFailResult("手机号码不存在");
            } else {
                if (type.equals("1")) {
                    // 关联menu表中的print_type ; 1.电信2.联通3.移动;
                    Long id = Long.parseLong(phoneInfo.getPrintType().toString());
                    String mobilePrefix = phone.substring(0, 7);
                    Long phonel = Long.parseLong(mobilePrefix);
                    List<PosItemVo> menulst = posItemMapper.getMenuListByPhoneAndType(phonel);
                    List<PosItemVo> posItemVos = new ArrayList<>();
                    BigDecimal a = new BigDecimal(100);
                    for (PosItemVo posItemVo : menulst) {
                        PosItemVo posItemVo1 = new PosItemVo();
                        posItemVo1 = posItemVo;

                        posItemVo1.setSalesPrice(posItemVo.getSalesPrice().divide(a, 2, BigDecimal.ROUND_HALF_UP));

                        posItemVo1.setStockPrice(posItemVo.getStockPrice().divide(a, 2, BigDecimal.ROUND_HALF_UP));
                    }
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("phoneInfo", phoneInfo);
                    jsonObject.put("menuList", menulst);
                    return BasicResult.createSuccessResult("获取话费充值商品成功", jsonObject);
                } else if (type.equals("2")) {
                    long starTime=System.currentTimeMillis();
                    logger.info("=====================获取流量充值商品开始时间===============:"+starTime);
                    Product pd = new Product();
                    pd.setMobile(phone);
                    pd.setMerchantId(extendedConfig.getExtendedFlowMerchantId());
                  //  String result = FlowCharge19EApi.queryProduct(extendedConfig.getExtendedFlowProductUrl(), pd);
                    String result = queryProduct(extendedConfig.getExtendedFlowProductUrl(), pd);
                    JSONObject jb = JSON.parseObject(result);
                    long endTime=0L;
                    List<PosItemVo> posItemVos = new ArrayList<>();
                    if (jb.get("resultCode").equals("00000") && jb.get("resultDesc").equals("SUCCESS")) {
                        JSONArray ja = jb.getJSONArray("productList");
                        List<ProductListDto> lst = JSONArray.parseArray(ja.toString(), ProductListDto.class);
                        logger.info("流量充值商品列表信息：====" + ja.toString());
                        endTime=System.currentTimeMillis();
                        logger.info("=====================获取流量充值商品列表时间===============:"+(endTime-starTime));
                        for (ProductListDto pl : lst) {
                            //根据第三方商品id获取本地商品信息
                            String productId = pl.getProductId();
                            logger.info("根据第三方商品id获取本地商品:====" + productId);
                            PosItem posItem = posItemJpaRepository
                                    .findAllByMenuKeyAndIsShelveAndIsDelete(productId, "Y", "N");
                            PosItemVo posItemVo = new PosItemVo();
                            if (posItem != null) {
                                posItemVo.setId(posItem.getId());
                                posItemVo.setPosTypeId(posItem.getItemTypeId());
                                posItemVo.setPosTypeName("");
                                posItemVo.setItemTemplateId(posItem.getItemTemplateId());
                                posItemVo.setItemName(posItem.getItemName());
                                BigDecimal sMoney = new BigDecimal(posItem.getStockPrice());
                                BigDecimal saMoney = new BigDecimal(posItem.getSalesPrice());
                                BigDecimal a = new BigDecimal(100);
                                BigDecimal sPrice = sMoney.divide(a, 2, BigDecimal.ROUND_UP);
                                BigDecimal saPrice = saMoney.divide(a, 2, BigDecimal.ROUND_UP);
                                posItemVo.setStockPrice(sPrice);
                                posItemVo.setSalesPrice(saPrice);
                                posItemVo.setItemTypeId(posItem.getItemTypeId());
                                posItemVo.setBuyCount(posItem.getBuyCount());
                                posItemVo.setCreateDate(posItem.getCreateTime());
                                posItemVo.setBarcode(posItem.getBarcode());
                                posItemVo.setIsDelete(posItem.getIsDelete());
                                posItemVo.setIsShelve(posItem.getIsShelve());
                                posItemVo.setItemImg(posItem.getItemImgUrl());
                                posItemVo.setRepertory(posItem.getRepertory());
                                posItemVo.setWarningRepertory(posItem.getWarningRepertory());
                                posItemVo.setShelfLife(posItem.getShelfLife());
                                posItemVo.setPassportId(posItem.getPassportId());
                                posItemVo.setItemUnitId(1L);
                                posItemVo.setItemUnitName("");
                                posItemVo.setGeneratedDate(posItem.getGeneratedDate());
                                posItemVos.add(posItemVo);
                            }
                        }

                    }

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("phoneInfo", phoneInfo);
                    jsonObject.put("menuList", posItemVos);
                    long time=System.currentTimeMillis();
                    logger.info("=====================获取流量充值商品成功时间===============:"+ (time-endTime));
                    return BasicResult.createSuccessResult("获取流量充值商品成功", jsonObject);
                } else {
                    return BasicResult.createSuccessResult("获取流量充值商品成功");
                }
            }
        }


    }

    @Override
    public PosCharge19eOrder findByMerchantOrderId(String merchantOrderId) {
        return charge19eJpaRepository.findByMerchantOrderId(merchantOrderId);
    }


}
