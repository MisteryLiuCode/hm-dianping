package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * All rights Reserved, Designed By jiexingcloud. 实现锁
 *
 * @author liushuaibiao@jiexingcloud.com
 * @since 2023/7/26 16:34 Copyright ©2023 jiexingcloud. All rights reserved. 注意：本内容仅限于结行云创内部传阅，禁止外泄以及用于其他的商业用途。
 */
public class SimpleRedisLock implements ILock {
    //业务名称
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock";

    //生成随机数,拼接上随机数,释放锁的时候判断释放的是否是自己的锁.
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识,可以看到是哪个线程获取到了锁,作为 value,key 是业务类型,前缀+业务名称
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        //防止拆箱出现空指针,不能直接返回
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //获取本线程
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取你要释放的是哪把锁
        String lock = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //如果相等,则释放,
        if (threadId.equals(lock)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
