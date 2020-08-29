# yggdrasil-mock
[![Build status](https://img.shields.io/github/workflow/status/yushijinhun/yggdrasil-mock/CI?style=flat-square)](https://github.com/yushijinhun/yggdrasil-mock/actions?query=workflow%3ACI)
[![license](https://img.shields.io/github/license/yushijinhun/yggdrasil-mock.svg?style=flat-square)](https://github.com/yushijinhun/yggdrasil-mock/blob/master/LICENSE)

简易的 Yggdrasil 服务端，用于测试及演示用途。参考 [authlib-injector wiki](https://github.com/yushijinhun/authlib-injector) 以了解更多。

## 构建 & 运行
在 `server` 目录下执行 `gradle` 命令进行构建。

构建输出位于 `server/build/libs/` 下。JAR 可以直接运行，要求 Java 版本为 11 或以上。

第一次运行时，程序会在当前目录下释放配置文件 `application.yaml`，你可以编辑其中设置然后重新运行。
