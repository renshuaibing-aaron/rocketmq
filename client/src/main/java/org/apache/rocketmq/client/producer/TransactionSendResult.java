package org.apache.rocketmq.client.producer;

public class TransactionSendResult extends SendResult {
    private LocalTransactionState localTransactionState;

    public TransactionSendResult() {
    }

    public LocalTransactionState getLocalTransactionState() {
        return localTransactionState;
    }

    public void setLocalTransactionState(LocalTransactionState localTransactionState) {
        this.localTransactionState = localTransactionState;
    }
}
