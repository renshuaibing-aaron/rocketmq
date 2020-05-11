package org.apache.rocketmq.client.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.remoting.RPCHook;

public class MQClientManager {
    private final static InternalLogger log = ClientLogger.getLog();
    private static MQClientManager instance = new MQClientManager();
    private AtomicInteger factoryIndexGenerator = new AtomicInteger();
    //注意这个缓存结果  说明一个clintID 只有一个MQClientInstance
    private ConcurrentMap<String/* clientId */, MQClientInstance> factoryTable =
        new ConcurrentHashMap<String, MQClientInstance>();

    private MQClientManager() {

    }

    public static MQClientManager getInstance() {
        return instance;
    }

    public MQClientInstance getAndCreateMQClientInstance(final ClientConfig clientConfig) {
        return getAndCreateMQClientInstance(clientConfig, null);
    }

    /**
     * 获取MQClientInstance
     * @param clientConfig
     * @param rpcHook
     * @return
     */
    public MQClientInstance getAndCreateMQClientInstance(final ClientConfig clientConfig, RPCHook rpcHook) {

        String clientId = clientConfig.buildMQClientId();
        //检查单例对象MQClientManager的factoryTable:ConcurrentHashMap<String/* clientId */, MQClientInstance>变量中
        // 是否存在该ClientID的对象，若存在则直接返回该MQClientInstance对象，若不存在，则创建MQClientInstance对象，
        // 并以该ClientID为key值将新创建的MQClientInstance对象存入并返回，
        // 将返回的MQClientInstance对象赋值给DefaultMQProducerImpl.mQClientFactory变量；
        // 说明一个IP客户端下面的应用，只有在启动多个进程的情况下才会创建多个MQClientInstance对象
        //todo  这句话怎么理解
        System.out.println("===112===clientId==========="+clientId);
        MQClientInstance instance = this.factoryTable.get(clientId);
        System.out.println("====2233=====instance==========="+instance);
        if (null == instance) {

            instance =
                new MQClientInstance(clientConfig.cloneClientConfig(),
                    this.factoryIndexGenerator.getAndIncrement(), clientId, rpcHook);

            //putIfAbsent   如果传入key对应的value已经存在，就返回存在的value，不进行替换
            MQClientInstance prev = this.factoryTable.putIfAbsent(clientId, instance);
            if (prev != null) {
                instance = prev;
                log.warn("Returned Previous MQClientInstance for clientId:[{}]", clientId);
            } else {
                log.info("Created new MQClientInstance for clientId:[{}]", clientId);
            }
        }

        return instance;
    }

    public void removeClientFactory(final String clientId) {
        this.factoryTable.remove(clientId);
    }
}
