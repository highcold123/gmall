package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class OrderReceiver {
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentFeignClient paymentFeignClient;
    @Autowired
    private RabbitService rabbitService;

    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    private void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        //判断订单id是否为空
        if (orderId != null) {
            //为了这个消息重复消费 判断订单的状态
            //通过id获取订单对象
            OrderInfo orderInfo = orderService.getById(orderId);

            if (orderInfo != null && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus())) {
                //关闭过期订单
//                orderService.execExpireOrder(orderId);
                //是否有交易记录产生
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                if(paymentInfo!=null&&paymentInfo.getPaymentStatus().equals(ProcessStatus.UNPAID.name())){
                    //看看用户是否扫了二维码，如果扫了此时才会关闭交易记录
                    Boolean aBoolean = paymentFeignClient.checkPayment(orderId);
                    if(aBoolean){
                        //扫了，有交易记录
                        //关闭支付宝
                        Boolean flag = paymentFeignClient.closePay(orderId);
                        if(flag){
                            //支付宝交易可以关闭，说明没付过款
                            orderService.execExpireOrder(orderId,"2");
                        }else {
                            //否则用户已经付款
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                        }
                    }else {
                        //没扫，没交易记录
                        orderService.execExpireOrder(orderId,"2");
                    }
                }else {
                    //paymentInfo中根本没有交易记录
                    orderService.execExpireOrder(orderId,"1");
                }
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    //订单支付更改订单状态 通知库存
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void updOrder(Long orderId, Message message, Channel channel) throws IOException {
        //判断orderId不为空
        if (orderId != null) {
            //更新状态 订单、进程
            OrderInfo orderInfo = orderService.getById(orderId);
            //判断状态 才准备更新数据
            if (orderInfo != null && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus())) {
                orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
                //发送消息通知库存减库存
                orderService.sendOrderStatus(orderId);
            }
        }
//手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        ;
    }

    //监听减库存的消息队列
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER, durable = "true"),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updOrderStockStatus(String msgJson, Message message, Channel channel) throws IOException {
        //获取json数据
        if (StringUtils.isNotEmpty(msgJson)) {
            Map<String, Object> map = JSON.parseObject(msgJson, Map.class);
            Object orderId = map.get("orderId");
            Object status = map.get("status");
            //根据状态去判断减库存的结果
            if ("DEDUCTED".equals(status)) {
                //说明减库存成功,更新订单状态
                orderService.updateOrderStatus((Long) orderId, ProcessStatus.WAITING_DELEVER);

            } else {
                //说明库存超卖
                orderService.updateOrderStatus((Long) orderId, ProcessStatus.STOCK_EXCEPTION);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
