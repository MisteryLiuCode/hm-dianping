package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;

import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到 session(不再使用 session，使用 redis)
//        session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功,验证码:{}",code);
        //返回 ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            //不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //校验验证码,不一致报错,改为从 redis 里获取
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码校验错误");
        }
        //根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        //判断用户是否存在
        if (user == null){
            //不存在,创建用户
            user = createUserWithPhone(loginForm.getPhone());
        }
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user, userDTO);

        String token = UUID.randomUUID().toString(true);
        String tokenKey=LOGIN_USER_KEY+token;
        //将用户放入 session(改为保存到 redis)
//        session.setAttribute("user", userDTO);
        //这里使用的是stringRedisTemplate,存储的必须要是 string 类型,但是数据里 Id 是 long类型所以报错了,
        // 用下面方法解决,将 value 值转化为 string,但是会导致代码可读性变差,所以不推荐
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                //返回值是 map
                new HashMap<>(),
                //允许忽略 null 值
                CopyOptions.create().setIgnoreNullValue(true)
                        //对 value 值转化为 string
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置 token 有效期,模拟 session,当用户无操作 30 分钟之后,删除用户信息,重新登录
        //在拦截器里判断,如果有新请求,则重置该用户的过期时间.
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }



    public User createUserWithPhone(String phone){
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
