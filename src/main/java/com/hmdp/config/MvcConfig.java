package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * All rights Reserved, Designed By jiexingcloud.
 * 配置拦截器
 *
 * @author liushuaibiao@jiexingcloud.com
 * @since 2023/7/20 15:40 Copyright ©2023 jiexingcloud. All rights reserved. 注意：本内容仅限于结行云创内部传阅，禁止外泄以及用于其他的商业用途。
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/blog/hot",
                        "/voucher/**",
                        "/upload/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/user/code",
                        "/user/login"
                ).order(1);
        //order 越小,先执行
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
