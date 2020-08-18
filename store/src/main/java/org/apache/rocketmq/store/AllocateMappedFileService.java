package org.apache.rocketmq.store;

import java.io.File;
import java.io.IOException;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.config.BrokerRole;

/**
 * Create MappedFile in advance
 */
public class AllocateMappedFileService extends ServiceThread {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);
    private static int waitTimeOut = 1000 * 5;
    private ConcurrentMap<String, AllocateRequest> requestTable =
        new ConcurrentHashMap<String, AllocateRequest>();
    private PriorityBlockingQueue<AllocateRequest> requestQueue =
        new PriorityBlockingQueue<AllocateRequest>();
    private volatile boolean hasException = false;
    private DefaultMessageStore messageStore;

    public AllocateMappedFileService(DefaultMessageStore messageStore) {
        this.messageStore = messageStore;
    }

    /**
     * MappedFile预分配
     * @param nextFilePath
     * @param nextNextFilePath
     * @param fileSize
     * @return
     */
    public MappedFile putRequestAndReturnMappedFile(String nextFilePath, String nextNextFilePath, int fileSize) {
        //每次最多只能提交两个分配请求
        int canSubmitRequests = 2;
        if (this.messageStore.getMessageStoreConfig().isTransientStorePoolEnable()) {
            if (this.messageStore.getMessageStoreConfig().isFastFailIfNoBufferInStorePool()
                && BrokerRole.SLAVE != this.messageStore.getMessageStoreConfig().getBrokerRole()) { //if broker is slave, don't fast fail even no buffer in pool
                //因为预分配使用了暂存池，所以这里重新计算可提交请求书为
                //暂存池剩余ByteBuffer数量-已提交的预分配请求个数
                canSubmitRequests = this.messageStore.getTransientStorePool().remainBufferNumbs() - this.requestQueue.size();
            }
        }
        //创建分配任务，这个任务表示的是当前实际需要的且要等待期返回的
        //MappedFile分配请求
        AllocateRequest nextReq = new AllocateRequest(nextFilePath, fileSize);
        //AllocateRequest自定义了equal/hashCode方法，这里不再列出其源码
        //如果该filePath和fileSize已经在requestTable中，则表示
        //此次所需分配的MappedFile已经在上次分配时被预分配了
        //同时要注意的是AllocateRequest也实现了Comparable接口
        //放入优先队列中可自动排序，文件偏移小的会先被分配
        boolean nextPutOK = this.requestTable.putIfAbsent(nextFilePath, nextReq) == null;
        //如果nextPutOK为true，表示该分片请求被成功放入requestTable中，
        //也就表示该请求原先没有预分配过
        if (nextPutOK) {
            //此时检查可提交请求个数，如果小于等于0，则表示不可提交
            //预分配失败，返回null，通过上面介绍过的使用new MappedFile
            //创建MappedFile
            if (canSubmitRequests <= 0) {
                log.warn("[NOTIFYME]TransientStorePool is not enough, so create mapped file error, " +
                    "RequestQueueSize : {}, StorePoolSize: {}", this.requestQueue.size(), this.messageStore.getTransientStorePool().remainBufferNumbs());
                //移除刚放入requestTable中的请求
                this.requestTable.remove(nextFilePath);
                return null;
            }
            //如果可提交请求个数符合要求，则将分配请求加入队列中排序
            boolean offerOK = this.requestQueue.offer(nextReq);
            if (!offerOK) {
                log.warn("never expected here, add a request to preallocate queue failed");
            }
            //已经提交了一个请求，所以可提交请求数减一
            canSubmitRequests--;
        }
        //预分配此次所需的下一个MappedFile
        //过程和上面类似，不再介绍
        AllocateRequest nextNextReq = new AllocateRequest(nextNextFilePath, fileSize);
        boolean nextNextPutOK = this.requestTable.putIfAbsent(nextNextFilePath, nextNextReq) == null;
        if (nextNextPutOK) {
            if (canSubmitRequests <= 0) {
                log.warn("[NOTIFYME]TransientStorePool is not enough, so skip preallocate mapped file, " +
                    "RequestQueueSize : {}, StorePoolSize: {}", this.requestQueue.size(), this.messageStore.getTransientStorePool().remainBufferNumbs());
                this.requestTable.remove(nextNextFilePath);
            } else {
                boolean offerOK = this.requestQueue.offer(nextNextReq);
                if (!offerOK) {
                    log.warn("never expected here, add a request to preallocate queue failed");
                }
            }
        }

        if (hasException) {
            log.warn(this.getServiceName() + " service has exception. so return null");
            return null;
        }
        //获取刚提交的或者以前预分配的此次实际需要创建的MappedFile的创建
        //请求对象
        AllocateRequest result = this.requestTable.get(nextFilePath);
        try {
            if (result != null) {
                //AllocateRequest使用CountDownLatch进行阻塞
                boolean waitOK = result.getCountDownLatch().await(waitTimeOut, TimeUnit.MILLISECONDS);
                if (!waitOK) {
                    log.warn("create mmap timeout " + result.getFilePath() + " " + result.getFileSize());
                    return null;
                } else {
                    //如果没有超时，MappedFile创建完成，则返回创建的
                    //MappedFile
                    this.requestTable.remove(nextFilePath);
                    return result.getMappedFile();
                }
            } else {
                log.error("find preallocate mmap failed, this never happen");
            }
        } catch (InterruptedException e) {
            log.warn(this.getServiceName() + " service has exception. ", e);
        }

        return null;
    }

    @Override
    public String getServiceName() {
        return AllocateMappedFileService.class.getSimpleName();
    }

    public void shutdown() {
        this.stopped = true;
        this.thread.interrupt();

        try {
            this.thread.join(this.getJointime());
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
        }

        for (AllocateRequest req : this.requestTable.values()) {
            if (req.mappedFile != null) {
                log.info("delete pre allocated maped file, {}", req.mappedFile.getFileName());
                req.mappedFile.destroy(1000);
            }
        }
    }

    public void run() {
        log.info(this.getServiceName() + " service started");
        //调用mmapOperation进行任务处理
        while (!this.isStopped() && this.mmapOperation()) {

        }
        log.info(this.getServiceName() + " service end");
    }

    /**
     * Only interrupted by the external thread, will return false
     *  //方法注释说明，只有被其他线程中断会返回false，其他情况都会返回true
     */
    private boolean mmapOperation() {
        boolean isSuccess = false;
        AllocateRequest req = null;
        try {
            // 从 requestQueue 阻塞队列中获取 AllocateRequest  任务。
            //从队列获取创建请求，这里的队列为优先队列，里面的
            //AllocateRequest分配请求已经根据文件偏移进行过排序
            req = this.requestQueue.take();
            //同时从requestTable获取创建请求，如果获取失败，表示已经超时
            //被移除，此时记录日志，此任务已经不需要处理
            AllocateRequest expectedRequest = this.requestTable.get(req.getFilePath());
            if (null == expectedRequest) {
                log.warn("this mmap request expired, maybe cause timeout " + req.getFilePath() + " "
                    + req.getFileSize());
                return true;
            }
            if (expectedRequest != req) {
                log.warn("never expected here,  maybe cause timeout " + req.getFilePath() + " "
                    + req.getFileSize() + ", req:" + req + ", expectedRequest:" + expectedRequest);
                return true;
            }
            //如果取得的分配任务尚没有持有MappedFile对象，则进行分配
            if (req.getMappedFile() == null) {
                long beginTime = System.currentTimeMillis();
                //如果启用了暂存池TransientStorePool，则进行存储池分配
                MappedFile mappedFile;

                //mappedfile文件初始化 根据isTransientStorePoolEnable 分两种情况
                // 判断是否开启 isTransientStorePoolEnable ，如果开启则使用直接内存进行写入数据，最后从直接内存中 commit 到 FileChannel 中
                if (messageStore.getMessageStoreConfig().isTransientStorePoolEnable()) {
                    try {
                        //todo isTransientStorePoolEnable 为true 表示内容先存储在堆外内存
                        // 然后通过commit线程将数据提交到内存映射Buffer中  再通过Flush线程将内存映射Buffer中的数据
                        // 持久化到磁盘中
                        //这里有个ServiceLoader扩展，用户可通过
                        //ServiceLoader使用自定义的MappedFile实现
                        mappedFile = ServiceLoader.load(MappedFile.class).iterator().next();
                        mappedFile.init(req.getFilePath(), req.getFileSize(), messageStore.getTransientStorePool());
                    } catch (RuntimeException e) {
                        //如果用户没有通过ServiceLoader使用
                        //自定义的MappedFile，则使用RocketMQ
                        //默认的MappedFile实现
                        log.warn("Use default implementation.");
                        mappedFile = new MappedFile(req.getFilePath(), req.getFileSize(), messageStore.getTransientStorePool());
                    }
                } else {
                    // 使用 mmap 方式创建 MappedFile
                    //如果没有启用暂存池，则直接使用new MappedFile创建
                    //没有暂存池的初始化
                    mappedFile = new MappedFile(req.getFilePath(), req.getFileSize());
                }

                long eclipseTime = UtilAll.computeEclipseTimeMilliseconds(beginTime);
                if (eclipseTime > 10) {
                    int queueSize = this.requestQueue.size();
                    log.warn("create mappedFile spent time(ms) " + eclipseTime + " queue size " + queueSize
                        + " " + req.getFilePath() + " " + req.getFileSize());
                }
                //如果配置了MappedFile预热，则进行MappedFile预热
                // pre write mappedFile
                // pre write mappedFile
                // 判断 mappedFile 大小，只有 CommitLog 才进行文件预热
                // 预写入数据。按照系统的 pagesize 进行每个pagesize 写入一个字节数据。
                //为了把mmap 方式映射的文件都加载到内存中。
                if (mappedFile.getFileSize() >= this.messageStore.getMessageStoreConfig()
                    .getMapedFileSizeCommitLog()
                    &&
                    this.messageStore.getMessageStoreConfig().isWarmMapedFileEnable()) {
                    //具体的预热方法不具体介绍，简单的说就是通过加载分配
                    //空间的每个内存页进行写入，使分配的ByteBuffer
                    //加载到内存中，并和暂存池一样，避免其被操作系统
                    //换出
                    mappedFile.warmMappedFile(this.messageStore.getMessageStoreConfig().getFlushDiskType(),
                        this.messageStore.getMessageStoreConfig().getFlushLeastPagesWhenWarmMapedFile());
                }
                //分配完成的MappedFile放入请求中
                req.setMappedFile(mappedFile);
                this.hasException = false;
                isSuccess = true;
            }
        } catch (InterruptedException e) {
            log.warn(this.getServiceName() + " interrupted, possibly by shutdown.");
            this.hasException = true;
            return false;
        } catch (IOException e) {
            log.warn(this.getServiceName() + " service has exception. ", e);
            this.hasException = true;
            //发生异常则将请求重新放回队列，下次重新尝试分配
            if (null != req) {
                requestQueue.offer(req);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }
        } finally {
            if (req != null && isSuccess)
                //创建完毕，则让阻塞的获取方法返回
            {
                req.getCountDownLatch().countDown();
            }
        }
        return true;
    }

    static class AllocateRequest implements Comparable<AllocateRequest> {
        // Full file path
        private String filePath;
        private int fileSize;
        private CountDownLatch countDownLatch = new CountDownLatch(1);
        private volatile MappedFile mappedFile = null;

        public AllocateRequest(String filePath, int fileSize) {
            this.filePath = filePath;
            this.fileSize = fileSize;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getFileSize() {
            return fileSize;
        }

        public void setFileSize(int fileSize) {
            this.fileSize = fileSize;
        }

        public CountDownLatch getCountDownLatch() {
            return countDownLatch;
        }

        public void setCountDownLatch(CountDownLatch countDownLatch) {
            this.countDownLatch = countDownLatch;
        }

        public MappedFile getMappedFile() {
            return mappedFile;
        }

        public void setMappedFile(MappedFile mappedFile) {
            this.mappedFile = mappedFile;
        }

        public int compareTo(AllocateRequest other) {
            if (this.fileSize < other.fileSize)
                return 1;
            else if (this.fileSize > other.fileSize) {
                return -1;
            } else {
                int mIndex = this.filePath.lastIndexOf(File.separator);
                long mName = Long.parseLong(this.filePath.substring(mIndex + 1));
                int oIndex = other.filePath.lastIndexOf(File.separator);
                long oName = Long.parseLong(other.filePath.substring(oIndex + 1));
                if (mName < oName) {
                    return -1;
                } else if (mName > oName) {
                    return 1;
                } else {
                    return 0;
                }
            }
            // return this.fileSize < other.fileSize ? 1 : this.fileSize >
            // other.fileSize ? -1 : 0;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((filePath == null) ? 0 : filePath.hashCode());
            result = prime * result + fileSize;
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
            AllocateRequest other = (AllocateRequest) obj;
            if (filePath == null) {
                if (other.filePath != null)
                    return false;
            } else if (!filePath.equals(other.filePath))
                return false;
            if (fileSize != other.fileSize)
                return false;
            return true;
        }
    }
}
