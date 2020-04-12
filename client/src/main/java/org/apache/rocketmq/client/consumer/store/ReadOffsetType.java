package org.apache.rocketmq.client.consumer.store;

public enum ReadOffsetType {
    /**
     * 从内存读取
     * From memory
     */
    READ_FROM_MEMORY,
    /**
     * 从存储文件中读取 broker或者文件
     * From storage
     */
    READ_FROM_STORE,
    /**
     * From memory,then from storage
     * 优先从内存读取读取不到从存储器中读取
     */
    MEMORY_FIRST_THEN_STORE;
}
