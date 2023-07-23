package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    //创建10个线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //互斥解决缓存击穿
//        Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicalExpire(id);

        //逻辑过期

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    /*
    解决缓存穿透
     */
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //从 redis 里查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值
        if (shopJson != null) {
            return null;
        }
        //不存在，按照 id 查询数据库
        Shop shop = getById(id);

        // 不存在返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        Shop shop;
        String key = CACHE_SHOP_KEY + id;
        //从 redis 里查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值
        if (shopJson != null) {
            return null;
        }

        //redis里没有缓存，还是缓存重建
        //尝试拿到互斥锁，成功查询数据库，写入redis,失败，等待，重新获取缓存，获取锁
        String lockKey = "lock:shop:" + id;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //失败，休眠并重试,这里做一个递归，一直尝试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //不存在，按照 id 查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            // 不存在返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在写入缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //写入缓存之后释放锁
            unlock(lockKey);
        }
        return shop;

    }


    //逻辑过期
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //从 redis 里查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data =(JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，返回数据
            return shop;
        }
        //过期，缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            //创建线程池，进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //实际中应该设置时间长一些，这里为了看到效果，设置20秒
                    saveShopToRedis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //没有拿到锁，返回旧数据
        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺 id 不能为空");
        }
        //按照之前分析的，先操作数据库，再删除缓存
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /*
    尝试获取锁，也就是使用setnx设置值看是否成功
     */
    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //这是包装类，直接return会转换成基本数据类型，也就是拆箱，直接拆箱可能会遇到空指针，所以使用工具来进行拆箱
        return BooleanUtil.isTrue(aBoolean);
    }

    /*
    释放锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /*
    查询数据库，封装逻辑过期，写入redis
     */
    public void saveShopToRedis(Long id,Long expireSeconds) throws InterruptedException {
        //查询数据
        Shop shop = getById(id);
        //模拟缓存重建时长
        Thread.sleep(500);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
