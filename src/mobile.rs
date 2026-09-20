// 引入 serde 的反序列化 trait，用于解析配置参数
use serde::de::DeserializeOwned;
// 引入 Tauri 插件 API 相关类型
use tauri::{
    // PluginApi 用于注册原生插件，PluginHandle 用于持有插件句柄
    plugin::{PluginApi, PluginHandle},
    // AppHandle 是应用句柄，Runtime 是运行时泛型参数
    AppHandle, Runtime,
};

// 引入同 crate 下的模型模块
use crate::models::*;

// Android 插件标识符常量
// 此字符串必须与 Android 端 Kotlin 文件的包名一致
// 在 Tauri v2 最新版中，Android 不需要 android_plugin_binding! 宏
// 只有 iOS 才需要 ios_plugin_binding! 宏进行绑定
const PLUGIN_IDENTIFIER: &str = "com.plugin.lingyi_toolbox";

/// 移动端 API 访问句柄
/// 包装 PluginHandle，提供类型安全的方法调用
pub struct LingyiBox<R: Runtime>(PluginHandle<R>);

/// 初始化移动端插件实现
/// 此函数在插件 setup 阶段被调用
pub fn init<R: Runtime, C: DeserializeOwned>(
    // 应用句柄，当前未使用但保留参数位置
    _app: &AppHandle<R>,
    // Tauri 提供的插件 API，用于注册 Android 原生插件
    api: PluginApi<R, C>,
) -> crate::Result<LingyiBox<R>> {
    // 注册 Android 原生插件
    // 第一个参数是插件标识符（即 Kotlin 文件的包名）
    // 第二个参数是 Kotlin 插件类的类名
    let handle = api.register_android_plugin(PLUGIN_IDENTIFIER, "ToolBoxPlugin")?;
    // 使用注册成功的句柄构造 LingyiBox 并返回
    Ok(LingyiBox(handle))
}


// 为 LingyiBox 实现业务方法
impl<R: Runtime> LingyiBox<R> {
    // 打开应用详情
    pub fn open_app_details(&self) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("openAppDetails", OpenAppDetailsRequest {})
            .map_err(Into::into)
    }
    // 打开浏览器
    pub fn open_browser(&self,url: String, package_name: Option<String>) -> crate::Result<()> {
        self.0
            // 返回结果，models.rs中定义的结构体
            .run_mobile_plugin("openBrowser",OpenBrowserRequest {url,package_name})
            // 报错信息
            .map_err(Into::into)
    }
    // 打开系统相册选择单张图片
    pub fn pick_image(&self) -> crate::Result<PickImageResponse> {
        self.0
            .run_mobile_plugin("pickImage", PickImageRequest {})
            .map_err(Into::into)
    }
    // 通用权限申请
    pub fn request_permission(&self, permission: String) -> crate::Result<RequestPermissionResponse> {
        self.0
            .run_mobile_plugin(
                "requestPermission",
                RequestPermissionRequest { permission },
            )
            .map_err(Into::into)
    }
    // 通用权限检查
    pub fn check_permission(&self, permission: String) -> crate::Result<CheckPermissionResponse> {
        self.0
            .run_mobile_plugin("checkPermission", CheckPermissionRequest { permission })
            .map_err(Into::into)
    }

    // 通用权限设置页面跳转
    pub fn open_permission_settings(&self, permission: String) -> crate::Result<()> {
        self.0
            .run_mobile_plugin(
                "openPermissionSettings",
                OpenPermissionSettingsRequest { permission },
            )
            .map_err(Into::into)
    }
}