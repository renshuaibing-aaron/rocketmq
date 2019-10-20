package com.aaron.ren;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderDao {
    public List<YzyOrder> findAll() {
        return null;
    }

    public void save(YzyOrder yzyOrder) {
    }

    public void updatePayStatusByOrderId(String orderId, int pay_done) {
    }
}
