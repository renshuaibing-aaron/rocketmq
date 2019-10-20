package com.aaron.ren;

//import com.alibaba.dubbo.config.annotation.Reference;
//import com.transaction.repository.IRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OrderService {
    @Autowired
    OrderDao orderDao;

    public final int PAY_DONE = 1;

    /**
     *  检查订单是否存在并且状态是支付完成
     **/
    public boolean checkOrderPaySuccess(String orderId){
        List<YzyOrder> allOrders = orderDao.findAll();
        return  allOrders.stream()
                .anyMatch(order -> order.getOrderId().equals(orderId) && order.getPayStatus() == PAY_DONE);
    }

    /**
     *  更新订单是为支付完成
     **/
    public void updatePayStatusByOrderId(String orderId){
        orderDao.updatePayStatusByOrderId(orderId, PAY_DONE);
    }

    /**
     *  生成订单，状态默认是未支付
     **/

    public void save(String orderId, int num, int goodId,int userId) {

        YzyOrder yzyOrder = new YzyOrder();
        yzyOrder.setOrderId(orderId);
        yzyOrder.setBuyNum(num);
        yzyOrder.setGoodId(goodId);
        yzyOrder.setUserId(userId);

        orderDao.save(yzyOrder);
    }
}