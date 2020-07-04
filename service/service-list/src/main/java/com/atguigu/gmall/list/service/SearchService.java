package com.atguigu.gmall.list.service;

import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;

public interface SearchService {
    //商品上架
    void upperGoods(Long skuId);
    //商品下架
    void lowerGoods(Long skuId);
    //热点排序
    void incrHotScore(Long skuId) ;

    SearchResponseVo search(SearchParam searchParam) throws Exception;
}
