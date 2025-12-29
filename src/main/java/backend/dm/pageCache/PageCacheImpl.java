package backend.dm.pageCache;

import backend.common.AbstractCache;
import backend.dm.page.Page;
import backend.dm.page.PageImpl;
import backend.utils.Error;
import backend.utils.Panic;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 页面缓存具体实现
 * 继承自 AbstractCache，管理 Page 对象
 */
public class PageCacheImpl extends AbstractCache<Page> implements PageCache {

    private static final int MEM_MIN_LIM = 10; // 最小内存页限制
    public static final String DB_SUFFIX = ".db"; // 数据库文件后缀
    // public static final int PAGE_SIZE = 1 << 13; // 页面大小 8192 字节 (8KB)

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    // 记录总页数，AtomicInteger 保证线程安全
    private AtomicInteger pageNumbers;

    PageCacheImpl(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        super(maxResource);
        if (maxResource < MEM_MIN_LIM) {
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fileChannel;
        this.fileLock = new ReentrantLock();

        // 简单粗暴：文件大小 / 页大小 = 总页数
        this.pageNumbers = new AtomicInteger((int) (length / PAGE_SIZE));
    }

    /**
     * 新建页面-
     * 1. 页号 +1
     * 2. 创建 Page 对象
     * 3. 立刻刷入磁盘 (flush)
     */
    public int newPage(byte[] initData) {
        int pgno = pageNumbers.incrementAndGet();
        Page pg = new PageImpl(pgno, initData, null);
        flush(pg);
        return pgno;
    }

    public Page getPage(int pgno) throws Exception {
        return get(pgno);
    }

    /**
     * 从数据源（文件）读取数据，用来填充缓存
     * 这是 AbstractCache 要求的抽象方法
     */
    @Override
    protected Page getForCache(long key) throws Exception {
        int pgno = (int) key;
        long offset = pageOffset(pgno);
        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);

        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }

        return new PageImpl(pgno, buf.array(), this);
    }

    /**
     * 当页面被驱逐出缓存时，如果是脏页，需要写回磁盘
     * 这是 AbstractCache 要求的抽象方法
     */
    @Override
    protected void releaseForCache(Page pg) {
        if (pg.isDirty()) {
            flush(pg);
            pg.setDirty(false);
        }
    }

    public void release(Page page) {
        release((long) page.getPageNumber());
    }

    public void flushPage(Page pg) {
        flush(pg);
    }

    /**
     * 核心写回方法：将 Page 的 data 写回到文件对应的 offset 位置
     */
    private void flush(Page pg) {
        int pgno = pg.getPageNumber();
        long offset = pageOffset(pgno);

        fileLock.lock();
        try {
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset);
            fc.write(buf);
            fc.force(false); // 强制刷盘
        } catch (IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }

    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try {
            file.setLength(size);
        } catch (IOException e) {
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    public int getPageNumber() {
        return pageNumbers.intValue();
    }

    // 根据页号计算偏移量：(页号-1) * 8192
    private static long pageOffset(int pgno) {
        return (long) (pgno - 1) * PAGE_SIZE;
    }

    /**
     * 创建新的数据库文件并返回页面缓存实例
     * 
     * @param path   数据库文件路径（不含后缀）
     * @param memory 缓存大小（字节）
     * @return PageCacheImpl 实例
     */
    public static PageCacheImpl create(String path, long memory) {
        File f = new File(path + DB_SUFFIX);
        try {
            // 创建新文件，如果文件已存在则 panic
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        // 检查文件读写权限
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            // 以读写模式打开文件
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 计算最大缓存页数：总内存 / 页大小
        return new PageCacheImpl(raf, fc, (int) memory / PAGE_SIZE);
    }

    /**
     * 打开已有的数据库文件并返回页面缓存实例
     * 
     * @param path   数据库文件路径（不含后缀）
     * @param memory 缓存大小（字节）
     * @return PageCacheImpl 实例
     */
    public static PageCacheImpl open(String path, long memory) {
        File f = new File(path + DB_SUFFIX);
        // 检查文件是否存在
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        // 检查文件读写权限
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            // 以读写模式打开文件
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 计算最大缓存页数：总内存 / 页大小
        return new PageCacheImpl(raf, fc, (int) memory / PAGE_SIZE);
    }
}