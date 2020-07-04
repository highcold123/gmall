package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;//注入自定义线程池

    @Autowired
    private ListFeignClient listFeignClient;

    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        HashMap<String, Object> map = new HashMap<>();
        /*  1，Sku基本信息
            2，Sku图片信息
            3，Sku分类信息
            4，Sku销售属性相关信息
            5，Sku价格信息*/
        /*异步 编排*/
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            // 通过skuId 查询skuInfo
            SkuInfo skuInfo = productFeignClient.getSkuInfoById(skuId);
            // 保存skuInfo
            map.put("skuInfo", skuInfo);
            return skuInfo;
        });
        //选用串行
        CompletableFuture<Void> saleAttrListCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            // 销售属性-销售属性值回显并锁定
            List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());
            // 保存数据
            map.put("spuSaleAttrList", spuSaleAttrList);
        },threadPoolExecutor);
        //保存，不需要返回值，用消费型接口方法
        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            //获取商品分类
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
//保存商品分类数据
            map.put("categoryView", categoryView);

        },threadPoolExecutor);
        //runAsync()不需要返回值
        CompletableFuture<Void> priceCompletableFuture = CompletableFuture.runAsync(() -> {
            //获取商品最新价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            // 保存价格
            map.put("price", skuPrice);
        },threadPoolExecutor);
        CompletableFuture<Void> skuJsonCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            //根据spuId 查询map 集合属性
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            // 保存 json字符串
            String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
            // 保存valuesSkuJson
            map.put("valuesSkuJson", valuesSkuJson);
        },threadPoolExecutor);
        //supplyAsync()有返回值  runAsync()无返回值
        //更新商品incrHotScore
        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);
        CompletableFuture.allOf(skuInfoCompletableFuture, saleAttrListCompletableFuture, categoryViewCompletableFuture, priceCompletableFuture, skuJsonCompletableFuture,incrHotScoreCompletableFuture).join();
     /*   // 通过skuId 查询skuInfo
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        // 销售属性-销售属性值回显并锁定
        List<SpuSaleAttr> spuSaleAttrList =  productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());

        //根据spuId 查询map 集合属性
        Map skuValueIdsMap =  productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());

        //获取商品最新价格
        BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
        //获取商品分类
        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
//保存商品分类数据
        map.put("categoryView",categoryView);

        // 保存 json字符串
        String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
        // 获取价格
        map.put("price",skuPrice);
        // 保存valuesSkuJson
        map.put("valuesSkuJson",valuesSkuJson);
          // 保存skuInfo
            map.put("skuInfo",skuInfo);
        // 保存数据
        map.put("spuSaleAttrList",spuSaleAttrList);*/

        return map;
    }

}
