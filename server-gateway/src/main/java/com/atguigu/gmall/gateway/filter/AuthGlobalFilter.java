package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * @author mqx
 * @date 2020/6/23 9:24
 */
@Component
public class AuthGlobalFilter implements GlobalFilter {

    @Autowired
    private RedisTemplate redisTemplate;

    // 路径匹配的工具类
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Value("${authUrls.url}")
    private String authUrlsUrl; // authUrlsUrl=trade.html,myOrder.html,list.html

    // 过滤器
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取用户在浏览器中输入的访问路径URL
        // 获取到请求对象
        ServerHttpRequest request = exchange.getRequest();
        // 通过请求对象来获取URL api/product/inner/getSkuInfo/30
        String path = request.getURI().getPath();
        // 判断用户发起的请求中是否有inner，说明是内部接口，内部接口不允许在浏览器直接访问！
        // 做一个路径匹配工作
        if (antPathMatcher.match("/**/inner/**",path)){
            // 给提示信息，没用权限访问
            // 获取响应对象
            ServerHttpResponse response = exchange.getResponse();
            // out 方法提示信息
            return out(response, ResultCodeEnum.PERMISSION);
        }
        // 想获取用户登录信息，用户登录成功之后，我们存储了一个userId 在缓存。
        // 如果在缓存中获取到了userId 那么就说明用户已经登录了，反之。
        // 缓存中是如何存储userId 的么？key=user:login:token
        // token 在登录的过程中，将token 放入了两个地方，一个是cookie ，要给是header。
        // 在缓存中放入了一个 IP 地址，
        String userId = getUserId(request);
        // 获取临时用户Id
        String userTempId = getUserTempId(request);
        // 判断防止 token 被盗用
        if ("-1".equals(userId)){
            // 获取响应对象
            ServerHttpResponse response = exchange.getResponse();
            // out 方法提示信息
            return out(response, ResultCodeEnum.PERMISSION);
        }

        // 用户登录认证 http://localhost/api/product/auth/hello
        if (antPathMatcher.match("/api/**/auth/**",path)){
            // 如果用户的访问url 中包含此路径，则用户必须登录
            if (StringUtils.isEmpty(userId)){
                // 获取响应对象
                ServerHttpResponse response = exchange.getResponse();
                // out 方法提示信息
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        // 验证用户访问web-all时是否带有黑名单中的控制器！
        // url: trade.html,myOrder.html,list.html
        for (String authUrl : authUrlsUrl.split(",")) {
            // 用户访问的路径中是否包含了上述的内容
            // http://list.gmall.com/list.html?category3Id=61 用户必须登录
            // http://item.gmall.com/30.html // 用户可以不登录
            // 用户访问的路径中有上述内容，并且用户没用登录
            if (path.indexOf(authUrl)!=-1 && StringUtils.isEmpty(userId)){
                // 获取响应对象
                ServerHttpResponse response = exchange.getResponse();
                // 返回一个响应的状态码，重定向获取请求资源
                response.setStatusCode(HttpStatus.SEE_OTHER);
                // 访问登录页面
                response.getHeaders().set(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                // 设置返回
                return response.setComplete();
            }
        }

        // 用户在访问任何一个微服务的过程中，必须先走网关。既然在网关中获取到了用户Id，那么我就可以将用户Id传递给每个微服务。
        // item.gmall.com  list.gmall.com order.gmall.com
        // 传递用户Id，临时用户Id 到各个微服务！
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){
            if (!StringUtils.isEmpty(userId)){
                // 将用户Id 存储在请求头中
                request.mutate().header("userId",userId).build();
            }
            if (!StringUtils.isEmpty(userTempId)){
                // 将临时用户Id 存储在请求头中
                request.mutate().header("userTempId",userTempId).build();
            }
            // 固定写法
            return chain.filter(exchange.mutate().request(request).build());
        }
        return chain.filter(exchange);
    }

    /**
     * 获取用户Id
     * @param request
     * @return
     */
    private String getUserId(ServerHttpRequest request) {
        // 用户Id 在缓存中存储 缓存中是如何存储userId 的么？key=user:login:token
        // 关键是token  一个是cookie ，要给是header。
        String token = "";
        // 从header 中获取
        List<String> list = request.getHeaders().get("token");
        if (null!=list){
            // 集合中的数据是如何存储，集合中只有一个数据因为key 是同一个
            token = list.get(0);
        }else {
            // 从cookie 中获取
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
//            List<HttpCookie> token1 = cookies.get("token");
//            HttpCookie httpCookie = token1.get(0);
            HttpCookie cookie = cookies.getFirst("token");
            if (null!=cookie){
                // 因为token 要经过url进行传送
                token = URLDecoder.decode(cookie.getValue());
            }
        }
        if (!StringUtils.isEmpty(token)){
            // 组成key=user:login:token
            String userKey = "user:login:"+token;
            // 从缓存中获取数据
            String userJson = (String) redisTemplate.opsForValue().get(userKey);
            // 使用 JSONObject 进行数据转化  这个数据中有 userId，ip
            JSONObject jsonObject = JSONObject.parseObject(userJson);
            // 获取ip地址，是在登录时获取的ip地址，这个地址是在缓存的！
            String ip = jsonObject.getString("ip");
            // 获取到当前正在登录电脑的IP地址。
            String curIp = IpUtil.getGatwayIpAddress(request);
            // 校验token 是否能被盗用
            if (curIp.equals(ip)){
                return jsonObject.getString("userId");
            }else {
                // ip 地址不一样，说明不是在同一台电脑。
                return "-1";
            }
        }
        return null;
    }

    // 获取临时用户Id,添加购物车时，临时用户Id 已经存在cookie 中！ 同时也可能存在header 中
    private String getUserTempId(ServerHttpRequest request){
        String userTempId = "";
        // 从header 中获取
        List<String> list = request.getHeaders().get("userTempId");
        if (null!=list){
            // 集合中的数据是如何存储，集合中只有一个数据因为key 是同一个
            userTempId = list.get(0);
        }else {
            // 从cookie 中获取
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
            HttpCookie cookie = cookies.getFirst("userTempId");
            if (null!=cookie){
                // 因为token 要经过url进行传送
                userTempId = URLDecoder.decode(cookie.getValue());
            }
        }
        return userTempId;
    }

    /**
     * 提示信息方法
     * @param response
     * @param resultCodeEnum
     * @return
     */
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        // 返回用户的权限通知提示
        Result<Object> result = Result.build(null, resultCodeEnum);
        //result对象变成一个字节数组
        byte[] bytes = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8); // 设置字符集
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        // 目的是给用户提示，显示到页面
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");
        // Publisher --->CorePublisher ---> Mono
        return response.writeWith(Mono.just(wrap));
    }
}
