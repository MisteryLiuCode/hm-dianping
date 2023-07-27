package com.hmdp.utils;

/**
 * All rights Reserved, Designed By jiexingcloud.
 * 锁
 *
 * @author liushuaibiao@jiexingcloud.com
 * @since 2023/7/26 16:20 Copyright ©2023 jiexingcloud. All rights reserved. 注意：本内容仅限于结行云创内部传阅，禁止外泄以及用于其他的商业用途。
 */
public interface ILock {


    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return boolean
     * @since 2023/7/26 16:23 by liushuaibiao@jiexingcloud.com
     **/
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     *
     * @return boolean
     * @since 2023/7/26 16:23 by liushuaibiao@jiexingcloud.com
     **/
    void unlock();


}
