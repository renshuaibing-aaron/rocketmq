package org.apache.rocketmq.example.quickstart;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

/**
 * todo 默认的消费者实现方式
 * This example shows how to subscribe and consume messages using providing {@link DefaultMQPushConsumer}.
 */
public class PushConsumer {

    public static void main(String[] args) throws InterruptedException, MQClientException {

        /*
         * Instantiate with specified consumer group name.
         * 消费组
         */
        //以consumerGroup名称为参数初始化DefaultMQPushConsumer对象
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("please_rename_unique_group_name_4");

        /*
         * Specify name server addresses.
         * <p/>
         *
         * Alternatively, you may specify name server addresses via exporting environmental variable: NAMESRV_ADDR
         * <pre>
         * {@code
         * consumer.setNamesrvAddr("name-server1-ip:9876;name-server2-ip:9876");
         * }
         * </pre>
         */
        //设置NameServer值；
        consumer.setNamesrvAddr("127.0.0.1:9876");


        /*
         * Specify where to start in case the specified consumer group is a brand new one.
         *
         * 从何处开始消费？？
         */
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);

        /*
         * Subscribe one more more topics to consume.
         * 订阅的topic
         * 构建Consumer端的订阅数据SubscriptionData对象
         */
        consumer.subscribe("20200406topic", "*");

        //注意这里有个坑 当consumer消费者先启动时  不会生效 其实还是一条一条的消费  原因消费者启动了 不断轮训生产者
        //生产者 写数据 还是一条一条写的
        //为了使这个生效 需要先启动生产者 写一些数据 然后 消费者会进行
        consumer.setConsumeMessageBatchMaxSize(10);


        /*
         *  Register callback to execute on arrival of messages fetched from brokers.
         * 设置拉取消息后的回调类
         */
        consumer.registerMessageListener(new MessageListenerConcurrentlyImpl());

        /*
         *  Launch the consumer instance.
         * 启动DefaultMQPushConsumer
         */
        consumer.start();

        System.out.printf("rocketMQ消费者启动.%n");
    }

    /**
     * 回调方法 在进行push时 当有消息时可以 可以回调这个方法
     */
    static class MessageListenerConcurrentlyImpl implements MessageListenerConcurrently {
        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            System.out.println("接受消息的数量是" + msgs.size());
            // System.out.printf("%s 接收到新消息: %s %n", Thread.currentThread().getName(), msgs);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }


}
