由于<start.spring.io>默认仅提供 Spring Boot 4.x，本项目选择手动构建。

在IDEA中新建Java项目，名称: `backend`，构建系统：`Maven`，JDK：`temurin-17`，勾选`添加示例代码`。

## 添加依赖

获得的`pom.xml`文件如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.phrolova</groupId>
    <artifactId>backend</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

</project>
```

将其手动修改为：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.15</version>
        <relativePath/>
    </parent>

    <groupId>com.phrolova</groupId>
    <artifactId>backend</artifactId>
    <version>1.0-SNAPSHOT</version>
    <name>backend</name>
    <description>Async Forge backend</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## 新建启动类

将`Main.java`删除，新建`AsyncForgeApplication.java`:

```java
package com.phrolova;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsyncForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncForgeApplication.class, args);
    }
}
```

## 新建配置文件

新建配置文件`src/main/resources/application.yml`:

```yaml
server:
# 修改为任意未占用的端口
  port: 8080

spring:
  application:
    name: async-forge
```

## 刷新Maven

右键`pom.xml`，选择`添加为Maven项目`。  
重新加载所有Maven项目。

## 运行验证

运行`AsyncForgeApplication`。