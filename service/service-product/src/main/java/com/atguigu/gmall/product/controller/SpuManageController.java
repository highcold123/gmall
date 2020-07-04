package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseSaleAttr;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/product")
public class SpuManageController {

    // 引入服务层
    @Autowired
    private ManageService manageService;

    // 销售属性http://api.gmall.com/admin/product/baseSaleAttrList
    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){
        // 查询所有的销售属性集合
        List<BaseSaleAttr> baseSaleAttrList = manageService.getBaseSaleAttrList();

        return Result.ok(baseSaleAttrList);
    }
    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo) {
        if (spuInfo != null) {
            manageService.saveSpuInfo(spuInfo);
        }
        return Result.ok();
    }
}