# yggdrasil-mock
[![Build Status](https://travis-ci.org/to2mbn/yggdrasil-mock.svg?branch=master)](https://travis-ci.org/to2mbn/yggdrasil-mock) [![license](https://img.shields.io/github/license/to2mbn/yggdrasil-mock.svg)](https://github.com/to2mbn/yggdrasil-mock/blob/master/LICENSE)

Yggdrasil API测试样例，和一个简易的Yggdrasil服务端。

> 注意：本项目还没写完

本项目提供了一套[Yggdrasil API](https://github.com/to2mbn/authlib-injector/wiki/Yggdrasil%E6%9C%8D%E5%8A%A1%E7%AB%AF%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83)的测试样例（位于`test/`下），和一个实现了Yggdrasil API的简易服务端（位于`server/`下）。

## 如何用它测试Yggdrasil API服务端
首先，你需要向你的服务端中添加测试用的数据。这些数据定义在`test/yggdrasil-config.js`中，你可以按需进行修改（如修改Email、密码等）。

然后你需要对你服务端的一些时限进行调整。例如Token过期需要很长时间（如几天），我们不可能等这么久再去测试，因此需要将这个时限缩短。这些时限也定义在`test/yggdrasil-config.js`中。

再然后，你需要将`test/yggdrasil-config.js`中的`apiRoot`修改为你Yggdrasil服务端的API Root。

然后执行：
```bash
cd test
npm i .
npm run integration-test
```

## 如何用它mock Yggdrasil服务端
账户、角色等数据都是预先在`server/src/main/resources/application.yaml`中定义好的。如果你不需要修改，你可以直接从[Jenkins](https://ci.to2mbn.org/job/yggdrasil-mock/)上下载构建好的JAR。

如果你修改了，那么需要重新构建。
```bash
cd server
gradle clean bootJar
```
构建输出位于`server/build/libs/`下。直接运行即可。
