package backend.tbm;

import backend.dm.DataManager;
import backend.parser.statement;
import backend.utils.Parser;
import backend.vm.VersionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TableManagerImpl 是 TableManager 接口的实现类
 */
public class TableManagerImpl implements TableManager {
    VersionManager vm; // 版本管理器
    DataManager dm; // 数据管理器
    private Booter booter; // 启动信息管理
    private Map<String, Table> tableCache; // 表缓存
    private Map<Long, List<Table>> xidTableCache; // 事务关联的表
    private Lock lock; // 锁

    public TableManagerImpl(VersionManager vm, DataManager dm, Booter booter) {
        this.vm = vm;
        this.dm = dm;
        this.booter = booter;
        this.tableCache = new HashMap<>();
        this.xidTableCache = new HashMap<>();
        this.lock = new ReentrantLock();
        loadTables();
    }

    /**
     * 加载所有表
     */
    private void loadTables() {
        long uid = firstTableUid();
        while (uid != 0) {
            Table tb = Table.loadTable(this, uid);
            uid = tb.nextUid;
            tableCache.put(tb.name, tb);
        }
    }

    /**
     * 获取头表 UID
     */
    private long firstTableUid() {
        byte[] raw = booter.load();
        return Parser.parseLong(raw);
    }

    /**
     * 更新头表 UID
     */
    private void updateFirstTableUid(long uid) {
        byte[] raw = Parser.long2Byte(uid);
        booter.update(raw);
    }

    @Override
    public BeginRes begin(statement.Begin begin) {
        BeginRes res = new BeginRes();
        int level = begin.level;
        res.xid = vm.begin(level);
        res.result = ("begin xid=" + res.xid).getBytes();
        return res;
    }

    @Override
    public byte[] commit(long xid) throws Exception {
        vm.commit(xid);
        return "commit".getBytes();
    }

    @Override
    public byte[] abort(long xid) {
        vm.abort(xid);
        return "abort".getBytes();
    }

    @Override
    public byte[] show(long xid) {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            for (Table tb : tableCache.values()) {
                sb.append(tb.toString()).append("\n");
            }
            return sb.toString().getBytes();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] create(long xid, statement.Create create) throws Exception {
        lock.lock();
        try {
            if (tableCache.containsKey(create.tableName)) {
                throw new RuntimeException("Duplicate table name: " + create.tableName);
            }
            // 头插法：新表的 nextUid 指向原来的头表
            Table tb = Table.createTable(this, firstTableUid(), xid, create);
            updateFirstTableUid(tb.uid);
            tableCache.put(tb.name, tb);
            if (!xidTableCache.containsKey(xid)) {
                xidTableCache.put(xid, new ArrayList<>());
            }
            xidTableCache.get(xid).add(tb);
            return ("create table " + create.tableName).getBytes();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] insert(long xid, statement.Insert insert) throws Exception {
        lock.lock();
        Table tb = tableCache.get(insert.tableName);
        lock.unlock();
        if (tb == null) {
            throw new RuntimeException("Table not found: " + insert.tableName);
        }
        tb.insert(xid, insert);
        return "insert".getBytes();
    }

    @Override
    public byte[] read(long xid, statement.Select select) throws Exception {
        lock.lock();
        Table tb = tableCache.get(select.tableName);
        lock.unlock();
        if (tb == null) {
            throw new RuntimeException("Table not found: " + select.tableName);
        }
        return tb.read(xid, select).getBytes();
    }

    @Override
    public byte[] update(long xid, statement.Update update) throws Exception {
        lock.lock();
        Table tb = tableCache.get(update.tableName);
        lock.unlock();
        if (tb == null) {
            throw new RuntimeException("Table not found: " + update.tableName);
        }
        int count = tb.update(xid, update);
        return ("update " + count).getBytes();
    }

    @Override
    public byte[] delete(long xid, statement.Delete delete) throws Exception {
        lock.lock();
        Table tb = tableCache.get(delete.tableName);
        lock.unlock();
        if (tb == null) {
            throw new RuntimeException("Table not found: " + delete.tableName);
        }
        int count = tb.delete(xid, delete);
        return ("delete " + count).getBytes();
    }

    @Override
    public void close() {
        // 关闭资源
    }
}
