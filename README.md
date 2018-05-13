# yggdrasil-mock
[![Build status](https://img.shields.io/travis/to2mbn/yggdrasil-mock.svg?style=flat-square)](https://travis-ci.org/to2mbn/yggdrasil-mock)
[![license](https://img.shields.io/github/license/to2mbn/yggdrasil-mock.svg?style=flat-square)](https://github.com/to2mbn/yggdrasil-mock/blob/master/LICENSE)
![languages](https://img.shields.io/badge/languages-javascript,_java-yellow.svg?style=flat-square)
![require java 10+](https://img.shields.io/badge/require_java-10+-orange.svg?style=flat-square)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fto2mbn%2Fyggdrasil-mock.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fto2mbn%2Fyggdrasil-mock?ref=badge_shield)

Yggdrasil API测试样例，和一个简易的Yggdrasil服务端。

本项目提供了一套[Yggdrasil API](https://github.com/to2mbn/authlib-injector/wiki/Yggdrasil%E6%9C%8D%E5%8A%A1%E7%AB%AF%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83)的测试样例（位于`test/`下），和一个实现了Yggdrasil API的简易服务端（位于`server/`下）。

## 如何用它测试Yggdrasil API服务端
首先，向你的服务端中添加测试数据。这些数据定义在`test/yggdrasil-config.js`中，你可以按需进行修改（如修改Email、密码、角色名等）。其次，将`test/yggdrasil-config.js`中的`rateLimits.login`修改为你服务端的登录API的速率限制。

然后执行：
```bash
cd test
npm i .
npm run integration-test [api_root]
```

`[api_root]`为你服务端的API Root，默认值为`http://localhost:8080`（不要在末尾添加`/`）。

## 如何用它mock Yggdrasil服务端
你可以直接从[Jenkins](https://ci.to2mbn.org/job/yggdrasil-mock/)上下载构建好的JAR，或者通过以下命令构建：
```bash
cd server
gradle
```

构建输出位于`server/build/libs/`下。JAR可以直接运行。

第一次运行会在当前目录下释放配置文件`application.yaml`，你可以编辑其中设置。


## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fto2mbn%2Fyggdrasil-mock.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fto2mbn%2Fyggdrasil-mock?ref=badge_large)