1.普通顺序消息  这里只是把消息发送到相同的队列
```java
                SendResult sendResult = producer.send(msg, new MessageQueueSelector() {
                    @Override
                     public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                        Integer id = (Integer) arg;
                         int index = id % mqs.size();
                         return mqs.get(index);
                     }
                 }, orderId);
```

2.首先介绍写的不错的三个博客
https://blog.csdn.net/prestigeding/article/details/79422514
http://www.iocoder.cn/RocketMQ/message-send-and-consume-orderly/
https://blog.csdn.net/hosaos/article/details/90675978

顺序消息包含全局顺序消息 所有发到rocaketMQ的信息都被顺序消费 类似于数据库的binlog日志 
这就要求设置读写队列的数量为1  只有这种情况下才能保证全局有序

但是在生产中没有必要做到全局有序 这样严重降低了性能 一般做到局部有序就能保证生产的需要
局部有序需要做到三个维度：
1.消息的顺序发送  多线程无法保证 需要在单线程里面保证顺序
2.消息的顺序存储  同一个业务编号的消息需要发送到同一个消息队列里面即可(调用rocketmq提供的接口)
3.消息的顺序消费  这个是重点，rocketMq做的处理比较多  大概的原理是采用三把锁 ，一个是分布式锁,用来分配消费者对应的消息队列,保证在集群种同一个队列的消息
只能有一个消费者的一个线程消费(注意这个两个一),只有保证了消息队列被同一个消费者的同一个消费线程消费，才能保证顺序消费
这里感觉应该是两把锁 一把是分布式锁 就是不同的消费者只能一个能够抢到这个消息队列 然后再进行消费的时候 再来保证线程池中的消费线程只有一个获取成功
 
  在进一步深入理解 