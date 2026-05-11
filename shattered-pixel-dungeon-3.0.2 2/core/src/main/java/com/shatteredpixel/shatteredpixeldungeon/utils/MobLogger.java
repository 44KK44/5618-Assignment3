package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MobLogger {
    private static final String LOG_FILE = "mob_behavior.log";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public static void log(Mob mob, String eventType, String details) {
        try {
            // 获取实时时间和游戏刻 (Tick)
            String realTime = TIME_FORMAT.format(new Date());
            float gameTime = Actor.now();

            // 格式化日志内容
            String logEntry = String.format("[%s] [TICK:%.2f] [%s:%d] [%s] %s",
                    realTime, gameTime, mob.name(), mob.id(), eventType, details);

            // 1. 打印至终端 (Requirement 5.7)
            System.out.println(logEntry);

            // 2. 持久化到日志文件 (Requirement 5.7)
            try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                out.println(logEntry);
            }
        } catch (Exception e) {
            // 异常处理：确保日志系统故障不干扰游戏运行 (Requirement 5.8)
        }
    }
}