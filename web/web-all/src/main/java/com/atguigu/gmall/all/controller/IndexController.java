package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class IndexController {

   @Autowired
   private ProductFeignClient productFeignClient;
   // 访问 / 或者 index.html 时都可以显示首页信息
   @GetMapping({"/","index.html"})
   public String index(HttpServletRequest request){
      Result result = productFeignClient.getBaseCategoryList();
      // 保存后台获取到的数据
      request.setAttribute("list",result.getData());
      return "index/index";
   }
}
