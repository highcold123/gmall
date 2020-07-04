package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public UserInfo login(UserInfo userInfo) {
        String newPwd = DigestUtils.md5DigestAsHex(userInfo.getPasswd().getBytes());
        QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
        userInfoQueryWrapper.eq("login_name",userInfo.getLoginName()).eq("passwd",newPwd);
        UserInfo user = userInfoMapper.selectOne(userInfoQueryWrapper);
        if (user != null) {
            return user;
        }
            return null;
    }

}
