package com.atguigu.gmall.order.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.impl.OrderDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(value = "service-order", fallback = OrderDegradeFeignClient.class)
public interface OrderFeignClient {

    @GetMapping("/api/order/auth/trade")
     Result<Map<String,Object>> trade();


    //根据订单id查询订单的数据对象
    @GetMapping("/api/order/inner/getOrderInfo/{orderId}")
     OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId);

    /**
     * 提交秒杀订单
     * @param orderInfo
     * @return
     */
    @PostMapping("/api/order/inner/seckill/submitOrder")
    Long submitOrder(@RequestBody OrderInfo orderInfo);
}