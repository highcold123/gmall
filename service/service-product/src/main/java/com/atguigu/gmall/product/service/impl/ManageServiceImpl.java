package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ManageServiceImpl implements ManageService {

    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;
    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;
    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;
    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;
    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;
    @Autowired
    private SpuInfoMapper spuInfoMapper;
    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;
    @Autowired
    private SpuImageMapper spuImageMapper;
    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;
    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;
    @Autowired
    private SkuInfoMapper skuInfoMapper;
    @Autowired
    private SkuImageMapper skuImageMapper;
    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;
    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;
    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;
    @Autowired
    private RabbitService rabbitService;

    @Override
    public List<BaseCategory1> getCategory1() {
        List<BaseCategory1> category1List = baseCategory1Mapper.selectList(null);
        return category1List;
    }

    @Override
    public List<BaseCategory2> getCategory2(Long categroy1Id) {
        QueryWrapper<BaseCategory2> wrapper = new QueryWrapper<>();
        wrapper.eq("category1_id", categroy1Id);
        List<BaseCategory2> category2List = baseCategory2Mapper.selectList(wrapper);
        return category2List;
    }

    @Override
    public List<BaseCategory3> getCategory3(Long categroy2Id) {
        QueryWrapper<BaseCategory3> wrapper = new QueryWrapper<>();
        wrapper.eq("category2_id", categroy2Id);
        List<BaseCategory3> category3List = baseCategory3Mapper.selectList(wrapper);
        return category3List;
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        List<BaseAttrInfo> baseAttrInfos = baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
        return baseAttrInfos;
    }

    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        //平台属性表操作
        if (baseAttrInfo.getId() != null) {
            //修改功能
            baseAttrInfoMapper.updateById(baseAttrInfo);
        } else {
            //插入数据
            baseAttrInfoMapper.insert(baseAttrInfo);
        }
        //先删除数据
        QueryWrapper<BaseAttrValue> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_id", baseAttrInfo.getId());
        baseAttrValueMapper.delete(wrapper);
        //再添加
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if (attrValueList != null && attrValueList.size() > 0) {
            for (BaseAttrValue baseAttrValue : attrValueList) {
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                //循环将数据添加到数据库中
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }

    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        QueryWrapper<BaseAttrValue> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_id", attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.selectList(wrapper);
        //需要给属性值集合赋值
        baseAttrInfo.setAttrValueList(baseAttrValueList);
        return baseAttrInfo;
    }

    @Override
    public IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {

        //封装查询条件
        QueryWrapper<SpuInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("category3_id", spuInfo.getCategory3Id());
        wrapper.orderByDesc("id");
        return spuInfoMapper.selectPage(pageParam, wrapper);

    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        return baseSaleAttrMapper.selectList(null);

    }

    @Override
    @Transactional
    public void saveSpuInfo(SpuInfo spuInfo) {
        //先插入
        spuInfoMapper.insert(spuInfo);
        //获取到数据
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (spuImageList != null && spuImageList.size() > 0) {
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
//        spuSaleAttr 销售属性表
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (spuSaleAttrList != null && spuSaleAttrList.size() > 0) {
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);

                //        spuSaleAttrValue 销售属性值表
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (spuSaleAttrValueList != null && spuSaleAttrValueList.size() > 0) {
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);

                    }
                }
            }
        }
    }

    //查询spuimage列表
    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        return spuImageMapper.selectList(new QueryWrapper<SpuImage>().eq("spu_id", spuId));
    }

    //查询销售属性列表
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        //由于数据存在多张表中，需要我们自定义XML文件实现sql
        List<SpuSaleAttr> spuSaleAttrs = spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
        System.out.println(spuSaleAttrs);
        return spuSaleAttrs;
    }

    @Override
    public void saveSkuInfo(SkuInfo skuInfo) {
        skuInfoMapper.insert(skuInfo);
//销售属性
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)) {
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                //在已知条件中获取spuID skuId赋值给销售属性值对象
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }
        //平台属性
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)) {
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (skuImageList != null && skuImageList.size() > 0) {

            // 循环遍历
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuInfo.getId());
    }

    //分页查询skuInfo列表
    @Override
    public IPage<SkuInfo> selectPage(Page<SkuInfo> skuInfoPage) {
        //需要使用Mapper
        QueryWrapper<SkuInfo> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        return skuInfoMapper.selectPage(skuInfoPage, wrapper);
    }

    //上架状态 1
    @Override
    public void onSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(1);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);
        //发送一个上架的消息
        //商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);

    }

    //下架状态为 0
    @Override
    public void cancelSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(0);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);
        //发送一个下架的消息
        //商品下架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_LOWER, skuId);
    }

    @Override
    @GmallCache(prefix = "sku")
    public SkuInfo getSkuInfo(Long skuId) {
        // ctrl+alt+m
        // return getSkuInfoRedisson(skuId);
        SkuInfo skuInfo = getSkuInfoDB(skuId);
        return skuInfo;

    }
