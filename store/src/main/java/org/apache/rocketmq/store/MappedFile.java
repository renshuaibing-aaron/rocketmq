package org.apache.rocketmq.store;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageExtBatch;
import org.apache.rocketmq.store.config.FlushDiskType;
import org.apache.rocketmq.store.util.LibC;
import sun.nio.ch.DirectBuffer;

/**
 * MappedFile#落盘
 * 方式一	写入内存字节缓冲区(writeBuffer)	从内存字节缓冲区(write buffer)提交(commit)到文件通道(fileChannel)	文件通道(fileChannel)flush
 * 方式二		写入映射文件字节缓冲区(mappedByteBuffer)	映射文件字节缓冲区(mappedByteBuffer)flush
 */
public class MappedFile extends ReferenceResource {
    /**
     * 操作系统每页的大小默认4K
     */
    public static final int OS_PAGE_SIZE = 1024 * 4;
    protected static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    /**
     * 当前JVM中MappedFile虚拟内存
     */
    private static final AtomicLong TOTAL_MAPPED_VIRTUAL_MEMORY = new AtomicLong(0);

    /**
     * 当前JVM实例中MappedFile对象个数
     */
    private static final AtomicInteger TOTAL_MAPPED_FILES = new AtomicInteger(0);
    /**
     * 当前文件的写指针 从0开始(内存映射文件的写指针)
     */
    protected final AtomicInteger wrotePosition = new AtomicInteger(0);

    /**
     * 当前文件的提交指针
     * 如果开启transientStorePoolEnable 则数据会存储在TransientStorePool 中 然后提交到内存映射文件ByteBuffer
     * 再刷写到磁盘
     */
    //ADD BY ChenYang
    protected final AtomicInteger committedPosition = new AtomicInteger(0);

    /**
     * 刷写到磁盘指针 该指针之前的数据持久化到磁盘中
     */
    private final AtomicInteger flushedPosition = new AtomicInteger(0);
    /**
     * 文件大小
     */
    protected int fileSize;
    /**
     * 文件通道
     */
    protected FileChannel fileChannel;
    /**
     * Message will put to here first, and then reput to FileChannel if writeBuffer is not null.
     * 堆内存ByteBuffer 如果不为空 数据首先存储在该Buffer中  然后提交到MappedFile对应的内存映射文件Buffer
     *  transientStorePoolEnable 为true时不为空
     */

    //如果启用了TransientStorePool，则writeBuffer为从暂时存储池中借用
    //的buffer，此时存储对象（比如消息等）会先写入该writeBuffer，然后
    //commit到fileChannel，最后对fileChannel进行flush刷盘
    protected ByteBuffer writeBuffer = null;

    //一个内存ByteBuffer池实现，如果如果启用了TransientStorePool则不为空
    protected TransientStorePool transientStorePool = null;
    //文件名称 起始就是文件的起始位置
    private String fileName;


    //该文件的初始偏移量  注意这个不一定是0
    //该文件中内容相对于整个文件的偏移，其实和文件名相同
    private long fileFromOffset;
    //物理文件
    private File file;

    //物理文件对应的内存映射
    //通过fileChannel.map得到的可读写的内存映射buffer，如果没有启用
    //TransientStorePool则写数据时会写到该缓冲中，刷盘时直接调用该
    //映射buffer的force函数，而不需要进行commit操作
    private MappedByteBuffer mappedByteBuffer;


    //文件最后一次内存写入时间
    private volatile long storeTimestamp = 0;
    //是否是文件队列中的第一个文件
    private boolean firstCreateInQueue = false;

    public MappedFile() {
    }

    public MappedFile(final String fileName, final int fileSize) throws IOException {
        init(fileName, fileSize);
    }

    public MappedFile(final String fileName, final int fileSize,
        final TransientStorePool transientStorePool) throws IOException {
        init(fileName, fileSize, transientStorePool);
    }

