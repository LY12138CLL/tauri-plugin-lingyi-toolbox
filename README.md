# tauri-plugin-lingyi-toolbox 插件使用说明（Tauri v2）

一款基于 Tauri v2 原生插件，封装了应用设置跳转、浏览器跳转、系统相册选图、权限申请 / 检查 / 设置页跳转等常用能力。目前只支持Android，桌面端为占位实现（调用将返回 `Unsupported platform`）。

---

## 一、功能一览

| 命令 | 功能 | 平台 |
|---|---|---|
| `openAppDetails` | 打开本应用的应用详情页 | Android |
| `openBrowser` | 打开浏览器跳转到指定网址（可指定浏览器包名） | Android |
| `pickImage` | 打开系统相册选一张图片，返回 base64 + 缓存文件路径 | Android |
| `requestPermission` | 申请单个权限（危险权限 / 特殊权限均可），返回 granted / neverAskAgain | Android |
| `checkPermission` | 检查单个权限是否已授权 | Android |
| `openPermissionSettings` | 跳转到指定权限的最佳系统设置页 | Android |

底层权限能力基于 [XXPermissions](https://github.com/getActivity/XXPermissions) 框架（v28.3）实现。

---

## 二、安装与接入

### 安装

#### Rust

在 Cargo.toml：

~~~toml
tauri-plugin-lingyi-toolbox = "0.1.0"
~~~

#### 前端

在项目根目录：

~~~cmd
npm install tauri-plugin-lingyi-toolbox
~~~

### 接入

####添加Android XXPermissions 框架下载网址： 

在 src-tauri\gen\android\build.gradle.kts：

~~~kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }   // 添加这一行
    }
}
~~~

### 1. Rust 侧注册

在 `src-tauri/src/lib.rs`（或 `main.rs`）中注册插件：

```rust
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_lingyi_toolbox::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 2. 前端权限声明

插件的 `permissions/default.toml` 中定义了一个 `[default]` 权限组，把全部 6 个命令的 `allow-*` 权限预先打包在一起。宿主项目中有两种开启方式，**二选一**：

**方式一：使用默认权限组（推荐，一键开启全部命令）**

```json
{
  "permissions": ["lingyi-toolbox:default"]
}
```

**方式二：按需单独开启（粒度更细，最小权限原则）**

```json
{
  "permissions": [
    "lingyi-toolbox:allow-open-app-details",
    "lingyi-toolbox:allow-open-browser",
    "lingyi-toolbox:allow-pick-image",
    "lingyi-toolbox:allow-request-permission",
    "lingyi-toolbox:allow-check-permission",
    "lingyi-toolbox:allow-open-permission-settings"
  ]
}
```

说明：
- 每个 `#[tauri::command]` 在编译时会由 `build.rs` 中的 `COMMANDS` 列表自动生成对应的 `allow-<命令名>` 和 `deny-<命令名>` 权限，命名规则为命令名转 kebab-case（如 `open_app_details` → `allow-open-app-details`）。
- `default.toml` 只是把这些 `allow-*` 项组合成 `default` 组，**不影响单独引用**；也可以混用 `"lingyi-toolbox:default"` 再加 `"deny-pick-image"` 做排除。
- 未在 capabilities 中声明的命令，前端调用会被 Tauri 拦截并报权限错误。

### 3. AndroidManifest 声明权限

**必须在宿主 App 的 `src-tauri/gen/android/app/src/main/AndroidManifest.xml`（或 `app/src/main/AndroidManifest.xml`）中显式声明**用到的 Android 权限，例如：

```xml
<manifest ...>
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    ...
</manifest>
```

> ⚠️ 只在前端 / Kotlin 中处理权限是不够的，`uses-permission` 必须写进 Manifest，否则系统不会授予。

### 4. 前端调用

```ts
import {
  openAppDetails,
  openBrowser,
  pickImage,
  checkPermission,
  requestPermission,
  openPermissionSettings,
} from "@tauri-apps/lingyi-toolbox";
```

---

## 三、API 详细说明

### 1. openAppDetails() 打开应用详情页

```ts
try {
  await openAppDetails();
} catch (error) {
  console.log("报错:", error); // "无法打开应用详情页面: xxx"
}
```

### 2. openBrowser(url, packageName?) 打开浏览器

```ts
// 使用默认浏览器
await openBrowser("https://www.baidu.com/");

// 指定浏览器
await openBrowser("https://www.baidu.com/", "com.android.chrome");
```

