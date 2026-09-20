// 引入 serde 的序列化/反序列化宏，用于 JSON 与 Rust 结构体之间的转换
use serde::{Deserialize, Serialize};


// ============ 打开应用详情 ============
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenAppDetailsRequest {}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenAppDetailsResponse {}


// ============ 打开浏览器 ============
#[derive(Debug, Deserialize,Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenBrowserRequest {
    // 目标网址
    pub url: String,
    // 可选，指定浏览器包名
    pub package_name: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenBrowserResponse {}

// ============ 选择图片 ============
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PickImageRequest {}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PickImageResponse {
    /// 图片的 content:// URI
    pub uri: String,
    /// Base64 编码的图片数据（NO_WRAP，无换行）
    pub base64: String,
    /// MIME 类型，如 image/jpeg
    pub mime_type: String,
    /// 图片字节数
    pub size: u64,
    // 图片名称
    #[serde(default)]
    pub name: Option<String>,
    // 图片的绝对路径
    pub file_path: String
}

// ============ 通用权限申请 ============
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestPermissionRequest {
    /// Android 权限全名（必传），例如 "android.permission.CAMERA"
    pub permission: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestPermissionResponse {
    /// 权限是否被授予
    pub granted: bool,
    /// 用户是否勾选了"不再询问"选项（仅在权限申请回调中判断才准确）
    #[serde(default)]
    pub never_ask_again: bool,
}


// ============ 通用权限检查 ============
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CheckPermissionRequest {
    /// Android 权限全名（必传），例如 "android.permission.CAMERA"
    pub permission: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CheckPermissionResponse {
    /// 是否已授予该权限
    pub granted: bool,
}


// ============ 通用权限设置页面跳转 ============
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenPermissionSettingsRequest {
    /// Android 权限全名（必传，不允许缺省），例如 "android.permission.SYSTEM_ALERT_WINDOW"
    pub permission: String,
}