package backend.vm;

import backend.utils.Error;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LockTable 维护事务的等待图，并进行死锁检测
 * 
 * 数据结构：
 * - x2u: XID -> 该事务持有的 UID 列表
 * - u2x: UID -> 持有该资源的 XID
 * - wait: UID -> 等待该资源的 XID 列表
 * - waitLock: XID -> 等待时使用的锁
 * - waitU: XID -> 正在等待的 UID
 */
public class LockTable {

    private Map<Long, List<Long>> x2u; // XID 已获得的 UID 列表
    private Map<Long, Long> u2x; // UID 被哪个 XID 持有
    private Map<Long, List<Long>> wait; // UID -> 等待该 UID 的 XID 列表
    private Map<Long, Lock> waitLock; // XID -> 等待时使用的锁
    private Map<Long, Long> waitU; // XID -> 正在等待的 UID
    private Lock lock;

    // 死锁检测用的访问戳
    private Map<Long, Integer> xidStamp;
    private int stamp;

    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    /**
     * 尝试获取资源的锁
     * 
     * @param xid 事务 ID
     * @param uid 资源 ID
     * @return null 表示无需等待直接获取；否则返回需要等待的锁对象
     * @throws Exception 检测到死锁时抛出 DeadlockException
     */
    public Lock add(long xid, long uid) throws Exception {
        lock.lock();
        try {
            // 已持有该资源，无需再次获取
            if (isInList(x2u, xid, uid)) {
                return null;
            }
            // 资源未被占用，直接获取
            if (!u2x.containsKey(uid)) {
                u2x.put(uid, xid);
                putIntoList(x2u, xid, uid);
                return null;
            }
            // 资源被其他事务占用，需要等待
            waitU.put(xid, uid);
            putIntoList(wait, uid, xid);
            // 死锁检测
            if (hasDeadLock()) {
                waitU.remove(xid);
                removeFromList(wait, uid, xid);
                throw Error.DeadlockException;
            }
            // 创建等待锁并返回
            Lock l = new ReentrantLock();
            l.lock();
            waitLock.put(xid, l);
            return l;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 事务结束时释放所有持有的资源
     * 
     * @param xid 事务 ID
     */
    public void remove(long xid) {
        lock.lock();
        try {
            List<Long> l = x2u.get(xid);
            if (l != null) {
                while (l.size() > 0) {
                    Long uid = l.remove(0);
                    selectNewXID(uid);
                }
            }
            waitU.remove(xid);
            x2u.remove(xid);
            waitLock.remove(xid);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从等待队列中选择一个 XID 来占用释放的 UID
     * 【修复】：同时更新 u2x 和 x2u，保持数据一致性
     * 
     * @param uid 被释放的资源 ID
     */
    private void selectNewXID(long uid) {
        u2x.remove(uid);
        List<Long> l = wait.get(uid);
        if (l == null)
            return;
        while (l.size() > 0) {
            long xid = l.remove(0);
            if (!waitLock.containsKey(xid)) {
                continue;
            } else {
                // 将资源分配给等待的事务
                u2x.put(uid, xid);
                // 【Bug 修复】同时更新 x2u，保持双向映射一致
                putIntoList(x2u, xid, uid);
                // 释放等待锁，让事务继续执行
                Lock lo = waitLock.remove(xid);
                waitU.remove(xid);
                lo.unlock();
                break;
            }
        }
        if (l.size() == 0)
            wait.remove(uid);
    }

    /**
     * 死锁检测：检查等待图中是否存在环
     * 使用访问戳标记已访问节点，通过 DFS 检测环
     */
    private boolean hasDeadLock() {
        xidStamp = new HashMap<>();
        stamp = 1;
        for (long xid : x2u.keySet()) {
            Integer s = xidStamp.get(xid);
            if (s != null && s > 0) {
                continue;
            }
            stamp++;
            if (dfs(xid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFS 遍历等待图检测环
     * 
     * @param xid 起始事务 ID
     * @return true 表示发现环（死锁）
     */
    private boolean dfs(long xid) {
        Integer stp = xidStamp.get(xid);
        if (stp != null && stp == stamp) {
            return true; // 发现环，同一次遍历中再次访问
        }
        if (stp != null && stp < stamp) {
            return false; // 已在之前的连通分量中访问过
        }
        xidStamp.put(xid, stamp);
        // 查找当前事务在等待哪个资源
        Long uid = waitU.get(xid);
        if (uid == null)
            return false;
        // 查找该资源被哪个事务持有
        Long x = u2x.get(uid);
        if (x == null)
            return false;
        // 继续深搜
        return dfs(x);
    }

    // ================= 工具方法 =================

    /**
     * 从 Map<Long, List<Long>> 中移除指定元素
     */
    private void removeFromList(Map<Long, List<Long>> listMap, long key, long value) {
        List<Long> l = listMap.get(key);
        if (l == null)
            return;
        Iterator<Long> i = l.iterator();
        while (i.hasNext()) {
            if (i.next() == value) {
                i.remove();
                break;
            }
        }
        if (l.size() == 0) {
            listMap.remove(key);
        }
    }

    /**
     * 向 Map<Long, List<Long>> 中添加元素
     */
    private void putIntoList(Map<Long, List<Long>> listMap, long key, long value) {
        if (!listMap.containsKey(key)) {
            listMap.put(key, new ArrayList<>());
        }
        listMap.get(key).add(value);
    }

    /**
     * 检查 Map<Long, List<Long>> 中是否包含指定元素
     */
    private boolean isInList(Map<Long, List<Long>> listMap, long key, long value) {
        List<Long> l = listMap.get(key);
        if (l == null)
            return false;
        for (long e : l) {
            if (e == value)
                return true;
        }
        return false;
    }
}