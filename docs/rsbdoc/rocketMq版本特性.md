由于rocketMq的一些问题 跟版本有关
-----------------------------Apache RocketMQ-版本4.0.0-------------------------------------
虫子
[ ROCKETMQ-2 ]-代理测试失败，显示“地址已在使用中”
[ ROCKETMQ-5 ]-避免在UtilAll＃getDiskPartitionSpaceUsedPercent（）中创建目录
[ ROCKETMQ-19 ]-MQAdminImpl＃queryMessage中的线程不安全
[ ROCKETMQ-22 ]-版本3.5.8（4.0.0）中的新功能'printWaterMark'将导致ClassCastException
[ ROCKETMQ-25 ]-按键查询消息：可能同时访问LinkedList
[ ROCKETMQ-30 ]-邮件筛选器示例的方法签名不正确
[ ROCKETMQ-31 ]-`bin / mqbroker`中的$ HOME / rmq_bk_gc.log`需要删除
[ ROCKETMQ-33 ]-CPU占用率100％
[ ROCKETMQ-34 ]-NettyConnetManageHandler＃connect中潜在的NPE
[ ROCKETMQ-35 ]-消费者客户端无法将消费抵消表持久保存到代理
[ ROCKETMQ-45 ]-删除消耗队列挂起文件
[ ROCKETMQ-47 ]-代理在启动时两次更新NameServer地址列表
[ ROCKETMQ-59 ]-RocketMQSerializable中的字符集滥用
[ ROCKETMQ-64 ]-删除BrokerOuterAPI.registerBroker方法中的重复代码行
[ ROCKETMQ-70 ] -NettyRemotingClient中的重复方法
[ ROCKETMQ-74 ]-DataVersion等于未按预期工作。
[ ROCKETMQ-83 ]-快速入门样本失败
改善
[ ROCKETMQ-8 ]-使用Maven包装器标准化构建脚本
[ ROCKETMQ-11 ]-改进简洁性-在RouteInfoManager.getSystemTopicList方法中重用局部变量'brokerAddrs'
[ ROCKETMQ-14 ]-远程调用回调应在执行程序而不是当前线程中调用。
[ ROCKETMQ-16 ]-改进设置topic.json和subscriptionGroup.json路径的代码
[ ROCKETMQ-18 ]-将com.alibaba重新打包为org.apache并更改Maven坐标
[ ROCKETMQ-20 ]-默认“ consumeFromWhere”与示例的不一致
[ ROCKETMQ-29 ] -org.apache.rocketmq.common.help.FAQUrl是指中文文档
[ ROCKETMQ-32 ]-改进简洁性-在RouteInfoManager.getSystemTopicList方法中重用局部变量'brokerAddrs'
[ ROCKETMQ-37 ]-日志输出信息不准确
[ ROCKETMQ-43 ]-代码类型文件与类型转换中的检查样式不匹配
[ ROCKETMQ-50 ] -RocketMQ的波兰语单元测试
[ ROCKETMQ-60 ] -4.0.0孵化版的清单审查
[ ROCKETMQ-69 ]-将指向RocketMQ网页的链接添加到README.md中
[ ROCKETMQ-85 ]-波兰自述文件并删除其中的所有第3方链接。
[ ROCKETMQ-87 ]-抛光LICENSE和NOTICE文件以匹配所有依赖项
[ ROCKETMQ-1 ]-更新构建并清理4.0.0孵化的IP
[ ROCKETMQ-38 ]- 火箭遥控的波兰语单元测试
[ ROCKETMQ-51 ]-火箭火箭经纪人的波兰语单元测试
[ ROCKETMQ-52 ]-火箭单元客户的波兰语单元测试
[ ROCKETMQ-53 ]-火箭测试的波兰语单元测试
[ ROCKETMQ-54 ]- 火箭的波兰语单元测试-namesrv
[ ROCKETMQ-56 ]-火箭商店的波兰语单元测试
[ ROCKETMQ-57 ]-火箭炮工具的波兰语单元测试
[ ROCKETMQ-58 ]-为RocketMQ添加集成测试
[ ROCKETMQ-62 ]-波兰Maven组件发布
[ ROCKETMQ-3 ]-清理robotmq的单元测试
[ ROCKETMQ-13 ] -AcceptSocketService终止的日志级别错误
[ ROCKETMQ-9 ]-rocketmq-store模块中的错误

-----------------------------Apache RocketMQ-版本4.1.0-------------------------------------
新功能
[ ROCKETMQ-80 ]-添加批处理功能
[ ROCKETMQ-121 ]-支持基于SQL92的消息过滤
[ ROCKETMQ-194 ]-使用火箭的日志附加器
[ ROCKETMQ-186 ]-实现OpenMessaging规范0.1.0-alpha版本
改善
[ ROCKETMQ-67 ]-一致的哈希分配策略支持
[ ROCKETMQ-99 ]-为Windows平台添加脚本
[ ROCKETMQ-36 ]-改进经纪人的GC日志存储
[ ROCKETMQ-39 ]-filterrv和namesrv模块中的代码重复
[ ROCKETMQ-86 ]-波兰语发行文件格式
[ ROCKETMQ-88 ]-波兰语pom.xml中的开发人员列表
[ ROCKETMQ-90 ]-在使用者进度命令输出的每个消息队列中包含客户端IP
[ ROCKETMQ-114 ]-将Javadoc添加到代码库
[ ROCKETMQ-138 ]-添加AuthenticationException类以删除硬编码的Aliyun身份验证类
[ ROCKETMQ-139 ]-将客户端相关模块的JDK版本降级到1.6
[ ROCKETMQ-144 ]-将分发特定的文件聚合到新模块
[ ROCKETMQ-154 ]-帮助信息后添加换行符
[ ROCKETMQ-160 ]-SendHeartBeart日志可能不会在预期的相同时间内触发
[ ROCKETMQ-161 ]-更新runbroker.sh和runserver.sh以支持用户定义的jvm内存标志
[ ROCKETMQ-168 ] -Maven中生命周期的重复调用。
[ ROCKETMQ-172 ]-rocketmq客户端的日志改进
[ ROCKETMQ-176 ]-改进自述文件中的Maven中央徽章
[ ROCKETMQ-187 ]-测量集成测试的代码覆盖率
[ ROCKETMQ-148 ]-将所有相关文档从旧的Github项目的Wiki迁移到ASF站点
[ ROCKETMQ-218 ]-为通过SQL 92筛选添加IT测试
[ ROCKETMQ-219 ]-添加批次示例
[ ROCKETMQ-220 ]-README.md更新，删除一些github链接
虫子
[ ROCKETMQ-77 ]-[TEST] org.apache.rocketmq.tools。*具有NPE
[ ROCKETMQ-89 ]-WS_DOMAIN_NAME，SUBGROUP的默认值将覆盖Java选项传递的自定义值
[ ROCKETMQ-95 ]-客户端日志的配置文件已损坏
[ ROCKETMQ-98 ]-永远无法释放putMessage Lock的风险
[ ROCKETMQ-101 ]-以发送异步方式重试时可能出现NullPointerException
[ ROCKETMQ-107 ]-当start（）或shutdown（）时，访问ServiceState不是线程安全的
[ ROCKETMQ-119 ]-正确关闭PullMessageService
[ ROCKETMQ-140 ]-针对旧名称服务器注册更高版本的代理
[ ROCKETMQ-143 ]-将fastjson从1.2.12更新到1.2.29
[ ROCKETMQ-145 ]-发生doWaitTransfer中的ConcurrentModificationException异常
[ ROCKETMQ-153 ]-动态获取名称服务器地址
[ ROCKETMQ-155 ]-修复ClientConfig中的错字
[ ROCKETMQ-165 ]-限制最大拉出批次大小的硬编码
[ ROCKETMQ-175 ]-消费者可能由于订阅不一致而错过消息
[ ROCKETMQ-178 ]-经纪人-m -p选项已损坏
[ ROCKETMQ-179 ]-修复测试用例的错误
[ ROCKETMQ-188 ] -RemotingExecption在异步调用和单向调用之间不一致
[ ROCKETMQ-189 ]-关于消费时间标记的误导性提示和错误的消费时间标记异常消息
[ ROCKETMQ-191 ]-修复了错误的套接字选项
[ ROCKETMQ-199 ]-消费者无法接收库存信息。
[ ROCKETMQ-200 ]-从名称服务器获取ClusterInfo时，总是缺少群集名称
[ ROCKETMQ-206 ]-如果存在非1字节字符，则加载JSON配置文件错误
[ ROCKETMQ-208 ]-运行客户端时在JDK 1.7的环境中发现不兼容问题
-----------------------------Apache RocketMQ-版本4.2.0-------------------------------------
新功能
[ ROCKETMQ-28 ]-支持运输层安全
[ ROCKETMQ-224 ]-支持客户端中的log4j2
[ ROCKETMQ-294 ]-支持数字和大小尺寸的PushConsumer流量控制
改善
[ ROCKETMQ-6 ]-使用记录器记录异常，而不是e.printStackTrace（）
[ ROCKETMQ-23 ]-最好在MappedFileQueue＃flush成功时返回true
[ ROCKETMQ-96 ]-重命名tmp变量
[ ROCKETMQ-258 ]-将基准脚本移至分发模块
[ ROCKETMQ-259 ]-减少解码远程命令头时的反射调用
[ ROCKETMQ-263 ]-降低OpenMessaging模块中单元测试的成本
[ ROCKETMQ-266 ]-当consumerThreadMax小于consumerThreadMin时，抛光异常消息
[ ROCKETMQ-273 ]-当方法没有写操作时，简化代码
[ ROCKETMQ-279 ]-添加一致的提交日志数据检查，并在启动代理时使用队列数据
[ ROCKETMQ-281 ]-添加检查以防止重复启动经纪人
[ ROCKETMQ-307 ]-更改Java 8和直接内存GC的JVM参数
[ ROCKETMQ-308 ]-通过增加名称服务器的套接字缓冲区大小来提高代理注册速度
[ ROCKETMQ-311 ]-为代理的请求请求队列添加快速故障机制
[ ROCKETMQ-312 ]-对QueryMessageProcessor使用独立的线程池
[ ROCKETMQ-315 ]-增强TLS的默认设置
[ ROCKETMQ-323 ]-在异步过程中完成回调后释放信号量
[ ROCKETMQ-324 ]-公开一个接口供客户端指定异步回叫执行器
[ ROCKETMQ-327 ]-添加接口以支持解密加密的私钥文件
虫子
[ ROCKETMQ-231 ]-固定拉取结果的大小
[ ROCKETMQ-234 ]-修复了批量方案中的双返回错误
[ ROCKETMQ-238 ]-确保在ScheduledExecutorService的定期任务中捕获异常
[ ROCKETMQ-242 ]-确保客户端可以定期获取nameSrvAddr
[ ROCKETMQ-254 ]-修复LoggerAppender的随机测试失败并减少成本的时间
[ ROCKETMQ-260 ]-销毁IndexService时修复了错误的锁定
[ ROCKETMQ-270 ]-如果主代理已清除提交日志，请确保从代理可以正常启动
[ ROCKETMQ-277 ]-修复服务器主机名不在主机中时getLocalHost中的异常
[ ROCKETMQ-284 ]-确保sql过滤器与旧标签过滤器没有冲突
[ ROCKETMQ-285 ]-修复建立链接时的文件测试错误
[ ROCKETMQ-291 ]-修复了System.out.printf的UnknownFormatConversionException
[ ROCKETMQ-292 ]-修复了发生args解析问题时主线程中的退出问题
[ ROCKETMQ-320 ]-确保关闭后进行分派时没有消息丢失
[ ROCKETMQ-321 ]-确保不要删除中间的映射文件
-----------------------------Apache RocketMQ-版本4.3.0-------------------------------------
新功能
[ ISSUE-203 ]-在使用者中添加对IDC感知分配器的支持
[ ISSUE-292 ]-添加对事务性消息的支持
改善
[ ISSUE-184 ]-当主服务器崩溃并且只有从属代理可用时，优化消费性能
[ ISSUE-308 ]-增加名称服务器的套接字缓冲区大小，以更好地适应网络带宽
[ ISSUE-311 ]-添加用于提取消息请求的快速失败机制
[ ISSUE-315 ]-增强TLS模式配置
[ ISSUE-316 ]-将专用线程池用于心跳处理程序
[ ISSUE-324 ]-使生产者客户端的异步回调执行器可插入
[ ISSUE-325 ]-增强代理注册性能，并减少内存占用
[ ISSUE-353 ]-将发送和使用消息命令添加到mqadmin工具带
[ ISSUE-367 ]-重构日志记录组件以支持log4j，log4j2和logback库
虫子
[ ISSUE-66 ]-修复了重新发送邮件时邮件正文多次压缩的问题。
[ ISSUE-260 ]-修复StoreStatsService中的并发问题，以产生更准确的统计信息。
[ ISSUE-276 ]-修复了在计划新的请求请求之前缺少请求消息服务的状态验证的问题
[ ISSUE-290 ]-修复了WaitNotifyObject＃waitingThreadTable中的内存泄漏问题
[ ISSUE-314 ]-解决了消息处理超时时消息队列大小未同步的问题
[ ISSUE-321 ]-修复了RMQAsyncSendProducer单元测试中的并发问题
[ ISSUE-323 ]-修复了异步调用回调完成后无法释放信号量的问题
[ ISSUE-332 ]-修复了MappedFileQueue＃findMappedFileByOffset中的并发问题
[ ISSUE-336 ]-修复mqadmin中使用的System.out.printf格式错误
[ ISSUE-355 ]-修复异步发送方法的超时语义
-----------------------------Apache RocketMQ-版本4.3.1-------------------------------------
改善
[ ISSUE-395 ]-增强事务性生产者API的兼容性，并将默认主题更改为“ TBW102”，以确保服务器可以与较旧的客户端向后兼容。
[ ISSUE-396 ]-增强事务性消息的实现，为EndTransactionProcessor添加管理工具和单独的线程池。
[ ISSUE-430 ]-删除与mqfilter服务器相关的脚本。
虫子
[ ISSUE-392 ]-修复了在生产方关闭过程中发生的Nullpointer异常。
[ ISSUE-408 ]-合并过程中丢失的已还原代码
-----------------------------Apache RocketMQ-版本4.3.2-------------------------------------
改善
[ ISSUE-411 ]-修复了获取商店实例时的ClassCastException。
[ ISSUE-461 ]-清除客户端中与filterserv相关的代码。
[ ISSUE-478 ]-波兰语异步发送消息示例。
虫子
[ ISSUE-406 ]-修复了使用管理工具获取storehost时发生的NPE问题。
[ ISSUE-433 ]-修复了运行“ mvn clean install”时无法执行集成测试的问题。
[ ISSUE-439 ]-修复了ConsumeMessageCommand -g设置的问题。
[ ISSUE-447 ]-修复了checkLocalTransaction方法不生效的问题。
[ ISSUE-490 ]-解决了某些版本的服务器上的testGetLocalInetAddress失败的问题。
-----------------------------Apache RocketMQ-版本4.4.0-------------------------------------
新功能
[ ISSUE-403 ]-支持RocketMQ的ACL标准。
[ ISSUE-502 ]-支持使用者中的SQL92筛选器。
[ ISSUE-525 ]-支持消息跟踪。
改善
[ ISSUE-511 ]-CountDownLatch的波兰语注释。
[ ISSUE-536 ]-将fastjson版本更新为1.2.51。
[ ISSUE-571 ]-抛光数据库消息的默认值。
[ ISSUE-581 ]-NOTICE文件中的波兰语版权。
[ ISSUE-582 ]-将异步发送线程池从回调执行程序更新为独占执行程序。
[ ISSUE-586 ]-在管理工具中格式化输出编号。
[ ISSUE-640 ]-travis-ci的波兰语配置文件。
[ ISSUE-693 ]-添加实例名称以启用将跟踪消息发送到不同群集的功能。
虫子
[ ISSUE-512 ]-修复了RocketMQ无法使用-p选项打印配置项的问题。
[ ISSUE-544 ]-在极端情况下，固定的交易消息将丢失。
[ ISSUE-556 ]-修复了代理中epoll本机选择器启动错误的问题。
[ ISSUE-604 ]-修复了异步调用超时时未释放信号量的问题。
-----------------------------Apache RocketMQ-版本4.5.0-------------------------------------
新功能
[ ISSUE-1046 ]-支持RocketMQ的多个副本。
改善
[ RIP-9 ]-提供中英文《 RocketMQ开发人员指南》。
[ RIP-10 ]-添加单元测试用例。
[ ISSUE-608 ]-完善消息过滤器的示例。
[ ISSUE-742 ]-更改TransactionalMessageServiceImpl中的日志级别。
[ ISSUE-776 ]-使mqadmin使用信息更加友好。
虫子
[ ISSUE-762 ]-修复了DefaultMQProducerImpl关闭时defaultAsyncSenderExecutor不关闭的问题。
[ ISSUE-789 ]-修复了PlainAccessValidator＃parse（）中的NullPointerException。
-----------------------------Apache RocketMQ-版本4.5.1-------------------------------------
新功能
[ ISSUE-1174 ]-名称服务器的支持域主机。
改善
[ RIP-9 ]-提供中英文《 RocketMQ开发人员指南》。
[ RIP-10 ]-添加单元测试用例。
[ ISSUE-1129 ]-将发行包样式更改为rocketmq- {version}。
[ ISSUE-1138 ]-弃用生产者/消费者中公开的管理界面。
[ ISSUE-1200 ]-波兰语默认消息跟踪主题配置。
虫子
[ ISSUE-1078 ]-修复了用户如果不将tool.yml文件复制到相关折叠并关闭AclEnable标志的情况，将无法正常使用mqadmin命令的问题。
[ ISSUE-1147 ]-修复了如果同时打开aclEnable和enableDLegerCommitLog标志，代理将报告异常的问题。
[ ISSUE-1164 ]-修复了当主服务器被杀死时，当群集处于高级别tps时，消费者实例无法使用来自从服务器的消息的问题
-----------------------------Apache RocketMQ-版本4.5.2-------------------------------------
改善
[ RIP-9 ]-修复了rocketmq文件中的一些描述。
[ RIP-9 ]-修复best_practice文档中的类型。
[ ISSUE-1156 ]-为ACL配置添加新的mqadmin API。
[ ISSUE-1241 ]-针对RocketMQ客户端的第三方依赖关系优化代码。
[ ISSUE-598 ]-通过将超过最大检查时间的消息放入系统主题来增强事务。
[ ISSUE-1316 ]-增强股票净值处理程序。
[ ISSUE-1315 ]-为MQPullConsumerScheduleService添加RPCHook构造方法。
[ ISSUE-1290 ]-支持匹配一些acl ip范围。
[ ISSUE-1163 ]-延迟客户端Loggger中的工厂日志追加程序。
[ ISSUE-1318 ]-修复HaConnection文件中的类型。
[ ISSUE-1326 ]-Travis-CI Java 8构建环境。
[ 问题1317 ]-修复travis.yml。
[ ISSUE-1308 ]-修复了方法名称的一些拼写错误。
[ ISSUE-1325 ]-修复RocketMQ文档类型设置错误。
[ ISSUE-860 ]-次要类型修复。
[ ISSUE-1225 ]-修改异常声明。
[ ISSUE-1319 ]-更新concept.md。
[ ISSUE-1339 ]-中文文档中的键入修复。
[ ISSUE-1344 ]-修复在某些操作系统上找不到的/ dev / shm。
虫子
[ ISSUE-1140 ]-如果将JAVA_HOME设置为JRE，则找不到FIX HmacSHA1。
[ ISSUE-1253 ]-添加事务名称空间支持。
-----------------------------Apache RocketMQ-版本4.6.0-------------------------------------
新功能
[ ISSUE-1388 ]-添加对RocketMQ的精简版消费者支持。
[ RIP-15 ]-添加对RocketMQ的IPv6支持。
[ RIP-16 ]-添加对RocketMQ的请求-答复支持。
改善
[ ISSUE-504 ]-波兰语“此主题的无路线信息”例外。
[ ISSUE-1483 ]-使QueryMsgByIdSubCommand只打印一次offsetID。
[ ISSUE-1435 ]-修复了废弃的mqadmin子命令文档描述。
[ ISSUE-1528 ]-模拟相关功能以使生产者获得正确的topicrouteinfo并调用回调函数。
[ ISSUE-1519 ]-优化交易消息的性能/稳定性。
[ ISSUE-1531 ]-升级fastjson版本。
[ RIP-10 ]-为ConsumeMessageOrderlyService＃consumeMessageDirectly添加测试用例。

虫子
[ ISSUE-1082 ]-解决了HA断开的问题。
[ ISSUE-1456 ]-修复副本异常恢复缓慢的问题。
[ ISSUE-1108 ]-修复了客户端连接创建中的并发问题。
[ ISSUE-1464 ]-解决了当节点以分类器模式加入组时，主节点路由信息丢失的问题。
[ ISSUE-1470 ]-修复了导致用户丢失邮件的问题。
[ ISSUE-1491 ]-删除关闭挂钩时忽略IllegalStateException。
[ ISSUE-1535 ]-修复ha同步传输超时。
[ ISSUE-1528 ]-修复DefaultMQProducer的单元测试。
[ ISSUE-1568 ]-解决事务消息的重复压缩问题。
[ ISSUE-1564 ]-修复了IPV6 / IPV4共存环境中的IP过滤器逻辑。
文档和代码样式改进
[ ISSUE-1420 ]-波兰代码样式。
[ ISSUE-1556 ]-修复了指南文档中的摄影术。
[ ISSUE-1438 ]-波兰定冠词和不定冠词的用法。
[ ISSUE-1439 ]-修复文档中的交易错字。
[ ISSUE-1526 ]-修改docs-cn说明错误。
[ ISSUE-1503 ]-修复了docs / cn / design.md中的一些错字。
-----------------------------Apache RocketMQ-版本4.6.1-------------------------------------
改善
[ ISSUE-1612 ]-为拉动消费者添加了开始/结束搜索支持。
[ ISSUE-1110 ]-修正了robotmq客户端中错误的主题最大长度。
[ ISSUE-1188 ]-解决了同一过程中有多个生产者或消费者只能跟踪一个生产者或消费者的问题。
[ ISSUE-1639 ]-选择本地IP时首先使用IPv4。
[ ISSUE-1620 ]-修复mqadmin的拼写错误。
[ ISSUE-1701 ]-删除无效的方法以获得下一个pullBatchSize。
[ ISSUE-1699 ]-修复了TopicValidator中错误的主题最大长度。
[ ISSUE-1706 ]-重构使用者偏移量更新逻辑。
[ ISSUE-1694 ]-修复了ProducerManager中的并发问题。
[ ISSUE-1659 ]-添加拉动请求sysFlag并从启动位置开始支持消耗。
[ ISSUE-1656 ]-修复统计数据的第一分钟/小时/天可能不正确。
[ ISSUE-1720 ]-在基准测试中优化TransactionProducer。
[ ISSUE-1721 ]-修复了精简版使用者的名称空间问题。
[ ISSUE-1722 ]-避免在计划队列中延迟消息进入一半消息队列。
[ ISSUE-1724 ]-修复了litePullConsumerImpl seekToEnd中的拼写错误。
[ ISSUE-1735 ]-优化基准使用者并添加消耗失败率选项。
[ ISSUE-1736 ]-修复commitlog中的拼写错误。
bug
[ ISSUE-1648 ]-修复了作为事务消息处理的回发消息。
-----------------------------Apache RocketMQ-版本4.7.0-------------------------------------
新功能
[ ISSUE-1515 ]-同步复制更改为管道方式。
改善
[ ISSUE-1809 ]-改进了事务检查服务的异常处理。
[ ISSUE-1794 ]-升级fastjson版本。
[ ISSUE-1774 ]-防止客户端过于频繁地提交。
[ ISSUE-1771 ]-启用MessageExt以获取代理名称信息。
bug
[ ISSUE-1805 ]-在MQPullConsumerScheduleService中找不到回调。
[ ISSUE-1787 ]-mqadmin的queryCq命令返回了错误的数据。
[ ISSUE-1751 ]-修复了MessageClientIDSetter注入错误pid的错误。
[ ISSUE-1409 ]-启用acl时，使用queryMsgByKey或queryMsgByUniqueKey命令修复错误。
[ ISSUE-1781 ]-修复了异步重试错误。
[ ISSUE-1821 ]-修复了MessageClientIDSetter＃getIPFromID返回错误pid的错误。