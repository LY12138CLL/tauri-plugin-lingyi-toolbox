// 引入 Tauri 的核心类型：AppHandle（应用句柄）和 Runtime（运行时抽象）
use tauri::{AppHandle, Runtime};

// 引入同 crate 下的模型模块中定义的数据结构
use crate::models::*;
// 引入为 AppHandle 扩展的 lingyi_toolbox() 方法
use crate::{ LingyiBoxExt};
// 引入本 crate 的 Result 类型别名
use crate::Result;

// 打开应用详情
#[tauri::command]
pub(crate) async fn open_app_details<R: Runtime>(
    app: AppHandle<R>,
    _payload: OpenAppDetailsRequest,
) -> Result<OpenAppDetailsResponse> {
    app.lingyi_toolbox().open_app_details()?;
    Ok(OpenAppDetailsResponse { })
}

// 打开浏览器
#[tauri::command]
pub(crate) async fn open_browser<R: Runtime>(
    app: AppHandle<R>,
    _payload: OpenBrowserRequest,
) -> Result<OpenBrowserResponse> {
    app.lingyi_toolbox().open_browser(_payload.url,_payload.package_name)?;
    Ok(OpenBrowserResponse {  })
}
// 选择单张图片
#[tauri::command]
pub(crate) async fn pick_image<R: Runtime>(
    app: AppHandle<R>,
    _payload: PickImageRequest,
) -> Result<PickImageResponse> {
    let response = app.lingyi_toolbox().pick_image()?;
    Ok(response)
}

// 通用权限申请
#[tauri::command]
pub(crate) async fn request_permission<R: Runtime>(
    app: AppHandle<R>,
    payload: RequestPermissionRequest,
) -> Result<RequestPermissionResponse> {
    let response = app.lingyi_toolbox().request_permission(payload.permission)?;
    Ok(response)
}

// 通用权限检查
#[tauri::command]
pub(crate) async fn check_permission<R: Runtime>(
    app: AppHandle<R>,
    payload: CheckPermissionRequest,
) -> Result<CheckPermissionResponse> {
    let response = app.lingyi_toolbox().check_permission(payload.permission)?;
    Ok(response)
}

// 通用权限设置页面跳转命令
#[tauri::command]
pub(crate) async fn open_permission_settings<R: Runtime>(
    app: AppHandle<R>,
    payload: OpenPermissionSettingsRequest,
) -> Result<()> {
    app.lingyi_toolbox().open_permission_settings(payload.permission)?;
    Ok(())
}