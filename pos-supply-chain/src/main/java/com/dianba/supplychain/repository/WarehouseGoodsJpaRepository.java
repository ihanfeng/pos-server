package com.dianba.supplychain.repository;

import com.dianba.supplychain.po.WarehouseGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarehouseGoodsJpaRepository extends JpaRepository<WarehouseGoods, Integer> {

    List<WarehouseGoods> findByWarehouseIdAndGoodsId(Integer warehouseId, Integer goodsId);
}
