package com.atguigu.gmall.payment.client;

import com.atguigu.gmall.model.payment.PaymentInfo;
import org.springframework.stereotype.Component;

@Component
public class PaymentFeignClientImpl implements PaymentFeignClient{
    @Override
    public Boolean closePay(Long orderId) {
        return null;
    }

    @Override
    public Boolean checkPayment(Long orderId) {
        return null;
    }

    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo) {
        return null;
    }
}
