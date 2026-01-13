package backend.tm;

public interface TransactionManager {
    long begin(); // 开启一个新事务

    void commit(long xid); // 提交一个事务

    void abort(long xid); // 取消一个事务

    boolean isActive(long xid); // 查询一个事务的状态是否是正在进行的状态

    boolean isCommitted(long xid); // 查询一个事务的状态是否是已提交

    boolean isAborted(long xid); // 查询一个事务的状态是否是已取消

    void close(); // 关闭 TM

    // 静态工厂方法：创建新的 XID 文件
    static TransactionManager create(String path) {
        return TransactionManagerImpl.create(path);
    }

    // 静态工厂方法：打开已有的 XID 文件
    static TransactionManager open(String path) {
        return TransactionManagerImpl.open(path);
    }
}