    public static void ensureDirOK(final String dirName) {
        if (dirName != null) {
            File f = new File(dirName);
            if (!f.exists()) {
                boolean result = f.mkdirs();
                log.info(dirName + " mkdir " + (result ? "OK" : "Failed"));
            }
        }
    }

    public static void clean(final ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect() || buffer.capacity() == 0)
            return;
        invoke(invoke(viewed(buffer), "cleaner"), "clean");
    }

    private static Object invoke(final Object target, final String methodName, final Class<?>... args) {
        return AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    Method method = method(target, methodName, args);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static Method method(Object target, String methodName, Class<?>[] args)
        throws NoSuchMethodException {
        try {
            return target.getClass().getMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            return target.getClass().getDeclaredMethod(methodName, args);
        }
    }

    private static ByteBuffer viewed(ByteBuffer buffer) {
        String methodName = "viewedBuffer";

        Method[] methods = buffer.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals("attachment")) {
                methodName = "attachment";
                break;
            }
        }

        ByteBuffer viewedBuffer = (ByteBuffer) invoke(buffer, methodName);
        if (viewedBuffer == null)
            return buffer;
        else
            return viewed(viewedBuffer);
    }

    public static int getTotalMappedFiles() {
        return TOTAL_MAPPED_FILES.get();
    }

    public static long getTotalMappedVirtualMemory() {
        return TOTAL_MAPPED_VIRTUAL_MEMORY.get();
    }

    /**
     *
     * @param fileName
     * @param fileSize
     * @param transientStorePool
     * @throws IOException
     */
    public void init(final String fileName, final int fileSize,  final TransientStorePool transientStorePool) throws IOException {
        init(fileName, fileSize);

        //如果isTransientStorePoolEnable为true
        //则初始化writeBuffer 和transientStorePool
        //不同就是这里的writeBuffer会被赋值，后续写入操作会优先
        //写入writeBuffer中
        this.writeBuffer = transientStorePool.borrowBuffer();
        //记录transientStorePool主要为了释放时归还借用的ByteBuffer
        this.transientStorePool = transientStorePool;
    }

    /**
     * 文件初始化
     * @param fileName
     * @param fileSize
     * @throws IOException
     */
    private void init(final String fileName, final int fileSize) throws IOException {
        //记录文件名、大小等信息
        this.fileName = fileName;
        this.fileSize = fileSize;
        //新建File对象
        this.file = new File(fileName);
        //初始化fileFromOffset为文件名 也就是文件名代表文件的起始偏移量
        this.fileFromOffset = Long.parseLong(this.file.getName());
        boolean ok = false;
        //保证路径都存在
        ensureDirOK(this.file.getParent());

        try {
           //通过RandomAccessFile 创建文件通道  并且将文件内容使用NIO内存映射Buffer将文件映射到内存里面
            //获取fileChannel对象
            this.fileChannel = new RandomAccessFile(this.file, "rw").getChannel();
            //获取内存映射缓冲
            this.mappedByteBuffer = this.fileChannel.map(MapMode.READ_WRITE, 0, fileSize);
            //统计计数器更新
            TOTAL_MAPPED_VIRTUAL_MEMORY.addAndGet(fileSize);
            TOTAL_MAPPED_FILES.incrementAndGet();
            ok = true;
        } catch (FileNotFoundException e) {
            log.error("create file channel " + this.fileName + " Failed. ", e);
            throw e;
        } catch (IOException e) {
            log.error("map file " + this.fileName + " Failed. ", e);
            throw e;
        } finally {
            if (!ok && this.fileChannel != null) {
                this.fileChannel.close();
            }
        }
    }

    public long getLastModifiedTimestamp() {
        return this.file.lastModified();
    }

    public int getFileSize() {
        return fileSize;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public AppendMessageResult appendMessage(final MessageExtBrokerInner msg, final AppendMessageCallback cb) {
        return appendMessagesInner(msg, cb);
    }

    public AppendMessageResult appendMessages(final MessageExtBatch messageExtBatch, final AppendMessageCallback cb) {
        return appendMessagesInner(messageExtBatch, cb);
    }

    /**
     * 插入消息到 MappedFile，并返回插入结果
     * @param messageExt
     * @param cb
     * @return
     */
    public AppendMessageResult appendMessagesInner(final MessageExt messageExt, final AppendMessageCallback cb) {
        assert messageExt != null;
        assert cb != null;
        //1、获取当前的write position
        int currentPos = this.wrotePosition.get();

        //这个获取写指针 是不是就是文件的名字
        if (currentPos < this.fileSize) {
            //获取需要写入的字节缓冲区
            // TODO: 2020/4/18
            //   1 从FileChannel获取直接内存映射，收到消息后，将数据写入到这块内存中，内存和物理文件的数据交互由操作系统负责
            //   2  CommitLog启动的时候初始化一块内存池(通过ByteBuffer申请的堆外内存)，消息数据首先写入内存池中，
            //     然后后台有个线程定时将内存池中的数据commit到FileChannel中。这种方式只有MessageStore是ASYNC模式时才能开启。
            //     代码中if判断writeBuffer不为空的情况就是使用的这种写入方式。
            //最终回调的Callback类将数据写入buffer中，消息的序列化也是在callback里面完成的
            //我们重点看这里，如果writeBuffer不为空，则优先
            //写入writeBuffer，否则写入mappedByteBuffer，
            //通过前面的介绍可以知道，如果启用了暂存池
            //TransientStorePool则writeBuffer会被初始化
            //否则writeBuffer为空
            //slice方法返回一个新的byteBuffer，但是这里新的
            //byteBuffer和原先的ByteBuffer共用一个存储空间
            //只是自己维护的相关下标
            ByteBuffer byteBuffer = writeBuffer != null ? writeBuffer.slice() : this.mappedByteBuffer.slice();


            //这里的共享内存 并且当前的写入指针是什么？
            //设置写入 position，执行写入，更新 wrotePosition(当前写入位置，下次开始写入开始位置)
            //下面的写入我们不展开介绍，无非对消息进行编码
            //然后将编码后的数据写入这里得到的byteBuffer等待刷盘
            byteBuffer.position(currentPos);

            AppendMessageResult result = null;
            if (messageExt instanceof MessageExtBrokerInner) {
                //3、写单条消息到byteBuffer
                result = cb.doAppend(this.getFileFromOffset(), byteBuffer, this.fileSize - currentPos, (MessageExtBrokerInner) messageExt);

            } else if (messageExt instanceof MessageExtBatch) {
                //3、批量消息到byteBuffer
                result = cb.doAppend(this.getFileFromOffset(), byteBuffer, this.fileSize - currentPos, (MessageExtBatch) messageExt);
            } else {
                return new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR);
            }
            //4、更新write position，到最新值
            this.wrotePosition.addAndGet(result.getWroteBytes());
            this.storeTimestamp = result.getStoreTimestamp();
            return result;
        }
        log.error("MappedFile.appendMessage return null, wrotePosition: {} fileSize: {}", currentPos, this.fileSize);
        return new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR);
    }

    public long getFileFromOffset() {
        return this.fileFromOffset;
    }

    public boolean appendMessage(final byte[] data) {
        int currentPos = this.wrotePosition.get();

        if ((currentPos + data.length) <= this.fileSize) {
            try {
                this.fileChannel.position(currentPos);
                this.fileChannel.write(ByteBuffer.wrap(data));
            } catch (Throwable e) {
                log.error("Error occurred when append message to mappedFile.", e);
            }
            this.wrotePosition.addAndGet(data.length);
            return true;
        }

        return false;
    }

    /**
     * Content of data from offset to offset + length will be wrote to file.
     *
     * @param offset The offset of the subarray to be used.
     * @param length The length of the subarray to be used.
     */
    public boolean appendMessage(final byte[] data, final int offset, final int length) {
        int currentPos = this.wrotePosition.get();

        if ((currentPos + length) <= this.fileSize) {
            try {
                this.fileChannel.position(currentPos);
                this.fileChannel.write(ByteBuffer.wrap(data, offset, length));
            } catch (Throwable e) {
                log.error("Error occurred when append message to mappedFile.", e);
            }
            this.wrotePosition.addAndGet(length);
            return true;
        }

        return false;
    }


    /*
    * MappedFile刷盘操作根据具体配置分为同步和异步刷盘两种方式，这里不管同步异步，其操作类似，
    都是通过MappedFile.commit和MappedFile.flush,如果启用了暂存池TransientStorePool则会先调用MappedFile.commit
    * 把writeBuffer中的数据写入fileChannel中，然后再调用MappedFile.flush；
    * 而MappedFile.flush通过fileChannel.force或者mappedByteBuffer.force()进行实际的刷盘动作，具体方法不再展开介绍
     */

    /**
     * 刷盘
     * 将内存里面的数据刷写到磁盘 持久化处理
     *   直接调用mappedByteBuffer或者fileChannel的force方法将内存的数据持久化到磁盘
     * @return The current flushed position
     */
    public int flush(final int flushLeastPages) {
        if (this.isAbleToFlush(flushLeastPages)) {
            if (this.hold()) {
                int value = getReadPosition();

                try {
                    //We only append data to fileChannel or mappedByteBuffer, never both.
                    if (writeBuffer != null || this.fileChannel.position() != 0) {

                        this.fileChannel.force(false);
                    } else {

                        this.mappedByteBuffer.force();
                    }
                } catch (Throwable e) {
                    log.error("Error occurred when force data to disk.", e);
                }
                //设置flushedPosition 为MappedbyteBuffer 的写指针
                this.flushedPosition.set(value);
                this.release();
            } else {
                log.warn("in flush, hold failed, flush offset = " + this.flushedPosition.get());
                this.flushedPosition.set(getReadPosition());
            }
        }
        return this.getFlushedPosition();
    }

    /**
     * 内存映射文件的提交
     * @param commitLeastPages  本次提交的最小页数
     * @return
     */
    public int commit(final int commitLeastPages) {
        //如果writeBuffer 是空直接返回wrotePosition 无须执行commit操作 表明commit的主题是writeBuffer
        if (writeBuffer == null) {
            //no need to commit data to file channel, so just regard wrotePosition as committedPosition.
            return this.wrotePosition.get();
        }

        //如果待提交的数据不满commitLeastPages 则不执行commit  待下次提交
        if (this.isAbleToCommit(commitLeastPages)) {
            if (this.hold()) {
                commit0(commitLeastPages);
                this.release();
            } else {
                log.warn("in commit, hold failed, commit offset = " + this.committedPosition.get());
            }
        }

        // All dirty data has been committed to FileChannel.
        if (writeBuffer != null && this.transientStorePool != null && this.fileSize == this.committedPosition.get()) {
            this.transientStorePool.returnBuffer(writeBuffer);
            this.writeBuffer = null;
        }

        return this.committedPosition.get();
    }

    /**
     * 具体commit实现，将writeBuffer写入fileChannel
     * todo  这里可以看出ByteBuffer的使用技巧 slice()方法创建一个共享缓冲区 与原先的ByteBuffer共享内存
     *  但是维护一套独立的指针(position mark limit)
     * @param commitLeastPages   commit最小页数。用不上该参数
     */
    protected void commit0(final int commitLeastPages) {
        int writePos = this.wrotePosition.get();
        int lastCommittedPosition = this.committedPosition.get();

        if (writePos - this.committedPosition.get() > 0) {
            try {
                // 设置需要写入的byteBuffer
                //首先创建writeBuffer的共享缓冲区
                ByteBuffer byteBuffer = writeBuffer.slice();
                //将新创建的position 回退到上一次提交的位置lastCommittedPosition
                byteBuffer.position(lastCommittedPosition);
                //设置limit为writePos 当前最大有效数据指针
                byteBuffer.limit(writePos);
                // 写入fileChannel
                //然后把CommittedPosition和writePos的数据复制写入到fileChannel 中
                this.fileChannel.position(lastCommittedPosition);
                this.fileChannel.write(byteBuffer);
                // 设置committedPosition 更新writePos指针
                this.committedPosition.set(writePos);
            } catch (Throwable e) {
                log.error("Error occurred when commit data to FileChannel.", e);
            }
        }
    }

    /**
     * 是否能够flush。满足如下条件任意条件：
     * 1. 映射文件已经写满
     * 2. flushLeastPages > 0 && 未flush部分超过flushLeastPages
     * 3. flushLeastPages = 0 && 有新写入部分
     * @param flushLeastPages  flush最小分页
     * @return  flush最小分页
     */
    private boolean isAbleToFlush(final int flushLeastPages) {
        int flush = this.flushedPosition.get();
        int write = getReadPosition();

        if (this.isFull()) {
            return true;
        }

        if (flushLeastPages > 0) {
            return ((write / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE)) >= flushLeastPages;
        }

        return write > flush;
    }

    /**
     * 是否能够commit。满足如下条件任意条件：
     * 1. 映射文件已经写满 返回true
     * 2. commitLeastPages > 0 && 未commit部分超过commitLeastPages
     * 3. commitLeastPages = 0 && 有新写入部分
     * @param commitLeastPages  commit最小分页
     * @return 是否能够写入
     */
    protected boolean isAbleToCommit(final int commitLeastPages) {
        int flush = this.committedPosition.get();
        int write = this.wrotePosition.get();

        //文件已满 返回true
        if (this.isFull()) {
            return true;
        }
         //commitLeastPages 大于0
        if (commitLeastPages > 0) {
            //比较wrotePosition (当前writeBuffer的写指针)与上一次提交指针（committedPosition）的差值除以OS_PAGE_SIZE
            //得到脏页的数量
            return ((write / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE)) >= commitLeastPages;
        }
          //这里表示commitLeastPages 小于0 表示只要存在脏页就提交
        return write > flush;
    }

    public int getFlushedPosition() {
        return flushedPosition.get();
    }

    public void setFlushedPosition(int pos) {
        this.flushedPosition.set(pos);
    }

    public boolean isFull() {
        return this.fileSize == this.wrotePosition.get();
    }

    public SelectMappedBufferResult selectMappedBuffer(int pos, int size) {
        int readPosition = getReadPosition();
        if ((pos + size) <= readPosition) {

            if (this.hold()) {
                ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
                byteBuffer.position(pos);
                ByteBuffer byteBufferNew = byteBuffer.slice();
                byteBufferNew.limit(size);
                return new SelectMappedBufferResult(this.fileFromOffset + pos, byteBufferNew, size, this);
            } else {
                log.warn("matched, but hold failed, request pos: " + pos + ", fileFromOffset: "
                    + this.fileFromOffset);
            }
        } else {
            log.warn("selectMappedBuffer request pos invalid, request pos: " + pos + ", size: " + size
                + ", fileFromOffset: " + this.fileFromOffset);
        }

        return null;
    }

    /**
     * 查找pos到当前最大可读之间的数据
     * @param pos
     * @return
     */
    public SelectMappedBufferResult selectMappedBuffer(int pos) {
        int readPosition = getReadPosition();
        if (pos < readPosition && pos >= 0) {
            if (this.hold()) {
                //由于在整个写入期间都未曾改变MappedByte Buffer的指针
                //所以mappedByteBuffer.slice() 这个方法返回的是共享缓冲区间为整个MappedFile
                ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
                //设置byteBuffer.position(pos)
                byteBuffer.position(pos);
                //读取字节长度为当前可读字节长度  最终返回的ByteBuffer的limmit(可读最大长度size)
                int size = readPosition - pos;
                ByteBuffer byteBufferNew = byteBuffer.slice();
                byteBufferNew.limit(size);
                return new SelectMappedBufferResult(this.fileFromOffset + pos, byteBufferNew, size, this);
            }
        }

        return null;
    }

    @Override
    public boolean cleanup(final long currentRef) {

        //isAvailable 表示MappedFile当前可用无须清理
        if (this.isAvailable()) {
            log.error("this file[REF:" + currentRef + "] " + this.fileName
                + " have not shutdown, stop unmapping.");
            return false;
        }

        //如果资源已经被清除 返回true
        if (this.isCleanupOver()) {
            log.error("this file[REF:" + currentRef + "] " + this.fileName
                + " have cleanup, do not do it again.");
            return true;
        }
        //如果是堆外内存 调用堆外内存的cleanup方法
        clean(this.mappedByteBuffer);
        //维护MappedFile类变量的TOTAL_MAPPED_VIRTUAL_MEMORY  TOTAL_MAPPED_FILES
        TOTAL_MAPPED_VIRTUAL_MEMORY.addAndGet(this.fileSize * (-1));
        TOTAL_MAPPED_FILES.decrementAndGet();
        log.info("unmap file[REF:" + currentRef + "] " + this.fileName + " OK");
        //返回true
        return true;
    }

    /**
     *
     * @param intervalForcibly  拒绝被销毁的最大存活时间
     * @return
     */
    public boolean destroy(final long intervalForcibly) {
        this.shutdown(intervalForcibly);

        if (this.isCleanupOver()) {
            try {
                this.fileChannel.close();
                log.info("close file channel " + this.fileName + " OK");

                long beginTime = System.currentTimeMillis();
                boolean result = this.file.delete();
                log.info("delete file[REF:" + this.getRefCount() + "] " + this.fileName
                    + (result ? " OK, " : " Failed, ") + "W:" + this.getWrotePosition() + " M:"
                    + this.getFlushedPosition() + ", "
                    + UtilAll.computeEclipseTimeMilliseconds(beginTime));
            } catch (Exception e) {
                log.warn("close file channel " + this.fileName + " Failed. ", e);
            }

            return true;
        } else {
            log.warn("destroy mapped file[REF:" + this.getRefCount() + "] " + this.fileName
                + " Failed. cleanupOver: " + this.cleanupOver);
        }

        return false;
    }

    public int getWrotePosition() {
        return wrotePosition.get();
    }

    public void setWrotePosition(int pos) {
        this.wrotePosition.set(pos);
    }

    /**
     * @return The max position which have valid data
     * 获取MappedFile最大读指针
     */
    public int getReadPosition() {
        //这里看出获取当前文件的最大可读指针 如果writebuffer是空 则直接返回当前的写指针 数据是直接进入到MappedByteBuffer wrotePosition 代表MappedByteBuffer的指针
        //不为空返回上一次提交的指针  因为上一次提交的数据就是进入到MappedByteBuffer的数据
        //在Mappedfile设计中只有提交的数据（写入到MappedByteBuffer或者fileChannel的数据才是安全的数据）
        //
        return this.writeBuffer == null ? this.wrotePosition.get() : this.committedPosition.get();
    }

    public void setCommittedPosition(int pos) {
        this.committedPosition.set(pos);
    }

    /**
     * 这里 MappedFile 已经创建，对应的 Buffer 为 mappedByteBuffer。
     * mappedByteBuffer 已经通过 mmap 映射，此时操作系统中只是记录了该文件和该 Buffer 的映射关系，
     * 而没有映射到物理内存中。这里就通过对该 MappedFile 的每个 Page Cache 进行写入一个字节，通过读写操作把 mmap 映射全部加载到物理内存中。
     *
     * @param type
     * @param pages
     */
    public void warmMappedFile(FlushDiskType type, int pages) {
        long beginTime = System.currentTimeMillis();
        ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
        int flush = 0;
        long time = System.currentTimeMillis();
        for (int i = 0, j = 0; i < this.fileSize; i += MappedFile.OS_PAGE_SIZE, j++) {
            byteBuffer.put(i, (byte) 0);
            // force flush when flush disk type is sync
            // 如果是同步写盘操作，则进行强行刷盘操作
            if (type == FlushDiskType.SYNC_FLUSH) {
                if ((i / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE) >= pages) {
                    flush = i;
                    mappedByteBuffer.force();
                }
            }

            // prevent gc
            // prevent gc  （有什么用？）
            if (j % 1000 == 0) {
                log.info("j={}, costTime={}", j, System.currentTimeMillis() - time);
                time = System.currentTimeMillis();
                try {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                }
            }
        }

        // force flush when prepare load finished
        // 把剩余的数据强制刷新到磁盘中
        if (type == FlushDiskType.SYNC_FLUSH) {
            log.info("mapped file warm-up done, force to disk, mappedFile={}, costTime={}",
                this.getFileName(), System.currentTimeMillis() - beginTime);
            mappedByteBuffer.force();
        }
        log.info("mapped file warm-up done. mappedFile={}, costTime={}", this.getFileName(),
            System.currentTimeMillis() - beginTime);

        this.mlock();
    }

    public String getFileName() {
        return fileName;
    }

    public MappedByteBuffer getMappedByteBuffer() {
        return mappedByteBuffer;
    }

    public ByteBuffer sliceByteBuffer() {
        return this.mappedByteBuffer.slice();
    }

    public long getStoreTimestamp() {
        return storeTimestamp;
    }

    public boolean isFirstCreateInQueue() {
        return firstCreateInQueue;
    }

    public void setFirstCreateInQueue(boolean firstCreateInQueue) {
        this.firstCreateInQueue = firstCreateInQueue;
    }

    /**
     * 该方法主要是实现文件预热后，防止把预热过的文件被操作系统调到swap空间中。当程序在次读取交换出去的数据的时候会产生缺页异常
     */
    public void mlock() {
        final long beginTime = System.currentTimeMillis();
        final long address = ((DirectBuffer) (this.mappedByteBuffer)).address();
        Pointer pointer = new Pointer(address);
        {
            //实现是将锁住指定的内存区域避免被操作系统调到swap空间中
            int ret = LibC.INSTANCE.mlock(pointer, new NativeLong(this.fileSize));
            log.info("mlock {} {} {} ret = {} time consuming = {}", address, this.fileName, this.fileSize, ret, System.currentTimeMillis() - beginTime);
        }

        {
            //实现是一次性先将一段数据读入到映射内存区域，这样就减少了缺页异常的产生
            int ret = LibC.INSTANCE.madvise(pointer, new NativeLong(this.fileSize), LibC.MADV_WILLNEED);
            log.info("madvise {} {} {} ret = {} time consuming = {}", address, this.fileName, this.fileSize, ret, System.currentTimeMillis() - beginTime);
        }
    }

    public void munlock() {
        final long beginTime = System.currentTimeMillis();
        final long address = ((DirectBuffer) (this.mappedByteBuffer)).address();
        Pointer pointer = new Pointer(address);
        int ret = LibC.INSTANCE.munlock(pointer, new NativeLong(this.fileSize));
        log.info("munlock {} {} {} ret = {} time consuming = {}", address, this.fileName, this.fileSize, ret, System.currentTimeMillis() - beginTime);
    }

    //testable
    File getFile() {
        return this.file;
    }

    @Override
    public String toString() {
        return this.fileName;
    }
}
