package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;
//登录的控制器
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo, HttpServletRequest request){
        UserInfo login = userService.login(userInfo);
        if(login!=null){
            //登陆成功之后 需要返回一个token 由UUID组成
            String token = UUID.randomUUID().toString();
            //声明一个Map
            HashMap<String, Object> map = new HashMap<>();
            map.put("token",token);
            map.put("nickName",login.getNickName());
            // 如果登陆成功，我们需要将用户信息存储到缓存中，只需要通过一个userid就可以了
            //将次登陆的用户ip地址放入缓存中
            //声明一个对象
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId",login.getId().toString());
            //存放ip地址
            jsonObject.put("ip", IpUtil.getIpAddress(request));
//将数据存入缓存
            String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
            redisTemplate.opsForValue().set(userKey,jsonObject.toJSONString(),RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);
            //将数据放入ok中
            return Result.ok(map);
        }else {
            return Result.fail().message("用户名密码错误！");
        }
    }

    //退出登录的控制器
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        //删除缓存中的数据
        //登录的时候token不仅仅放在了cookie中，还放在了请求头中Header
        String token = request.getHeader("token");
        String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
        redisTemplate.delete(userKey);
        return Result.ok();
    }
}
