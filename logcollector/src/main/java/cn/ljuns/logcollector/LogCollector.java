package cn.ljuns.logcollector;

import android.content.Context;
import android.support.annotation.NonNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class LogCollector implements CrashHandlerListener {

    private Context mContext;
    private File mCacheFile;    // 缓存文件
    private String[] mLogType;    //过滤类型
    private String mBgColor = "#FFFFFFFF";    // 背景颜色
    private boolean mCleanCache = false;    // 是否清除缓存日志文件
    private boolean mShowLogColors = false;  // 是否设置颜色
    private String[] mLogcatColors;

    private LogRunnable mLogRunnable;

    private LogCollector() {
        mLogcatColors = new String[TagUtils.TAGS.length];
    }

    @Override
    public void crashHandler() {
        mLogRunnable.isCrash = true;
    }

    private static class SingletonHolder {
        private static final LogCollector INSTANCE = new LogCollector();
    }

    public static LogCollector getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 设置缓存文件
     *
     * @param file file
     * @return LogCollector
     */
    private LogCollector setCacheFile(@NonNull File file) {
        this.mCacheFile = file;
        return this;
    }

    private LogCollector setCacheFile(@NonNull String path) {
        this.mCacheFile = new File(path);
        return this;
    }

    /**
     * 设置缓存类型
     *
     * @param types LogType
     * @return LogCollector
     */
    public LogCollector setLogcatType(@TagUtils.LogcatType String... types) {
        this.mLogType = types;
        return this;
    }

    /**
     * 是否清除之前的缓存
     *
     * @param cleanCache cleanCache
     * @return LogCollector
     */
    public LogCollector setCleanCache(boolean cleanCache) {
        this.mCleanCache = cleanCache;
        return this;
    }

    /**
     * 设置背景颜色
     * @param bgColor bgColor
     * @return LogCollector
     */
    private LogCollector setBgColor(int bgColor) {
//        this.mBgColor = ColorUtils.parseColor(bgColor);
        return this;
    }

    /**
     * 设置各种 logcat 颜色
     * @param logcatColors logColors
     * @return LogCollector
     */
    public LogCollector setLogcatColors(int... logcatColors) {
        for (int i = 0; i < logcatColors.length; i++) {
            mLogcatColors[i] = ColorUtils.parseColor(mContext, logcatColors[i]);
        }
        mShowLogColors = true;

        if (mLogcatColors.length < TagUtils.TAGS.length) {
            for (int i = mLogcatColors.length; i < TagUtils.TAGS.length; i++) {
                mLogcatColors[i] = "#FF000000";
            }
        }

        return this;
    }

    /**
     * 启动
     * @param context Context
     */
    public synchronized void start(Context context) {
        mContext = context;
        mCacheFile = CacheFile.createLogCacheFile(context, mCleanCache, mShowLogColors);
        CrashHandler.getInstance().init(context, mCleanCache).crash(this);

        mLogRunnable = new LogRunnable();
        new Thread(mLogRunnable).start();
    }

    private class LogRunnable implements Runnable {
        volatile boolean isCrash = false;

        @Override
        public void run() {
            BufferedReader reader = null;
            BufferedWriter writer = null;
            try {
                // 获取 logcat
                Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "time"});

                reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"));
                writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(mCacheFile), "UTF-8"));

                String str = null;

                if (mShowLogColors) {
                    writer.write("<body bgcolor=\" " + mBgColor + " \">");
                }
                while (!isCrash && ((str = reader.readLine()) != null)) {
                    Runtime.getRuntime().exec(new String[]{"logcat", "-c"});
                    outputLogcat(writer, str);
                }
                if (mShowLogColors) {
                    writer.write("</body>");
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                CloseUtils.close(reader);
                CloseUtils.close(writer);
            }
        }
    }

    /**
     * 输出 logcat
     * @param writer BufferedWriter
     * @param str str
     * @throws IOException
     */
    private void outputLogcat(BufferedWriter writer, String str) throws IOException {
        String[] logcats = null;

        if (mLogType != null && mLogType.length > 0) {
            logcats = mLogType;
        } else {
            logcats = TagUtils.TAGS;
        }

        if (mShowLogColors) {
            for (int i = 0; i < logcats.length; i++) {
                if (str.contains(logcats[i])) {
                    writeWithColors(writer, mLogcatColors[i], str);
                }
            }
        } else {
            if (mLogType != null && mLogType.length > 0) {
                for (String logcat : logcats) {
                    if (str.contains(logcat)) {
                        writeWithoutColors(writer, str);
                    }
                }
            } else {
                writeWithoutColors(writer, str);
            }
        }
    }

    /**
     * 写数据
     * @param writer BufferedWriter
     * @param str str
     * @throws IOException
     */
    private void writeWithoutColors(BufferedWriter writer, String str) throws IOException {
        writer.write(str);
        flush(writer);
    }


    /**
     * 写数据
     * @param writer BufferedWriter
     * @param color color
     * @param str str
     * @throws IOException
     */
    private void writeWithColors(BufferedWriter writer, String color, String str) throws IOException {
        writer.write("<font size=\"3\" color=\"" + color + "\">" + str + "</font></br>");
        flush(writer);
    }

    private void flush(BufferedWriter writer) throws IOException {
        writer.newLine();
        writer.flush();
    }
}