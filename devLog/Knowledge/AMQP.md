# AMQP

AMQP（Advanced Message Queuing Protocol，高级消息队列协议）是一套**消息中间件的通信协议标准**，规定了客户端和消息服务器之间怎么建立连接、怎么发消息、怎么收消息、怎么确认。

可以拿 HTTP 来类比：HTTP 规定浏览器和 Web 服务器怎么对话，AMQP 规定生产者/消费者和消息 Broker 怎么对话。

RabbitMQ 是实现了 AMQP 的一个 Broker，就像 Nginx 是实现了 HTTP 的一个服务器。

## 它规定了什么

### 1. 消息模型

```
生产者 ──► Exchange（交换机）──路由键/绑定──► Queue（队列）──► 消费者
```

- **Exchange**：接收生产者的消息，按规则转发。生产者不直接往队列里发。
- **Queue**：存放消息，等消费者来取。
- **Binding**：交换机和队列之间的绑定关系，带一个路由键（routing key）。

本项目里这三样分别是 `task.exchange`、`task.execute.q`、`task.execute`，配置在 `backend/src/main/resources/application.yml` 的 `async-forge.mq.*`，说明见 [RabbitMQ 环境配置](../环境配置/RabbitMQ环境配置.md) 的「业务拓扑」一节。

### 2. 连接结构

- **Connection**：一条 TCP 连接。
- **Channel**：在一条 Connection 上复用出来的多条逻辑通道，大部分操作都在 Channel 上完成。

`TaskConsumer.consume()` 的参数里有个 `Channel channel`（`backend/src/main/java/com/phrolova/asyncforge/mq/TaskConsumer.java:31`），用的就是它。

### 3. 一组命令（method）


| 命令                          | 作用                             | 本项目里的对应                                                                             |
| ----------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------ |
| `basic.publish`               | 发消息到交换机                   | `TaskProducer` 里的 `rabbitTemplate.convertAndSend(...)`（`mq/TaskProducer.java:28`）      |
| `basic.consume`               | 注册消费者，让 Broker 推消息过来 | `@RabbitListener` 底层（`mq/TaskConsumer.java:25`）                                        |
| `basic.qos`                   | 设置 prefetch                    | `application.yml` 的 `prefetch: 1`                                                         |
| `basic.ack`                   | 确认处理成功                     | `channel.basicAck(...)`（`mq/TaskConsumer.java:45`）                                       |
| `basic.nack` / `basic.reject` | 拒绝消息，可选是否重回队列       | `channel.basicNack(..., false)` → 经 DLX 进入死信队列（`mq/TaskConsumer.java:41`、`:49`） |

所以 prefetch、手动 ACK、NACK 进死信这些机制，并不是 Spring 或 RabbitMQ 自己发明的，是 AMQP 协议本身就有的能力。Spring AMQP 只是把它们封装成了配置项和注解。

## 补充：prefetch 卡在哪一步

`basic.consume` 是**推模式**：消费者注册一次之后，Broker 只要有消息、且未超过额度，就主动推送，Worker 并不会处理完一条再去"要"下一条。

prefetch（`basic.qos`）限制的是：**一个消费者最多持有多少条已投递但未 ACK 的消息（含正在处理的那条）。**

在 Spring AMQP 中，"获取"和"消费"之间隔着一个本地缓冲：

```
RabbitMQ 队列
   │  Broker 推送（受 prefetch 限制：未 ACK 数 ≤ prefetch）
   ▼
消费者本地的 BlockingQueue（Spring 容器内部缓冲）
   │  监听线程从缓冲里取一条
   ▼
TaskConsumer.consume()  →  taskExecutionService.execute()
   │
   ▼
basicAck / basicNack  →  未 ACK 数减 1，Broker 才会再推下一条
```

- prefetch = N：1 条在执行，最多还有 N−1 条在本地缓冲里等。
- prefetch = 1（本项目）：正在执行的那条已占满额度，本地没有排队的消息，ACK/NACK 之后 Broker 才推下一条。
- 这个效果依赖手动确认（`acknowledge-mode: manual`）。自动确认时消息一推过来就算确认，prefetch 限制不住。
- `concurrency: 20` 会起 20 个消费者，每个有自己的 channel 和 prefetch 额度，所以整个 Worker 同时最多 20 条未 ACK 的消息，且每条都在执行。代价是 ACK 之后要等一次网络往返才能收到下一条，任务越短，这部分开销占比越高。

注意区分：prefetch 是 MQ 层的消息投递，发生在 `consume()` 被调用**之前**；`claimForExecution` 是业务层在数据库里抢占任务状态，发生在 `execute()` 内部，属于消费阶段。

## 补充：为什么 taskId 放消息体、重试次数放消息头

AMQP 消息分两部分：**body**（业务载荷，字节流，本项目用 `Jackson2JsonMessageConverter` 序列化成 JSON）和 **properties / headers**（投递元数据，键值对，Broker 和客户端不用解析 body 就能读到）。

