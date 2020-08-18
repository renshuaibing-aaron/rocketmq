package org.apache.rocketmq.remoting;

/**
 * 通信接口
 */
public interface RemotingService {
    void start();

    void shutdown();

    void registerRPCHook(RPCHook rpcHook);
}
