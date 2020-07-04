package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

public interface PaymentService {
    /**
     * 保存交易记录
     * @param orderInfo
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    void savePaymentInfo(OrderInfo orderInfo, String paymentType);

    PaymentInfo getPaymentInfo(String out_trade_no, String name);

    void paySuccess(String out_trade_no, String name, Map<String, String> paramMap);

    void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfo);
//关闭支付宝交易
    void closePayment(Long orderId);
}
