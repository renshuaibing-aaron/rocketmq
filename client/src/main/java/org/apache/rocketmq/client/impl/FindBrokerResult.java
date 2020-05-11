package org.apache.rocketmq.client.impl;

public class FindBrokerResult {
    //broker地址
    private final String brokerAddr;
    //是否是从节点
    private final boolean slave;
    //broker版本
    private final int brokerVersion;

    public FindBrokerResult(String brokerAddr, boolean slave) {
        this.brokerAddr = brokerAddr;
        this.slave = slave;
        this.brokerVersion = 0;
    }

    public FindBrokerResult(String brokerAddr, boolean slave, int brokerVersion) {
        this.brokerAddr = brokerAddr;
        this.slave = slave;
        this.brokerVersion = brokerVersion;
    }

    public String getBrokerAddr() {
        return brokerAddr;
    }

    public boolean isSlave() {
        return slave;
    }

    public int getBrokerVersion() {
        return brokerVersion;
    }
}
