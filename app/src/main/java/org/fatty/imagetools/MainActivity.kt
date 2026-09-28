package org.fatty.imagetools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.fatty.imagetools.ui.screens.ImageStitcherScreen
import org.fatty.imagetools.ui.theme.ImageToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageToolsTheme {
                ImageStitcherScreen()
            }
        }
        initLog()
    }

    fun initLog() {
        MXLogger logger = MXLogger . initialize (
                context,
        "org.fatty.imagetools",              // nameSpace
        null,                                 // diskCacheDirectory，null 用默认目录
        MXStoragePolicyType.YYYY_MM_DD,
        "mxlog",                              // fileName
        "{\"app_version\":\"2.0.0\"}",        // fileHeader
        "abcuioqbsdguijlk",                   // cryptKey，16 字节
        "bccuioqbsdguijiv");                  // iv

        logger.setConsoleEnable(true);
        logger.setMaxDiskAge(60 * 60 * 24 * 7);
        logger.setMaxDiskSize(1024 * 1024 * 10);
        logger.setLevel(0);

// 注意 Android 端 tag 在第一个参数：debug(tag, name, msg)
        logger.debug("login,service", "login", "开始校验本地 token");
        logger.info("network,POST,200", "network", responseJson);
        logger.warn("network", "network", "第 2 次重试");
        logger.error("crash", "flutter", stack);
        logger.fatal("db,fatal", "database", "数据库连接丢失");

        Log.d("MXLogger", "日志目录 " + logger.getDiskCachePath());
    }
}
