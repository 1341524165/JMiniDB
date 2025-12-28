package backend.dm.page;

import backend.dm.pageCache.PageCache;
import backend.utils.Parser;
import java.util.Arrays;

/**
 * 普通页面管理逻辑
 * 页面结构：[2字节 FSO] [数据区域...]
 */
public class PageX {
    // 空闲空间偏移量的起始位置 (0)
    private static final short OF_FREE = 0;
    // 数据起始位置 (2)
    private static final short OF_DATA = 2;
    // 页面能存的最大数据量 (8192 - 2)
    public static final int MAX_FREE_SPACE = PageCache.PAGE_SIZE - OF_DATA;

    /**
     * 初始化一个空页面 (实际上就是设置 FSO = 2)
     */
    public static byte[] initRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setFSO(raw, OF_DATA);
        return raw;
    }

    // 设置 FSO 值
    private static void setFSO(byte[] raw, short ofData) {
        System.arraycopy(Parser.short2Byte(ofData), 0, raw, OF_FREE, OF_DATA);
    }

    // 获取 FSO 值
    public static short getFSO(Page pg) {
        return getFSO(pg.getData());
    }

    private static short getFSO(byte[] raw) {
        return Parser.parseShort(Arrays.copyOfRange(raw, 0, 2));
    }

    /**
     * 将 raw 插入 pg 中，返回插入位置
     * 1. 获取 FSO (确认写在哪)
     * 2. 写入数据
     * 3. 更新 FSO
     */
    public static short insert(Page pg, byte[] raw) {
        pg.setDirty(true);
        short offset = getFSO(pg.getData());
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
        setFSO(pg.getData(), (short) (offset + raw.length));
        return offset;
    }

    /**
     * 获取剩余空闲空间大小
     */
    public static int getFreeSpace(Page pg) {
        return PageCache.PAGE_SIZE - (int) getFSO(pg.getData());
    }

    /**
     * 崩溃恢复时的插入操作
     * 将 raw 插入 pg 中的 offset 位置，并将 pg 的 offset 更新为较大值
     */
    public static void recoverInsert(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
        short rawFSO = getFSO(pg.getData());
        // 恢复时，如果现在的 FSO 比恢复后的位置小，说明 FSO 没来得及更新，需要补上
        if (rawFSO < offset + raw.length) {
            setFSO(pg.getData(), (short) (offset + raw.length));
        }
    }

    /**
     * 崩溃恢复时的更新操作
     * 仅将 raw 插入 pg 中的 offset 位置，不更新 FSO
     */
    public static void recoverUpdate(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
    }
}