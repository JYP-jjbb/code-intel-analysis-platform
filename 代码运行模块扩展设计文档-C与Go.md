# 代码运行模块扩展设计文档（C 与 Go）

## 1. 文档说明

### 1.1 文档目的
当前平台在学习模式下已经支持 **C++、Java、Python** 三种语言的单文件代码运行。  
本文档用于说明如何在现有代码运行模块基础上，进一步扩展对 **C 语言** 和 **Go 语言** 的支持，使平台能够在同一套页面与任务链路下，完成这两种语言的代码编译、执行、结果展示与错误反馈。

### 1.2 文档范围
本文档仅讨论以下内容：

- 学习模式下新增 **C / Go** 语言支持
- 前端语言选项扩展
- 后端运行任务参数与状态兼容
- Runner 对 C / Go 的编译与运行策略
- 本地环境与 Docker 环境的处理方式
- 错误处理与验收标准

本文档不涉及：

- 判题系统
- 多文件工程
- 第三方依赖下载
- 网络型程序运行
- 工程代码审查模块改造

### 1.3 当前背景
平台当前已经具备：

- 学习模式页面
- 代码输入区
- 标准输入区
- 输出展示区
- 运行代码按钮
- 后端任务创建与查询能力
- Runner 执行链路
- C++ / Java / Python 的编译运行能力

因此，本次扩展的核心不是重新设计整套模块，而是在现有基础上新增两种语言的完整支持。

---

## 2. 扩展目标

### 2.1 总体目标
在不破坏当前学习模式页面结构、不影响已支持语言运行的前提下，新增以下能力：

- 支持 **C 语言** 单文件编译与运行
- 支持 **Go 语言** 单文件编译与运行
- 支持 C / Go 的标准输入传递
- 支持 C / Go 的编译错误展示
- 支持 C / Go 的运行错误展示
- 支持前端语言切换与文件上传识别

### 2.2 关键要求
本次扩展必须满足以下要求：

1. 与现有 C++ / Java / Python 运行逻辑保持一致
2. 前端操作方式不新增复杂学习成本
3. 若本机没有安装 Go 编译器，仍应有明确解决方案
4. 编译失败时不得继续进入运行阶段
5. 输出区必须清晰区分“编译错误”和“运行错误”

---

## 3. 当前逻辑基础与扩展原则

## 3.1 当前已具备的统一运行链路
平台当前的运行链路可以概括为：

**前端输入代码 -> 提交语言与 stdin -> 后端创建运行任务 -> Runner 编译/执行 -> 返回 stdout/stderr -> 前端展示结果**

新增 C / Go 时，不应再新建一套独立链路，而应复用这条主链路。

## 3.2 扩展原则
新增语言必须遵循以下统一原则：

- 都按“单文件运行”处理
- 都通过统一任务表记录执行信息
- 都通过统一输出区展示结果
- 都通过统一状态字段反映执行阶段
- 都由 Runner 根据语言策略分发执行

---

## 4. 前端改造设计

## 4.1 语言设置扩展
当前“语言设置”下拉框已有：

- C++
- Java
- Python

本次应新增：

- C
- Go

建议前端内部语言值统一如下：

- `cpp`
- `java`
- `python`
- `c`
- `go`

展示名称建议为：

- C++
- Java
- Python
- C
- Go

## 4.2 文件上传识别扩展
上传代码文件后，应根据后缀自动识别语言。

新增后缀映射如下：

- `.c` -> `c`
- `.go` -> `go`

原有映射继续保留：

- `.cpp` / `.cc` / `.cxx` -> `cpp`
- `.java` -> `java`
- `.py` -> `python`

## 4.3 前端运行请求
前端在提交运行请求时，不需要为 C / Go 单独增加新的请求结构，继续沿用原有请求体：

