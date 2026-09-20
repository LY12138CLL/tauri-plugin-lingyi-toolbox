// 定义本插件支持的所有命令名称列表
// 这些名称将用于自动生成权限文件和前端调用类型定义
const COMMANDS: &[&str] = &[
    "open_app_details",
    "open_browser",
    "pick_image",
    "request_permission",
    "check_permission",
    "open_permission_settings", 
    ];

/// 构建脚本主函数，在编译时执行
fn main() {
    // 使用 tauri_plugin 构建器生成插件元数据
    tauri_plugin::Builder::new(COMMANDS)
        // 指定 Android 原生代码的相对路径
        .android_path("android")
        // 指定 iOS 原生代码的相对路径（即使无 iOS 实现也保留）
        .ios_path("ios")
        // 执行构建，生成权限定义等文件
        .build();
}