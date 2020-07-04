package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(){
        //创建corsconfiguration
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        //设置跨域属性
        corsConfiguration.addAllowedOrigin("*");//设置允许访问的网络是谁，*代表所有的网络
        corsConfiguration.setAllowCredentials(true);//表示是否从服务器中够获取到cookie
        corsConfiguration.addAllowedMethod("*");//表示允许所有的请求方法，或者写get，delete
        corsConfiguration.addAllowedHeader("*");//所有的请求头都可以跨域
        //创建源source
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",corsConfiguration);
        //返回CorsWebFilter
        return new CorsWebFilter(source);
    }
}
