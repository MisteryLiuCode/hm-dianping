package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All rights Reserved, Designed By jiexingcloud.
 * 配置 redisson
 *
 * @author liushuaibiao@jiexingcloud.com
 * @since 2023/7/27 14:33 Copyright ©2023 jiexingcloud. All rights reserved. 注意：本内容仅限于结行云创内部传阅，禁止外泄以及用于其他的商业用途。
 */

@Configuration
public class RedissonConfig {

    //一个方法,返回的是一个对象,注入 spring 中
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://101.43.145.108:6370").setPassword("l198923.");
        //创建 RedissionClient 对象
        return Redisson.create(config);
    }
}
