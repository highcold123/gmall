package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SpuSaleAttrMapper extends BaseMapper<SpuSaleAttr> {
    //根据spuid查询销售属性列表
    List<SpuSaleAttr> selectSpuSaleAttrList(Long spuId);

    List<SpuSaleAttr> selectSpuSaleAttrListCheckBySku(@Param("skuId") Long skuId, @Param("spuId") Long spuId);
}
