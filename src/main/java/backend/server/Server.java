package backend.server;

import backend.tbm.TableManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Server 监听端口并处理客户端连接
 */
public class Server {
    private int port;
    private TableManager tbm;

    public Server(int port, TableManager tbm) {
        this.port = port;
        this.tbm = tbm;
    }

    /**
     * 启动服务器
     */
    public void start() {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Server started on port " + port);

        // 创建线程池处理客户端连接
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                10, // 核心线程数
                20, // 最大线程数
                1L, // 空闲线程存活时间
                TimeUnit.SECONDS, // 时间单位
                new ArrayBlockingQueue<>(100), // 任务队列
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );

        try {
            while (true) {
                Socket socket = ss.accept();
                Runnable worker = new HandleSocket(socket, tbm);
                tpe.execute(worker);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
