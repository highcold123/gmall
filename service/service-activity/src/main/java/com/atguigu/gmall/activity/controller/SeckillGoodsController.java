package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsController {

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OrderFeignClient orderFeignClient;


    /**
     * 返回全部列表
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        return Result.ok(seckillGoodsService.findAll());
    }

    /**
     * 获取实体
     *
     * @param skuId
     * @return
     */
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable("skuId") Long skuId) {
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    /**
     * 获取下单码
     *
     * @param skuId
     * @param request
     * @return
     */
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        //怎么生成下单码 用用户id做一个MD5加密
        String userId = AuthContextHolder.getUserId(request);
        //通过当前的商品skuId 查到当前秒杀商品的对象 看看当前的商品是否正在秒杀 如果正在秒杀获取下单码 否则不获取
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        if (seckillGoods != null) {
            //判断当前的商品是否正在参与秒杀，可以通过时间
            Date curTime = new Date();
            //判断当前系统时间手否是在秒杀时间范围内
            if (DateUtil.dateCompare(seckillGoods.getStartTime(), curTime)
                    && DateUtil.dateCompare(curTime, seckillGoods.getEndTime())) {
                //生成下单码
                if (StringUtils.isNotEmpty(userId)) {
                    String encrypt = MD5.encrypt(userId);
                    return Result.ok(encrypt);
                }
            }
        }
        return Result.fail().message("获取下单码失败");
    }

    /**
     * 根据用户和商品ID实现秒杀下单
     *
     * @param skuId
     * @return
     */
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable("skuId") Long skuId, HttpServletRequest request) throws Exception {
        //校验下单码（抢购码规则可以自定义）
        String userId = AuthContextHolder.getUserId(request);
        //验证下单码
        String skuIdStr = request.getParameter("skuIdStr");
        if (!skuIdStr.equals(MD5.encrypt(userId))) {
            //下单码没有验证通过
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);

        }
        //验证状态位 获取秒杀商品所对应的状态位
        String state = (String) CacheHelper.get(skuId.toString());
        if (StringUtils.isNotEmpty(state)) {
            //状态位是空 请求不合法
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);

        }
        //表示能够下单
        if ("1".equals(state)) {
            //记录用户
            UserRecode userRecode = new UserRecode();
            userRecode.setSkuId(skuId);
            userRecode.setUserId(userId);
            //将信息放到消息队列
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER, MqConst.ROUTING_SECKILL_USER, userRecode);
        } else {
            //请求不合法 0 表示没有商品
            //已售罄
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }
        return Result.ok();
    }

    @GetMapping("auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        //获取用户id
        String userId = AuthContextHolder.getUserId(request);
        //调用服务层检查订单方法
        Result result = seckillGoodsService.checkOrder(skuId, userId);
        return result;
    }
    /**
     * 秒杀确认订单
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request) {
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);
        List<UserAddress> userAddressListByUserId = userFeignClient.findUserAddressListByUserId(userId);
        //显示送货清单OderDetail
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if(null==orderRecode){
            return Result.fail().message("非法操作，下单失败");
        }
        //获取秒杀的商品
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();
        //给数据赋值
        ArrayList<OrderDetail> orderDetailList = new ArrayList<>();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        orderDetail.setSkuNum(orderRecode.getNum());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        orderDetailList.add(orderDetail);
        //订单总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();

        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressListByUserId);
        result.put("detailArrayList", orderDetailList);
        // 保存总金额
        result.put("totalAmount", orderInfo.getTotalAmount());
        return Result.ok(result);


    }
    /**
     * 秒杀提交订单
     *
     * @param orderInfo
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);
        //调用订单服务的方法
        orderInfo.setUserId(Long.parseLong(userId));
        //数据都在缓存中
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if(orderRecode==null){
            return Result.fail().message("非法操作");
        }
        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if(orderId==null){
            return Result.fail().message("下单失败");
        }

        //下单成功
        //删除一下下单的信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);
        //保存一个下单记录
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId, orderId.toString());

        //返回数据
        return Result.ok(orderId);
    }
}