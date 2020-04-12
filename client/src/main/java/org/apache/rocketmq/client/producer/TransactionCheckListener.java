package org.apache.rocketmq.client.producer;

import org.apache.rocketmq.common.message.MessageExt;
/**
 * 【事务消息回查】检查监听器
 * This interface will be removed in the version 5.0.0, interface {@link TransactionListener} is recommended.
 */
@Deprecated
public interface TransactionCheckListener {
    /**
     * 获取（检查）【本地事务】状态
     * @param msg  消息
     * @return 事务状态
     */
    LocalTransactionState checkLocalTransactionState(final MessageExt msg);
}
