package org.apache.rocketmq.common.protocol.heartbeat;

/**
 * Message model
 */
public enum MessageModel {
    /**
     * broadcast
     */
    BROADCASTING("BROADCASTING"),
    /**
     * clustering
     */
    CLUSTERING("CLUSTERING");

    private String modeCN;

    MessageModel(String modeCN) {
        this.modeCN = modeCN;
    }

    public String getModeCN() {
        return modeCN;
    }
}
