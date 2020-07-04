package com.atguigu.gmall.cart.service.impl;


import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author mqx
 * @date 2020/6/23 15:35
 */
@Service
public class CartAsyncServiceImpl implements CartAsyncService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Override
    @Async
    public void updateCartInfo(CartInfo cartInfo) {
        System.out.println("更新方法-----");
        cartInfoMapper.updateById(cartInfo);
    }

    @Async
    @Override
    public void saveCartInfo(CartInfo cartInfo) {
        System.out.println("插入方法-----");
        cartInfoMapper.insert(cartInfo);
    }

    @Async
    @Override
    public void deleteCartInfo(String userId) {
        System.out.println("删除方法-----");
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userId));
    }

    @Async
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // update cartInfo set is_checked = isChecked where user_id =userId and sku_id = skuId;
        // 第一个参数，表示要修改的数据，第二个参数更新条件。
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId).eq("sku_id",skuId);
        cartInfoMapper.update(cartInfo,cartInfoQueryWrapper);
    }

    @Override
    public void deleteCartInfo(String userId, Long skuId) {
        System.out.println("删除方法---userId----skuId");
        // delete from cart_info where user_id =userId and sku_id = skuId;
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userId).eq("sku_id",skuId));

    }
}