| 参数 | 类型 | 说明 |
|---|---|---|
| url | String | 目标网址，**必传，必须带 scheme**（如 `https://`） |
| packageName | String? | 可选，指定浏览器包名 |

可能的异常：`url 不能为空`、`url 无效：缺少 scheme`、`浏览器未安装或无法处理该网址: xxx`、`没有可用的浏览器来处理该网址`、`打开浏览器失败: xxx`。

### 3. pickImage() 系统相册选图

```ts
const { uri, base64, mimeType, size, name, filePath } = await pickImage();
```

返回值 `PickImageResult`：

| 字段 | 说明 |
|---|---|
| uri | 原始 `content://` URI（仅作标识，**不能直接用于 img 渲染**） |
| base64 | 图片 base64（NO_WRAP，无换行） |
| filePath | 已复制到 App 私有缓存目录的绝对路径，配合 `convertFileSrc` 使用 |
| mimeType | MIME 类型，如 `image/jpeg` |
| size | 图片字节数 |
| name | 图片文件名 |

前端渲染图片推荐两种方式：

```ts
// 方式一：base64
img.src = `data:${mimeType};base64,${base64}`;

// 方式二：convertFileSrc（更省内存，推荐大图使用）
import { convertFileSrc } from "@tauri-apps/api/core";
img.src = convertFileSrc(filePath);
```

可能的异常：`用户取消选择`、`返回数据为空`、`未获取到图片`、`无法读取图片数据`、`没有可用的相册应用` 等。

> ⚠️ 大图直接转 base64 会占用大量内存（base64 体积约为原图 1.33 倍），建议优先使用 `filePath` + `convertFileSrc`。

### 4. checkPermission(permission) 检查权限

```ts
const { granted } = await checkPermission("android.permission.CAMERA");
```

> ⚠️ **本命令刻意不区分"临时拒绝 / 永久拒绝"**。原因：XXPermissions 的 `isDoNotAskAgainPermissions` 在非申请场景调用会污染状态，导致后续无法弹窗；Android 官方 API 也无法区分"从未申请"与"永久拒绝"。

### 5. requestPermission(permission) 申请权限

```ts
const { granted, neverAskAgain } = await requestPermission("android.permission.CAMERA");
```

| 返回字段 | 说明 |
|---|---|
| granted | 权限是否被授予 |
| neverAskAgain | 用户是否勾选"不再询问"（**仅在本次申请的回调中判断才准确**，系统限制） |

### 6. openPermissionSettings(permission) 跳转权限设置页

```ts
await openPermissionSettings("android.permission.CAMERA");
```

框架会自动根据权限类型选择最佳设置页（悬浮窗 → 悬浮窗设置页、所有文件访问 → 文件管理页、通知 → 通知设置页、普通危险权限 → 应用权限管理页），并自带 Intent 兜底。

---

---

## 三-R、Rust 端使用示例

除前端 JS 调用外，Rust 侧（其他插件、命令处理器、后台任务中）也可直接调用插件能力，通过 `AppsettiingExt` trait 获取句柄：

### 1. 注册插件（必需，仅一次）

```rust
// src-tauri/src/lib.rs
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_lingyi_toolbox::init())  // 注册 lingyi-toolbox 插件
        // .plugin(tauri_plugin_notification::init())  // 其他插件...
        .invoke_handler(tauri::generate_handler![my_command])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 2. 在自己的命令中调用插件

```rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;

