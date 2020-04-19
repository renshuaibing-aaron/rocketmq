1.所有的消息在存储时都是按顺序存在一起的，不会按topic和queueId做物理隔离
 2 每条消息存储时都会有一个offset，通过offset是定位到消息位置并获取消息详情的唯一办法，所有的消息查询操作最终都是转化成通过offset查询消息详情
3  每条消息存储前都会产生一个Message ID，通过这个id可以快速的得到消息存储的broker和它在CommitLog中的offset
4  Broker收到消息后的处理线程只负责消息存储，不负责通知consumer或者其它逻辑，最大化消息吞吐量
 5  Broker返回成功不代表消息已经写入磁盘，如果对消息的可靠性要求高的话，可以将FlushDiskType设置成SYNC_FLUSH，这样每次收到消息写入文件后都会做flush操作
 
 
 二：
 
 ConsumeQueue存储结构需要了解
 ConsumeQueue文件数据生成的整个步骤就讲到这里了。Consumer来读取文件的时候，只要指定要读的topic和queueId，
 以及开始offset。因为每个CQUnit的大小是固定的，所以很容易就可以在文件中定位到。找到开始的位置后，
 只需要连续读取后面指定数量的Unit，然后根据Unit中存的CommitLog的offset就可以到CommitLog中读取消息详情了。
 
 
 三.消息存储消息存储Index
 
 
  
