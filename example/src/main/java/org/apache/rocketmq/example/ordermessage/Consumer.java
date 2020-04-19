package org.apache.rocketmq.example.ordermessage;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

public class Consumer {

    public static void main(String[] args) throws MQClientException {

        //1、新建一个consumer，提供group name
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("please_rename_unique_group_name_3");
        //2、设置消费的起始偏移量
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        //3、订阅的topic和tag过滤条件
        consumer.subscribe("TopicTest", "TagA || TagC || TagD");


        //4、用户自定义消息listener，实现Orderly的接口
        consumer.registerMessageListener(new MessageListenerOrderly() {
            AtomicLong consumeTimes = new AtomicLong(0);

            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                context.setAutoCommit(false);
                System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
                this.consumeTimes.incrementAndGet();
                if ((this.consumeTimes.get() % 2) == 0) {
                    return ConsumeOrderlyStatus.SUCCESS;
                } else if ((this.consumeTimes.get() % 3) == 0) {
                    return ConsumeOrderlyStatus.ROLLBACK;
                } else if ((this.consumeTimes.get() % 4) == 0) {
                    return ConsumeOrderlyStatus.COMMIT;
                } else if ((this.consumeTimes.get() % 5) == 0) {
                    context.setSuspendCurrentQueueTimeMillis(3000);
                    // 看看这个地方的区别 和普通消息来说不一样  普通消息这里消费失败 是要重新发送给broker的
                    //意思是 让发送给broker  然后broker进行重新发送 然后会进行负载均衡发送 这样不同的消费者 就有机会和可能进行消费
                     //但是对于顺序消息来说 没啥意义 因为 顺序消息还是要发送给自己来消费
                    //而是会放到本地的缓存队列中重新处理。另外两个状态ROLLBACK和COMMIT已经被设置成deprecated了，我们就不关心了
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }

                return ConsumeOrderlyStatus.SUCCESS;
            }
        });
        //5、启动consumer
        consumer.start();
        System.out.printf("PushConsumer Started.%n");
    }

}
