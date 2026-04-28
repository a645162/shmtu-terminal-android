# Code Rule

## Gradle

代码编写完成后记得使用gradle进行编译测试！

## 利用 Android Lint 进行静态检查

你可以在命令行中通过 Gradle 来运行 Lint 检查

```bash
./gradlew lint
```

注意Error与Warning都是需要处理的！

## Warning

请尽量避免出现Warning，这样的代码是不安全的！
ruff检查到的warning也要进行修复！

## 变量与函数命名

请你仔细思考变量与函数的命名符合Java或者C++或者Kotlin的规范！

## 代码注释

代码要添加必要的注释。
javadoc
代码注释请使用中文！

## Old

不允许动任何路径带old的内容！读取都不允许！
