package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
public class SeckillReceiver {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importItemToRedis(Message message, Channel channel) throws IOException {
        //查询数据库取出秒杀商品
        QueryWrapper<SeckillGoods> wrapper = new QueryWrapper<>();
        //查审核通过的
        wrapper.eq("status", 1).gt("stock_count", 0);
        //而且查询的秒杀商品开始时间应该是今天
        wrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(wrapper);
        //将商品放入缓存
        if (!CollectionUtils.isEmpty(seckillGoodsList)) {
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());
                if (flag == true) {
                    //说明已经有这个商品了
                    continue;
                }
                //如果flag=false说明缓存中没有，放缓存
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
                //控制库存的超卖 redis-list
                for (int i = 0; i < seckillGoods.getStockCount(); i++) {
                    //放入数据
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId()).leftPush(seckillGoods.getSkuId());
                }
                //消息的发布和订阅 channel发送的频道，message表示发送的内容（skuId:1表示当前商品可以秒杀）
                redisTemplate.convertAndSend("seckillpush", seckillGoods.getSkuId() + ":1");
            }
            // 手动确认接收消息成功
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }

    //监听秒杀下单时发送过来的消息
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel) throws IOException {
        //预下单
        seckillGoodsService.seckillOrder(userRecode.getSkuId(),userRecode.getSkuId());
        //消息确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);


    }

    /**
     * 秒杀结束清空缓存
     *
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void clearRedis(Message message, Channel channel) throws IOException {
        //获取活动结束的商品
        QueryWrapper<SeckillGoods> wrapper = new QueryWrapper<>();
        wrapper.eq("status",1).le("end_time",new Date());
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(wrapper);
        //清空缓存
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            //清空缓存数量
            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId());
        }
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);
        //将审核状态更新一下
        SeckillGoods seckillGoods = new SeckillGoods();
        seckillGoods.setStatus("2");
        seckillGoodsMapper.update(seckillGoods,wrapper);
        //手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

}
