package org.apache.rocketmq.client.impl.consumer;

import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.logging.InternalLogger;

/**
 * 均衡消息队列服务，负责分配当前 Consumer 可消费的消息队列( MessageQueue )。当有新的 Consumer 的加入或移除，都会重新分配消息队列
 */
public class RebalanceService extends ServiceThread {

    /**
     * 等待间隔，单位：毫秒
     */
    private static long waitInterval = Long.parseLong(System.getProperty( "rocketmq.client.rebalance.waitInterval", "20000"));
    private final InternalLogger log = ClientLogger.getLog();

    /**
     * MQClient对象
     */
    private final MQClientInstance mqClientFactory;

    public RebalanceService(MQClientInstance mqClientFactory) {
        this.mqClientFactory = mqClientFactory;
    }

    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");

        while (!this.isStopped()) {
            this.waitForRunning(waitInterval);
            //分配当前 Consumer 可消费的消息队列( MessageQueue )
            System.out.println("====【测试消息队列分配服务RebalanceService】==="+waitInterval+"----"+System.currentTimeMillis());
            this.mqClientFactory.doRebalance();
        }

        log.info(this.getServiceName() + " service end");
    }

    @Override
    public String getServiceName() {
        return RebalanceService.class.getSimpleName();
    }
}
