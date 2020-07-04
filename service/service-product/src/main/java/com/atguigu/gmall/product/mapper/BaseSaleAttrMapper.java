package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.BaseSaleAttr;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BaseSaleAttrMapper extends BaseMapper<BaseSaleAttr> {




    /**
     * 查询所有的销售属性数据
     *
     * @return
     */
    List<BaseSaleAttr> getBaseSaleAttrList();
}