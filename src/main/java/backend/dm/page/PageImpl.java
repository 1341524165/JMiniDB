package backend.dm.page;

import backend.dm.pageCache.PageCache;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Page 接口的具体实现类
 */
public class PageImpl implements Page {
    private int pageNumber; // 页号
    private byte[] data; // 实际数据 (8KB)
    private boolean dirty; // 脏标志
    private Lock lock; // 页面锁
    private PageCache pc; // 对应的缓存引用，用于快速释放

    public PageImpl(int pageNumber, byte[] data, PageCache pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    /**
     * 释放当前页面的引用。
     * 实际是调用 PageCache 的 release 方法。
     */
    public void release() {
        pc.release(this);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public byte[] getData() {
        return data;
    }
}