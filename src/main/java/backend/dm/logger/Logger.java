package backend.dm.logger;

/**
 * 日志接口
 * 提供日志写入 (log) 和迭代读取 (next) 功能
 */
public interface Logger {
    // 写入一条日志
    void log(byte[] data);

    // 截断日志文件到指定位置 (用于移除 BadTail)
    void truncate(long x) throws Exception;

    // 获取下一条日志数据 (迭代器模式)
    byte[] next();

    // 重置迭代器位置
    void rewind();

    // 关闭日志文件
    void close();

    // 静态方法：创建 Logger 实例
    public static Logger create(String path) {
        return LoggerImpl.create(path);
    }

    // 静态方法：打开现有 Logger 实例
    public static Logger open(String path) {
        return LoggerImpl.open(path);
    }
}