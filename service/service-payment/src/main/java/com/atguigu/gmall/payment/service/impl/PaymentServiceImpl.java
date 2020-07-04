package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;
    @Autowired
    private RabbitService rabbitService;

    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderInfo.getId());
        queryWrapper.eq("payment_type", paymentType);
        Integer count = paymentInfoMapper.selectCount(queryWrapper);
        if (count > 0) return;
        //创建一个对象
        PaymentInfo paymentInfo = new PaymentInfo();
        //给对象属性赋值
        // 保存交易记录
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        paymentInfo.setSubject(orderInfo.getTradeBody());
        //paymentInfo.setSubject("test");
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfoMapper.insert(paymentInfo);
    }

    @Override
    public PaymentInfo getPaymentInfo(String out_trade_no, String name) {
        QueryWrapper<PaymentInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("out_trade_no", out_trade_no).eq("payment_type", name);
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(wrapper);
        return paymentInfo;
    }

    @Override
    public void paySuccess(String out_trade_no, String name, Map<String, String> paramMap) {
        //获取到订单id,为后面订单id使用
        PaymentInfo paymentInfo = getPaymentInfo(out_trade_no, name);
        if(paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED)||
        paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID)
        ){
            return;
        }

        //更新状态
        PaymentInfo paymentInfoUPD = new PaymentInfo();
        paymentInfoUPD.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfoUPD.setCallbackTime(new Date());
        //更新支付宝交易号
        paymentInfoUPD.setTradeNo(paramMap.get("trade_no"));
        paymentInfoUPD.setCallbackContent(paramMap.toString());
        //构造更新条件
        QueryWrapper<PaymentInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("out_trade_no", out_trade_no).eq("payment_type", name);
        paymentInfoMapper.update(paymentInfoUPD, wrapper);

        //发送消息通知订单

        // 后续更新订单状态！ 使用消息队列！
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY,paymentInfo.getOrderId());
    }

    //更新交易记录
    @Override
    public void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfo) {
        QueryWrapper<PaymentInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("out_trade_no", outTradeNo);
        paymentInfoMapper.update(paymentInfo, wrapper);
    }

    @Override
    public void closePayment(Long orderId) {
        //关闭交易
        QueryWrapper<PaymentInfo> wrapper = new QueryWrapper<>();
        //查询是否有这条记录
        wrapper.eq("order_id",orderId);

        Integer count = paymentInfoMapper.selectCount(wrapper);
        if(count==null||count.intValue()==0){
            //说明这个订单没有交易记录
            return;
        }
        //否则要关闭
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentInfoMapper.update(paymentInfo,wrapper);
    }
}
