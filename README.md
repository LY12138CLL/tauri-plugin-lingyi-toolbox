# tauri-plugin-lingyi-toolbox 插件使用说明（Tauri2）

一款基于 Tauri2 原生插件，封装了应用设置跳转、浏览器跳转、系统相册选图、权限申请 / 检查 / 设置页跳转等常用能力。目前只支持Android，桌面端为占位实现（调用将返回 `Unsupported platform`）。

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

在 `src-tauri/src/lib.rs`中注册插件：

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

在`src-tauri\capabilities\default.json`中添加：

方式一：使用默认权限组（推荐，一键开启全部命令）

```json
{
  "permissions": ["lingyi-toolbox:default"]
}
```

方式二：按需单独开启（粒度更细，最小权限原则）

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

### 3. AndroidManifest 声明权限

若需要使用权限相关的功能，则需要在`src-tauri/gen/android/app/src/main/AndroidManifest.xml`中添加相相对于的权限：

```xml
<manifest ...>
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    ...
</manifest>
```

---

## 三、API 详细说明

### 1. openAppDetails() 打开应用详情页

JavaScript：

```ts
import {openAppDetails} from "tauri-plugin-lingyi-toolbox"
try {
  await openAppDetails();
} catch (error) {
  console.log("报错:", error); // "无法打开应用详情页面: xxx"
}
```

rust：

~~~rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;
async fn open_app_details_cmd(app: AppHandle) -> Result<(), String> {
    app.lingyi_toolbox()
        .open_app_details()
        .map_err(|e| e.to_string())
}
~~~

### 2. openBrowser(url, packageName?) 打开浏览器

JavaScript：

```ts
import {openBrowser} from "tauri-plugin-lingyi-toolbox"
// 使用默认浏览器
await openBrowser("https://www.baidu.com/");

// 指定浏览器
await openBrowser("https://www.baidu.com/", "com.android.chrome");
```

rust：

~~~rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;
async fn open_browser_cmd(app: AppHandle, url: String, package_name: Option<String>) -> Result<(), String> {
    app.lingyi_toolbox()
        .open_browser(url, package_name)
        .map_err(|e| e.to_string())
}
~~~

| 参数 | 类型 | 说明 |
|---|---|---|
| url | String | 目标网址，**必传，必须带 scheme**（如 `https://`） |
| packageName | String? | 可选，指定浏览器包名 |

可能的异常：`url 不能为空`、`url 无效：缺少 scheme`、`浏览器未安装或无法处理该网址: xxx`、`没有可用的浏览器来处理该网址`、`打开浏览器失败: xxx`。

### 3. pickImage() 系统相册选图

JavaScript：

```ts
import {pickImage} from "tauri-plugin-lingyi-toolbox"
const { uri, base64, mimeType, size, name, filePath } = await pickImage();
```

rust：

~~~rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;
async fn pick_image_cmd(app: AppHandle) -> Result<tauri_plugin_lingyi_toolbox::models::PickImageResponse, String> {
    app.lingyi_toolbox()
        .pick_image()
        .map_err(|e| e.to_string())
}
~~~

返回值：

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

JavaScript：

```ts
import {checkPermission} from "tauri-plugin-lingyi-toolbox"
const { granted } = await checkPermission("android.permission.CAMERA");
```

rust：

~~~rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;
async fn check_permission_cmd(
    app: AppHandle,
    permission: String,
) -> Result<tauri_plugin_lingyi_toolbox::models::CheckPermissionResponse, String> {
    app.lingyi_toolbox()
        .check_permission(permission)
        .map_err(|e| e.to_string())
}
~~~
### 5. requestPermission(permission) 申请权限

JavaScript：

```ts
import {requestPermission} from "tauri-plugin-lingyi-toolbox"
const { granted, neverAskAgain } = await requestPermission("android.permission.CAMERA");
```

rust：

~~~rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;
async fn request_camera_cmd(app: AppHandle) -> Result<tauri_plugin_lingyi_toolbox::models::RequestPermissionResponse, String> {
    app.lingyi_toolbox()
        .request_permission("android.permission.CAMERA".to_string())
        .map_err(|e| e.to_string())
}
~~~

| 返回字段 | 说明 |
|---|---|
| granted | 权限是否被授予 |
| neverAskAgain | 是否永久拒绝 |

### 6. openPermissionSettings(permission) 跳转权限设置页

JavaScript：


```ts
import {openPermissionSettings} from "tauri-plugin-lingyi-toolbox"
await openPermissionSettings("android.permission.CAMERA");
```

rust：

~~~rust
use tauri::AppHandle;
use tauri_plugin_lingyi_toolbox::AppsettiingExt;
async fn open_permission_settings_cmd(app: AppHandle, permission: String) -> Result<(), String> {
    app.lingyi_toolbox()
        .open_permission_settings(permission)
        .map_err(|e| e.to_string())
}
~~~

框架会自动根据权限类型选择最佳设置页（悬浮窗 → 悬浮窗设置页、所有文件访问 → 文件管理页、通知 → 通知设置页、普通危险权限 → 应用权限管理页），并自带 Intent 兜底。

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

## 七、注意事项与常见问题

1. **Manifest 必须声明权限**：`uses-permission` 不声明时，系统直接拒绝且部分机型不弹窗。特殊权限（悬浮窗、所有文件等）还需在设置页手动开启。
2. **一次只申请一个权限**：`requestPermission` / `checkPermission` 为单权限设计，批量申请需循环调用（建议串行并做防抖）。
3. **相册选图内存问题**：`base64` 适合小图；大图请用 `filePath` + `convertFileSrc`。复制到缓存目录失败时 `filePath` 为空字符串但 `base64` 仍返回。
4. **content:// URI 不能直接渲染**：相册返回的 `uri` 仅供标识；跨进程 URI 没有读取权限，必须用返回的 `filePath` 或 `base64`。
5. **异常信息字符串**：所有 reject / Rust Error 序列化为字符串（如 `"Unsupported platform"`、`"不支持的权限: xxx"`），前端按字符串匹配处理。
6. **Android 版本适配**：XXPermissions 会自动处理新旧版本差异（如 Android 13 用 `READ_MEDIA_IMAGES` 替代 `READ_EXTERNAL_STORAGE`），但仍建议按版本声明 Manifest 权限。





