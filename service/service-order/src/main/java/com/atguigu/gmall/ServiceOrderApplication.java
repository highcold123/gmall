package com.atguigu.gmall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.atguigu")
@EnableFeignClients(basePackages= {"com.atguigu"})
@EnableDiscoveryClient
public class ServiceOrderApplication {
    public static void main(String[] args) {

        SpringApplication.run(ServiceOrderApplication.class,args);
    }
}