```json
{
  "language": "go",
  "sourceCode": "package main\n...",
  "stdin": "2 3"
}

也就是说，本次前端改动主要集中在：

语言枚举增加
上传识别增加
提示文案与知识点内容联动
输出区对错误展示保持一致
5. 后端改造设计
5.1 语言枚举扩展

后端若已有语言枚举或校验逻辑，应新增两个合法值：

c
go

例如：

原支持：cpp / java / python
现支持：cpp / java / python / c / go

所有参数校验、数据库写入、Runner 调度都应兼容这两个值。

5.2 任务创建接口

任务创建接口无需新增字段，仍然使用现有接口即可。

请求示例：C
{
  "language": "c",
  "sourceCode": "#include <stdio.h>\nint main(){int a,b;scanf(\"%d%d\",&a,&b);printf(\"%d\\n\",a+b);return 0;}",
  "stdin": "2 3"
}
请求示例：Go
{
  "language": "go",
  "sourceCode": "package main\nimport \"fmt\"\nfunc main(){var a,b int; fmt.Scan(&a,&b); fmt.Println(a+b)}",
  "stdin": "2 3"
}
5.3 数据表兼容性

如果当前任务表 code_run_task 中的 language 字段已为字符串类型，则通常不需要改表结构，只需确保：

枚举校验允许 c 和 go
前后端查询展示支持新值

因此，这一扩展一般不需要新增字段。

6. Runner 扩展设计
6.1 扩展方式

Runner 当前既然已经支持三种语言，建议继续采用语言策略分发模式，新增两个策略：

CRunStrategy
GoRunStrategy

每个策略负责：

源码文件名
编译命令
运行命令
是否需要编译
编译产物文件名
错误信息提取方式
7. C 语言运行策略设计
7.1 文件命名

C 语言单文件统一写入：

main.c

7.2 编译命令
Linux / Docker 环境建议
gcc /workspace/main.c -O2 -std=c11 -o /workspace/main
Windows 本地 Runner 建议

如果是 Windows 主机直接编译，建议输出为：

gcc main.c -O2 -std=c11 -o main.exe
7.3 运行命令
Linux / Docker
/workspace/main < /workspace/input.txt
Windows
main.exe < input.txt

但在 Windows 环境中，Runner 不应依赖不稳定的相对路径，建议始终使用绝对路径执行。

7.4 编译器要求

支持 C 语言运行，必须具备 C 编译器。
推荐使用以下任一方案：

方案一：Docker 内编译（推荐）

在容器中使用已安装 gcc 的镜像执行，不依赖宿主机是否安装编译器。

优点：

环境更稳定
与现有隔离执行逻辑一致
不污染本机开发环境
方案二：本机编译

若 Runner 直接在本机 Windows 上执行，则本机必须安装可用的 gcc，例如：

MinGW-w64
MSYS2 中的 gcc

如果本机没有 gcc，则 C 语言无法在本机直接编译。

8. Go 语言运行策略设计
8.1 文件命名

Go 单文件统一写入：

main.go

8.2 编译命令
Linux / Docker 环境建议
go build -o /workspace/main /workspace/main.go
Windows 本地 Runner 建议
go build -o main.exe main.go
8.3 运行命令
Linux / Docker
/workspace/main < /workspace/input.txt
Windows
main.exe < input.txt

同样建议在 Windows 中使用绝对路径，而不是 .\main.exe 这种依赖当前目录的写法。

8.4 Go 编译器要求

这是本次扩展中最需要明确的问题。

当前已知前提

你的电脑很可能没有安装 Go 编译器。
因此，如果 Runner 采用的是“本机直接编译执行”的方式，那么 Go 语言当前是无法直接运行的。

8.5 Go 的推荐处理方案
方案一：使用 Docker 作为 Go 运行环境（强烈推荐）

这是最适合当前平台的方案。

做法如下：

Runner 检测语言为 go
启动包含 Go 工具链的容器
在容器内执行 go build
执行生成的二进制文件
返回 stdout / stderr

优点：

不要求宿主机安装 Go
环境统一，便于部署
与现有受控执行模型一致
不影响本机其他开发环境
方案二：本机安装 Go 工具链

如果当前 Runner 必须直接运行在 Windows 本机，则需要先安装 Go，并确保以下命令可用：

go version

只有当 go 命令已加入系统环境变量后，Runner 才能正常执行：

go build -o main.exe main.go
8.6 结论

如果希望最少折腾本机环境，并保持平台架构干净，Go 语言优先采用 Docker 镜像编译运行，而不是要求本机安装 Go。

9. 环境方案建议
9.1 推荐方案：统一走容器化执行

如果当前 C++ / Java / Python 已经具备隔离执行能力，那么新增 C / Go 时，建议继续采用同一思路：

C 在含 gcc 的镜像中执行
Go 在含 Go 工具链的镜像中执行
推荐原因
不需要依赖本机是否安装 gcc / go
降低 Windows 本地环境不一致带来的问题
编译命令、路径、权限更容易控制
更利于错误定位与清理临时文件
9.2 若当前 Runner 仍依赖本机环境

如果当前 Runner 不是基于 Docker，而是基于本机命令执行，则需要分别检查：

C 语言
gcc --version 是否可用
Go 语言
go version 是否可用

若不可用，则需要：

安装对应编译器
配置环境变量
重启 Runner 服务
10. 任务状态与错误处理
10.1 状态设计

C / Go 不需要新增新的状态字段，继续沿用现有设计即可：

task_status
PENDING
RUNNING
SUCCESS
FAILED
compile_status
PENDING
SUCCESS
ERROR
NOT_REQUIRED

其中，C 和 Go 都属于需要编译的语言，因此不会使用 NOT_REQUIRED。

run_status
PENDING
SUCCESS
RUNTIME_ERROR
TIMEOUT
SYSTEM_ERROR
10.2 C 语言典型错误
编译错误示例
缺少分号
未定义函数
头文件错误
scanf / printf 格式不匹配
运行错误示例
非法内存访问
除零
输入不足导致读取异常
10.3 Go 语言典型错误
编译错误示例
缺少 package main
缺少 func main()
导入未使用包
变量声明或语法错误
运行错误示例
panic
输入类型不匹配
数组越界等运行时异常
10.4 错误展示要求

前端输出区应继续保持如下规则：

编译失败：优先展示编译错误
编译成功但运行失败：展示运行错误
编译成功且运行成功：展示标准输出

不要把所有信息混在一起。

11. 前端提示文案调整

新增 C / Go 后，学习模式中的一些提示文案也应同步更新，避免仍然固定显示 Python 或 Java 相关文案。

例如：

当前语言为 C 时，知识点与易错提醒应尽量体现 scanf / printf / main / 分号 / 指针基础
当前语言为 Go 时，应体现 package main / import / fmt.Scan / fmt.Println / := / 未使用变量

这部分不影响运行本身，但会影响学习模式整体一致性。

12. 测试用例设计
12.1 C 语言最小测试程序
#include <stdio.h>

int main() {
    int a, b;
    scanf("%d%d", &a, &b);
    printf("%d\n", a + b);
    return 0;
}
标准输入
2 3
预期输出
5
12.2 Go 语言最小测试程序
package main

import "fmt"

func main() {
    var a, b int
    fmt.Scan(&a, &b)
    fmt.Println(a + b)
}
标准输入
2 3
预期输出
5
12.3 C 编译错误测试
#include <stdio.h>

int main() {
    int a
    printf("%d\n", a);
    return 0;
}
预期结果
编译失败
输出区显示编译错误
不进入运行阶段
12.4 Go 编译错误测试
package main

import "fmt"

func main() {
    fmt.Println("hello")
预期结果
编译失败
输出区显示编译错误
不进入运行阶段
13. 验收标准

当以下条件全部满足时，可认为本次 C / Go 语言扩展基本完成。

13.1 前端验收
语言设置中可选 C / Go
上传 .c / .go 文件可自动识别语言
输出区可展示 C / Go 的执行结果
页面整体布局不被破坏
13.2 后端验收
接口能接收 c / go 语言值
任务表能正常记录 C / Go 运行任务
查询接口能返回相应状态与输出
13.3 Runner 验收
C 代码可编译并运行
Go 代码可编译并运行
编译失败时不会继续运行
运行失败时能返回可读错误信息
13.4 关键场景验收
C
输入 2 3 输出 5
Go
输入 2 3 输出 5
Go 本机无编译器场景
若采用 Docker 运行，任务仍可成功执行
若采用本机编译，系统应明确提示 Go 工具链缺失，而不是给出模糊报错
14. 实施建议

结合你当前平台已经支持三种语言的现实情况，新增 C / Go 的最合理做法是：

第一优先级

先在现有 Runner 中补齐：

c 策略
go 策略
第二优先级

优先让 Go 走 Docker 编译运行，避免宿主机必须安装 Go。

第三优先级

同步修正文案、文件识别和错误展示，使学习模式整体逻辑保持统一。

15. 结论

当前平台已经具备单文件代码运行的基础能力，因此新增 C 和 Go 的关键不是重做前后端，而是在现有统一链路中补齐两种语言的运行策略。

其中：

C 语言 的核心在于补齐 gcc 编译与 main.c 单文件运行策略
Go 语言 的核心在于补齐 go build 编译策略，并解决“本机可能未安装 Go 编译器”的现实问题

从平台稳定性与落地成本来看，Go 最推荐使用 Docker 内置工具链执行，这样既不依赖本机安装环境，也与当前学习模式的受控执行思路保持一致。

最终，学习模式下的语言支持将从：

C++
Java
Python

扩展为：

C
C++
Java
Python
Go

从而形成一个覆盖主流基础教学语言的统一在线运行能力。