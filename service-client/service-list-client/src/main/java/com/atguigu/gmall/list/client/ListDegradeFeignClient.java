package com.atguigu.gmall.list.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ListDegradeFeignClient implements ListFeignClient{
    @Override
    public Result incrHotScore(Long skuId) {
        return null;
    }

    @Override
    public Result<Map> list(SearchParam searchParam) {
        return null;
    }
}
