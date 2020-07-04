package com.atguigu.gmall.mq.reciver;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Configuration
public class ConfirmReceiver {

    //消息接收者
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "queue.confirm",autoDelete = "false"),
            exchange = @Exchange(value = "exchange.confirm",autoDelete = "true"),
            key = {"routing.confirm"}
    ))
    public void process(Message message, Channel channel) throws IOException {
        //获取消息
        System.out.println("RabbitListener:"+new String(message.getBody()));
        //确认消息 第二个参数表示每次确认一个消息
        try {
            int a = 1/0;
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (IOException e) {
//            e.printStackTrace();
            System.out.println("出现异常！！！");
            //判断是否已经处理过一次消息
            if(message.getMessageProperties().getRedelivered()){
                System.out.println("消息已经被处理过");
                //给一个拒接消息
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            }else {
                System.out.println("消息即将返回队列");
                //第二个参数代表是否批量处理
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true);
            }
        }
    }
}
