1.了解生产者往Broker集群发送消息  就需要知道Topic MessageQueue Broker之间的关系
简单的说MessageQueue 就是分片  通过MessageQueue将一个Topic的数据拆分了很多的数据分片 然后在每个Broker上都存储一些MessageQueue


2.问题生产者在发送消息时 如何知道发送到哪个MessageQueue里面？
 在生产者在发送消息的时候 会进行连接NameServer ，这个家伙什么都知道 ，那么生产者便也知道怎么写

3.生产者在选择MessageQueue时 有什么策略？


4. 生产者 分组的意义是什么？
  其中一个作用是  在发送分布式事务消息时 如果producer中途意外宕机  Broker会回调producer Group内的任意一台节点
  确认事务的状态
 