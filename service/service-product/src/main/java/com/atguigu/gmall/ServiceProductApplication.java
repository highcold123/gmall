package com.atguigu.gmall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.atguigu.gmall.product.mapper")
@ComponentScan("com.atguigu")
@EnableFeignClients("com.atguigu")
public class ServiceProductApplication {
    public static void main(String[] args){
        SpringApplication.run(ServiceProductApplication.class,args);
    }
}