/// 检查相机权限并在未授权时申请
#[tauri::command]
async fn ensure_camera(app: AppHandle) -> Result<bool, String> {
    // 1. 先检查
    let checked = app
        .lingyi_toolbox()
        .check_permission("android.permission.CAMERA".to_string())
        .map_err(|e| e.to_string())?;

    if checked.granted {
        return Ok(true);
    }

    // 2. 申请（注意：权限弹窗必须在主线程/UI 上下文发起，
    //    若在异步任务中调用请通过 spawn_blocking 或 State 管理）
    let result = app
        .lingyi_toolbox()
        .request_permission("android.permission.CAMERA".to_string())
        .map_err(|e| e.to_string())?;

    if result.granted {
        return Ok(true);
    }

    // 3. 永久拒绝 → 引导去设置页
    if result.never_ask_again {
        app.lingyi_toolbox()
            .open_permission_settings("android.permission.CAMERA".to_string())
            .map_err(|e| e.to_string())?;
    }

    Ok(false)
}
```

### 3. 打开应用详情 / 浏览器

```rust
#[tauri::command]
fn open_about_page(app: AppHandle) -> Result<(), String> {
    // 打开本应用详情页
    app.lingyi_toolbox().open_app_details().map_err(|e| e.to_string())?;

    // 打开浏览器
    app.lingyi_toolbox()
        .open_browser("https://www.baidu.com/".to_string(), None)
        .map_err(|e| e.to_string())?;

    Ok(())
}
```

### 4. Rust 侧注意事项

- 通过 `app.lingyi_toolbox()`（`AppHandle` / `App` / `Window` 均可，凡实现 `Manager<R>` 者）获取插件实例，无需手动管理状态。
- trait 名称为 `AppsettiingExt`（注意拼写），`use tauri_plugin_lingyi_toolbox::AppsettiingExt;` 后即可使用。
- 桌面端所有方法均返回 `Err("Unsupported platform")`，Rust 侧如需跨平台，请先判断 `cfg!(mobile)` 或捕获该错误。
- 错误类型实现了 `Serialize`（序列化为字符串），通过 `.map_err(|e| e.to_string())` 即可传给前端。
- 权限申请涉及 UI 弹窗，避免在非 UI 线程直接发起（`async` 命令体内调用前可考虑 `tauri::async_runtime::spawn_blocking`）。

---

## 四、推荐的权限处理流程（重要）

```
checkPermission ──granted:true──→ ✅ 直接继续
        │
        granted:false
        ▼
requestPermission ──granted:true──→ ✅ 直接继续
        │
        granted:false
        ▼
neverAskAgain ──true──→ openPermissionSettings（引导用户手动开启）
        │
        false（临时拒绝）
        ▼
   下次再申请 / 提示用户
