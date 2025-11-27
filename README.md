# 基于Closure Compiler 实现JS压缩
- JDK 21

## 使用方法
```
PS D:\07_code\java\closure-compiler-tool\target> D:\06_devptools\jdk-21.0.2\bin\java -jar .\closure-compiler-tool-20251127.jar -h
JsCompiler - Closure Compiler 批量编译 JS 并覆盖源文件

参数说明:
  -help, -h, --help    显示帮助信息
  -dir=目录路径        设置要扫描的JS根目录，默认: src/main/resources
  -file=文件列表路径   从文本文件读取JS文件列表进行处理
  -root=根目录路径     配合-file使用，指定文件列表中的相对路径的根目录
  -keywords=关键字1,关键字2,关键字3  设置路径关键字过滤，默认: 无
  -size=文件大小阈值   设置文件大小阈值(KB)，超过该大小的文件才会被处理，默认: 0 ( 无限制)

注意: -dir 和 -file 参数不能同时使用

使用示例:
  java -jar JsCompiler.jar -dir=src/main/resources
  java -jar JsCompiler.jar -file=filelist.txt -root=/project/root
  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts,chart
  java -jar JsCompiler.jar -dir=src/main/resources -size=100
  java -jar JsCompiler.jar -dir=src/main/resources -keywords=echarts -size=50
  java -jar JsCompiler.jar -help

```