package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品SKU接口")
@RestController
@RequestMapping("admin/product")
public class SkuManageController {

    @Autowired
    private ManageService manageService;

    @GetMapping("spuImageList/{spuId}")
    public Result<List<SpuImage>> getSpuImageList(@PathVariable("spuId") Long spuId) {
        List<SpuImage> spuImageList = manageService.getSpuImageList(spuId);
        return Result.ok(spuImageList);
    }
    //回显销售属性，查询销售属性的集合
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result<List<SpuSaleAttr>> getSpuSaleAttrList(@PathVariable("spuId") Long spuId) {
        List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrList(spuId);
        return Result.ok(spuSaleAttrList);
    }

    //保存sku
    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo){
        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

    @GetMapping("list/{page}/{limit}")
    public Result getList(@PathVariable Long page,
                          @PathVariable Long limit
                          )
    {
        //需要将page，limit传给Page对象
        Page<SkuInfo> skuInfoPage = new Page<>(page,limit);

       IPage<SkuInfo> skuInfoIPage = manageService.selectPage(skuInfoPage);
        return Result.ok(skuInfoIPage);
    }
    //商品的上架
    @GetMapping("onSale/{skuId}")
    public Result onSale(@PathVariable Long skuId){
        manageService.onSale(skuId);
        return Result.ok();

    }

    //商品的下架
    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale(@PathVariable Long skuId){
        manageService.cancelSale(skuId);
        return Result.ok();

    }
}
