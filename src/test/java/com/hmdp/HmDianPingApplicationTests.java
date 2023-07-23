package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl service;



    /*
    测试缓存重建
     */
    @Test
    public void testCacheBuild() throws InterruptedException {
        service.saveShopToRedis(1L,20L);
    }
}
