package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.RedisConst;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
@Aspect
public class GmallCacheAspect {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    //编写一个环绕通知
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint point) throws Throwable {
        Object result = null;
        //获取到方法上传递的参数
        Object[] args = point.getArgs();
        //获取方法上的注解
        MethodSignature signature = (MethodSignature) point.getSignature();
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
        //获取注解上的前缀
        String prefix = gmallCache.prefix();
        //定义一个key
        String key = prefix + Arrays.asList(args).toString();
        //有了key将数据存储到缓存中,值是目标方法执行后的返回值

        //根据key获取缓存中的数据
        result = cacheHit(signature, key);

        //判断缓存中是否获得到了数据
        if (result != null) {
            return result;
        }
        //如果是空，加分布式锁，去数据库寻找
        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            try {
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX2, RedisConst.SKUKEY_TEMPORARY_TIMEOUT, TimeUnit.SECONDS);
                //说明上锁成功
                if (res) {
                    //查询数据库中的数据
                    result = point.proceed(point.getArgs());

                    if (result == null) {
                        Object object = new Object();
                        redisTemplate.opsForValue().set(key, JSON.toJSONString(object), RedisConst.SKUKEY_TEMPORARY_TIMEOUT, TimeUnit.SECONDS);
                        return object;

                    }
                    //查询出来不是空
                    redisTemplate.opsForValue().set(key, JSON.toJSONString(result), RedisConst.SKUKEY_TEMPORARY_TIMEOUT, TimeUnit.SECONDS);
                    return result;
                } else {
                    Thread.sleep(1000);
                    return cacheHit(signature, key);
                }
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private Object cacheHit(MethodSignature signature, String key) {
        //根据key 获取缓存数据
        String object = (String) redisTemplate.opsForValue().get(key);
        //此时获取返回值必须明确
        if(!StringUtils.isEmpty(object)){
            //表示缓存中有数据
            Class returnType = signature.getReturnType();
            //返回数据
            return JSON.parseObject(object,returnType);
        }
        return null;
    }
}
