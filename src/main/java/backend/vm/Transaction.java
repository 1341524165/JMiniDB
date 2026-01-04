package backend.vm;

import java.util.HashMap;
import java.util.Map;
import backend.tm.TransactionManagerImpl;

/**
 * Transaction 维护了事务的结构
 */
public class Transaction {
    public long xid; // 事务 ID
    public int level; // 隔离级别：0=读提交, 1=可重复读
    public Map<Long, Boolean> snapshot; // 事务快照（仅可重复读需要）
    public Exception err; // 事务错误
    public boolean autoAborted; // 是否自动回滚

    /**
     * 创建新事务
     * 
     * @param xid    事务 ID
     * @param level  隔离级别
     * @param active 当前所有活跃事务的映射
     * @return Transaction 实例
     */
    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        // 如果是可重复读，需要记录当前活跃事务的快照
        if (level != 0) {
            t.snapshot = new HashMap<>();
            for (Long x : active.keySet()) {
                t.snapshot.put(x, true);
            }
        }
        return t;
    }

    /**
     * 判断某个事务是否在当前事务的快照中
     * 
     * @param xid 要判断的事务 ID
     * @return true 表示在快照中（事务开始时该事务还活跃）
     */
    public boolean isInSnapshot(long xid) {
        // 超级事务（XID=0）永远不在快照中
        if (xid == TransactionManagerImpl.SUPER_XID) {
            return false;
        }
        return snapshot.containsKey(xid);
    }
}