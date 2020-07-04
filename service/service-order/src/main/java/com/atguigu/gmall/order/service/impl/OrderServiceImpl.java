package com.atguigu.gmall.order.service.impl;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RabbitService rabbitService;
    @Value("${ware.url}")
    private String WARE_URL;

    @Override
    public Long saveOrderInfo(OrderInfo orderInfo) {
        //检查页面提交过来的数据是否吻合
        orderInfo.sumTotalAmount();//计算总金额
        //订单编号{对接支付}
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(100);
        orderInfo.setOutTradeNo(outTradeNo);
        //定单描述
//        orderInfo.setTradeBody("");
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuffer sb = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            sb.append(orderDetail.getSkuName() + "  ");
        }
        if (sb.toString().length() > 100) {
            //如果名称太长 截取
            orderInfo.setTradeBody(sb.toString().substring(0, 100));
        } else {
            orderInfo.setTradeBody(sb.toString());
        }
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        //创建时间
        orderInfo.setCreateTime(new Date());
        //过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        orderInfo.setExpireTime(calendar.getTime());

        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        orderInfoMapper.insert(orderInfo);
        if (!CollectionUtils.isEmpty(orderDetailList)) {
            for (OrderDetail orderDetail : orderDetailList) {
                orderDetail.setOrderId(orderInfo.getId());
                orderDetailMapper.insert(orderDetail);
            }
        }
        //发送延迟队列，如果定时未支付，取消订单
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL, MqConst.ROUTING_ORDER_CANCEL, orderInfo.getId(), MqConst.DELAY_TIME);
        return orderInfo.getId();
    }

    @Override
    public String getTradeNo(String userId) {
        //定义一个key
        String tradeNoKey = "user:" + userId + ":tradeNo";
        String tradeNo = UUID.randomUUID().toString();
        //将流水号放入缓存
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo);
        return tradeNo;
    }

    @Override
    public boolean checkTradeNo(String tradeNo, String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeNo.equals(redisTradeNo);
    }

    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // 删除数据
        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }

    @Override
    public void execExpireOrder(Long orderId) {
        //更新数据库中表的状态
        updateOrderStatus(orderId, ProcessStatus.CLOSED);
        //发送信息关闭支付宝交易
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
    }

    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfo.setProcessStatus(processStatus.name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        //查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderId));

        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    @Override
    public void sendOrderStatus(Long orderId) {
        //根据管理手册得知 发送的数据一半来自orderInfo
        updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);

        String wareJson = initWareOrder(orderId);
        //发送消息通知库存
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, wareJson);
    }

    // 根据orderId 获取json 字符串
    private String initWareOrder(Long orderId) {
        // 通过orderId 获取orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);

        // 将orderInfo中部分数据转换为Map
        Map map = initWareOrder(orderInfo);

        return JSON.toJSONString(map);
    }

    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！
    /*
    details:[{skuId:101,skuNum:1,skuName:
    ’小米手64G’},
    {skuId:201,skuNum:1,skuName:’索尼耳机’}]
     */
        ArrayList<Map> mapArrayList = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId", orderDetail.getSkuId());
            orderDetailMap.put("skuNum", orderDetail.getSkuNum());
            orderDetailMap.put("skuName", orderDetail.getSkuName());
            mapArrayList.add(orderDetailMap);
        }
        map.put("details", mapArrayList);
        return map;
    }

    /**
     * 拆单的实现类
     *
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    public List<OrderInfo> orderSplit(long orderId, String wareSkuMap) {
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        /*
         * 1.先获取原始订单
         * 2.wareSkuMap (json) 变成java能够识别的对象
         * 3.去创建新的子订单
         * 4.给子订单赋值
         * 5.保存子订单
         * 6.修改原始订单的状态为已拆分
         * 7.test
         * */

        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        //创建子订单
        for (Map map : mapList) {
            String wareId = (String) map.get("wareId");
            List<String> skuIdList = (List<String>) map.get("skuIds");
            OrderInfo subOrderInfo = new OrderInfo();
            //属性copy
            BeanUtils.copyProperties(orderInfoOrigin, subOrderInfo);
            //id不能拷贝
            subOrderInfo.setId(null);
            subOrderInfo.setParentOrderId(orderId);
            subOrderInfo.setWareId(wareId);
            ArrayList<OrderDetail> orderDetails = new ArrayList<>();
            //计算总金额 需要将子订单的订单明细准备好,它们都来源于原始订单的明细
            List<OrderDetail> originOrderDetailList = orderInfoOrigin.getOrderDetailList();
            if (!CollectionUtils.isEmpty(originOrderDetailList)) {
                //遍历原始的订单明细
                for (OrderDetail orderDetail : originOrderDetailList) {
                    //再遍历仓库中所对应的商品id
                    for (String skuId : skuIdList) {
                        if(Long.parseLong(skuId)==orderDetail.getSkuId()){
                            //如果相同，则这个商品就是子订单的商品
                            orderDetails.add(orderDetail);
                        }
                    }
                }
            }
            //需要将子订单的明细准备好，添加到子订单中
            subOrderInfo.setOrderDetailList(orderDetails);

            //获取到总金额
            subOrderInfo.sumTotalAmount();
            //保存子订单
            saveOrderInfo(subOrderInfo);
            //将新的子订单放到集合中
            subOrderInfoList.add(subOrderInfo);

        }
        //更新原始订单状态
        updateOrderStatus(orderId,ProcessStatus.SPLIT);
        return subOrderInfoList;
    }

    @Override
    public void execExpireOrder(Long orderId, String flag) {
        // 调用方法 状态
        updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if ("2".equals(flag)){
            // 发送消息队列，关闭支付宝的交易记录。
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }
}