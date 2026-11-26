# RabbitMQ

RabbitMQ相关配置:[application.yml](../../backend/src/main/resources/application.yml)

## 连接配置

`spring.rabbitmq.*` -> 怎么连RabbitMQ

```yml
rabbitmq:
host: ${RABBITMQ_HOST:localhost}
port: ${RABBITMQ_PORT:5672}
username: ${RABBITMQ_USER:guest}
password: ${RABBITMQ_PASSWORD:guest}
listener:
  simple:
    acknowledge-mode: manual
    prefetch: 1
    default-requeue-rejected: false
```

### `acknowledge-mode:manual`手动确认模式

默认的自动确认模式：
RabbitMQ--发送消息--消费者收到--自动确认--消息删除。
消息被交给消费者后，RabbitMQ认为已经处理成功。

手动确认模式会在业务处理完成后确认消息，成功才发送Ack，否则发送Nack。

手动确认模式保证消费可靠。
自动确认模式适用于不需要绝对可靠，且吞吐量大的消费。

### `prefetch: 1`预取数量

一个消费者最多持有多少条已投递但未 ACK 的消息（含正在处理的那条）。

@RabbitListener 底层用的是 basic.consume，也就是推模式。消费者启动时向 RabbitMQ 注册一次，之后 Broker 只要有消息、而且没超过额度，就主动推给它。
prefetch（AMQP 里的 basic.qos）限制的是：一个消费者手上"已经推过来、但还没 ACK"的消息，最多能有几条。


prefetch=1,任务分配均衡，避免单个消费者占用大量消息。但吞吐量可能下降。

### `default-requeue-rejected: false`

消费失败后，RabbitMQ是否重新放回队列

## 业务拓扑

`async-forge.mq.*` -> 连接RabbitMQ之后使用哪些交换机/队列/路由键

```yml
mq:
  exchange: task.exchange
  queue: task.execute.q
  routing-key: task.execute
  retry-2s-queue: task.retry.2s.q
  retry-2s-routing-key: task.retry.2s
  retry-8s-queue: task.retry.8s.q
  retry-8s-routing-key: task.retry.8s
  dlx-exchange: task.dlx
  dlq: task.execute.dlq
  dlq-routing-key: task.execute.dlq
```

项目自定义配置，通过 `MqProperties` 读取。[MqProperties](../../backend/src/main/java/com/phrolova/asyncforge/config/MqProperties.java)

### 参数解释


| 参数项          | 说明                     |
| --------------- | ------------------------ |
| exchange        | 主交换机（任务投递入口） |
| queue           | 主消费队列               |
| routing-key     | 主路由键                 |
| retry-2s-queue / retry-8s-queue | 延迟重试队列（TTL 2s / 8s，无消费者） |
| retry-2s-routing-key / retry-8s-routing-key | 投递到对应重试队列的路由键 |
| dlx-exchange    | 死信交换机               |
| dlq             | 死信队列                 |
| dlq-routing-key | 死信路由键               |

定义 **消息路由拓扑的名字**。
这些值将被 `RabbitMqConfig` 用于声明 Exchange,Queue,Binding；也被 TaskProducer / TaskConsumer / DlqListener 用来 发消息和监听。

#### exchange

主交换机，任务投递入口。
exchange是生产者发送消息的入口。
生产者不会直接发送消息到queue，而是发送给exchange：producer--exchange--queue。

例如：

```java
rabbitTemplate.convertAndSend(
    "task.exchange",
    "task.create",
    message
);
```

- task.exchange->exchange名称
- task.create->routing-key
- message->消息内容

exchange根据routing-key和绑定规则，将消息转发到对应队列。

#### queue

主消费队列。
实际存储消息的地方。

消息生命周期：

```text
消息产生--exchange--queue保存--consumer读取--ack确认--删除
```

#### routing-key

主路由键。
routing-key是消息携带的一个标签。
exchange根据routing-key决定消息去哪。

例如：绑定关系

```text
task.exchange--routing-key=task.execute--task.queue
```

则当发送消息：

```text
exchange=task.exchange
routing-key=task.execute
```

匹配到`task.queue`收到消息。

#### dlx-exchange(dead letter exchange)

死信交换机。专门接受失败消息的交换机。

