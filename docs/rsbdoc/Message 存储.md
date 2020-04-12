CommitLog 存储在 MappedFile的结构：

MESSAGE[1]	MESSAGE[2]	...	MESSAGE[n - 1]	MESSAGE[n]	BLANK
MESSAGE 在 CommitLog 存储结构：

第几位	字段	说明	数据类型	字节数
1	MsgLen	消息总长度	Int	4
2	MagicCode	MESSAGE_MAGIC_CODE	Int	4
3	BodyCRC	消息内容CRC	Int	4
4	QueueId	消息队列编号	Int	4
5	Flag	flag	Int	4
6	QueueOffset	消息队列位置	Long	8
7	PhysicalOffset	物理位置。在 CommitLog 的顺序存储位置。	Long	8
8	SysFlag	MessageSysFlag	Int	4
9	BornTimestamp	生成消息时间戳	Long	8
10	BornHost	生效消息的地址+端口	Long	8
11	StoreTimestamp	存储消息时间戳	Long	8
12	StoreHost	存储消息的地址+端口	Long	8
13	ReconsumeTimes	重新消费消息次数	Int	4
14	PreparedTransationOffset		Long	8
15	BodyLength + Body	内容长度 + 内容	Int + Bytes	4 + bodyLength
16	TopicLength + Topic	Topic长度 + Topic	Byte + Bytes	1 + topicLength
17	PropertiesLength + Properties	拓展字段长度 + 拓展字段	Short + Bytes	2 + PropertiesLength
BLANK 在 CommitLog 存储结构：

第几位	字段	说明	数据类型	字节数
1	maxBlank	空白长度	Int	4
2	MagicCode	BLANK_MAGIC_CODE	Int	4