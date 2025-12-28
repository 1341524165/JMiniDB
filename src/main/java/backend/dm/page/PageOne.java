package backend.dm.page;

import backend.dm.pageCache.PageCache;
import backend.utils.RandomUtil;
import java.util.Arrays;

/**
 * 特殊管理第一页
 * 主要用于 Valid Check (启动校验)
 */
public class PageOne {
    private static final int OF_VC = 100; // 校验字节起始偏移量
    private static final int LEN_VC = 8; // 校验字节长度

    /**
     * 启动时设置初始校验字节
     */
    public static void setVcOpen(Page pg) {
        pg.setDirty(true);
        setVcOpen(pg.getData());
    }

    private static void setVcOpen(byte[] raw) {
        // 生成随机字节，放到 [100, 108)
        System.arraycopy(RandomUtil.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

    /**
     * 关闭时拷贝校验字节
     * 将 [100, 108) 的内容拷贝到 [108, 116)
     */
    public static void setVcClose(Page pg) {
        pg.setDirty(true);
        setVcClose(pg.getData());
    }

    private static void setVcClose(byte[] raw) {
        System.arraycopy(raw, OF_VC, raw, OF_VC + LEN_VC, LEN_VC);
    }

    /**
     * 启动检查：校验两个位置的字节是否一致
     */
    public static boolean checkVc(Page pg) {
        return checkVc(pg.getData());
    }

    private static boolean checkVc(byte[] raw) {
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC + LEN_VC),
                Arrays.copyOfRange(raw, OF_VC + LEN_VC, OF_VC + 2 * LEN_VC));
    }
}