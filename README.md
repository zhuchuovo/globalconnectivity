# Global Connectivity（全局互联）— NBT 结构文件管理

一个由两部分组成的项目：

| 目录 | 说明 |
|---|---|
| `neoforge/` | Minecraft **NeoForge 1.21.1** 客户端模组：游戏内 NBT 文件管理界面 |
| `csharp/` | **C# / .NET 10** 云端服务器（可用 Visual Studio 2026 打开），存储与分发 .nbt 文件 |

模组的设计思路类似机械动力（Create）的蓝图文件夹：所有 .nbt 文件都放在游戏目录下的
`nbtfiles/` 文件夹里，玩家在游戏内界面中浏览、导入、上传和下载。

---

## 一、NeoForge 模组（neoforge/）

### 功能

- **按 `K` 键**（游戏内，可在按键设置中修改）打开「NBT 文件管理器」界面
- **本地文件**标签页：
  - 浏览游戏目录下 `nbtfiles/` 文件夹中的所有 .nbt 文件（列表支持鼠标滚轮、滚动条、**按住拖动**滚动）
  - 单击选中，双击直接上传；「打开文件夹」按钮在资源管理器中打开该文件夹
  - **把 .nbt 文件直接拖到游戏窗口上即可导入**（自动去重命名）
  - 「上传到服务器」把选中文件上传到当前服务器；「删除」删除本地文件
- **云端下载**标签页：
  - 显示当前服务器的文件列表，双击或点「下载」即可下载到本地 `nbtfiles/` 文件夹
- **设置**标签页：
  - 服务器列表，默认内置**官方服务器 `47.103.169.249`**（显示为「官方服务器」）
  - 可自行输入名称 + 地址**添加自定义服务器**，点击列表项切换当前服务器
  - 「测试连接」检查服务器可达性；「删除当前服务器」移除不用的服务器
- 服务器配置保存在 `config/globalconnectivity-servers.json`
- 自带中文（zh_cn）与英文（en_us）语言文件

### 构建

需要 **JDK 21**（网络可达，首次构建会下载依赖）：

```bash
cd neoforge
gradlew.bat build        # Windows
./gradlew build          # Linux / macOS
```

构建产物：`neoforge/build/libs/globalconnectivity-1.0.0.jar`，丢进 `.minecraft/mods` 即可。

开发运行：`gradlew.bat runClient`。

> - NeoForge 版本、ModDevGradle 插件版本见 `gradle.properties` / `build.gradle`，可按需升级到
>   https://projects.neoforged.net/neoforged/moddev 上的最新版本。
> - 也可以用 IntelliJ IDEA 直接打开 `neoforge/` 目录，Gradle 会自动导入（需配置 JDK 21）。

---

## 二、C# 服务器（csharp/）

Windows / Linux 均可运行。

### 用 Visual Studio 2026 打开

直接双击 `csharp/GlobalConnectivity.NbtServer.sln`，或 VS2026 → 打开项目/解决方案 → 选择该 `.sln`，F5 即可运行。

### 端口与配置（appsettings.json）

端口在 `csharp/NbtServer/appsettings.json` 中配置：

```json
{
  "NbtServer": {
    "Urls": "http://0.0.0.0:8080",
    "AdminKey": ""
  }
}
```

- `Urls`：监听地址与端口，可填多个（分号分隔），例如 `"http://0.0.0.0:9090;https://0.0.0.0:9443"`
- `AdminKey`：管理员密钥。设置后，**删除文件仅限管理员**（网页管理页输入密钥，或请求携带 `X-Admin-Key` 请求头 / `?key=` 参数）；留空则删除接口对所有人开放（启动时控制台会打印警告）

优先级：`--urls` 命令行参数 > `ASPNETCORE_URLS` 环境变量 > appsettings.json > 默认 `http://0.0.0.0:8080`。

启动后浏览器打开 `http://服务器IP:端口/` 就是网页管理页（上传/下载/删除）；控制台日志的 `Now listening on: ...` 显示实际监听地址。项目自带的 `Properties/launchSettings.json` 已固定不使用随机端口，因此 VS2026 F5 与 `dotnet run` 都遵循上面的配置。

### Windows 运行

```powershell
cd csharp\NbtServer
dotnet run                                # 使用 appsettings.json 中的端口
dotnet run -- --urls=http://0.0.0.0:9090  # 临时覆盖端口
```

### Linux 运行

需要 .NET 10 SDK（Ubuntu: `sudo apt install dotnet-sdk-10.0`，或从 https://dotnet.microsoft.com/download 下载）。

方式一：开发运行（自带脚本）

```bash
cd csharp/NbtServer
chmod +x run.sh
./run.sh                                  # 使用 appsettings.json 端口
./run.sh -- --urls=http://0.0.0.0:9090
```

