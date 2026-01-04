package backend.vm;

import backend.tm.TransactionManager;

/**
 * Visibility 负责判断版本对事务的可见性
 */
public class Visibility {
    /**
     * 判断版本对事务是否可见
     * 
     * @param tm TransactionManager
     * @param t  当前事务
     * @param e  要判断的记录版本
     * @return true 表示可见
     */
    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if (t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    /**
     * 读提交隔离级别的可见性判断
     */
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        // 由当前事务创建且未被删除
        if (xmin == xid && xmax == 0)
            return true;
        // 由已提交事务创建
        if (tm.isCommitted(xmin)) {
            // 尚未被删除
            if (xmax == 0)
                return true;
            // 被其他事务删除
            if (xmax != xid) {
                // 删除事务未提交
                if (!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 可重复读隔离级别的可见性判断
     */
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        // 由当前事务创建且未被删除
        if (xmin == xid && xmax == 0)
            return true;
        // 由已提交事务创建 && 该事务在当前事务之前 && 该事务不在快照中（已提交）
        if (tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            // 尚未被删除
            if (xmax == 0)
                return true;
            // 被其他事务删除
            if (xmax != xid) {
                // 删除事务未提交 || 删除事务在当前事务之后 || 删除事务在快照中（当时还活跃）
                if (!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检测是否发生版本跳跃
     * 可重复读隔离级别下，如果要修改的数据已被不可见的事务修改，需要回滚
     * 
     * @param tm TransactionManager
     * @param t  当前事务
     * @param e  要修改的记录
     * @return true 表示发生版本跳跃，需要回滚
     */
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();
        // 读提交允许版本跳跃
        if (t.level == 0) {
            return false;
        } else {
            // 可重复读：如果 XMAX 已提交，且对当前事务不可见，则发生版本跳跃
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }
}