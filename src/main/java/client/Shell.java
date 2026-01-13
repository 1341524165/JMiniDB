package client;

import java.util.Scanner;

/**
 * Shell 提供命令行界面
 * 读取用户输入并调用 Client 执行
 */
public class Shell {
    private Client client;

    public Shell(Client client) {
        this.client = client;
    }

    /**
     * 运行 Shell
     */
    public void run() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                System.out.print("JTxBase> ");
                String statStr = scanner.nextLine();
                if (statStr.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(statStr) || "quit".equalsIgnoreCase(statStr)) {
                    break;
                }
                try {
                    byte[] res = client.execute(statStr.getBytes());
                    System.out.println(new String(res));
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } finally {
            scanner.close();
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
