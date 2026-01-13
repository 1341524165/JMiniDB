package backend.tbm;

import backend.dm.DataManager;
import backend.parser.statement;
import backend.vm.VersionManager;

/**
 * TableManager 是 TBM 层对外提供的接口
 */
public interface TableManager {

    /**
     * 开始一个事务
     */
    BeginRes begin(statement.Begin begin);

    /**
     * 提交事务
     */
    byte[] commit(long xid) throws Exception;

    /**
     * 回滚事务
     */
    byte[] abort(long xid);

    /**
     * 显示所有表
     */
    byte[] show(long xid);

    /**
     * 创建表
     */
    byte[] create(long xid, statement.Create create) throws Exception;

    /**
     * 插入数据
     */
    byte[] insert(long xid, statement.Insert insert) throws Exception;

    /**
     * 查询数据
     */
    byte[] read(long xid, statement.Select select) throws Exception;

    /**
     * 更新数据
     */
    byte[] update(long xid, statement.Update update) throws Exception;

    /**
     * 删除数据
     */
    byte[] delete(long xid, statement.Delete delete) throws Exception;

    /**
     * 关闭 TableManager
     */
    void close();

    /**
     * 创建新的 TableManager
     */
    static TableManager create(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.create(path);
        booter.update(new byte[8]); // 初始化为空（头表 UID = 0）
        return new TableManagerImpl(vm, dm, booter);
    }

    /**
     * 打开已有的 TableManager
     */
    static TableManager open(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.open(path);
        return new TableManagerImpl(vm, dm, booter);
    }
}
