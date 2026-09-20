// 公开 commands 模块，使其中的命令函数可被 Tauri 宏引用
pub mod commands;
// 公开 models 模块，使数据结构可被外部使用
pub mod models;

// 仅在桌面平台编译时包含 desktop 模块
#[cfg(desktop)]
mod desktop;
// 仅在移动平台（Android/iOS）编译时包含 mobile 模块
#[cfg(mobile)]
mod mobile;

// 引入 Tauri 插件构建器和应用管理器
use tauri::plugin::Builder;
// 引入 Manager trait（提供 state 方法）和 Runtime 泛型
use tauri::{Manager, Runtime};

// 根据编译目标条件引入对应的 LingyiBox 类型
#[cfg(desktop)]
use desktop::LingyiBox;
#[cfg(mobile)]
use mobile::LingyiBox;

// 引入 serde 的序列化相关 trait，用于手动实现 Error 的序列化
use serde::{Serialize, Serializer};

/// 自定义错误枚举类型
#[derive(Debug, thiserror::Error)]
pub enum Error {
    // 平台不支持错误，带有描述信息
    #[error("Unsupported platform")]
    UnsupportedPlatform,
    // 透明包装 Tauri 内部错误，自动实现 From 转换
    #[error(transparent)]
    Tauri(#[from] tauri::Error),
    // 移动端插件调用错误（仅移动平台编译时包含）
    // 必须包含此变体，否则 run_mobile_plugin 返回的 PluginInvokeError
    // 无法通过 map_err(Into::into) 自动转换
    #[cfg(mobile)]
    #[error(transparent)]
    PluginInvoke(
        #[from]
        tauri::plugin::mobile::PluginInvokeError,
    ),
}

// 手动为 Error 实现 Serialize
// Tauri v2 要求命令返回的错误类型必须能序列化为 JSON 才能传递给前端
impl Serialize for Error {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        // 将错误序列化为简单字符串，前端收到形如 "Unsupported platform" 的文本
        serializer.serialize_str(&self.to_string())
    }
}

// 定义本 crate 的 Result 类型别名为标准 Result，错误类型为自定义 Error
pub type Result<T> = std::result::Result<T, Error>;

/// 为 AppHandle/App/Window 扩展 lingyi_toolbox() 方法的 trait
pub trait LingyiBoxExt<R: Runtime> {
    // 获取 NotificationSettings 实例的引用
    fn lingyi_toolbox(&self) -> &LingyiBox<R>;
}

// 为所有实现了 Manager<R> 的类型实现该 trait
// 这包括 AppHandle、App、Window 等
impl<R: Runtime, T: Manager<R>> LingyiBoxExt<R> for T {
    // 从 Tauri 状态管理中获取 LingyiBox 实例
    fn lingyi_toolbox(&self) -> &LingyiBox<R> {
        // state() 从 Tauri 托管状态中取出值
        // inner() 获取内部引用
        self.state::<LingyiBox<R>>().inner()
    }
}

/// 插件初始化函数
/// 返回配置好的 TauriPlugin，供应用注册使用
pub fn init<R: Runtime>() -> tauri::plugin::TauriPlugin<R> {
    // 使用 Builder 构造插件,"lingyi-toolbox"，一般为插件名称，用于前端调用时的|前端的内容
    Builder::new("lingyi-toolbox")
        // 注册前端可调用的命令处理器
        .invoke_handler(tauri::generate_handler![
            // 注册打开通知设置的命令，open_app_details，用于前端调用时|后面的内容
            commands::open_app_details,
            commands::open_browser,
            commands::pick_image,
            commands::request_permission,
            commands::check_permission,
            commands::open_permission_settings,
        ])
        // setup 在插件加载时执行，用于初始化状态
        .setup(|app, api| {
            // 根据编译目标初始化对应平台的实现
            #[cfg(mobile)]
            let app_settiing = mobile::init(app, api)?;
            #[cfg(desktop)]
            let app_settiing = desktop::init(app, api)?;
            // 将初始化后的实例注册到 Tauri 状态管理中
            // 这样后续可通过 app.app_settiing() 获取
            app.manage(app_settiing);
            // 返回 Ok 表示 setup 成功
            Ok(())
        })
        // 构建并返回插件实例
        .build()
}