package backend.dm.pageCache;

import backend.common.AbstractCache;
import backend.dm.page.Page;
import backend.dm.page.PageImpl;
import backend.utils.Error;
import backend.utils.Panic;
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
}