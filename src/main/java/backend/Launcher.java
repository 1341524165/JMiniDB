package backend;

import backend.dm.DataManager;
import backend.server.Server;
import backend.tbm.TableManager;
import backend.tm.TransactionManager;
import backend.vm.VersionManager;
import backend.vm.VersionManagerImpl;

/**
 * 服务端启动入口
 * 使用 -create 创建新数据库
 * 使用 -open 打开已有数据库
 */
public class Launcher {
    public static final int DEFAULT_PORT = 9999;
    public static final long DEFAULT_MEM = (1 << 20) * 64; // 64MB

    public static void main(String[] args) {
        // 解析命令行参数
        String path = null;
        boolean create = false;
        long mem = DEFAULT_MEM;
        int port = DEFAULT_PORT;

        for (int i = 0; i < args.length; i++) {
            if ("-create".equals(args[i])) {
                create = true;
                if (i + 1 < args.length) {
                    path = args[++i];
                }
            } else if ("-open".equals(args[i])) {
                if (i + 1 < args.length) {
                    path = args[++i];
                }
            } else if ("-mem".equals(args[i]) && i + 1 < args.length) {
                mem = Long.parseLong(args[++i]);
            } else if ("-port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        if (path == null) {
            System.out.println("Usage:");
            System.out.println("  Create: java backend.Launcher -create <path>");
            System.out.println("  Open:   java backend.Launcher -open <path> [-port <port>] [-mem <memory>]");
            return;
        }

        if (create) {
            createDB(path);
        } else {
            openDB(path, mem, port);
        }
    }

    /**
     * 创建新数据库
     */
    private static void createDB(String path) {
        TransactionManager tm = TransactionManager.create(path);
        DataManager dm = DataManager.create(path, DEFAULT_MEM, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager.create(path, vm, dm);
        tm.close();
        dm.close();
        System.out.println("Database created: " + path);
    }

    /**
     * 打开已有数据库并启动服务器
     */
    private static void openDB(String path, long mem, int port) {
        TransactionManager tm = TransactionManager.open(path);
        DataManager dm = DataManager.open(path, mem, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager tbm = TableManager.open(path, vm, dm);
        new Server(port, tbm).start();
    }
}