本项目的划分：

| 位置 | 内容 | 代码 |
| --- | --- | --- |
| body | `TaskMessage { taskId }` | `mq/TaskMessage.java` |
| header | `task-retry-count`（只有重试消息带） | 写：`mq/TaskProducer.java:59`；读：`mq/TaskConsumer.java:33` |

> 提交 `332df6e` 引入这个消息头时没有写明理由，下面的理由是根据现有代码整理出来的。

### 理由

1. **语义分层：body 说明"是哪个任务"，header 说明"这是第几次投递"。** 同一个任务的首次消息、2s 重试消息、8s 重试消息，body 完全相同，都是 `new TaskMessage(taskId)`，区别只在投递属性上。所以 `TaskConsumer` 和 `DlqListener` 能用同一个 `TaskMessage` 类型反序列化。`DlqListener.onDeadLetter` 根本不读这个消息头，直接查库判断状态。
2. **数据库才是事实来源，消息只当"抢占凭据"。** 任务的真实 `retry_count` 存在 `task` 表里，由 `handleFailure` 累加。消息头里的值只用来和数据库比对（`mapper/TaskMapper.java:17-18`），相当于乐观锁的期望版本号，消费者不会拿它当执行依据。把它放进 header，就表明它属于这一次投递，不是任务数据的一部分。body 只放 `taskId` 一个字段，Worker 必须回库读最新状态，不会因为消息里带了旧的任务快照而误用过期数据。
3. **"没有这个头"本身就是一种状态。** 首次投递走 `publish(taskId)`，不设消息头，消费者读到 `null`（`required = false`），只能抢占 `PENDING`。这同时兼容了引入重试机制之前就已经在队列里的旧消息，它们不需要迁移。
4. **在 Broker 侧可观测。** 在管理界面"Get messages"查看延迟队列时，消息头直接显示，不用解析 JSON 就能看出这条消息是第几次重试。消息从 TTL 队列到期、经死信转发回执行队列时，RabbitMQ 会保留原来的 headers，再追加 `x-death`，所以消息头能一路带回 Worker。

### 说实话：哪些理由站不住

- "兼容旧消息"不是 header 独有的优势。如果在 `TaskMessage` 里加一个 `Integer retryCount` 字段，Jackson 遇到旧 JSON 里缺这个字段也会给 `null`，效果一样。
- TTL 死信转发同样会原样保留 body。所以这两点只能说明 header **可行**，不能说明它**更好**。真正有区分度的是第 1、2 点的语义划分。

### 代价

- 消息头不在 `TaskMessage` 的类型里，编译器检查不到。生产者如果忘了设置这个头，重试消息会被当作首次消息，因为任务是 `FAILED`，抢不到，于是被跳过，任务一直停在 `FAILED`。两端都引用 `TaskMessage.RETRY_COUNT_HEADER` 常量，至少避免了键名拼错。
- 消费端要多一个 `@Header(..., required = false) Integer` 参数，并且要处理 `null`。

### 为什么不用 RabbitMQ 自带的 `x-death` 计数

`x-death` 按"队列 + 死信原因"分别计数。本项目的重试先经过 `task.retry.2s.q`，再经过 `task.retry.8s.q`，两条队列各算各的，要加起来才是总次数。而且它反映的是消息被死信转发了几次，不是业务上失败了几次，和数据库的 `retry_count` 不一定一致。自己维护一个消息头，语义上和 `retry_count` 一一对应。

### 面试版

> body 只放 `taskId`，代表任务身份；重试次数是这次投递的元数据，放在 header。数据库的 `retry_count` 才是权威值，header 只当抢占时比对的期望版本号；没有 header 就代表首次投递，只能抢占 `PENDING`。代价是 header 不受类型约束，所以两端共用一个常量。没用 `x-death`，是因为它按队列分别计数，和业务重试次数对不上。

## 几个容易混的点

- **版本**：RabbitMQ 主要实现的是 **AMQP 0-9-1**。另外还有一个 AMQP 1.0，模型差别很大（比如没有 Exchange 这个概念），两者基本算两套协议。平时说"RabbitMQ 用 AMQP"，指的都是 0-9-1。
- **分层**：
  - AMQP：协议
  - RabbitMQ：实现协议的服务器
  - `com.rabbitmq.client`：实现协议的 Java 客户端
  - Spring AMQP / `spring-boot-starter-amqp`：在客户端上再包一层，提供 `RabbitTemplate`、`@RabbitListener`、监听容器
- **Kafka 不是 AMQP**：Kafka 用的是自己的二进制协议，模型也不一样（分区日志 + 消费者自己维护 offset，属于拉模式）。

## 一句话版本

> AMQP 是消息队列的应用层协议标准，定义了 Exchange / Queue / Binding 的路由模型和 publish、consume、ack、qos 这些命令；RabbitMQ 实现的是 AMQP 0-9-1，项目里的手动 ACK、prefetch、NACK 进死信都是协议层面的能力，Spring AMQP 只是做了封装。
