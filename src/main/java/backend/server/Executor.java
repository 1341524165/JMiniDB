package backend.server;

import backend.parser.Parser;
import backend.parser.statement;
import backend.tbm.BeginRes;
import backend.tbm.TableManager;

/**
 * Executor 执行 SQL 语句
 * 解析 SQL 后调用 TBM 对应方法
 */
public class Executor {
    private long xid;
    TableManager tbm;

    public Executor(TableManager tbm) {
        this.tbm = tbm;
        this.xid = 0;
    }

    public void close() {
        if (xid != 0) {
            System.out.println("Abnormal Abort: " + xid);
            tbm.abort(xid);
        }
    }

    /**
     * 执行 SQL 语句
     */
    public byte[] execute(byte[] sql) throws Exception {
        System.out.println("Execute: " + new String(sql));
        Object stat = Parser.Parse(sql);

        if (stat instanceof statement.Begin) {
            // 开始事务
            if (xid != 0) {
                throw new RuntimeException("Nested transaction not supported!");
            }
            BeginRes res = tbm.begin((statement.Begin) stat);
            xid = res.xid;
            return res.result;
        } else if (stat instanceof statement.Commit) {
            // 提交事务
            if (xid == 0) {
                throw new RuntimeException("No transaction!");
            }
            byte[] res = tbm.commit(xid);
            xid = 0;
            return res;
        } else if (stat instanceof statement.Abort) {
            // 回滚事务
            if (xid == 0) {
                throw new RuntimeException("No transaction!");
            }
            byte[] res = tbm.abort(xid);
            xid = 0;
            return res;
        } else {
            return execute2(stat);
        }
    }

    /**
     * 执行非事务控制语句
     */
    private byte[] execute2(Object stat) throws Exception {
        boolean tmpTransaction = false;
        Exception e = null;
        if (xid == 0) {
            tmpTransaction = true;
            BeginRes r = tbm.begin(new statement.Begin());
            xid = r.xid;
        }
        try {
            byte[] res = null;
            if (stat instanceof statement.Show) {
                res = tbm.show(xid);
            } else if (stat instanceof statement.Create) {
                res = tbm.create(xid, (statement.Create) stat);
            } else if (stat instanceof statement.Select) {
                res = tbm.read(xid, (statement.Select) stat);
            } else if (stat instanceof statement.Insert) {
                res = tbm.insert(xid, (statement.Insert) stat);
            } else if (stat instanceof statement.Delete) {
                res = tbm.delete(xid, (statement.Delete) stat);
            } else if (stat instanceof statement.Update) {
                res = tbm.update(xid, (statement.Update) stat);
            } else if (stat instanceof statement.Drop) {
                // Drop 暂未实现
                res = "drop not implemented".getBytes();
            }
            return res;
        } catch (Exception e1) {
            e = e1;
            throw e;
        } finally {
            if (tmpTransaction) {
                if (e != null) {
                    tbm.abort(xid);
                } else {
                    tbm.commit(xid);
                }
                xid = 0;
            }
        }
    }
}
