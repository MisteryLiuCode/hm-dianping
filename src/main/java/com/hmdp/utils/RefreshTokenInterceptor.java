package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * All rights Reserved, Designed By jiexingcloud.
 * 拦截器
 *
 * @author liushuaibiao@jiexingcloud.com
 * @since 2023/7/20 15:30 Copyright ©2023 jiexingcloud. All rights reserved. 注意：本内容仅限于结行云创内部传阅，禁止外泄以及用于其他的商业用途。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    /**
     * 因为这个 bean 不是spring 管理的,所以使用构造器注入的方式进行依赖注入
     *
     * @param stringRedisTemplate
     * @return
     * @since 2023/7/21 11:30 by liushuaibiao@jiexingcloud.com
     **/
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        //从请求头获取 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        //获取这个用户的所有信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()){
            return true;
        }
        //将查询的hash 数据转为 userDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //存在,则放入 ThreadLocal 中,方便后面业务处理获取
        UserHolder.saveUser(userDTO);
        //刷新 token 的有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        //移出用户
        UserHolder.removeUser();
    }
}
