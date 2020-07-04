package com.atguigu.gmall.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class DeadLetterMqConfig {

    public static final String exchange_dead = "exchange.dead";
    public static final String routing_dead_1 = "routing.dead.1";
    public static final String routing_dead_2 = "routing.dead.2";
    public static final String queue_dead_1 = "queue.dead.1";
    public static final String queue_dead_2 = "queue.dead.2";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(exchange_dead, true, false, null);
    }

    @Bean
    public Queue queue1() {
        //配置相应的参数
        HashMap<String, Object> map = new HashMap<>();
        //参数绑定
        map.put("x-dead-letter-exchange", exchange_dead);
        map.put("x-dead-letter-routing-key", routing_dead_2);
        //方式二，统一延迟时间
        map.put("x-message-ttl", 10 * 1000);
        return new Queue(queue_dead_1, true, false, false, map);
    }

    @Bean
    public Binding binding(){
        //将对列1通过routing_dead_1绑定到exchange_dead死信交换机上
        return BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);
    }

    @Bean
    public Queue queue2() {
        //将队列2通过routing_dead_2 key 绑定到交换机上
        return new Queue(queue_dead_2, true, false, false, null);
    }
}