#### dlq

死信队列。本质上没有特殊功能，只是一个普通队列。但绑定了死信交换机。

#### dlq-routing-key

死信路由键。死信消息进入dlq时使用的routing-key。

拓扑关系：

```text
task.exchange ──routing-key: task.execute──► task.execute.q
                                                    │
                              (消费失败 nack)        │
                                                    ▼
task.dlx ──routing-key: task.execute.dlq──► task.execute.dlq
```

消息流向：

```text
生产者
  |
  v
exchange（主交换机）
  |
  | routing-key 匹配
  v
queue（主消费队列）
  |
  v
消费者
  |
  | 失败 / 超时 / 拒绝
  v
dlx-exchange（死信交换机）
  |
  | dlq-routing-key 匹配
  v
dlq（死信队列）
```

## 延迟重试拓扑

### 消息流向

```text
TaskProducer.publishRetryAfterCommit
   │ 发到 task.exchange，路由键 task.retry.2s / task.retry.8s
   ▼
task.exchange ──task.retry.2s──► task.retry.2s.q（TTL 2s，没有消费者）
              ──task.retry.8s──► task.retry.8s.q（TTL 8s，没有消费者）
                                     │ 消息过期，成为死信
                                     │ 死信交换机 = task.exchange，路由键 = task.execute
                                     ▼
task.exchange ──task.execute──► task.execute.q ──► TaskConsumer
```

- 投递重试消息：[TaskProducer](../../backend/src/main/java/com/phrolova/asyncforge/mq/TaskProducer.java) 的 `publishRetryAfterCommit`，第 1 次重试走 `task.retry.2s`，之后走 `task.retry.8s`。
- 重试队列声明与绑定：[RabbitMqConfig](../../backend/src/main/java/com/phrolova/asyncforge/config/RabbitMqConfig.java) 的 `taskRetryTopology` / `retryQueue`，队列的 `deadLetterExchange` 设为主交换机，`deadLetterRoutingKey` 设为 `task.execute`。

`task.exchange` 被用了两次：一次投递重试消息，一次把过期的重试消息送回执行队列，每次靠不同的路由键走到不同的队列。

### 为什么不为延迟重试单独建交换机

核心原因：**direct 交换机靠路由键区分消息就够了，多一个交换机不会多出能力。**

1. **交换机只是一张路由表，没有状态。** 它不存消息、不排队。"延迟"来自队列上的 TTL，加上"无消费者、过期后转成死信"这个组合，是队列的能力。新建 `task.retry.exchange`，里面也只是两条绑定，路由键照样要分 2s / 8s。
2. **direct 交换机本来就按路由键分发。** `task.execute`、`task.retry.2s`、`task.retry.8s` 三个路由键已经把三类消息分开，不会混。
3. **改动更小。** 生产者始终只往一个交换机发消息，`MqProperties` 不用多一个配置项，符合"能用现有组件就不新增"的取舍。

### 什么情况下值得单独建

| 场景                               | 原因                                                                                   |
| ---------------------------------- | -------------------------------------------------------------------------------------- |
| 用 `rabbitmq_delayed_message_exchange` 插件 | 延迟能力由交换机类型 `x-delayed-message` 提供，必须单独声明这种类型的交换机 |
| 需要不同的交换机类型               | 比如重试消息要 fanout / topic，而主链路是 direct                                       |
| 权限或运维隔离                     | RabbitMQ 权限可以按交换机名配置，有些团队按业务域拆开                                  |
| 想在管理界面单独看重试流量         | Message rate 按交换机统计，目前重试和正常消息都算在 `task.exchange` 上；不过看 `task.retry.*.q` 的速率也能知道重试量 |

本项目都不满足，所以复用 `task.exchange`。

### 那 `task.dlx` 为什么又单独建

这同样是设计选择，不是技术上必须：死信队列也可以绑到 `task.exchange` 上，用 `task.execute.dlq` 路由键。单独建 `task.dlx` 是常见惯例，语义更清楚：**进这个交换机的都是最终失败、等人处理的消息**，和正常流转（首次执行、重试）在拓扑上分开，排查时一眼可见。

重试消息还在正常流转中，最终会回到执行队列，所以和主链路放在同一个交换机更自然。
