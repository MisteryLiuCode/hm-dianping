package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * All rights Reserved, Designed By jiexingcloud.
 * redis生成唯一 id
 *
 * @author liushuaibiao@jiexingcloud.com
 * @since 2023/7/24 15:05 Copyright ©2023 jiexingcloud. All rights reserved. 注意：本内容仅限于结行云创内部传阅，禁止外泄以及用于其他的商业用途。
 */
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS  = 32;

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号 获取日期,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //这里加上 date日期,是防止键越来越长,最终超限,加上date每一天都是不同的键,也便于统计每天的数据.
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //拼接返回,但是不能直接拼接返回,因为直接拼接是字符串,不是 long 类型,所以需要用位运算
        return timestamp << COUNT_BITS | count;
    }
//
//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }

}
