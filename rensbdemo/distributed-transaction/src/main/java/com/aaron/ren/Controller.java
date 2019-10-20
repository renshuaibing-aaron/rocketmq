package com.aaron.ren;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

@RestController
public class Controller {

    @Autowired
    private OrderService orderService;


    @Autowired
    private TransactionProducer transactionProducer;
    /**
     * 生成订单接口
     * @param num
     * @param goodId
     * @param userId
     * @return
     */
    @GetMapping("save")
    public String makeOrder(
            @RequestParam("num") int num,
            @RequestParam("good_id") int goodId,
            @RequestParam("user_id") int userId) {

        orderService.save(UUID.randomUUID().toString(), num, goodId,userId);
        return "success";
    }


    @GetMapping("pay")
    public String pay(@RequestParam("order_id") String orderId)
            throws UnsupportedEncodingException, MQClientException, JsonProcessingException {
        transactionProducer.sendOrderPaySucessEvent(orderId);
        return "success";
    }
}
