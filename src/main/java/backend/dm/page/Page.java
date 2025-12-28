package backend.dm.page;

/**
 * 页面接口
 * 定义了对内存中 Page 对象的基本操作
 */
public interface Page {
    // 锁定页面，防止并发修改
    void lock();

    // 解锁页面
    void unlock();

    // 释放页面缓存（当上层模块使用完页面后调用）
    void release();

    // 标记页面为“脏”页面（已被修改）
    // 只有脏页面在被驱逐时才需要写回磁盘
    void setDirty(boolean dirty);

    // 判断页面是否是脏页面
    boolean isDirty();

    // 获取页号
    int getPageNumber();

    // 获取页面实际包含的字节数据
    byte[] getData();
}