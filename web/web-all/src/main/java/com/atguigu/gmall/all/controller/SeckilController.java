package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class SeckilController {
    @Autowired
    private ActivityFeignClient activityFeignClient;

    /**
     * 秒杀列表
     * @param model
     * @return
     */
    @GetMapping("seckill.html")
    public String index(Model model) {
        Result result = activityFeignClient.findAll();
        model.addAttribute("list", result.getData());
        return "seckill/index";
    }

    /**
     * 秒杀详情
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        // 通过skuId 查询skuInfo
        Result result = activityFeignClient.getSeckillGoods(skuId);
        model.addAttribute("item", result.getData());
        return "seckill/item";
    }
    @GetMapping("seckill/queue.html")
    public String queue(HttpServletRequest request){
        String skuId = request.getParameter("skuId");
        String skuIdStr = request.getParameter("skuIdStr");
        request.setAttribute("skuId",skuId);
        request.setAttribute("skuIdStr",skuIdStr);
        //由页面得知 需要在后台储存两个参数skuId skuIdStr

        return "seckill/queue";
    }

    /**
     * 确认订单
     * @param model
     * @return
     */
    @GetMapping("seckill/trade.html")
    public String trade(Model model) {
        Result<Map<String, Object>> result = activityFeignClient.trade();
        if(result.isOk()) {
            //下单正常
            model.addAllAttributes(result.getData());
            return "seckill/trade";
        } else {
            model.addAttribute("message",result.getMessage());

            return "seckill/fail";
        }
    }
}
