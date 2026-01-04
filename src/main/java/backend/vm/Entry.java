package backend.vm;

import backend.common.SubArray;
import backend.dm.dataItem.DataItem;
import backend.utils.Parser;
import java.util.Arrays;

/**
 * Entry 是 VM 层向上提供的数据抽象
 * 一个 Entry 对应一条记录，内部持有一个 DataItem
 */
public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN + 8;
    private static final int OF_DATA = OF_XMAX + 8;
    private long uid;
    private DataItem dataItem;
    private VersionManager vm;

    /**
     * 从 UID 加载 Entry
     */
    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl) vm).dm.read(uid);
        // 如果 DataItem 为 null，说明记录不存在或无效
        if (di == null) {
            return null;
        }
        return newEntry(vm, di, uid);
    }

    /**
     * 创建 Entry 实例
     */
    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    /**
     * 包装 Entry 的原始数据
     * 
     * @param xid  创建该记录的事务 ID
     * @param data 记录的实际数据
     * @return 完整的字节数组 [XMIN][XMAX][DATA]
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8]; // 初始 XMAX 为 0
        // 手动拼接字节数组，避免依赖 Guava
        byte[] result = new byte[xmin.length + xmax.length + data.length];
        System.arraycopy(xmin, 0, result, 0, xmin.length);
        System.arraycopy(xmax, 0, result, xmin.length, xmax.length);
        System.arraycopy(data, 0, result, xmin.length + xmax.length, data.length);
        return result;
    }

    /**
     * 释放 Entry（释放底层 DataItem）
     */
    public void release() {
        ((VersionManagerImpl) vm).releaseEntry(this);
    }

    /**
     * 移除 Entry（释放 DataItem 引用）
     */
    public void remove() {
        dataItem.release();
    }

    /**
     * 以拷贝的形式返回记录的数据部分
     */
    public byte[] data() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw, sa.start + OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnlock();
        }
    }

    /**
     * 获取 XMIN（创建该版本的事务 ID）
     */
    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start + OF_XMIN, sa.start + OF_XMAX));
        } finally {
            dataItem.rUnlock();
        }
    }

    /**
     * 获取 XMAX（删除该版本的事务 ID）
     */
    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start + OF_XMAX, sa.start + OF_DATA));
        } finally {
            dataItem.rUnlock();
        }
    }

    /**
     * 设置 XMAX（标记删除）
     * 
     * @param xid 删除该版本的事务 ID
     */
    public void setXmax(long xid) {
        dataItem.before();
        try {
            SubArray sa = dataItem.data();
            System.arraycopy(Parser.long2Byte(xid), 0, sa.raw, sa.start + OF_XMAX, 8);
        } finally {
            dataItem.after(xid);
        }
    }

    public long getUid() {
        return uid;
    }
}