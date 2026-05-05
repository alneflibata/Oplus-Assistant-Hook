package com.xdreamllc.oplus.hook

import android.content.Context
import com.xdreamllc.oplus.utils.PrefsHelper
import com.xdreamllc.oplus.utils.TriggerHelper
import com.xdreamllc.oplus.utils.XLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * 支持 ColorOS 15 / 16 的手势条长按 Hook
 * 尝试多种可能的 OplusOcrScreen / NavigationBar 相关类
 */
object GestureBarHooker {

    private val possibleClassNames = listOf(
        // ColorOS 16 原有
        "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness",
        // ColorOS 15 常见候选
        "com.oplus.systemui.navigationbar.ocrscreen.OplusScreenOcrHelper",
        "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenHelper",
        "com.oplus.breeno.ocrscreen.OplusOcrScreenBusiness",
        "com.coloros.systemui.navigationbar.OplusNavigationBar",
        "com.oplus.systemui.navigationbar.gesture.OplusGestureBarHelper",
        "com.oplus.systemui.navigationbar.assist.OplusAssistantGesture",
        "com.oplus.systemui.navigationbar.NavigationBarView",
        "com.oplus.systemui.navigationbar.OplusNavigationBarView"
    )

    private val possibleMethodNames = listOf(
        "onLongPressed",
        "handleLongPress",
        "onGestureLongClick",
        "onLongClick",
        "triggerOcrScreen"
    )

    fun hook(lpparam: LoadPackageParam) {
        var hooked = false

        for (className in possibleClassNames) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
            if (clazz == null) continue

            XLog.debug("GestureBarHooker: Found target class -> $className")

            for (methodName in possibleMethodNames) {
                val methods = XposedBridge.hookAllMethods(
                    clazz,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                XLog.debug("GestureBarHooker: $methodName triggered in \( className (args= \){param.args?.size ?: 0})")

                                if (!PrefsHelper.isGestureBarEnabled()) {
                                    XLog.debug("GestureBarHooker: disabled by pref, skip")
                                    return
                                }

                                // 阻止原方法执行
                                param.result = null

                                val ctx = getContextFromThisObject(param)
                                if (ctx != null) {
                                    TriggerHelper.performHapticFeedback(ctx)
                                }

                                TriggerHelper.triggerCircleToSearch()
                                XLog.debug("GestureBarHooker: Successfully triggered Circle to Search")

                            } catch (e: Throwable) {
                                XLog.error("GestureBarHooker: error in hook", e)
                            }
                        }
                    }
                )

                if (methods.isNotEmpty()) {
                    XLog.debug("GestureBarHooker: Hooked \( methodName successfully ( \){methods.size} overloads)")
                    hooked = true
                }
            }

            if (hooked) break  // 找到一个可用的类就停止
        }

        if (!hooked) {
            XLog.error("GestureBarHooker: No suitable class/method found for ColorOS 15/16")
            XLog.error("请用 LSPosed 日志 + Jadx 反编译 SystemUI.apk 查找包含 'ocr' 或 'long press' 的类")
        }
    }

    private fun getContextFromThisObject(param: XC_MethodHook.MethodHookParam): Context? {
        // 原有健壮的 Context 获取逻辑（保持不变）
        try {
            val method = param.thisObject.javaClass.getMethod("getContext")
            return method.invoke(param.thisObject) as? Context
        } catch (_: Throwable) {}

        val fieldNames = listOf("mContext", "context", "mOcrContext", "mContextHolder")
        for (fieldName in fieldNames) {
            try {
                return XposedHelpers.getObjectField(param.thisObject, fieldName) as? Context
            } catch (_: Throwable) {}
        }

        // 遍历所有字段查找 Context
        try {
            val fields = param.thisObject.javaClass.declaredFields
            for (field in fields) {
                if (Context::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    val value = field.get(param.thisObject) as? Context
                    if (value != null) {
                        XLog.debug("GestureBarHooker: found context in field '${field.name}'")
                        return value
                    }
                }
            }
        } catch (e: Throwable) {
            XLog.error("GestureBarHooker: field scan failed", e)
        }

        return null
    }
}
