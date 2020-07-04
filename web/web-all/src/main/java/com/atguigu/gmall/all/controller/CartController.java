package com.atguigu.gmall.all.controller;


import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 购物车页面
 * </p>
 *
 */
@Controller
public class CartController {

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    // http://cart.gmall.com/addCart.html?skuId=31&skuNum=1
    @RequestMapping("addCart.html")
    public String addCart(@RequestParam(name = "skuId") Long skuId ,
                          @RequestParam(name = "skuNum") Integer skuNum,
                          HttpServletRequest request){
//        String skuId1 = request.getParameter("skuId");
//        String skuNum1 = request.getParameter("skuNum");
        // 添加购物车
        cartFeignClient.addToCart(skuId,skuNum);

        // 通过skuId 查询skuInfo
        SkuInfo skuInfo = productFeignClient.getSkuInfoById(skuId);
        // 存储前台页面所需要的数据
        request.setAttribute("skuNum",skuNum);
        request.setAttribute("skuInfo",skuInfo);
        return "cart/addCart";
    }

    // 查询购物车列表控制器
    @RequestMapping("cart.html")
    public String cartList(){
        // 购物车列表页面
        return "cart/index";
    }
}
