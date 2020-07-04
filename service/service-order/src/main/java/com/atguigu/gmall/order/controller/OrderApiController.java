package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    //有auth必须登录
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);
        //先获取用户地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        //获取送货清单
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        String tradeNo = orderService.getTradeNo(userId);
        //循环遍历，经数据赋值给 orderDetail
        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();
        String totalNum = null;
        if (!CollectionUtils.isEmpty(cartCheckedList)) {
            for (CartInfo cartInfo : cartCheckedList) {
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setSkuId(cartInfo.getSkuId());
                orderDetail.setSkuName(cartInfo.getSkuName());
                orderDetail.setImgUrl(cartInfo.getImgUrl());
                orderDetail.setSkuNum(cartInfo.getSkuNum());
                orderDetail.setOrderPrice(cartInfo.getSkuPrice());
                totalNum += cartInfo.getSkuNum();

                // 添加到集合
                detailArrayList.add(orderDetail);
            }
        }
//声明一个map 集合来存储数据
        HashMap<String, Object> map = new HashMap<>();
        map.put("userAddressList", userAddressList);
        map.put("detailArrayList", detailArrayList);
        //保存总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        //计算总金额
        orderInfo.sumTotalAmount();
        map.put("totalAmount", orderInfo.getTotalAmount());
        //计算大件数
        map.put("totalNum", detailArrayList.size());
        //计算小件数
        map.put("tradeNo", tradeNo);
        return Result.ok(map);

    }

    // 提交订单：
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        // 用户Id
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));

        // 防止表单回退无刷新提交
        // 在后台能够获取页面提交过来的流水号
        // http://api.gmall.com/api/order/auth/submitOrder?tradeNo=null
        String tradeNo = request.getParameter("tradeNo");
        // 开始比较
        boolean flag = orderService.checkTradeNo(tradeNo, userId);
        // 如果返回的是true ，则说明第一次提交，如果是false 说明无刷新重复提交了！
//        if (flag){
//            // 正常
//        }else {
//            // 异常
//        }
        // 异常情况
        if (!flag) {
            // 如果是false 说明无刷新重复提交了！
            return Result.fail().message("不能回退无刷新重复提交订单!");
        }
        // 创建一个集合对象，来存储异常信息
        List<String> errorList = new ArrayList<>();
        // 使用异步编排来执行
        // 声明一个集合来存储异步编排对象
        List<CompletableFuture> futureList = new ArrayList<>();
        // 验证库存：验证每个商品，存在orderDetailList
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)) {
            for (OrderDetail orderDetail : orderDetailList) {
                // 开一个异步编排
                CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                    // 调用查询库存方法
                    boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                    if (!result) {
                        // 提示信息某某商品库存不足
                        // return Result.fail().message(orderDetail.getSkuName()+"库存不足！");
                        errorList.add(orderDetail.getSkuName() + "库存不足！");
                    }
                }, threadPoolExecutor);
                // 将验证库存的异步编排对象放入这个集合
                futureList.add(checkStockCompletableFuture);

                // 利用另一个异步编排来验证价格
                CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                    // 获取到商品的实时价格
                    BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                    // 判断 价格有变化，要么大于 1 ，要么小于 -1。说白了 ,相等 0
                    if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                        // 如果价格有变动，则重新查询。
                        // 订单的价格来自于购物车，只需要将购物车的价格更改了，重新下单就可以了。
                        cartFeignClient.loadCartCache(userId);
                        //return Result.fail().message(orderDetail.getSkuName()+"价格有变动,请重新下单！");
                        errorList.add(orderDetail.getSkuName() + "价格有变动,请重新下单！");
                    }
                }, threadPoolExecutor);
                // 将验证价格的异步编排添加到集合中
                futureList.add(skuPriceCompletableFuture);
            }
        }
        // 合并线程 所有的异步编排都在futureList
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        // 返回页面提示信息
        if (errorList.size() > 0) {
            // 获取异常集合的数据
            return Result.fail().message(StringUtils.join(errorList, ","));
        }

//        if (!CollectionUtils.isEmpty(orderDetailList)){
//            for (OrderDetail orderDetail : orderDetailList) {
//                // 调用查询库存方法
//                boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
//                if (!result){
//                    // 提示信息某某商品库存不足
//                    return Result.fail().message(orderDetail.getSkuName()+"库存不足！");
//                }
//
//                // 验证价格：
//                // 获取到商品的实时价格
//                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
//                // 判断 价格有变化，要么大于 1 ，要么小于 -1。说白了 ,相等 0
//                if (orderDetail.getOrderPrice().compareTo(skuPrice)!=0){
//                    // 如果价格有变动，则重新查询。
//                    // 订单的价格来自于购物车，只需要将购物车的价格更改了，重新下单就可以了。
//                    cartFeignClient.loadCartCache(userId);
//                    return Result.fail().message(orderDetail.getSkuName()+"价格有变动,请重新下单！");
//                }
//            }
//        }
        // 删除流水号
        orderService.deleteTradeNo(userId);

        Long orderId = orderService.saveOrderInfo(orderInfo);
        // 返回用户Id
        return Result.ok(orderId);
    }

    //根据订单id查询订单的数据对象
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId) {
        return orderService.getOrderInfo(orderId);
    }

    //拆单 的接口
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request) {
        String orderId = request.getParameter("orderId");
        //仓库与商品的对照关系
        String wareSkuMap = request.getParameter("wareSkuMap");
        //参考接口文档 获取子订单集合
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId), wareSkuMap);
        //将子订单中的部分数据转换为json字符串
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (OrderInfo orderInfo : subOrderInfoList) {
            Map map = orderService.initWareOrder(orderInfo);
            mapList.add(map);
        }
        return JSON.toJSONString(mapList);
    }
//封装秒杀订单数据
    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo) {
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }
}
