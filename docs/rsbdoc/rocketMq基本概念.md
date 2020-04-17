https://www.jianshu.com/p/6b833d01b249

1.Topic
 主题这个是逻辑概念 实际上 在项目中 消息的存储 不会按照主题进行分类
 
 
 2.Producer 和Consumer 
消息的发送端，Producer位于用户的进程内，Producer通过NameServ获取所有broker的路由信息，
根据负载均衡策略选择将消息发到哪个broker，然后调用broker接口提交消息

消息的消费端，位于用户进程内。Consumer通过向broker发送Pull请求来获取消息数据。如果consumer在请求
时没有数据，Broker可以将请求暂时hold住不返回，等待新消息来的时候再回复，这就是Push模式。Consumer可以以两种模
式启动，广播（Broadcast）和集群（Cluster），广播模式下，一条消息会发送给所有consumer，集群模式下消息只会发送给一个consumer

