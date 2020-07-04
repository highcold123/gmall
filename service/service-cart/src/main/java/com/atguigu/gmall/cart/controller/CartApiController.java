package com.atguigu.gmall.cart.controller;


import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author mqx
 * @date 2020/6/23 15:40
 */
@RestController
@RequestMapping("api/cart")
public class CartApiController {
    @Autowired
    private CartService cartService;

    // http://cart.gmall.com/addCart.html?skuId=30&skuNum=1 页面提交过来的数据，应该属于web-all项目的！
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request){

        // 需要一个用户Id ，是从网关传递过来的！ 用户信息都放入了header 中！common-util 中有工具类AuthContextHolder
        // 获取登录的用户Id，添加购物车的时候，一定会有登录的用户Id么？不一定！
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)){
            // 属于未登录时，添加购物车，会产生一个临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用添加购物车方法
        cartService.addToCart(skuId,userId,skuNum);
        // 返回
        return Result.ok();

    }

    // 以下控制器方法 不需要通过 web-all 来访问，
    // 获取购物车列表
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request){
        // 获取登录的用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 获取临时用户Id
        String userTempId = AuthContextHolder.getUserTempId(request);

        List<CartInfo> cartList = cartService.getCartList(userId, userTempId);

        // 返回数据
        return Result.ok(cartList);

    }

    // 更改选中状态控制器
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable Long skuId,
                            @PathVariable Integer isChecked,
                            HttpServletRequest request){

        // 选中状态的变更 在登录，未登录的情况下都可以！
        // 先获取登录的用户Id
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)){
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用方法
        cartService.checkCart(userId,isChecked,skuId);

        return Result.ok();
    }

    // 删除购物车方法
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable Long skuId,
                             HttpServletRequest request){
        // 先获取登录的用户Id
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)){
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用方法
        cartService.deleteCart(skuId,userId);
        return Result.ok();
    }

    // 根据用户Id 查询送货清单数据
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable String userId){
        return cartService.getCartCheckedList(userId);
    }

    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable String userId){
        cartService.loadCartCache(userId);
        return Result.ok();
    }
}
