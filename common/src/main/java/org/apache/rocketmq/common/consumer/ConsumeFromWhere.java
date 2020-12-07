package org.apache.rocketmq.common.consumer;

public enum ConsumeFromWhere {


    @Deprecated
    CONSUME_FROM_LAST_OFFSET_AND_FROM_MIN_WHEN_BOOT_FIRST,
    @Deprecated
    CONSUME_FROM_MIN_OFFSET,
    @Deprecated
    CONSUME_FROM_MAX_OFFSET,



    CONSUME_FROM_FIRST_OFFSET,  //从队列当前最小偏移量开始消费
    CONSUME_FROM_LAST_OFFSET,  //从队列当前最大偏移量开始消费
    CONSUME_FROM_TIMESTAMP,   //从消费者启动时间戳开始消费

}
