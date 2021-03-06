package com.dianba.pos.item.mapper;

import com.dianba.pos.item.po.PromotionMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromotionMenuMapper {

    List<PromotionMenu> findByMenuIdAndMerchantId(@Param(value = "menu_id") Long menuId
            , @Param(value = "merchant_id") Long merchantId);
}
