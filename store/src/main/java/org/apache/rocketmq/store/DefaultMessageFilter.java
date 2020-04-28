package org.apache.rocketmq.store;

import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;

import java.nio.ByteBuffer;
import java.util.Map;

public class DefaultMessageFilter implements MessageFilter {

    private SubscriptionData subscriptionData;

    public DefaultMessageFilter(final SubscriptionData subscriptionData) {
        this.subscriptionData = subscriptionData;
    }

    @Override
    public boolean isMatchedByConsumeQueue(Long tagsCode, ConsumeQueueExt.CqExtUnit cqExtUnit) {
        System.out.println("==========【消息队列消息是否匹配检测1】=============="+tagsCode+cqExtUnit);
        if (null == tagsCode || null == subscriptionData) {
            return true;
        }

        if (subscriptionData.isClassFilterMode()) {
            return true;
        }

        return subscriptionData.getSubString().equals(SubscriptionData.SUB_ALL)
            || subscriptionData.getCodeSet().contains(tagsCode.intValue());
    }

    @Override
    public boolean isMatchedByCommitLog(ByteBuffer msgBuffer, Map<String, String> properties) {
        return true;
    }
}
