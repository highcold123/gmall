package com.atguigu.gmall.list.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "service-list",fallback = ListDegradeFeignClient.class)
public interface ListFeignClient {
    //统计热点商品排序，热度排名的远程接口
    @GetMapping("/api/list/inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable("skuId") Long skuId);

    /**
     * 搜索商品
     * @param listParam
     * @return
     */
    @PostMapping("/api/list")
    Result list(@RequestBody SearchParam listParam);
}
