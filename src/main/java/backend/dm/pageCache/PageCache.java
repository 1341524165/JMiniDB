package backend.dm.pageCache;

import backend.dm.page.Page;

/**
 * 页面缓存接口
 * 负责管理所有页面，包括加载、释放、新建等
 */
public interface PageCache {
    // 页面大小 8192 字节 (8KB)，作为全局变量供 PageCacheImpl 和 PageX 使用
    public static final int PAGE_SIZE = 1 << 13;

    // 新建一个页面，以 initData 为初始数据
    int newPage(byte[] initData);

    // 获取第 pgno 页
    Page getPage(int pgno) throws Exception;

    // 关闭缓存，释放所有资源
    void close();

    // 释放某个被引用的页面
    void release(Page page);

    // 截断文件，只保留 pgno 之前的页面 (用于故障恢复)
    void truncateByBgno(int maxPgno);

    // 获取当前总页数
    int getPageNumber();

    // 强制将页面写回磁盘
    void flushPage(Page page);
}