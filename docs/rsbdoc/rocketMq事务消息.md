1.讲一讲RocketMq事务消息的内容，解决了哪些问题？
首先引入  什么是rocketMQ事务消息 这个需要联系分布式事务 在分布式事务的解决方案里面有一种情况是这样的
本地消息表+MQ消息 这种方案有个弊端 就是需要引入本地消息表 引入的原因是 保证消息投递成功

rocketMQ的事务消息解决了这个问题 就是 本地事务和消息发送一定会同时成立

过程
生产者先发送half消息到broker  成功
执行本地事务(增删改查) 成功 (这个时候假如失败会进行消息队列会进行rollback,然后进行本地事务回退)
提交消息commit (假如commit失败 rocketMQ有一个定时扫描机制，发现half的消息会进行回调生产的接口，然后
生产者查看本地数据表，然后消息队列再决定怎么操作)



2.rocketMQ的事务消息的底层原理

我的理解 rocketMq的事务利用half消息机制 保证了消息的发送和本地事务能够同时成功和同时失败 但是不能保证 消费一定成功 
如果出现消费时 消费失败的情况 需要用户手工处理

https://www.cnblogs.com/qdhxhz/p/11172585.html

主要了解rocketMq分布式事务的原理
https://blog.csdn.net/prestigeding/article/details/81277067


