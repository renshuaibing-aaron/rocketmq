消息消费的主要的几个问题

  消息队列的负载和重新分布
  消息消费模式
  消息的拉取模式
     推拉模式的本质都是拉模式 只不过推模式是在拉模式的基础上封装了一层 一个拉取任务完成后开始下一个拉取任务
  消息进度的反馈
  消息过滤
    支持两种过滤模式表达式(TAG SQL92)和类过滤器模式
  顺序消息
     只支持局部消息顺序消费 能够保证同一个消息队列的消息顺序消费 如果保证全局有序 可以设置topic的消息队列数为1 牺牲高可用性


1.rocketMQ消费的含义是啥？具体来说几个步骤？
rocketMQ的消费有两个步骤 第一个是 consumer从broker上拉取消息到本地(消息缓存队列ProcessQueue)
这个时候消息消费的主题是RocketMq的Consumer模块(  不论是拉消息还是推消息模式，底层的实现都是由Consumer从Broker拉取消息)
    Consumer从本地的消息缓存队列取出消息，并调用上层应用程序指定的回调函数对消息进行处理。这个步骤中，消费的主体是上层应用程序。
    
2.
Consumer启动后会初始化一个RebalanceImpl做rebalance操作，从而得到当前这个consumer负责处理哪些queue的消息。
RebalanceImpl到broker拉取制定queue的消息，然后把消息按照queueId放到对应的本地的ProcessQueue缓存中
ConsumeMessageService调用listener处理消息，处理成功后清除掉

