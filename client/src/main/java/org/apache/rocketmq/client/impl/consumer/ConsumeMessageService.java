package org.apache.rocketmq.client.impl.consumer;

import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;

/**
 * 消费消息服务，不断不断不断消费消息，并处理消费结果
 */
public interface ConsumeMessageService {
    void start();

    void shutdown();

    void updateCorePoolSize(int corePoolSize);

    void incCorePoolSize();

    void decCorePoolSize();

    int getCorePoolSize();

    ConsumeMessageDirectlyResult consumeMessageDirectly(final MessageExt msg, final String brokerName);

    void submitConsumeRequest(
        final List<MessageExt> msgs,
        final ProcessQueue processQueue,
        final MessageQueue messageQueue,
        final boolean dispathToConsume);
}
