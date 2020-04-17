package org.apache.rocketmq.client.latency;

import org.apache.rocketmq.client.impl.producer.TopicPublishInfo;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.common.message.MessageQueue;

/**
 * Producer消息发送容错策略。默认情况下容错策略关闭，即sendLatencyFaultEnable=false
 */
public class MQFaultStrategy {
    private final static InternalLogger log = ClientLogger.getLog();

    //延迟故障容错，维护每个Broker的发送消息的延迟
    private final LatencyFaultTolerance<String> latencyFaultTolerance = new LatencyFaultToleranceImpl();

    //默认容错策略关闭
    private boolean sendLatencyFaultEnable = false;
    //延迟级别数组
    private long[] latencyMax = {50L, 100L, 550L, 1000L, 2000L, 3000L, 15000L};

    //不可用时长数组
    private long[] notAvailableDuration = {0L, 0L, 30000L, 60000L, 120000L, 180000L, 600000L};

    public long[] getNotAvailableDuration() {
        return notAvailableDuration;
    }

    public void setNotAvailableDuration(final long[] notAvailableDuration) {
        this.notAvailableDuration = notAvailableDuration;
    }

    public long[] getLatencyMax() {
        return latencyMax;
    }

    public void setLatencyMax(final long[] latencyMax) {
        this.latencyMax = latencyMax;
    }

    public boolean isSendLatencyFaultEnable() {
        return sendLatencyFaultEnable;
    }

    public void setSendLatencyFaultEnable(final boolean sendLatencyFaultEnable) {
        this.sendLatencyFaultEnable = sendLatencyFaultEnable;
    }

    /**
     * 这里是生产者的负载均衡
     * 容错策略选择消息队列逻辑。优先获取可用队列，其次选择一个broker获取队列，最差返回任意broker的一个队列
     * @param tpInfo Topic发布信息
     * @param lastBrokerName   brokerName
     * @return  消息队列
     */
    public MessageQueue selectOneMessageQueue(final TopicPublishInfo tpInfo, final String lastBrokerName) {
        if (this.sendLatencyFaultEnable) {
            try {
                // 获取 brokerName=lastBrokerName && 可用的一个消息队列
                //1、首先获取上次使用的Queue index+1  注意看着方法 这个地方是实现负载均衡里面的轮询的机制
                int index = tpInfo.getSendWhichQueue().getAndIncrement();
                for (int i = 0; i < tpInfo.getMessageQueueList().size(); i++) {
                    int pos = Math.abs(index++) % tpInfo.getMessageQueueList().size();
                    if (pos < 0) {
                        pos = 0;
                    }
                    //2、找到index对应的queue
                    MessageQueue mq = tpInfo.getMessageQueueList().get(pos);

                    //3、如果queue对应的broker可用，则使用该broker
                    if (latencyFaultTolerance.isAvailable(mq.getBrokerName())) {
                        if (null == lastBrokerName || mq.getBrokerName().equals(lastBrokerName)) {
                            return mq;
                        }
                    }
                }
                //4、如果上一步没找个合适的broker，则从所有的broker中选择一个相对合适的，并且broker是可写的。
                final String notBestBroker = latencyFaultTolerance.pickOneAtLeast();
                int writeQueueNums = tpInfo.getQueueIdByBroker(notBestBroker);
                if (writeQueueNums > 0) {
                    final MessageQueue mq = tpInfo.selectOneMessageQueue();
                    if (notBestBroker != null) {
                        mq.setBrokerName(notBestBroker);
                        mq.setQueueId(tpInfo.getSendWhichQueue().getAndIncrement() % writeQueueNums);
                    }
                    return mq;
                } else {
                    latencyFaultTolerance.remove(notBestBroker);
                }
            } catch (Exception e) {
                log.error("Error occurred when selecting message queue", e);
            }
            // 选择一个消息队列，不考虑队列的可用性
            return tpInfo.selectOneMessageQueue();
        }
     // 获得 lastBrokerName 对应的一个消息队列，不考虑该队列的可用性
        return tpInfo.selectOneMessageQueue(lastBrokerName);
    }

    /**
     * 更新延迟容错信息。当 Producer 发送消息时间过长，则逻辑认为N秒内不可用。按照latencyMax，notAvailableDuration的配置，对应如下
     * Producer发送消息消耗时长	Broker不可用时长
     * >= 15000 ms	600 * 1000 ms
     * >= 3000 ms	180 * 1000 ms
     * >= 2000 ms	120 * 1000 ms
     * >= 1000 ms	60 * 1000 ms
     * >= 550 ms	30 * 1000 ms
     * >= 100 ms	0 ms
     * >= 50 ms	0 ms
     * 更新延迟容错信息
     * @param brokerName  brokerName
     * @param currentLatency  延迟
     * @param isolation  是否隔离。当开启隔离时，默认延迟为30000。目前主要用于发送消息异常时
     */
    public void updateFaultItem(final String brokerName, final long currentLatency, boolean isolation) {
        if (this.sendLatencyFaultEnable) {
            long duration = computeNotAvailableDuration(isolation ? 30000 : currentLatency);
            this.latencyFaultTolerance.updateFaultItem(brokerName, currentLatency, duration);
        }
    }

    /**
     * 计算延迟对应的不可用时间
     * @param currentLatency 延迟
     * @return  不可用时间
     */
    private long computeNotAvailableDuration(final long currentLatency) {
        for (int i = latencyMax.length - 1; i >= 0; i--) {
            if (currentLatency >= latencyMax[i]) {
                return this.notAvailableDuration[i];
            }
        }

        return 0;
    }
}
