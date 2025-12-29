package backend.dm.dataItem;

import backend.common.SubArray;
import backend.dm.page.Page;

public interface DataItem {
    SubArray data();

    void before();

    void unBefore();

    void after(long xid);

    void release();

    void lock();

    void unlock();

    void rLock();

    void rUnlock();

    Page page();

    long getUid();

    byte[] getOldRaw();

    SubArray getRaw();

    public static byte[] wrapDataItemRaw(byte[] raw) {
        byte[] valid = new byte[1];
        byte[] size = backend.utils.Parser.short2Byte((short) raw.length);
        byte[] dest = new byte[1 + 2 + raw.length];
        System.arraycopy(valid, 0, dest, 0, 1);
        System.arraycopy(size, 0, dest, 1, 2);
        System.arraycopy(raw, 0, dest, 3, raw.length);
        return dest;
    }
}