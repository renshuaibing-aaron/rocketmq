package org.apache.rocketmq.broker.transaction;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 我们知道当执行完本地事务后 返回给消息队列一些状态 rollback  commit unkown 当返回unkown是  rocketMq不会提交消息
 * 会启动这个线程 回查RMQ_SYS_TRANS_HALF_TOPIC主题中的消息，回查消息的事务状态
 * TransactionalMessageCheckService的检测频率默认1分钟，可通过在broker.conf文件中设置transactionCheckInterval的值来改变默认值，单位为毫秒
 */
public class TransactionalMessageCheckService extends ServiceThread {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.TRANSACTION_LOGGER_NAME);

    private BrokerController brokerController;

    private final AtomicBoolean started = new AtomicBoolean(false);

    public TransactionalMessageCheckService(BrokerController brokerController) {
        this.brokerController = brokerController;
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            super.start();
            this.brokerController.getTransactionalMessageService().open();
        }
    }

    @Override
    public void shutdown(boolean interrupt) {
        if (started.compareAndSet(true, false)) {
            super.shutdown(interrupt);
            this.brokerController.getTransactionalMessageService().close();
            this.brokerController.getTransactionalMessageCheckListener().shutDown();
        }
    }

    @Override
    public String getServiceName() {
        return TransactionalMessageCheckService.class.getSimpleName();
    }

    @Override
    public void run() {
        log.info("Start transaction check service thread!");
        long checkInterval = brokerController.getBrokerConfig().getTransactionCheckInterval();
        while (!this.isStopped()) {
            this.waitForRunning(checkInterval);
        }
        log.info("End transaction check service thread!");
    }

    @Override
    protected void onWaitEnd() {
        //从broker配置文件中获取transactionTimeOut参数值，表示事务的过期时间，一个消息的存储时间 + 该值 大于系统当前时间，才对该消息执行事务状态会查
        long timeout = brokerController.getBrokerConfig().getTransactionTimeOut();
        //从broker配置文件中获取transactionCheckMax参数值，表示事务的最大检测次数，如果超过检测次数，消息会默认为丢弃，即rollback消息
        int checkMax = brokerController.getBrokerConfig().getTransactionCheckMax();
        long begin = System.currentTimeMillis();
        log.info("Begin to check prepare message, begin time:{}", begin);
        this.brokerController.getTransactionalMessageService().check(timeout, checkMax, this.brokerController.getTransactionalMessageCheckListener());
        log.info("End to check prepare message, consumed time:{}", System.currentTimeMillis() - begin);
    }

}
