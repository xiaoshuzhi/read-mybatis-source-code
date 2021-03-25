## SqlSessionFactoryBuilder作用以及源码分析

#### SqlSessionfactoryBuilder

```java
public class Main {
    public static void main(String[] args) throws IOException {
        SqlSessionFactoryBuilder sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();
        SqlSessionFactory sqlSessionFactory = sqlSessionFactoryBuilder.build(Resources.getResourceAsStream("mybatis-config.xml"));
        SqlSession sqlSession = sqlSessionFactory.openSession();
        UserDao userDao = sqlSession.getMapper(UserDao.class);
        User user = userDao.selectUser(1);
        System.out.println(user.getId()+", "+user.getName()+", "+user.getAge());
    }
}
```

​		我们可以看到，用使用mybatis，我最先需要用的对象就是SqlSessionFactoryBuilder对象。从名字就能看出它的主要作用就是构造一个SqlSessionFactory。进入该类可以看到里面有一堆名为build()的方法，而事实上就三个build方法。第一个build()方法使用reader的方式读取配置文件，第二个build()方法使用stream()的方式读取配置文件，它两都会调用第三个build()方法来生成一个DefaultSqlSesssionFactory对象。

```
1. SqlSessionFactory build(Reader reader, String environment, Properties properties)
2. SqlSessionFactory build(InputStream inputStream, String environment, Properties properties)
3. SqlSessionFactory build(Configuration config)
```

​		其他的build()都是前2个build()的重载方法。前两个build()方法的逻辑是，通过三个传参构造一个XMLConfigBuilder对象，调用该对象的parse()构键一个传说中的Configuration类，这个类里包含了所有的配置信息，是mybatis中非常重要的一个类，后续在阅读这个类。下面我给出第二个build方法的源码。

```java
public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
  try {
    XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
    // 这里调用了第三个build(),返回一个DefaultSqlSessionfactory对象
    return build(parser.parse());
  } catch (Exception e) {
    throw ExceptionFactory.wrapException("Error building SqlSession.", e);
  } finally {
    ErrorContext.instance().reset();
    try {
      inputStream.close();
    } catch (IOException e) {
      // Intentionally ignore. Prefer previous error.
    }
  }
}
```

第三个build方法如下，就是初始化一个DefaultSqlSessionfactory对象

```java
public SqlSessionFactory build(Configuration config) {
  return new DefaultSqlSessionFactory(config);
}
```



------

#### DefualtSqlSessionFactory

通过名字可以知道这个类是一个用来产生SqlSession的工厂。该类是SqlSessionFacotry接口的实现类，实现了一大堆重构的openSession()，而这些openSession()内部最终都是调用私有的openSessionFromDataSource()，该方法有两个重载。

1. 根据执行器类型、事务隔离级别、是否自动提交作为参数创建一个DefaultSqlSession对象。

```java
private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
  Transaction tx = null;
  try {
    final Environment environment = configuration.getEnvironment();
    final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
    tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
    final Executor executor = configuration.newExecutor(tx, execType);
    return new DefaultSqlSession(configuration, executor, autoCommit);
  } catch (Exception e) {
    closeTransaction(tx); // may have fetched a connection so lets call close()
    throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
  } finally {
    ErrorContext.instance().reset();
  }
}
```

2. 根据执行器类型、sql连接作为参数创建一个DeafualtSqlSession对象。

```java
private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
  try {
    boolean autoCommit;
    try {
      autoCommit = connection.getAutoCommit();
    } catch (SQLException e) {
      // Failover to true, as most poor drivers
      // or databases won't support transactions
      autoCommit = true;
    }
    final Environment environment = configuration.getEnvironment();
    final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
    final Transaction tx = transactionFactory.newTransaction(connection);getTar
    final Executor executor = configuration.newExecutor(tx, execType);
    return new DefaultSqlSession(configuration, executor, autoCommit);
  } catch (Exception e) {
    throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
  } finally {
    ErrorContext.instanc
    e().reset();
  }
}
```

内部逻辑大致上都是：

- 获取Environment对象

- 调用私有方法getTransactionFactoryFromEnvironment()获得一个事务工厂：该方法的逻辑是，如果Environment对象为null，则显示返回ManagedTransactionFactory，否则从Environment对象的直接获取

- 通过事务工厂对象获取一个事务对象，这里不同的两个方法调用的方法有不通

- 根据传参执行器类型以及获取的事务对象得到一个执行器类对象

- 最终DefaultSqlSession

  

  我们可以看一下如何获取到执行器对象的：如果传入的是BATCH、REUSE类型则创建相应的执行器否则创建SimpleExecutor，但是如果配置的是支持缓存的则生产CachingExecutor

  ```java
  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    Executor executor;
    if (ExecutorType.BATCH == executorType) {
      executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
      executor = new ReuseExecutor(this, transaction);
    } else {
      executor = new SimpleExecutor(this, transaction);
    }
    if (cacheEnabled) {
      executor = new CachingExecutor(executor);
    }
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
  }
  ```



----

#### DeafualtSqlSession

终于走到了我们的重头戏，DeafaultSqlSession是SqlSession的实现类，这个类提供了诸多方法，我们可分未两类：

1. 内置的操作数据源的接口（这些接口后续再看）
2. 获取Dao对象的getMapper(Class<T> type)方法，这里也用到了JDK的动态代理。

重点看下getMapper方法，里面实际上调用Configuration类对象的getMapper方法。这里的this表示的是DeafaultSqlSession类对象

```java
@Override
public <T> T getMapper(Class<T> type) {
  return configuration.getMapper(type, this);
}
```

进入方法，里面实际上调用的是MapperRegistry对象的getMapper()，顾名思义它是映射注册类。它是Configuration的属性，当Configuration对象初始化之后，映射关系就确定了。

```java
@SuppressWarnings("unchecked")
public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
  // knowMappers是一个map，里面存了class对象和MapperProxyFactory的映射关系
  // 这里就是根据我们传入的class对象得到对应的代理类的工厂
  final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
  if (mapperProxyFactory == null) {
    throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
  }
  try {
    // 根据我们传入的SqlSession对象直接得到对应的被代理的Dao对象
    return mapperProxyFactory.newInstance(sqlSession);
  } catch (Exception e) {
    throw new BindingException("Error getting mapper instance. Cause: " + e, e);
  }
}
```



----

#### MapperProxyFactory

代理工厂类，但是该类生产的不是代理类对象，而是被代理类对象。

```java
@SuppressWarnings("unchecked")
protected T newInstance(MapperProxy<T> mapperProxy) {
  // 根据参数得到被代理类对象。
  return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
}

public T newInstance(SqlSession sqlSession) {
  final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
  // 调用上面的newInstance方法
  return newInstance(mapperProxy);
}
```