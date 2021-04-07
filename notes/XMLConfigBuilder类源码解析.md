#### XMLConfigBuilder类源码解析

##### 一、背景介绍

通过阅读SqlSessionFactoryBuilder类的源码，我们知道了：

- 通过SqlSessionFactoryBuilder对象的build()，得到的DefaultSqlSessionFactory对象。
- build()内部逻辑是：
  1. 通过传参配置文件流来初始化一个XMLConfigBuilder对象
  2. 再调用XMLConfigBuilder的parse()方法创建一个传说中的Configuration对象
  3. 最后用这个Configuration对象来初始化上面提到的DefaultSqlSessionFactory对象。

##### 二、源码阅读

首先我们先把配置文件代码给出：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <environments default="development">
        <environment id="development">
            <transactionManager type="JDBC"/>
            <dataSource type="POOLED">
                <property name="driver" value="com.mysql.jdbc.Driver"/>
                <property name="url" value="jdbc:mysql://localhost/study"/>
                <property name="username" value="root"/>
                <property name="password" value="123456"/>
            </dataSource>
        </environment>
    </environments>
    <mappers>
        <mapper resource="mappers/userMaper.xml"/>
    </mappers>
</configuration>
```



直入主题，看XMLConfigBuilder的parse()，代码如下：

``` java
public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }
```

parsed是类的一个属性，初始化时设置为false，从代码可以看出一个Configuration对象只能调用一次parse方法。这里又调用了内部一个私有方法parseConfiguration()并将configuration根节点传入。

```java
private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      propertiesElement(root.evalNode("properties"));
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      typeAliasesElement(root.evalNode("typeAliases"));
      pluginElement(root.evalNode("plugins"));
      objectFactoryElement(root.evalNode("objectFactory"));
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      environmentsElement(root.evalNode("environments"));
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      typeHandlerElement(root.evalNode("typeHandlers"));
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
```

environmentsElement()方法，读取的是environments标签内的配置，目的是创建一个Environment对象，设置给Configuration的属性：

```java
private void environmentsElement(XNode context) throws Exception {
  if (context != null) {
    if (environment == null) {
      environment = context.getStringAttribute("default");
    }
    for (XNode child : context.getChildren()) {
      String id = child.getStringAttribute("id");
      if (isSpecifiedEnvironment(id)) {
        TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
        DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
        DataSource dataSource = dsFactory.getDataSource();
        Environment.Builder environmentBuilder = new Environment.Builder(id)
            .transactionFactory(txFactory)
            .dataSource(dataSource);
        configuration.setEnvironment(environmentBuilder.build());
      }
    }
  }
}
```

这里的逻辑也很简单：

1. 首先获取environments标签的default属性值，该值表示激活哪个environment标签内的配置
2. 遍历environments内的environment，获取environment的id，与default的值对比，匹配的话读取该environment标签内的配置。
3. 读取environment标签内属性流程：
   1. 读取transactionManager标签值，调用私有方法transactionManagerElement()以初始化一个TransactionFactory对象。
   2. 读取dataSource标签内的值，同样私有方法dataSourceElement()以初始一个DataSourceFactory对象
   3. 通过以上两个对象构建一个Environment的一个内部类Builder对象
   4. 最后通过调用这个内部类对象的build()方法初始化一个Environment对象，并赋值给Configuration的属性。