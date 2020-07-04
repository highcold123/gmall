package com.atguigu.gmall.common.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)//注解在方法上使用
@Retention(RetentionPolicy.RUNTIME)//注解生命周期
public @interface GmallCache {
//表示一个前缀
String prefix() default "cache";

}
