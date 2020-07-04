package com.atguigu.gmall.product.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.model.product.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ManageService {
    //查询一级分类数据
    List<BaseCategory1> getCategory1();

    //根据一级分类id，查询二级分类数据
    List<BaseCategory2> getCategory2(Long categroy1Id);

    //根据二级分类id，查询三级分类数据
    List<BaseCategory3> getCategory3(Long categroy2Id);

    //根据分类id去查询平台属性数据
    List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id);

    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    BaseAttrInfo getAttrInfo(Long attrId);

    //分页查询，多个spuinfo必须指定，查询几页，每页显示条数，是否有抽出条件{category3Id=?}
    IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo);

    List<BaseSaleAttr> getBaseSaleAttrList();

    void saveSpuInfo(SpuInfo spuInfo);

    List<SpuImage> getSpuImageList(Long spuId);

    List<SpuSaleAttr> getSpuSaleAttrList(Long spuId);

    void saveSkuInfo(SkuInfo skuInfo);

    IPage<SkuInfo> selectPage(Page<SkuInfo> skuInfoPage);

    void onSale(Long skuId);

    void cancelSale(Long skuId);

    //api接口供远程调用，查询sku信息
    SkuInfo getSkuInfo(Long skuId);
    //api接口，查询分类串
    BaseCategoryView getCategoryViewByCategory3Id(Long category3Id);
    //根据skuId查询一下价格
    BigDecimal getSkuPriceBySkuId(Long skuId);
    //查询销售属性集合数据以及选中的sku
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId);
    //查询匹配好的sku，用于checked
    Map getSkuValueIdsMap(Long spuId);

    List<JSONObject> getBaseCategoryList();

    BaseTrademark getBaseTrademark(Long tmId);

    //根据skuid获取到平台属性，以及属性值
    List<BaseAttrInfo> getAttrList(Long skuId);
}
