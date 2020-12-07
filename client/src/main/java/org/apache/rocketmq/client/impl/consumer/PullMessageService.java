package org.apache.rocketmq.client.impl.consumer;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.common.utils.ThreadUtils;

/**
 * 拉取消息服务，不断不断不断从 Broker 拉取消息，并提交消费任务到 ConsumeMessageService
 * 消息消费拉取线程
 * todo  注意 需要明白的是每次都是在pullRequestQueue 这个阻塞队列里面获取请求 然后进行拉取
 *     之前有一个困惑 这个阻塞队列以为只是在客户端负载的时候进行put  我们知道负载的频率是2秒
 *     这样是不是太慢了 其实put的时间不只这一个地方 在每次拉取消息后 也会进行put 拉取队列的操作
 *     当然执行的不是pullRequestQueue.put()  而是executePullRequestImmediately()这个方法
 */
public class PullMessageService extends ServiceThread {
    private final InternalLogger log = ClientLogger.getLog();

    //拉取消息请求队列 这里面放的都是拉取任务
    //两种方式put 提供延迟添加与立即添加方式
    private final LinkedBlockingQueue<PullRequest> pullRequestQueue = new LinkedBlockingQueue<PullRequest>();

    //MQClient对象
    private final MQClientInstance mQClientInstance;
    /**
     * 定时器。用于延迟提交拉取请求  线程池
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "PullMessageServiceScheduledThread");
            }
        });

    public PullMessageService(MQClientInstance mQClientInstance) {
        this.mQClientInstance = mQClientInstance;
    }

    /**
     * 执行延迟拉取消息请求
     * @param pullRequest
     * @param timeDelay
     */
    public void executePullRequestLater(final PullRequest pullRequest, final long timeDelay) {
        if (!isStopped()) {
            this.scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    PullMessageService.this.executePullRequestImmediately(pullRequest);
                }
            }, timeDelay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("PullMessageServiceScheduledThread has shutdown");
        }
    }

    /**
     * 执行立即拉取消息请求
     * @param pullRequest
     */
    public void executePullRequestImmediately(final PullRequest pullRequest) {
        try {
            this.pullRequestQueue.put(pullRequest);
        } catch (InterruptedException e) {
            log.error("executePullRequestImmediately pullRequestQueue.put", e);
        }
    }

    /**
     * 执行延迟任务
     * @param r
     * @param timeDelay
     */
    public void executeTaskLater(final Runnable r, final long timeDelay) {
        if (!isStopped()) {
            this.scheduledExecutorService.schedule(r, timeDelay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("PullMessageServiceScheduledThread has shutdown");
        }
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    /**
     * 拉取消息
     * @param pullRequest
     */
    private void pullMessage(final PullRequest pullRequest) {
        //根据消费组名 从MQClientInstance 中获取MQConsumerInner
        final MQConsumerInner consumer = this.mQClientInstance.selectConsumer(pullRequest.getConsumerGroup());
        if (consumer != null) {
            //强制转换为DefaultMQPushConsumerImpl
            //说明 PullMessageService这个线程只为DefaultMQPushConsumerImpl 服务
            DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
            impl.pullMessage(pullRequest);
        } else {
            log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
        }
    }

    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");

        while (!this.isStopped()) {
            try {
                //获取拉取任务
                //PullRequest 是由 RebalanceService 产生，它根据主题消息队列个数和当前消费组内消费者个数进行负载，
                // 然后产生对应的 PullRequest 对象，再将这些对象放入到 PullMessageService 的 pullRequestQueue队列
                //其实可以看 pullservice的队列一直都为空
                //todo 但是这个需要明白的是在实际代码中，Rebalance模块和PullMessageService没有直接关联，
                //  是通过调用PushConsumer的executePullRequestImmediately接口来初始化消息拉取任务列表的
                PullRequest pullRequest = this.pullRequestQueue.take();
                this.pullMessage(pullRequest);
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                log.error("Pull Message Service Run Method exception", e);
            }
        }

        log.info(this.getServiceName() + " service end");
    }

    @Override
    public void shutdown(boolean interrupt) {
        super.shutdown(interrupt);
        ThreadUtils.shutdownGracefully(this.scheduledExecutorService, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getServiceName() {
        return PullMessageService.class.getSimpleName();
    }

}
