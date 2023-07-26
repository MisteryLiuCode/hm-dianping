package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl service;

    @Autowired
    private RedisIdWorker idWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    /*
    测试缓存重建
     */
    @Test
    public void testCacheBuild() throws InterruptedException {
        service.saveShopToRedis(1L, 20L);
    }

    /*
    测试
     */

    @Test
    public void testIdWorker() {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                System.out.println("开始执行");
                long order = idWorker.nextId("order");
                System.out.println("id = " + order);
            }
            latch.countDown();
        };
        long begin =System.currentTimeMillis();
        for (int i=0;i<300;i++){
            es.submit(task);
        }
        long end=System.currentTimeMillis();

        System.out.println("time:"+(end-begin));

    }
}
