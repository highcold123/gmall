package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product/baseTrademark")
public class BaseTrademarkController {

    @Autowired
    private BaseTrademarkService baseTrademarkService;

    @ApiOperation(value = "分页列表")
    @GetMapping("{page}/{limit}")
    public Result index(
            @ApiParam(name = "page", value = "当前页码", required = true)
            @PathVariable Long page,

            @ApiParam(name = "limit", value = "每页记录数", required = true)
            @PathVariable Long limit) {

        Page<BaseTrademark> pageParam = new Page<>(page, limit);
        IPage<BaseTrademark> pageModel = baseTrademarkService.selectPage(pageParam);
        return Result.ok(pageModel);
    }
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){
        //调用服务层
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }
    @PutMapping("update")
    public Result updateById(@RequestBody BaseTrademark baseTrademark) {
        baseTrademarkService.updateById(baseTrademark);
        return Result.ok();
    }
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        baseTrademarkService.removeById(id);
        return Result.ok();
    }
    @GetMapping("get/{id}")
    public Result get(@PathVariable String id) {
        BaseTrademark baseTrademark = baseTrademarkService.getById(id);
        return Result.ok(baseTrademark);
    }
    @GetMapping("getTrademarkList")
    public Result getTrademarkList(){
//        List<BaseTrademark> baseTrademarkList = baseTrademarkService.getTrademarkList();
        List<BaseTrademark> baseTrademarkList = baseTrademarkService.list(null);
        return Result.ok(baseTrademarkList);
    }
}
