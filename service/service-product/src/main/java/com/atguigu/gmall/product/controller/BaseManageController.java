package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api("后台接口测试")
@RestController
@RequestMapping("admin/product")
public class BaseManageController {

    @Autowired
    private ManageService manageService;

    @GetMapping("getCategory1")
    public Result getCategory1() {
        //最基本返回数据的方式
        List<BaseCategory1> category1 = manageService.getCategory1();
        return Result.ok(category1);
    }

    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable Long category1Id) {
        List<BaseCategory2> category2 = manageService.getCategory2(category1Id);
        return Result.ok(category2);
    }

    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id) {
        List<BaseCategory3> category3 = manageService.getCategory3(category2Id);
        return Result.ok(category3);
    }

    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result<List<BaseAttrInfo>> attrInfoList(@PathVariable Long category1Id,
                               @PathVariable Long category2Id,
                               @PathVariable Long category3Id) {

        List<BaseAttrInfo> attrInfoList = manageService.getAttrInfoList(category1Id, category2Id, category3Id);

        return Result.ok(attrInfoList);
    }

    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo) {
        manageService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }

    /**
     * 根据属性id查询属性值集合
     * @param attrId
     * @return
     */
    @GetMapping("getAttrValueList/{attrId}")
    public Result<List<BaseAttrValue>> getAttrValueList(@PathVariable Long attrId){
//       List<BaseAttrValue> baseAttrValueList = manageService.getAttrValueList(attrId);
       BaseAttrInfo baseAttrInfo = manageService.getAttrInfo(attrId);
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        return Result.ok(attrValueList);
    }
    //根据条件去查询spuinfo 数据列标配
    @GetMapping("{page}/{limit}")
    public Result getPageList(@PathVariable("page") Long page,
                              @PathVariable("limit") Long limit,
                              SpuInfo spuInfo
                              ){
        Page<SpuInfo> spuInfoPage = new Page<>(page,limit);

        IPage<SpuInfo> spuInfoList = manageService.selectPage(spuInfoPage, spuInfo);
        return Result.ok(spuInfoList);

    }
}
