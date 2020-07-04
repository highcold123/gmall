package com.atguigu.gmall.client;

import com.atguigu.gmall.common.result.Result;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ActivityDegradeFeignClient implements ActivityFeignClient{

    @Override
    public Result findAll() {
        return Result.fail();
    }

    @Override
    public Result getSeckillGoods(Long skuId) {
        return Result.fail();
    }

    @Override
    public Result<Map<String, Object>> trade() {
        return null;
    }
}
