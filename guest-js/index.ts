import { invoke } from '@tauri-apps/api/core';

/**
 * 打开应用设置
 * @returns Promise
 * @throws 异常信息，无法打开应用详情页面:xxx错误原因
 * @example
 * import {openAppDetails} from "@tauri-apps/lingyi-toolbox"
 * try {
 *    await openAppDetails()
 * } catch(error) {
 *    console.log("报错：${error}")
 * }
 */
export async function openAppDetails(): Promise<void> {
  return invoke('plugin:lingyi-toolbox|open_app_details', {
    payload: {},
  });
}


/**
 * 打开浏览器，跳转到指定网址
 * @param url 网址，如：https://www.baidu.com/
 * @param package_name 浏览器包名称，如：com.android.chrome
 * @returns Promise
 * @throws 异常信息，多种：
 * 1. url 不能为空
 * 2. url 无效：缺少 scheme（例如 https://）
 * 3. 浏览器未安装或无法处理该网址：xxx
 * 4. 没有可用的浏览器来处理该网址
 * 5. 打开浏览器失败：xxx
 * @example
 * import {openBrowser} from "@tauri-apps/lingyi-toolbox"
 * try {
 *    // 使用默认浏览器
 *    await openBrowser("https://www.baidu.com/")
 *    // 指定浏览器
 *    await openBrowser("https://www.baidu.com/", "com.android.chrome")
 * } catch(error) {
 *    console.log("报错：${error}")
 * }
 */
export async function openBrowser(url: String, package_name?: String): Promise<void> {
  return invoke('plugin:lingyi-toolbox|open_browser', {
    payload: {url,packageName:package_name},
  });
}


export interface PickImageResult {
  /** content:// URI */
  uri: string;
  /** Base64 编码的图片数据 */
  base64: string;
  /** MIME 类型，如 image/jpeg */
  mimeType: string;
  /** 字节数 */
  size: number;
  /** 图片名称 */
  name?: string,
  /** 图片的绝对路径 */
  filePath: String
}
/**
 * 打开系统相册，选择一张图片
 * @returns 图片信息对象
 * @throws 异常信息，多种：
 * 1. 用户取消选择
 * 2. 返回数据为空
 * 3. 未获取到图片
 * 4. 无法获取图片路径: xxx
 * 5. 无法读取图片数据
 * 6. 处理图片失败: xxx
 * 7. 没有可用的相册应用: xxx
 * 8. 打开相册失败: xxx
 * @example
 * import {pickImage} from "@tauri-apps/lingyi-toolbox"
 * try {
 *    const {rui, base64, mimeType, size, name, filePath} = await pickImage()
 * } catch(error) {
 *    console.log("报错：${error}")
 * }
 */
export async function pickImage(): Promise<PickImageResult> {
  return invoke<PickImageResult>('plugin:lingyi-toolbox|pick_image', {
    payload: {},
  });
}


export interface RequestPermissionResult {
  /** 是否已授予该权限 */
  granted: boolean;
  /** 是否永久拒绝了 */
  neverAskAgain: boolean;
}
/**
 * 申请 Android 运行时权限（单个，仅危险权限）
 * @param permission Android 权限全名，例如 "android.permission.CAMERA"
 * @returns 申请结果对象
 * @throws 异常信息，多种：
 * 1. permission 不能为空
 * 2. 不支持的权限: xxx
 * 3. 申请权限失败: xxx
 * @example
 * import {requestPermission} from "@tauri-apps/lingyi-toolbox"
 * try {
 *    const {granted,neverAskAgain} = await requestPermission("android.permission.CAMERA")
 * } catch(error) {
 *    console.log("报错：${error}")
 * }
 */
export async function requestPermission(
  permission: string
): Promise<RequestPermissionResult> {
  return invoke<RequestPermissionResult>('plugin:lingyi-toolbox|request_permission', {
    payload: { permission },
  });
}


export interface CheckPermissionResult {
  /** 是否已授权 */
  granted: boolean;
}
/**
 * 检查权限
 * @param permission Android 权限全名，例如 "android.permission.CAMERA"
 * @returns 检查结果对象
 * @throws 异常信息，多种：
 * 1. permission 不能为空
 * 2. 不支持的权限: xxx
 * 3. 检查权限失败: xxx
 * @example
 * import {checkPermission} from "@tauri-apps/lingyi-toolbox"
 * try {
 *    const {granted} = await checkPermission("android.permission.CAMERA")
 * } catch(error) {
 *    console.log("报错：${error}")
 * }
 */
export function checkPermission(
  permission: string
): Promise<CheckPermissionResult> {
  return invoke('plugin:lingyi-toolbox|check_permission', { payload: { permission } });
}

/**
 * 通用权限设置页面跳转
 * @param permission Android 权限全名，例如 "android.permission.POST_NOTIFICATIONS"
 * @returns Promise
 * @throws 异常信息，多种：
 * 1. permission 不能为空：本命令必须传入要跳转设置页的权限全名
 * 2. 不支持的权限: xxx
 * 3. 没有可处理的设置页面: xxx
 * 4. 打开权限设置页面失败: xxx
 * @example
 * import {openPermissionSettings} from "@tauri-apps/lingyi-toolbox"
 * try {
 *    await openPermissionSettings("android.permission.CAMERA")
 * } catch(error) {
 *    console.log("报错：${error}")
 * }
 */
export async function openPermissionSettings(permission: string): Promise<void> {
  return invoke('plugin:lingyi-toolbox|open_permission_settings', { payload: { permission } });
}