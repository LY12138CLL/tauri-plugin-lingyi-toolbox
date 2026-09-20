// 包名必须与 build.gradle.kts 中的 namespace 一致，且多词用下划线链接
package com.plugin.lingyi_toolbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.content.ActivityNotFoundException
import java.io.File

// Tauri 提供的 Activity 结果封装
import app.tauri.plugin.PluginManager
import app.tauri.plugin.PluginManager.ActivityResultCallback
import androidx.activity.result.ActivityResult

// Tauri 注解与基础类型
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin
import app.tauri.plugin.JSObject

// XXPermissions 框架
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission


@TauriPlugin
class ToolBoxPlugin(private val activity: Activity) : Plugin(activity) {

    /**
     * 打开当前应用详情
     *
     * 输入：无参数
     * 输出：resolve 无数据；reject 返回错误原因字符串
     */
    @Command
    fun openAppDetails(invoke: Invoke) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject("无法打开应用详情页面: ${e.message}")
        }
    }

    /**
     * 打开浏览器指定网址
     *
     * 输入：
     *   - url : String?          —— 目标网址（必传，需带 scheme，如 https://）
     *   - packageName : String?  —— 指定浏览器包名（可选）
     * 输出：resolve 无数据；reject 返回错误原因字符串
     */
    @Command
    fun openBrowser(invoke: Invoke) {
        val args = invoke.getArgs()
        val url = args.optString("url", null)
        val packageName =
            if (args.has("packageName") && !args.isNull("packageName"))
                args.optString("packageName")
            else null

        if (url.isNullOrBlank()) {
            invoke.reject("url 不能为空")
            return
        }

        val uri = Uri.parse(url)
        if (uri.scheme == null) {
            invoke.reject("url 无效：缺少 scheme（例如 https://）")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!packageName.isNullOrBlank()) setPackage(packageName)
            }
            activity.startActivity(intent)
            invoke.resolve()
        } catch (e: ActivityNotFoundException) {
            val msg = if (!packageName.isNullOrBlank())
                "浏览器未安装或无法处理该网址：$packageName"
            else
                "没有可用的浏览器来处理该网址"
            invoke.reject(msg)
        } catch (e: Exception) {
            invoke.reject("打开浏览器失败：${e.message}")
        }
    }

    /**
     * 打开系统相册，选择单张图片
     *
     * 输入：无参数
     * 输出（resolve）：JSObject
     *   - uri : String      —— 原始 content:// URI（仅作标识，不能直接用于 img 渲染）
     *   - base64 : String   —— 图片 base64（NO_WRAP，无换行）
     *   - filePath : String —— 已复制到 App 私有缓存目录的绝对路径，供 convertFileSrc 使用
     *   - mimeType : String —— MIME 类型，如 image/jpeg
     *   - size : Int        —— 图片字节数
     *   - name : String     —— 图片文件名
     * 输出（reject）：String —— 错误原因（如用户取消选择）
     */
    @Command
    fun pickImage(invoke: Invoke) {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }

        try {
            PluginManager.startActivityForResult(intent, object : ActivityResultCallback {
                // 防止 onResult 被重复触发（Activity 重建 / 多次回调）
                private var handled = false

                override fun onResult(result: ActivityResult) {
                    if (handled) return
                    handled = true

                    // 1. 先判断结果码
                    if (result.resultCode != Activity.RESULT_OK) {
                        invoke.reject("用户取消选择")
                        return
                    }

                    // 2. 严格判断 data 与 data.data
                    val data = result.data
                    if (data == null) {
                        invoke.reject("返回数据为空")
                        return
                    }

                    val uri: Uri = data.data ?: run {
                        invoke.reject("未获取到图片")
                        return
                    }

                    // 3. 第一时间把 Uri 转成 String，后续不再依赖 uri 对象
                    val uriString: String = try {
                        uri.toString()
                    } catch (e: Exception) {
                        invoke.reject("无法获取图片路径: ${e.message}")
                        return
                    }

                    try {
                        val contentResolver = activity.contentResolver

                        // 4. mimeType
                        val mimeType = try {
                            contentResolver.getType(uri) ?: "image/*"
                        } catch (_: Exception) {
                            "image/*"
                        }

                        // 5. 文件名（查询失败不影响主流程）
                        var fileName: String = ""
                        try {
                            contentResolver.query(
                                uri,
                                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                                null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val idx = cursor.getColumnIndex(
                                        android.provider.OpenableColumns.DISPLAY_NAME
                                    )
                                    if (idx >= 0) {
                                        cursor.getString(idx)?.let { fileName = it }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // 忽略，文件名留空
                        }

                        // 6. 读取字节（保留 base64 原有逻辑）
                        val bytes = try {
                            contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } catch (e: Exception) {
                            null
                        }

                        if (bytes == null || bytes.isEmpty()) {
                            invoke.reject("无法读取图片数据")
                            return
                        }

                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        // ============ 复制到 App 私有缓存目录 ============
                        // 文件名兜底：查不到就按 mimeType 猜后缀，用时间戳命名
                        val safeFileName: String = if (fileName.isBlank()) {
                            val ext = when (mimeType.lowercase()) {
                                "image/png" -> ".png"
                                "image/webp" -> ".webp"
                                "image/gif" -> ".gif"
                                "image/bmp" -> ".bmp"
                                else -> ".jpg"
                            }
                            "picked_${System.currentTimeMillis()}$ext"
                        } else {
                            fileName
                        }

                        val cacheDir = File(activity.cacheDir, "picked_images")
                        if (!cacheDir.exists()) cacheDir.mkdirs()

                        // 防止同名覆盖：若已存在，加时间戳前缀
                        var targetFile = File(cacheDir, safeFileName)
                        if (targetFile.exists()) {
                            targetFile = File(cacheDir, "${System.currentTimeMillis()}_$safeFileName")
                        }

                        val filePath: String = try {
                            targetFile.outputStream().use { output ->
                                output.write(bytes)
                            }
                            targetFile.absolutePath
                        } catch (e: Exception) {
                            // 复制失败不影响 base64 返回，filePath 留空
                            ""
                        }
                        // ============ 复制结束 ============

                        val resultObj = JSObject().apply {
                            put("uri", uriString)
                            put("base64", base64)
                            put("filePath", filePath)
                            put("mimeType", mimeType)
                            put("size", bytes.size)
                            put("name", if (safeFileName.isBlank()) fileName else safeFileName)
                        }
                        invoke.resolve(resultObj)

                    } catch (e: Exception) {
                        invoke.reject("处理图片失败: ${e.message}")
                    }
                }
            })
        } catch (e: ActivityNotFoundException) {
            invoke.reject("没有可用的相册应用: ${e.message}")
        } catch (e: Exception) {
            invoke.reject("打开相册失败: ${e.message}")
        }
    }

    /* ==================================================================
     * 以下三个命令基于 XXPermissions 框架实现
     * （支持危险权限 + 特殊权限，如悬浮窗、通知、所有文件访问等）
     * 文档：https://github.com/getActivity/XXPermissions
     * ================================================================== */

    /**
     * 通用权限申请（基于 XXPermissions 框架）
     *
     * 功能：申请单个 Android 权限，危险权限和特殊权限均可
     *
     * 输入：
     *   - permission : String? —— Android 权限全名（必传）
     *
     * 输出（resolve）：JSObject
     *   - granted : Boolean       —— 权限是否被授予
     *   - neverAskAgain : Boolean —— 用户是否勾选了"不再询问"
     *     （仅在权限申请的回调中判断才准确，系统限制）
     *
     * 输出（reject）：String —— 错误原因
     *
     * 说明：本命令是判断"临时拒绝 / 永久拒绝"的唯一可靠入口。
     *      neverAskAgain == true 时前端应引导用户去 openPermissionSettings。
     */
    @Command
    fun requestPermission(invoke: Invoke) {
        val args = invoke.getArgs()
        val permissionName = args.optString("permission", null)

        // 1. 参数校验
        if (permissionName.isNullOrBlank()) {
            invoke.reject("permission 不能为空")
            return
        }

        // 2. 权限名 → IPermission 对象（映射表 + 反射兜底）
        val permission = findPermission(permissionName) ?: run {
            invoke.reject("不支持的权限: $permissionName")
            return
        }

        // 3. 发起权限申请，结果在回调中返回
        try {
            XXPermissions.with(activity)
                .permission(permission)
                .request { grantedList, deniedList ->
                    // grantedList : 本次授予的权限列表（List<IPermission>）
                    // deniedList  : 本次拒绝的权限列表（List<IPermission>），拒绝即未授予
                    val granted = deniedList.isNullOrEmpty()

                    // 必须在权限申请的回调方法中调用才有效果（框架要求）
                    val neverAskAgain = if (granted) {
                        false
                    } else {
                        XXPermissions.isDoNotAskAgainPermissions(activity, deniedList)
                    }

                    invoke.resolve(JSObject().apply {
                        put("granted", granted)
                        put("neverAskAgain", neverAskAgain)
                    })
                }
        } catch (e: Exception) {
            invoke.reject("申请权限失败: ${e.message}")
        }
    }

    /**
     * 通用权限检查（基于 XXPermissions 框架，单个权限）
     *
     * 功能：只检查单个 Android 权限「是否已授权」。
     *
     * 输入：
     *   - permission : String? —— Android 权限全名（必传），
     *     例如 "android.permission.CAMERA"
     *
     * 输出（resolve）：JSObject
     *   - granted : Boolean —— 是否已授权
     *
     * 输出（reject）：String —— 错误原因
     *   （permission 为空 / 不支持的权限 / 检查过程异常）
     *
     * 【设计说明 - 方案 C】
     * 本命令刻意不区分"临时拒绝 / 永久拒绝"，原因：
     *   1. XXPermissions.isDoNotAskAgainPermissions 在申请之外调用会污染状态，
     *      导致后续 requestPermission 无法弹窗；
     *   2. Android 官方 API 又无法区分"从未申请"与"永久拒绝"；
     *   3. 而 requestPermission 的 neverAskAgain 返回值已经能准确表达"永久拒绝"。
     * 因此，把"临时/永久"的判断完全交给 requestPermission 处理。
     *
     * 推荐前端调用流程：
     *   1. checkPermission → granted 为 true 直接返回；
     *   2. granted 为 false → 调 requestPermission；
     *   3. requestPermission 返回 neverAskAgain 为 true → 调 openPermissionSettings。
     */
    @Command
    fun checkPermission(invoke: Invoke) {
        val args = invoke.getArgs()
        val permissionName = args.optString("permission", null)

        // 1. 参数校验
        if (permissionName.isNullOrBlank()) {
            invoke.reject("permission 不能为空")
            return
        }

        // 2. 权限名 → IPermission 对象
        val permission = findPermission(permissionName) ?: run {
            invoke.reject("不支持的权限: $permissionName")
            return
        }

        // 3. 只读检查授权状态（isGrantedPermission 无副作用，可放心调用）
        try {
            val granted = XXPermissions.isGrantedPermission(activity, permission)

            invoke.resolve(JSObject().apply {
                put("granted", granted)
            })
        } catch (e: Exception) {
            invoke.reject("检查权限失败: ${e.message}")
        }
    }

    /**
     * 通用权限设置页面跳转（基于 XXPermissions 框架）
     *
     * 功能：跳转到指定权限对应的系统设置页面。
     *       框架内部会根据权限类型选择最佳设置页：
     *       - 悬浮窗          → 悬浮窗权限设置页
     *       - 所有文件访问    → 所有文件管理页
     *       - 通知            → 通知权限设置页
     *       - 普通危险权限    → 应用权限管理页
     *       并且自带 Intent 跳转兜底机制（一个 Intent 失败自动尝试下一个）。
     *
     * 输入：
     *   - permission : String? —— Android 权限全名（必传，不传直接报错），
     *     例如 "android.permission.SYSTEM_ALERT_WINDOW"
     *
     * 输出：resolve 无数据；reject 返回错误原因字符串
     *   （permission 为空 / 不支持的权限 / 跳转过程异常）
     */
    @Command
    fun openPermissionSettings(invoke: Invoke) {
        val args = invoke.getArgs()
        val permissionName = args.optString("permission", null)

        // 1. 参数校验（本命令必填，不允许缺省跳转应用详情页）
        if (permissionName.isNullOrBlank()) {
            invoke.reject("permission 不能为空：本命令必须传入要跳转设置页的权限全名")
            return
        }

        // 2. 权限名 → IPermission 对象
        val permission = findPermission(permissionName) ?: run {
            invoke.reject("不支持的权限: $permissionName")
            return
        }

        // 3. 跳转到该权限的最佳设置页面
        try {
            XXPermissions.startPermissionActivity(activity, permission)
            invoke.resolve()
        } catch (e: ActivityNotFoundException) {
            invoke.reject("没有可处理的设置页面: ${e.message}")
        } catch (e: Exception) {
            invoke.reject("打开权限设置页面失败: ${e.message}")
        }
    }

    /**
     * 把权限名字符串解析为 XXPermissions 的 IPermission 对象
     *
     * 解析策略：
     * 1. 优先走显式映射表（常用权限，编译期校验，混淆安全）
     * 2. 未命中时按框架命名规律反射 PermissionLists 的静态无参方法兜底
     *    （框架 getter 命名规律：get + 权限名去前缀转驼峰 + Permission）
     *
     * 输入：name : String —— Android 权限全名
     * 输出：IPermission?  —— 解析成功返回权限对象，失败返回 null
     *
     * 不支持通用解析的权限（需要宿主类，返回 null）：
     * - BIND_ACCESSIBILITY_SERVICE          无障碍服务（需要 AccessibilityService 类）
     * - BIND_DEVICE_ADMIN                   设备管理器（需要 DeviceAdminReceiver 类）
     * - BIND_NOTIFICATION_LISTENER_SERVICE  通知栏监听（需要 NotificationListenerService 类）
     */
    private fun findPermission(name: String): IPermission? {
        when (name) {
            // ==================== 特殊权限 ====================
            // 读取应用列表权限（Android 11，国产厂商权限）
            "android.permission.GET_INSTALLED_APPS" ->
                return PermissionLists.getGetInstalledAppsPermission()
            // 全屏通知权限（Android 14）
            "android.permission.USE_FULL_SCREEN_INTENT" ->
                return PermissionLists.getUseFullScreenIntentPermission()
            // 闹钟提醒权限（Android 12）
            "android.permission.SCHEDULE_EXACT_ALARM" ->
                return PermissionLists.getScheduleExactAlarmPermission()
            // 管理媒体权限（Android 12）
            "android.permission.MANAGE_MEDIA" ->
                return PermissionLists.getManageMediaPermission()
            // 所有文件访问权限（Android 11）
            "android.permission.MANAGE_EXTERNAL_STORAGE" ->
                return PermissionLists.getManageExternalStoragePermission()
            // 安装应用权限（Android 8）
            "android.permission.REQUEST_INSTALL_PACKAGES" ->
                return PermissionLists.getRequestInstallPackagesPermission()
            // 画中画权限（Android 8）
            "android.permission.PICTURE_IN_PICTURE" ->
                return PermissionLists.getPictureInPicturePermission()
            // 悬浮窗权限（Android 6）
            "android.permission.SYSTEM_ALERT_WINDOW" ->
                return PermissionLists.getSystemAlertWindowPermission()
            // 写入系统设置权限（Android 6）
            "android.permission.WRITE_SETTINGS" ->
                return PermissionLists.getWriteSettingsPermission()
            // 忽略电池优化权限（Android 6）
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" ->
                return PermissionLists.getRequestIgnoreBatteryOptimizationsPermission()
            // 勿扰权限（Android 6）
            "android.permission.ACCESS_NOTIFICATION_POLICY" ->
                return PermissionLists.getAccessNotificationPolicyPermission()
            // 查看应用使用情况权限（Android 5）
            "android.permission.PACKAGE_USAGE_STATS" ->
                return PermissionLists.getPackageUsageStatsPermission()
            // VPN 权限
            "android.permission.BIND_VPN_SERVICE" ->
                return PermissionLists.getBindVpnServicePermission()

            // ==================== 危险权限（按 Android 版本新增） ====================
            // 访问局域网权限（Android 17）
            "android.permission.ACCESS_LOCAL_NETWORK" ->
                return PermissionLists.getAccessLocalNetworkPermission()
            // 访问部分照片和视频（Android 14）
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" ->
                return PermissionLists.getReadMediaVisualUserSelectedPermission()
            // 发送通知权限（Android 13，框架自动向下兼容通知开关权限）
            "android.permission.POST_NOTIFICATIONS" ->
                return PermissionLists.getPostNotificationsPermission()
            // 附近 WIFI 设备（Android 13）
            "android.permission.NEARBY_WIFI_DEVICES" ->
                return PermissionLists.getNearbyWifiDevicesPermission()
            // 后台传感器权限（Android 13）
            "android.permission.BODY_SENSORS_BACKGROUND" ->
                return PermissionLists.getBodySensorsBackgroundPermission()
            // 读取图片 / 视频 / 音频权限（Android 13）
            "android.permission.READ_MEDIA_IMAGES" ->
                return PermissionLists.getReadMediaImagesPermission()
            "android.permission.READ_MEDIA_VIDEO" ->
                return PermissionLists.getReadMediaVideoPermission()
            "android.permission.READ_MEDIA_AUDIO" ->
                return PermissionLists.getReadMediaAudioPermission()
            // 蓝牙权限（Android 12）
            "android.permission.BLUETOOTH_SCAN" ->
                return PermissionLists.getBluetoothScanPermission()
            "android.permission.BLUETOOTH_CONNECT" ->
                return PermissionLists.getBluetoothConnectPermission()
            "android.permission.BLUETOOTH_ADVERTISE" ->
                return PermissionLists.getBluetoothAdvertisePermission()
            // 后台定位权限（Android 10）
            "android.permission.ACCESS_BACKGROUND_LOCATION" ->
                return PermissionLists.getAccessBackgroundLocationPermission()
            // 获取活动步数权限（Android 10）
            "android.permission.ACTIVITY_RECOGNITION" ->
                return PermissionLists.getActivityRecognitionPermission()
            // 访问媒体位置信息权限（Android 10）
            "android.permission.ACCESS_MEDIA_LOCATION" ->
                return PermissionLists.getAccessMediaLocationPermission()
            // 允许呼叫应用继续呼叫权限（Android 9）
            "android.permission.ACCEPT_HANDOVER" ->
                return PermissionLists.getAcceptHandoverPermission()
            // 读取手机号码权限（Android 8）
            "android.permission.READ_PHONE_NUMBERS" ->
                return PermissionLists.getReadPhoneNumbersPermission()
            // 接听电话权限（Android 8）
            "android.permission.ANSWER_PHONE_CALLS" ->
                return PermissionLists.getAnswerPhoneCallsPermission()

            // ==================== 存储权限 ====================
            "android.permission.READ_EXTERNAL_STORAGE" ->
                return PermissionLists.getReadExternalStoragePermission()
            "android.permission.WRITE_EXTERNAL_STORAGE" ->
                return PermissionLists.getWriteExternalStoragePermission()

            // ==================== 基础危险权限（Android 6） ====================
            "android.permission.CAMERA" ->
                return PermissionLists.getCameraPermission()
            "android.permission.RECORD_AUDIO" ->
                return PermissionLists.getRecordAudioPermission()
            "android.permission.ACCESS_FINE_LOCATION" ->
                return PermissionLists.getAccessFineLocationPermission()
            "android.permission.ACCESS_COARSE_LOCATION" ->
                return PermissionLists.getAccessCoarseLocationPermission()
            "android.permission.READ_CONTACTS" ->
                return PermissionLists.getReadContactsPermission()
            "android.permission.WRITE_CONTACTS" ->
                return PermissionLists.getWriteContactsPermission()
            "android.permission.GET_ACCOUNTS" ->
                return PermissionLists.getGetAccountsPermission()
            "android.permission.READ_CALENDAR" ->
                return PermissionLists.getReadCalendarPermission()
            "android.permission.WRITE_CALENDAR" ->
                return PermissionLists.getWriteCalendarPermission()
            "android.permission.READ_PHONE_STATE" ->
                return PermissionLists.getReadPhoneStatePermission()
            "android.permission.CALL_PHONE" ->
                return PermissionLists.getCallPhonePermission()
            "android.permission.READ_CALL_LOG" ->
                return PermissionLists.getReadCallLogPermission()
            "android.permission.WRITE_CALL_LOG" ->
                return PermissionLists.getWriteCallLogPermission()
            "android.permission.ADD_VOICEMAIL" ->
                return PermissionLists.getAddVoicemailPermission()
            "android.permission.USE_SIP" ->
                return PermissionLists.getUseSipPermission()
            "android.permission.PROCESS_OUTGOING_CALLS" ->
                return PermissionLists.getProcessOutgoingCallsPermission()
            "android.permission.BODY_SENSORS" ->
                return PermissionLists.getBodySensorsPermission()
            "android.permission.SEND_SMS" ->
                return PermissionLists.getSendSmsPermission()
            "android.permission.RECEIVE_SMS" ->
                return PermissionLists.getReceiveSmsPermission()
            "android.permission.READ_SMS" ->
                return PermissionLists.getReadSmsPermission()
            "android.permission.RECEIVE_WAP_PUSH" ->
                return PermissionLists.getReceiveWapPushPermission()
            "android.permission.RECEIVE_MMS" ->
                return PermissionLists.getReceiveMmsPermission()

            // ==================== 健康数据权限（android.permission.health.*） ====================
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" ->
                return PermissionLists.getReadHealthDataInBackgroundPermission()
            "android.permission.health.READ_HEALTH_DATA_HISTORY" ->
                return PermissionLists.getReadHealthDataHistoryPermission()
            "android.permission.health.READ_HEART_RATE" ->
                return PermissionLists.getReadHeartRatePermission()
            "android.permission.health.WRITE_HEART_RATE" ->
                return PermissionLists.getWriteHeartRatePermission()
            "android.permission.health.READ_STEPS" ->
                return PermissionLists.getReadStepsPermission()
            "android.permission.health.WRITE_STEPS" ->
                return PermissionLists.getWriteStepsPermission()
            "android.permission.health.READ_SLEEP" ->
                return PermissionLists.getReadSleepPermission()
            "android.permission.health.WRITE_SLEEP" ->
                return PermissionLists.getWriteSleepPermission()
            "android.permission.health.READ_EXERCISE" ->
                return PermissionLists.getReadExercisePermission()
            "android.permission.health.WRITE_EXERCISE" ->
                return PermissionLists.getWriteExercisePermission()
            "android.permission.health.READ_WEIGHT" ->
                return PermissionLists.getReadWeightPermission()
            "android.permission.health.WRITE_WEIGHT" ->
                return PermissionLists.getWriteWeightPermission()
            "android.permission.health.READ_HEIGHT" ->
                return PermissionLists.getReadHeightPermission()
            "android.permission.health.WRITE_HEIGHT" ->
                return PermissionLists.getWriteHeightPermission()
            "android.permission.health.READ_BLOOD_PRESSURE" ->
                return PermissionLists.getReadBloodPressurePermission()
            "android.permission.health.WRITE_BLOOD_PRESSURE" ->
                return PermissionLists.getWriteBloodPressurePermission()
            "android.permission.health.READ_BLOOD_GLUCOSE" ->
                return PermissionLists.getReadBloodGlucosePermission()
            "android.permission.health.WRITE_BLOOD_GLUCOSE" ->
                return PermissionLists.getWriteBloodGlucosePermission()
            "android.permission.health.READ_OXYGEN_SATURATION" ->
                return PermissionLists.getReadOxygenSaturationPermission()
            "android.permission.health.WRITE_OXYGEN_SATURATION" ->
                return PermissionLists.getWriteOxygenSaturationPermission()
            "android.permission.health.READ_BODY_TEMPERATURE" ->
                return PermissionLists.getReadBodyTemperaturePermission()
            "android.permission.health.WRITE_BODY_TEMPERATURE" ->
                return PermissionLists.getWriteBodyTemperaturePermission()
            "android.permission.health.READ_DISTANCE" ->
                return PermissionLists.getReadDistancePermission()
            "android.permission.health.WRITE_DISTANCE" ->
                return PermissionLists.getWriteDistancePermission()
            "android.permission.health.READ_SPEED" ->
                return PermissionLists.getReadSpeedPermission()
            "android.permission.health.WRITE_SPEED" ->
                return PermissionLists.getWriteSpeedPermission()
            "android.permission.health.READ_NUTRITION" ->
                return PermissionLists.getReadNutritionPermission()
            "android.permission.health.WRITE_NUTRITION" ->
                return PermissionLists.getWriteNutritionPermission()
            "android.permission.health.READ_HYDRATION" ->
                return PermissionLists.getReadHydrationPermission()
            "android.permission.health.WRITE_HYDRATION" ->
                return PermissionLists.getWriteHydrationPermission()
            "android.permission.health.READ_MENSTRUATION" ->
                return PermissionLists.getReadMenstruationPermission()
            "android.permission.health.WRITE_MENSTRUATION" ->
                return PermissionLists.getWriteMenstruationPermission()
        }

        // 反射兜底：覆盖映射表未列出的权限（如健康 / 医疗记录的长尾权限）
        return findPermissionByReflection(name)
    }

    /**
     * 按框架命名规律反射获取权限对象（映射表的兜底方案）
     *
     * 框架 getter 命名规律：get + 权限名去前缀转驼峰 + Permission
     * 例如：android.permission.READ_WHEELCHAIR_PUSHES
     *   → 去前缀 → READ_WHEELCHAIR_PUSHES
     *   → 转驼峰 → ReadWheelchairPushes
     *   → 方法名 → getReadWheelchairPushesPermission
     *
     * 输入：name : String —— Android 权限全名
     * 输出：IPermission?  —— 命中返回权限对象，未命中或反射异常返回 null
     */
    private fun findPermissionByReflection(name: String): IPermission? {
        return try {
            val suffix = name
                .removePrefix("android.permission.health.")
                .removePrefix("android.permission.")
            val methodName = "get" + suffix.split('_')
                .filter { it.isNotEmpty() }
                .joinToString("") { segment ->
                    segment.substring(0, 1).uppercase() + segment.substring(1).lowercase()
                } + "Permission"
            val method = PermissionLists::class.java.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty()
            } ?: return null
            method.invoke(null) as? IPermission
        } catch (e: Exception) {
            null
        }
    }
}