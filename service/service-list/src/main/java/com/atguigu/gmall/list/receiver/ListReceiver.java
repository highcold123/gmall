package com.atguigu.gmall.list.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.list.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ListReceiver {
    /**
     * 商品的上架
     */
    @Autowired
    private SearchService searchService;
    //使用注解监听
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_UPPER,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS),
            key = {MqConst.ROUTING_GOODS_UPPER}
    ))
    public void upperGoods(Long skuId, Message message, Channel channel)  {
        //判断skuId
        if(skuId!=null){
            //商品上架
            searchService.upperGoods(skuId);
        }
        //手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    /**
     * 商品的下架
     */

    //使用注解监听
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_LOWER,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS),
            key = {MqConst.ROUTING_GOODS_LOWER}
    ))
    public void lowerGoods(Long skuId, Message message, Channel channel) {
        //判断skuId
        if(skuId!=null){
            //商品上架
            searchService.lowerGoods(skuId);
        }
        //手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
