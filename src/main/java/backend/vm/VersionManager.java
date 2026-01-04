package backend.vm;

import backend.dm.DataManager;
import backend.tm.TransactionManager;

/**
 * VersionManager 是 VM 层对外提供的接口
 */
public interface VersionManager {

    /**
     * 读取一条记录
     */
    byte[] read(long xid, long uid) throws Exception;

    /**
     * 插入一条记录
     */
    long insert(long xid, byte[] data) throws Exception;

    /**
     * 删除一条记录
     */
    boolean delete(long xid, long uid) throws Exception;

    /**
     * 开始一个事务
     */
    long begin(int level);

    /**
     * 提交一个事务
     */
    void commit(long xid) throws Exception;

    /**
     * 回滚一个事务
     */
    void abort(long xid);

    /**
     * 关闭 VM
     */
    void close();

    public static VersionManager newVersionManager(TransactionManager tm, DataManager dm) {
        return new VersionManagerImpl(tm, dm);
    }
}