//Redisson分布式锁

    public SkuInfo getSkuInfoRedisson(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            // 缓存存储数据：key-value
            // 定义key sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            // 获取里面的数据？ redis 有五种数据类型 那么我们存储商品详情 使用哪种数据类型？
            // 获取缓存数据
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 如果从缓存中获取的数据是空
            if (skuInfo == null) {
                // 直接获取数据库中的数据，可能会造成缓存击穿。所以在这个位置，应该添加锁。
                // 第二种：redisson
                // 定义锁的key sku:skuId:lock  set k1 v1 px 10000 nx
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                RLock lock = redissonClient.getLock(lockKey);
            /*
            第一种： lock.lock();
            第二种:  lock.lock(10,TimeUnit.SECONDS);
            第三种： lock.tryLock(100,10,TimeUnit.SECONDS);
             */
                // 尝试加锁
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                if (res) {
                    try {
                        // 处理业务逻辑 获取数据库中的数据
                        // 真正获取数据库中的数据 {数据库中到底有没有这个数据 = 防止缓存穿透}
                        skuInfo = getSkuInfo(skuId);
                        // 从数据库中获取的数据就是空
                        if (skuInfo == null) {
                            // 为了避免缓存穿透 应该给空的对象放入缓存
                            SkuInfo skuInfo1 = new SkuInfo(); //对象的地址
                            redisTemplate.opsForValue().set(skuKey, skuInfo1, RedisConst.SKUKEY_TEMPORARY_TIMEOUT, TimeUnit.SECONDS);
                            return skuInfo1;
                        }
                        // 查询数据库的时候，有值
                        redisTemplate.opsForValue().set(skuKey, skuInfo, RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                        // 使用redis 用的是lua 脚本删除 ，但是现在用么？ lock.unlock
                        return skuInfo;

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // 解锁：
                        lock.unlock();
                    }
                } else {
                    // 其他线程等待
                    Thread.sleep(1000);
                    return getSkuInfo(skuId);
                }
            } else {
                // 如果用户查询的数据在数据库中根本不存在的时候第一次会将一个空对象直接放入缓存。
                // 那么第二次查询的时候，缓存中有一个空对象 防止缓存穿透
                if (null == skuInfo.getId()) {
                    return null;
                }
                // 缓存数据不为空
                return skuInfo;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 为了防止缓存宕机：从数据库中获取数据
        return getSkuInfoDB(skuId);
    }

    //缓存获取商品详情,复杂分布式锁
    public SkuInfo getSkuInfoRedis(Long skuId) {
        SkuInfo skuInfo = null;
        try {
//        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
//        //查询sku图片赋值给skuInfo对象
//        List<SkuImage> skuImageList = skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId));
//        skuInfo.setSkuImageList(skuImageList);
//        return skuInfo;
            skuInfo = null;
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;

            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            if (skuInfo == null) {
                //应该获取数据库中的数据放入缓存
                //必须用分布式锁，防止数据库被击穿
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
                //还需要UUID作为锁的Value
                String uuid = UUID.randomUUID().toString();
                //分布式锁，锁住操作缓存与数据库的操作
                Boolean isExist = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                if (isExist) {
                    System.out.println("获取到锁！");
                    skuInfo = getSkuInfoRedis(skuId);
                    if (skuInfo == null) {
                        //为了防止缓存穿透，放入空值进缓存
                        SkuInfo skuInfo1 = new SkuInfo();
                        //放入缓存
                        redisTemplate.opsForValue().set(skuKey, skuInfo1, RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);
                        return skuInfo1;
                    }
                    //从数据库查出来不是空，放缓存
                    redisTemplate.opsForValue().set(skuKey, skuInfo);
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    // 使用redis执行lua执行
                    // 第一种传值
                    // DefaultRedisScript<Object> redisScript = new DefaultRedisScript<>(script);
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                    // 第二种传值
                    redisScript.setScriptText(script);
                    // 设置一下返回值类型 为Long
                    // 因为删除判断的时候，返回的0,给其封装为数据类型。如果不封装那么默认返回String 类型，那么返回字符串与0 会有发生错误。
                    redisScript.setResultType(Long.class);
                    // 第一个要是script 脚本 ，第二个需要判断的key，第三个就是key所对应的值。
                    redisTemplate.execute(redisScript, Arrays.asList(lockKey), uuid);
                } else {
                    //此时的县城并没有获取到分布式锁，应该等待
                    Thread.sleep(1000);
                    //等待完成数据
                    return getSkuInfo(skuId);
                }
            } else {
                // 弯！稍加严禁一点：
                //            if (skuInfo.getId()==null){ // 这个对象有地址，但是属性Id，price 等没有值！
                //                return null;
                //            }
                // 缓存中有数据，应该直接返回即可！
                return skuInfo; // 情况一：这个对象有地址，但是属性Id，price 等没有值！  情况二：就是既有地址，又有属性值！
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 如何中途发送了异常：数据库挺一下！
        return getSkuInfoDB(skuId);
    }

    // 提取方法
    private SkuInfo getSkuInfoDB(Long skuId) {
        // select * from sku_info where id = skuId
        // skuId=1000 在数据库中根本不存在！skuInfo 应该是空对象
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        if (null != skuInfo) {
            // 查询Sku图片赋值给skuInfo 对象，那么这个时候，skuInfo 对象中 sku基本数据，sku图片数据
            // select * from sku_image where sku_id = skuId
            List<SkuImage> skuImageList = skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId));
            skuInfo.setSkuImageList(skuImageList);
        }
        return skuInfo;
    }

    @Override
    @GmallCache(prefix = "categoryViewByCategory3Id:")
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @Override
    @GmallCache(prefix = "price")
    public BigDecimal getSkuPriceBySkuId(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if (skuInfo != null) {
            return skuInfo.getPrice();
        }
        return new BigDecimal("0");
    }

    @Override
//    @GmallCache(prefix = "spuSaleAttr")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    @Override
    @GmallCache(prefix = "saleAttrValuesBySpu:")
    public Map getSkuValueIdsMap(Long spuId) {
        HashMap<Object, Object> map = new HashMap<>();
        List<Map> mapList = skuSaleAttrValueMapper.getSaleAttrValuesBySpu(spuId);
        if (!CollectionUtils.isEmpty(mapList)) {
            for (Map skuMaps : mapList) {
                map.put(skuMaps.get("value_ids"), skuMaps.get("sku_id"));

            }
        }
        return map;
    }

  /*  @GmallCache(prefix = "index")
    @Override
    public List<JSONObject> getBaseCategoryList() {
        ArrayList<JSONObject> list = new ArrayList<>();
        //先获取所有的分类数据，通过视图来找
        List<BaseCategoryView> categoryViewList = baseCategoryViewMapper.selectList(null);
        //按照一级分类id 进行分组
        Map<Long, List<BaseCategoryView>> category1Map = categoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        //初始化一个index 构建json 字符串"index": 1
        int index = 1;

        //获取一级分类的数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry : category1Map.entrySet()) {
            Long category1Id = entry.getKey();
            //value 是一级分类下所有集合数据
            List<BaseCategoryView> category2List = entry.getValue();

            //声明一个JSON对象保存一级分类的数据
            JSONObject category1 = new JSONObject();
            category1.put("index", index);
            category1.put("categoryId", category1Id);
            String categoryName = category2List.get(0).getCategory1Name();
            category1.put("categoryName", categoryName);

            //变量迭代
            index++;
            //二级分类,map集合
            Map<Long, List<BaseCategoryView>> category2Map = category2List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            //创建一个二级分类对象的集合
            ArrayList<JSONObject> category2Child = new ArrayList<>();
            //获取二级分类中的数据
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                Long category2Id = entry2.getKey();
                //value 是二级分类下所有集合数据
                List<BaseCategoryView> category3List = entry2.getValue();
                //声明一个Json对象保存二级分类
                JSONObject category2 = new JSONObject();
                category2.put("categoryId", category2Id);
                category2.put("categoryName", category3List.get(0).getCategory2Name());

                category2Child.add(category2);
                //处理三级分类的数据
                ArrayList<JSONObject> category3child = new ArrayList<>();
                category3List.stream().forEach(category3View -> {
                    //创建一个三级分类的json对象
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId", category3View.getCategory3Id());
                    category3.put("categoryName", category3View.getCategory3Name());
                    category3child.add(category3);
                });
                //将三级分类的数据放在二级分类的的categoryChild
                category2.put("categoryChild", category3child);
            }
            //将三级分类的数据放在二级分类的的categoryChild
            category1.put("categoryChild", category2Child);
        }


        //按照数据接口的格式（json），分别去封装一级分类，二级分类，三级分类

        //封装完成之后将数据返回
        return list;
    }*/
    /**
     * 获取全部分类信息
     * @return
     */
    @Override
    @GmallCache(prefix = "index")
    public List<JSONObject> getBaseCategoryList() {
        /* 声明几个json 集合*/
        ArrayList<JSONObject> list = new ArrayList<>();

        //获取所有的分类数据
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);
        //按照一级分类id进行分组
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        //初始化一个index，构建json串 “index”：1
        int index = 1;
        //获取一级分类的数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry : category1Map.entrySet()) {
            //获取一级分类信息
            Long category1Id = entry.getKey();
            //获取一级分类下的所有数据
            List<BaseCategoryView> category2List = entry.getValue();
            //声明一个对象保存一级分类数据  一级分类的json字符串
            JSONObject category1 = new JSONObject();
            category1.put("index",index);
            category1.put("categoryId",category1Id);
            //获取一级分类名称      由于刚刚按照了一级分类ID 进行分组
            category1.put("categoryName",category2List.get(0).getCategory1Name());

            //变量迭代
            index++;

            //按照二级分类id进行分组
            Map<Long, List<BaseCategoryView>> category2Map = category2List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            //创建一个二级分类对象的集合
            ArrayList<JSONObject> category2Child = new ArrayList<>();
            //获取二级分类中的数据
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                //获取二级分类ID
                Long category2Id = entry2.getKey();
                //获取二级分类下的所有数据
                List<BaseCategoryView> category3List = entry2.getValue();
                //声明一个对象保存二级分类数据  二级分类的json字符串
                JSONObject category2 = new JSONObject();
                category2.put("categoryId",category2Id);
                category2.put("categoryName",category3List.get(0).getCategory2Name());
                //将二级分类对象添加到集合中
                category2Child.add(category2);

                //创建一个三级分类对象的集合
                ArrayList<JSONObject> category3Child = new ArrayList<>();
                //循环获取三级分类中的数据
                category3List.stream().forEach((category3View)->{
                    //创建一个三级分类对象
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",category3View.getCategory3Id());
                    category3.put("categoryName",category3View.getCategory3Name());
                    //将三级分类对象添加到集合中
                    category3Child.add(category3);
                });

                //将三级分类数据放入二级分类的child中
                category2.put("categoryChild",category3Child);
            }
            //将二级分类数据放入一级分类的child中
            category1.put("categoryChild",category2Child);
            //按照JSON的数据接口方式分别去封装一级分类、二级分类、三级分类数据
            list.add(category1);
        }

        //将封装后的数据返回
        return list;
    }

    @Override
    public BaseTrademark getBaseTrademark(Long tmId) {

        return baseTrademarkMapper.selectById(tmId);
    }

    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        //进行多表关联，因为这张表只有id外键关系
        return baseAttrInfoMapper.selectAttrInfoList(skuId);
    }
}