```

```ts
async function ensurePermission(permission: string): Promise<boolean> {
  const { granted } = await checkPermission(permission);
  if (granted) return true;

  const res = await requestPermission(permission);
  if (res.granted) return true;

  if (res.neverAskAgain) {
    // 引导用户去设置页手动开启
    await openPermissionSettings(permission);
  }
  return false;
}
```

---

## 五、支持的权限范围

### 1. 特殊权限

| 权限 | 说明 |
|---|---|
| `android.permission.GET_INSTALLED_APPS` | 读取应用列表（Android 11，国产厂商权限） |
| `android.permission.USE_FULL_SCREEN_INTENT` | 全屏通知（Android 14） |
| `android.permission.SCHEDULE_EXACT_ALARM` | 精确闹钟（Android 12） |
| `android.permission.MANAGE_MEDIA` | 管理媒体（Android 12） |
| `android.permission.MANAGE_EXTERNAL_STORAGE` | 所有文件访问（Android 11） |
| `android.permission.REQUEST_INSTALL_PACKAGES` | 安装应用（Android 8） |
| `android.permission.PICTURE_IN_PICTURE` | 画中画（Android 8） |
| `android.permission.SYSTEM_ALERT_WINDOW` | 悬浮窗（Android 6） |
| `android.permission.WRITE_SETTINGS` | 写入系统设置（Android 6） |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 忽略电池优化（Android 6） |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | 勿扰权限（Android 6） |
| `android.permission.PACKAGE_USAGE_STATS` | 应用使用情况（Android 5） |
| `android.permission.BIND_VPN_SERVICE` | VPN |

### 2. 危险权限（按版本新增节选）

- Android 17：`ACCESS_LOCAL_NETWORK`
- Android 14：`READ_MEDIA_VISUAL_USER_SELECTED`
- Android 13：`POST_NOTIFICATIONS`、`NEARBY_WIFI_DEVICES`、`BODY_SENSORS_BACKGROUND`、`READ_MEDIA_IMAGES/VIDEO/AUDIO`
- Android 12：`BLUETOOTH_SCAN/CONNECT/ADVERTISE`
- Android 10：`ACCESS_BACKGROUND_LOCATION`、`ACTIVITY_RECOGNITION`、`ACCESS_MEDIA_LOCATION`
- Android 6 基础组：`CAMERA`、`RECORD_AUDIO`、`ACCESS_FINE/COARSE_LOCATION`、`READ/WRITE_CONTACTS`、`READ/WRITE_CALENDAR`、`READ_PHONE_STATE`、`SEND/RECEIVE/READ_SMS` 等
- 健康数据：`android.permission.health.*` 系列（心率、步数、睡眠、体重、血压、血糖、血氧、体温、运动、营养、经期等读写权限）

### 3. 反射兜底机制

映射表未列出的权限（如健康类长尾权限），插件会按框架命名规律自动反射 `PermissionLists` 的静态无参方法（如 `android.permission.READ_WHEELCHAIR_PUSHES` → `getReadWheelchairPushesPermission`）。

### 4. 不支持解析的权限（返回错误）

以下权限需要宿主 Service/Receiver 类，插件无法通用解析：

- `BIND_ACCESSIBILITY_SERVICE`（无障碍服务）
- `BIND_DEVICE_ADMIN`（设备管理器）
- `BIND_NOTIFICATION_LISTENER_SERVICE`（通知栏监听）

这些需自行实现对应 Service 并通过原生代码申请。

---

## 六、项目结构

```
src-tauri/
├─ src/（Rust 插件侧）
│  ├─ lib.rs        # 插件入口：init() 注册命令与状态
│  ├─ commands.rs   # #[tauri::command] 命令定义
│  ├─ models.rs     # 请求/响应结构体（serde 序列化）
│  ├─ mobile.rs     # Android 实现：register_android_plugin 绑定 Kotlin 类
│  ├─ desktop.rs    # 桌面端占位实现（返回 UnsupportedPlatform）
│  ├─ error.rs      # 错误类型（注：当前未被 lib.rs 引用）
│  └─ build.rs      # tauri_plugin 构建脚本，生成权限定义
├─ android/
│  ├─ build.gradle.kts   # compileSdk=36, minSdk=21, 依赖 XXPermissions 28.3
│  ├─ settings.gradle    # 引入 :tauri-android 本地模块
│  └─ src/main/java/com/plugin/lingyi_toolbox/
│     └─ AppSettingPlugin.kt  # 6 个 @Command 原生命令
├─ permissions/default.toml  # 默认权限组
└─ guest-js/index.ts / dist-js/  # 前端 JS API 封装
```

构建相关：Kotlin 1.8.20、Android Gradle Plugin 8.0.2、XXPermissions 迁移至 JitPack（`com.github.getActivity:XXPermissions:28.3`），另引入 `DeviceCompat:2.6`。

---

## 七、注意事项与常见问题

1. **Manifest 必须声明权限**：`uses-permission` 不声明时，系统直接拒绝且部分机型不弹窗。特殊权限（悬浮窗、所有文件等）还需在设置页手动开启。

2. **neverAskAgain 只在申请回调中准确**：不要通过 `checkPermission` 推断永久拒绝状态，也不要在非申请场景调用 XXPermissions 的 `isDoNotAskAgainPermissions`，会污染状态导致后续无法弹窗。

3. **一次只申请一个权限**：`requestPermission` / `checkPermission` 为单权限设计，批量申请需循环调用（建议串行并做防抖）。

4. **相册选图内存问题**：`base64` 适合小图；大图请用 `filePath` + `convertFileSrc`。复制到缓存目录失败时 `filePath` 为空字符串但 `base64` 仍返回。

5. **content:// URI 不能直接渲染**：相册返回的 `uri` 仅供标识；跨进程 URI 没有读取权限，必须用返回的 `filePath` 或 `base64`。

6. **错误均为字符串**：所有 reject / Rust Error 序列化为字符串（如 `"Unsupported platform"`、`"不支持的权限: xxx"`），前端按字符串匹配处理。

7. **桌面端全部不可用**：桌面调用任一命令返回 `Unsupported platform` 错误，前端应做平台判断（如 `isAndroid()` 或 feature detect）。

8. **权限名大小写与全名**：必须传完整权限名（如 `android.permission.CAMERA`），健康权限为 `android.permission.health.XXX`，缩写或不带前缀将报"不支持的权限"。

9. **Android 版本适配**：XXPermissions 会自动处理新旧版本差异（如 Android 13 用 `READ_MEDIA_IMAGES` 替代 `READ_EXTERNAL_STORAGE`），但仍建议按版本声明 Manifest 权限。

10. **混淆**：release 包如需混淆，确认 `consumer-rules.pro` 保留 XXPermissions 相关类（框架本身已做 keep 处理）。

11. **命名细节**：Rust trait 名为 `AppsettiingExt`（拼写如此），若二次开发修改请保持 `commands.rs` / `lib.rs` 引用一致；Kotlin 包名 `com.plugin.lingyi_toolbox` 必须与 `mobile.rs` 中 `PLUGIN_IDENTIFIER` 一致。
