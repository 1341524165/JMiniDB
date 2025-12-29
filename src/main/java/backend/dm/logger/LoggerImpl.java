package backend.dm.logger;

import backend.utils.Error;
import backend.utils.Panic;
import backend.utils.Parser;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 简单的日志实现
 * 日志文件结构: [XChecksum(4B)] [Log1] [Log2] ... [LogN] [BadTail]
 * 单条日志结构: [Size(4B)] [Checksum(4B)] [Data]
 */
public class LoggerImpl implements Logger {

    private static final int SEED = 13331; // 校验和计算种子
    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;
    public static final String LOG_SUFFIX = ".log"; // 日志文件后缀
    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position; // 当前读取位置 (用于 next 迭代)
    private long fileSize; // 文件大小
    private int xChecksum; // 全局校验和

    LoggerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    // 初始化：检查并移除 BadTail
    void init() {
        long size = 0;
        try {
            size = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        if (size < 4) { // 文件太小，说明肯定没数据，panic
            Panic.panic(Error.BadLogFileException);
        }

        // 读取全局校验和
        ByteBuffer raw = ByteBuffer.allocate(4);
        try {
            fc.position(0);
            fc.read(raw);
        } catch (IOException e) {
            Panic.panic(e);
        }
        int xChecksum = Parser.parseInt(raw.array());
        this.fileSize = size;
        this.xChecksum = xChecksum;
        checkAndRemoveTail();
    }

    /**
     * 检查并移除文件尾部可能存在的损坏日志
     */
    private void checkAndRemoveTail() {
        rewind();
        int xCheck = 0;
        while (true) {
            byte[] log = internNext();
            if (log == null)
                break;
            xCheck = calChecksum(xCheck, log);
        }
        if (xCheck != xChecksum) {
            Panic.panic(Error.BadLogFileException);
        }
        try {
            truncate(position); // 截断到最后一个有效日志的末尾
        } catch (Exception e) {
            Panic.panic(e);
        }

        try {
            file.seek(position); // 移动文件指针准备追加写入
        } catch (IOException e) {
            Panic.panic(e);
        }
        rewind();
    }

    // 计算校验和
    private int calChecksum(int xCheck, byte[] log) {
        for (byte b : log) {
            xCheck = xCheck * SEED + b;
        }
        return xCheck;
    }

    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try {
            fc.position(fc.size());
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }
        updateXChecksum(log);
    }

    // 更新全局校验和并写回文件头
    private void updateXChecksum(byte[] log) {
        this.xChecksum = calChecksum(this.xChecksum, log);
        try {
            fc.position(0);
            fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // 将 Data 包装成 Log 格式
    private byte[] wrapLog(byte[] data) {
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));
        byte[] size = Parser.int2Byte(data.length);
        long packetSize = size.length + checksum.length + data.length;
        byte[] packet = new byte[(int) packetSize];
        System.arraycopy(size, 0, packet, 0, 4);
        System.arraycopy(checksum, 0, packet, 4, 4);
        System.arraycopy(data, 0, packet, 8, data.length);
        return packet;
    }

    @Override
    public byte[] next() {
        lock.lock();
        try {
            byte[] log = internNext();
            if (log == null)
                return null;
            return Arrays.copyOfRange(log, OF_DATA, log.length);
        } finally {
            lock.unlock();
        }
    }

    // 内部迭代方法：读取下一个完整的日志包
    private byte[] internNext() {
        if (position + OF_DATA >= fileSize) {
            return null;
        }
        // 读取 size
        ByteBuffer tmp = ByteBuffer.allocate(4);
        try {
            fc.position(position);
            fc.read(tmp);
        } catch (IOException e) {
            Panic.panic(e);
        }
        int size = Parser.parseInt(tmp.array());
        if (position + size + OF_DATA > fileSize) {
            return null;
        }
        // 读取 checksum + data
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
        try {
            fc.position(position);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        byte[] log = buf.array();
        // 校验 checksum (验证本条日志是否损坏)
        int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
        int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));
        if (checkSum1 != checkSum2) {
            return null;
        }
        position += log.length;
        return log;
    }

    @Override
    public void truncate(long x) throws Exception {
        lock.lock();
        try {
            fc.truncate(x);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rewind() {
        position = 4; // 跳过文件头 XChecksum
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    public static Logger create(String path) {
        java.io.File f = new java.io.File(path + LOG_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (IOException e) {
            Panic.panic(e);
        }

        // 写初始 XChecksum = 0
        ByteBuffer buf = ByteBuffer.wrap(Parser.int2Byte(0));
        try {
            fc.position(0);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }

        LoggerImpl lg = new LoggerImpl(raf, fc);
        lg.init();
        return lg;
    }

    public static Logger open(String path) {
        java.io.File f = new java.io.File(path + LOG_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (IOException e) {
            Panic.panic(e);
        }
        LoggerImpl lg = new LoggerImpl(raf, fc);
        lg.init();
        return lg;
    }
}