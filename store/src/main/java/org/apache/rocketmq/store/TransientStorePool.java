package org.apache.rocketmq.store;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.apache.rocketmq.store.util.LibC;
import sun.nio.ch.DirectBuffer;

/**
 * 短暂的存储池
 *  rocketMq 创建一个储存池 用来存储MappedByteBuffer
 *  作用
 *  用来临时存储数据 数据 先写入该内存映射中 然后由commit线程定时 将数据从该内存复制到与目的物理文件对应的内存映射中
 *  这里提供一种内存锁定 将当前的堆外内存一直锁定再内存中  避免进程内存交换到磁盘
 */
public class TransientStorePool {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    //availableBuffers 个数可以通过broker配置文件进行配置 默认是5个
    private final int poolSize;
    //每个ByteBuffer的大小 默认为MapedFileSizeCommitLog 表明这个类是为commitlog文件服务的
    private final int fileSize;
    //ByteBuffer 容器 双端队列
    private final Deque<ByteBuffer> availableBuffers;
    private final MessageStoreConfig storeConfig;

    public TransientStorePool(final MessageStoreConfig storeConfig) {
        this.storeConfig = storeConfig;
        this.poolSize = storeConfig.getTransientStorePoolSize();
        this.fileSize = storeConfig.getMapedFileSizeCommitLog();
        this.availableBuffers = new ConcurrentLinkedDeque<>();
    }

    /**
     * It's a heavy init method.
     * 初始化
     * //如源码注释，因为这里需要申请多个堆外ByteBuffer，所以是个
     * //十分heavy的初始化方法
     */
    public void init() {
        //创建poolSize 个堆外内存
        //申请poolSize个ByteBuffer
        for (int i = 0; i < poolSize; i++) {
            //申请直接内存空间
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(fileSize);

            final long address = ((DirectBuffer) byteBuffer).address();
            Pointer pointer = new Pointer(address);
            //todo  这里利用INSTANCE 将该批内存进行锁定
            // 避免被置换到交换区 提高存储性能
            //锁住内存，避免操作系统虚拟内存的换入换出
            LibC.INSTANCE.mlock(pointer, new NativeLong(fileSize));
            //将预分配的ByteBuffer方法队列中
            availableBuffers.offer(byteBuffer);
        }
    }
    //销毁内存池
    public void destroy() {
        //取消对内存的锁定
        for (ByteBuffer byteBuffer : availableBuffers) {
            final long address = ((DirectBuffer) byteBuffer).address();
            Pointer pointer = new Pointer(address);
            LibC.INSTANCE.munlock(pointer, new NativeLong(fileSize));
        }
    }
    //使用完毕之后归还ByteBuffer
    public void returnBuffer(ByteBuffer byteBuffer) {
        //ByteBuffer各下标复位
        byteBuffer.position(0);
        byteBuffer.limit(fileSize);
        //放入队头，等待下次重新被分配
        this.availableBuffers.offerFirst(byteBuffer);
    }
    //从池中获取ByteBuffer
    public ByteBuffer borrowBuffer() {
        //非阻塞弹出队头元素，如果没有启用暂存池，则
        //不会调用init方法，队列中就没有元素，这里返回null
        //其次，如果队列中所有元素都被借用出去，队列也为空
        //此时也会返回null
        ByteBuffer buffer = availableBuffers.pollFirst();
        //如果队列中剩余元素数量小于配置个数的0.4，则写日志提示
        if (availableBuffers.size() < poolSize * 0.4) {
            log.warn("TransientStorePool only remain {} sheets.", availableBuffers.size());
        }
        return buffer;
    }
    //剩下可借出的ByteBuffer数量
    public int remainBufferNumbs() {
        //如果启用了暂存池，则返回队列中元素个数
        if (storeConfig.isTransientStorePoolEnable()) {
            return availableBuffers.size();
        }
        //否则返会Integer.MAX_VALUE
        return Integer.MAX_VALUE;
    }
}
