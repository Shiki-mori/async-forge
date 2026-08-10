# RabbitMQ

RabbitMQ相关配置:[application.yml](backend/src/main/resources/application.yml)

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

一个消费者最多提前获取多少条消息。  
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
  dlx-exchange: task.dlx
  dlq: task.execute.dlq
  dlq-routing-key: task.execute.dlq
```

项目自定义配置，通过 `MqProperties` 读取。[MqProperties](backend/src/main/java/com/phrolova/asyncforge/config/MqProperties.java)

### 参数解释

| 参数项 | 说明 |
|--------|------|
| exchange | 主交换机（任务投递入口） |
| queue | 主消费队列 |
| routing-key | 主路由键 |
| dlx-exchange | 死信交换机 |
| dlq | 死信队列 |
| dlq-routing-key | 死信路由键 |

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