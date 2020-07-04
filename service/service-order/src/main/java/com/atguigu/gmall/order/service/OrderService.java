package com.atguigu.gmall.order.service;


import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<OrderInfo> {

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    //获取流水号
    String getTradeNo(String userId);

    //比较流水号
    boolean checkTradeNo(String tradeNo, String userId);

    //删除流水号
    void deleteTradeNo(String userId);

    /**
     * 验证库存
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean checkStock(Long skuId, Integer skuNum);

    void execExpireOrder(Long orderId);

    public void updateOrderStatus(Long orderId, ProcessStatus closed);

    /**
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);

    void sendOrderStatus(Long orderId);

    Map initWareOrder(OrderInfo orderInfo);

    List<OrderInfo> orderSplit(long parseLong, String wareSkuMap);

    void execExpireOrder(Long orderId, String s);
}
