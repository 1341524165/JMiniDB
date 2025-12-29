package backend.dm.dataItem;

import backend.common.SubArray;
import backend.dm.DataManagerImpl;
import backend.dm.page.Page;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataItemImpl implements DataItem {

    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;
    private SubArray raw; // 引用 Page 中的原生数据
    private byte[] oldRaw; // 修改前的旧数据备份
    private DataManagerImpl dm;
    private long uid;
    private Page pg;

    private ReadWriteLock lock;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        this.pg = pg;
        this.uid = uid;
        this.dm = dm;
        this.lock = new ReentrantReadWriteLock();
    }

    public boolean isValid() {
        return raw.raw[raw.start + OF_VALID] == (byte) 0;
    }

    @Override
    public SubArray data() {
        return new SubArray(raw.raw, raw.start + OF_DATA, raw.end);
    }

    public void before() {
        lock.writeLock().lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length);
    }

    public void unBefore() {
        System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
        lock.writeLock().unlock();
    }

    public void after(long xid) {
        dm.logDataItem(xid, this);
        lock.writeLock().unlock();
    }

    public void release() {
        dm.releaseDataItem(this);
    }

    public void lock() {
        lock.writeLock().lock();
    }

    public void unlock() {
        lock.writeLock().unlock();
    }

    public void rLock() {
        lock.readLock().lock();
    }

    public void rUnlock() {
        lock.readLock().unlock();
    }

    public Page page() {
        return pg;
    }

    public long getUid() {
        return uid;
    }

    public byte[] getOldRaw() {
        return oldRaw;
    }

    public SubArray getRaw() {
        return raw;
    }
}