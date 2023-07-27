package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private Redisson redisson;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始");
        }
        //判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long id = UserHolder.getUser().getId();

        //可以用 redis 的 setnx 代替
        //        synchronized (id.toString().intern()) {
        //            //获取代理对象
        //            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //            return proxy.createVoucherOrder(voucherId);
        //        }
        //一个用户一个锁,所以使用 id
        //            SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + id, redisTemplate);
        RLock rLock = redisson.getLock("order:" + id);
        //过期时间要比业务时间执行长，因为如果短的话,可能业务还未执行完,就有新的线程进来,造成并发错误.
        //            boolean lock = simpleRedisLock.tryLock(1200);
        boolean lock = rLock.tryLock();

        if (!lock) {
            //获取锁失败
            return Result.fail("不允许下单");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
        finally {
            //释放锁
            rLock.unlock();
        }

    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = iSeckillVoucherService.update().setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        }
    }
}
