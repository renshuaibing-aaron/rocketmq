package org.apache.rocketmq.client.consumer.listener;

import org.apache.rocketmq.common.message.MessageQueue;

/**
 * Consumer concurrent consumption context
 */
public class ConsumeConcurrentlyContext {
    private final MessageQueue messageQueue;

    /**
     * Message consume retry strategy<br>
     * -1,no retry,put into DLQ directly<br>  不重试直接回送到死信队列
     * 0,broker control retry frequency<br> 由Broker指定等待延时级别
     * >0,client control retry frequency  由回调函数指定等待延时级别
     * 这个表示消息失败后回送消息到Broker的重试消息队列的等待延时级别
     */
    private int delayLevelWhenNextConsume = 0;

    //这个是指最后一个正常消费的消息索引号  也就是 这个的最大值是这一次传入消息数量-1
    private int ackIndex = Integer.MAX_VALUE;

    public ConsumeConcurrentlyContext(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    public int getDelayLevelWhenNextConsume() {
        return delayLevelWhenNextConsume;
    }

    public void setDelayLevelWhenNextConsume(int delayLevelWhenNextConsume) {
        this.delayLevelWhenNextConsume = delayLevelWhenNextConsume;
    }

    public MessageQueue getMessageQueue() {
        return messageQueue;
    }

    public int getAckIndex() {
        return ackIndex;
    }

    public void setAckIndex(int ackIndex) {
        this.ackIndex = ackIndex;
    }
}