方式二：发布后运行（服务器只装 .NET 运行时即可）

```bash
cd csharp/NbtServer
dotnet publish -c Release -o publish
dotnet publish/NbtServer.dll
```

方式三：systemd 后台运行 + 开机自启（推荐，生产部署用）

项目自带一键部署脚本（发布 + 安装 systemd 服务，自动后台运行、开机自启、崩溃自动重启）：

```bash
cd csharp
sudo ./deploy/install.sh              # 安装到 /opt/nbtserver 并立即启动
sudo ./deploy/install.sh /srv/nbt     # 或自定义安装目录
```

手动执行等价步骤：

```bash
cd csharp/NbtServer
dotnet publish -c Release -o /opt/nbtserver
sudo cp ../deploy/nbtserver.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now nbtserver
```

服务管理：

```bash
systemctl status nbtserver          # 状态
journalctl -u nbtserver -f          # 实时日志
systemctl restart nbtserver         # 改完 /opt/nbtserver/appsettings.json 后重启生效
systemctl stop nbtserver            # 停止
systemctl disable nbtserver         # 取消开机自启
```

临时后台运行（不用 systemd 时）：`nohup dotnet NbtServer.dll > nbt.log 2>&1 &`

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查（版本、文件数） |
| GET | `/api/nbt/list` | 列出所有 .nbt 文件（名称、大小、上传时间） |
| POST | `/api/nbt/upload?name=x.nbt` | 上传（请求体为原始字节；也支持浏览器 multipart 表单，字段名 `file`） |
| GET | `/api/nbt/download/{name}` | 下载文件 |
| DELETE | `/api/nbt/{name}` | 删除文件——**仅管理员**（设置 AdminKey 后需携带 `X-Admin-Key` 头或 `?key=` 参数） |
| GET | `/` | 浏览器管理页面（上传/下载/删除，删除需输入管理员密钥） |

文件保存在运行目录的 `nbt_store/` 下；单文件上限 64MB；文件名清洗在 Windows/Linux 上行为一致（拒绝路径穿越，仅接受 `.nbt` 后缀）。

> 模组（玩家端）没有云端删除功能——删除只能由管理者在服务器网页端进行，与上面的权限设计一致。

### 部署到官方服务器

把 `csharp/` 目录上传到服务器（47.103.169.249），在 `appsettings.json` 中设置端口与管理员密钥后运行，并在防火墙/安全组放行对应端口。模组默认连接 `http://47.103.169.249:8080`。

> 如需修改默认官方服务器地址或端口，改模组里的
> `ServerListConfig.OFFICIAL_URL`（`neoforge/src/main/java/com/globalconnectivity/nbt/ServerListConfig.java`）即可；
> 客户端配置文件里已保存过服务器列表的话，删除 `config/globalconnectivity-servers.json` 让其重新生成。

---

## 三、联调流程

1. 启动 C# 服务器（本机测试用 `http://127.0.0.1:8080`）
2. 进入游戏，按 `K` 打开界面 → 设置标签页 → 添加服务器（如 `http://127.0.0.1:8080`）→ 测试连接
3. 本地文件页拖入/放入 .nbt → 「上传到服务器」
4. 云端下载页刷新 → 双击文件即可下载回本地 `nbtfiles/`

## 目录结构

```
globalconnectivity/
├── neoforge/                  # NeoForge 1.21.1 模组
│   ├── build.gradle / settings.gradle / gradle.properties
│   ├── gradlew(.bat) + gradle/wrapper/          # Gradle 8.14.2 wrapper
│   └── src/main/
│       ├── java/com/globalconnectivity/nbt/
│       │   ├── GlobalConnectivityMod.java       # 模组入口
│       │   ├── NbtFileStore.java                # 本地 nbtfiles 文件夹
│       │   ├── ServerListConfig.java            # 服务器列表配置（默认官方服务器）
│       │   ├── net/CloudApi.java                # 上传/下载/列表/健康检查 HTTP 客户端
│       │   └── client/
│       │       ├── ClientSetup.java             # K 键绑定
│       │       └── screen/                      # 管理界面（三个标签页）+ 可拖动列表
│       └── resources/                           # neoforge.mods.toml / 语言文件
└── csharp/                     # .NET 10 服务器（VS2026 解决方案，Windows/Linux 通用）
    ├── GlobalConnectivity.NbtServer.sln
    ├── deploy/                 # Linux 部署：一键安装脚本 + systemd 服务文件
    │   ├── install.sh
    │   └── nbtserver.service
    └── NbtServer/
        ├── NbtServer.csproj
        ├── Program.cs          # 服务器主程序（含网页管理页）
        ├── appsettings.json    # 端口（NbtServer:Urls）与管理员密钥（NbtServer:AdminKey）
        ├── run.sh              # Linux 启动脚本
        └── Properties/launchSettings.json
```
