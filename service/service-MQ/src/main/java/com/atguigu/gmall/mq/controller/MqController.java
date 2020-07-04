package com.atguigu.gmall.mq.controller;


import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/mq")
@Slf4j
public class MqController {
    @Autowired
    private RabbitService rabbitService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 消息发送
     */
    //http://cart.gmall.com/8282/mq/sendConfirm
    @GetMapping("sendConfirm")
    public Result sendConfirm() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitService.sendMessage("exchange.confirm", "routing.confirm", sdf.format(new Date()));
        return Result.ok();
    }
    @GetMapping("sendDeadLettle")
    public Result sendDeadLettle(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        //准备发送消息
//        rabbitTemplate.convertAndSend(DeadLetterMqConfig.exchange_dead,DeadLetterMqConfig.routing_dead_1,
//                "ok",message -> {
//            //设置消息发送的延迟时间
//                    message.getMessageProperties().setExpiration(1000*10+"");
//                    System.out.println(sdf.format(new Date())+"Delay sent.");
//                    return message;
//                }
//                );
        this.rabbitTemplate.convertAndSend(DeadLetterMqConfig.exchange_dead, DeadLetterMqConfig.routing_dead_1, "11");
        System.out.println(sdf.format(new Date()) + " Delay sent.");
        return Result.ok();
    }

    @GetMapping("sendDelay")
    public Result sendDelay() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.rabbitTemplate.convertAndSend(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay, sdf.format(new Date()), new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setDelay(10 * 1000);
                System.out.println(sdf.format(new Date()) + " Delay sent.");
                return message;
            }
        });
        return Result.ok();
    }

}
