package com.atguigu.gmall.item.service;

import java.util.Map;

public interface ItemService {
    //通过skuid获取数据
    Map<String,Object> getBySkuId(Long skuId);
}
