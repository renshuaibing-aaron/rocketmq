package org.apache.rocketmq.client.latency;

/**
 * 延迟故障容错接口
 * @param <T>
 */
public interface LatencyFaultTolerance<T> {

    /**
     * 更新对应的延迟和不可用时长
     * @param name
     * @param currentLatency
     * @param notAvailableDuration
     */
    void updateFaultItem(final T name, final long currentLatency, final long notAvailableDuration);

    /**
     * 对象是否可用
     * @param name
     * @return
     */
    boolean isAvailable(final T name);

    /**
     * 移除对象
     * @param name
     */
    void remove(final T name);

    /**
     * 获取一个对象
     * @return
     */
    T pickOneAtLeast();
}
