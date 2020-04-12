package org.apache.rocketmq.client.impl.consumer;

import org.apache.rocketmq.common.message.MessageQueue;

public class PullRequest {
    private String consumerGroup;
    private MessageQueue messageQueue;
    private ProcessQueue processQueue;
    private long nextOffset;
    private boolean lockedFirst = false;

    public boolean isLockedFirst() {
        return lockedFirst;
    }

    public void setLockedFirst(boolean lockedFirst) {
        this.lockedFirst = lockedFirst;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public MessageQueue getMessageQueue() {
        return messageQueue;
    }

    public void setMessageQueue(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    public long getNextOffset() {
        return nextOffset;
    }

    public void setNextOffset(long nextOffset) {
        this.nextOffset = nextOffset;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((consumerGroup == null) ? 0 : consumerGroup.hashCode());
        result = prime * result + ((messageQueue == null) ? 0 : messageQueue.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PullRequest other = (PullRequest) obj;
        if (consumerGroup == null) {
            if (other.consumerGroup != null)
                return false;
        } else if (!consumerGroup.equals(other.consumerGroup))
            return false;
        if (messageQueue == null) {
            if (other.messageQueue != null)
                return false;
        } else if (!messageQueue.equals(other.messageQueue))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "PullRequest [consumerGroup=" + consumerGroup + ", messageQueue=" + messageQueue
            + ", nextOffset=" + nextOffset + "]";
    }

    public ProcessQueue getProcessQueue() {
        return processQueue;
    }

    public void setProcessQueue(ProcessQueue processQueue) {
        this.processQueue = processQueue;
    }
}
