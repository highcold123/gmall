package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 服务实现层
 *
 * @author Administrator
 */
@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {


    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;


    /**
     * 查询全部
     */
    @Override
    public List<SeckillGoods> findAll() {
        List<SeckillGoods> seckillGoodsList = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
        return seckillGoodsList;
    }

    /**
     * 根据ID获取实体
     *
     * @param id
     * @return
     */
    @Override
    public SeckillGoods getSeckillGoods(Long id) {
        return (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(id.toString());
    }

    @Override
    public void seckillOrder(Long skuId, Long userId) {
        //判断状态位
        String state = (String) CacheHelper.get(skuId.toString());
        //商品已经售罄
        if("0".equals(state)){
            return;
        }
        //判断用户是否已经下单 如何防止重复下单setnx
        //如果用户下单成功将下单信息 放入缓存
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        //继续判断
        if(!isExist){
            return;
        }
        //isExist = true 表示在缓存中没有存在，即第一次下单
        //从缓存中查看当前商品是否有剩余库存
        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if(StringUtils.isEmpty(goodsId)){
            //说明没库存了 没秒到
            //通知其他兄弟节点 发布订阅统一
            redisTemplate.convertAndSend("seckillpush",skuId+":0");
            //没库存了商品受邀
            return;

        }
        //如果不为空 说明库存还在 我们要将信息记录起来
        OrderRecode orderRecode = new OrderRecode();
        //在下单的时候如何控制每个用户只能购买一个商品 数量写死
        orderRecode.setNum(1);
        orderRecode.setUserId(userId+"");
        orderRecode.setOrderStr(MD5.encrypt(userId+""));
        orderRecode.setSeckillGoods(getSeckillGoods(skuId));
        //将与下单的数据放入缓存
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(),orderRecode);
        //秒到了 更新库存
        this.updateStovkCount(orderRecode.getSeckillGoods().getSkuId());
    }

    @Override
    public Result checkOrder(Long skuId, String userId) {
        Boolean isExist = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        //如果返回true
        if(isExist){
            //判断用户是否下单
            Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if(flag){
                //说明抢单成功
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                return Result.build(orderRecode, ResultCodeEnum.SUCCESS);
            }
        }
        //判断用户是否正常下单
        Boolean res = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if(res){
            //说明下单成功
            //获取下单成功的数据
            String orderId = (String) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            return Result.build(orderId,ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }
        //判断我们商品的状态位
        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)){
            return Result.build(null,ResultCodeEnum.SECKILL_FAIL);
        }
        //正在排队中
        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }

    private void updateStovkCount(Long skuId) {
        Long size = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        //为了避免频繁更新数据库 是2的倍数时，则更新一次数据库
        if(size%2==0){
            //更新数据库 以缓存为基准
            SeckillGoods seckillGood = getSeckillGoods(skuId);
            seckillGood.setStockCount(size.intValue());
            seckillGoodsMapper.updateById(seckillGood);
            //缓存中秒杀商品对象中的库存数要不要更新
            redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(seckillGood.getSkuId().toString(),seckillGood);
        }
    }
}
