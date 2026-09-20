// 引入 serde 的反序列化 trait
use serde::de::DeserializeOwned;
// 引入 Tauri 插件 API 和应用句柄
use tauri::{plugin::PluginApi, AppHandle, Runtime};
// 引入 PhantomData，用于持有未使用的泛型参数 R
// 使用 fn() -> R 形式，因为函数指针天然是 Send + Sync 的
use std::marker::PhantomData;



/// 桌面端 API 访问句柄（空实现）
/// 添加泛型参数 R 以与 mobile 端保持接口一致，避免 lib.rs 中编译失败
/// 使用 PhantomData<fn() -> R> 而非 PhantomData<R>，因为函数指针类型
/// 天然满足 Send + Sync + 'static，无需对 R 额外添加线程安全约束
pub struct LingyiBox<R: Runtime> {
    // PhantomData<fn() -> R> 是零大小类型，仅用于关联泛型 R
    // fn() -> R 是函数指针类型，编译器始终视其为 Send + Sync
    _marker: PhantomData<fn() -> R>,
}

// 为桌面端 实现业务方法
impl<R: Runtime> LingyiBox<R> {
    pub fn open_app_details(&self) -> crate::Result<()> {
        Err(crate::Error::UnsupportedPlatform)
    }
    pub fn open_browser(&self, _url: String, _package_name: Option<String>) -> crate::Result<()> {
        Err(crate::Error::UnsupportedPlatform)
    }
    /// 桌面端不支持系统相册
    pub fn pick_image(&self) -> crate::Result<crate::models::PickImageResponse> {
        Err(crate::Error::UnsupportedPlatform)
    }
    /// 桌面端不支持权限申请
    pub fn request_permission(
        &self,
        _permission: String,
    ) -> crate::Result<crate::models::RequestPermissionResponse> {
        Err(crate::Error::UnsupportedPlatform)
    }
    /// 桌面端不支持权限检查
    pub fn check_permission(
        &self,
        _permission: String,
    ) -> crate::Result<crate::models::CheckPermissionResponse> {
        Err(crate::Error::UnsupportedPlatform)
    }
    /// 桌面端不支持权限设置页跳转
    pub fn open_permission_settings(
        &self,
        _permission: String,
    ) -> crate::Result<()> {
        Err(crate::Error::UnsupportedPlatform)
    }
}


/// 初始化桌面端插件实现
/// 桌面端不需要实际功能，因此返回空实现
pub fn init<R: Runtime, C: DeserializeOwned>(
    // 应用句柄，当前未使用
    _app: &AppHandle<R>,
    // 插件 API，当前未使用
    _api: PluginApi<R, C>,
) -> crate::Result<LingyiBox<R>> {
    // 返回空的桌面端实现，PhantomData 仅用于告诉编译器此结构体"关联"了泛型 R
    Ok(LingyiBox { _marker: PhantomData })
}