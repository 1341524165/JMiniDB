package benchmark;

import backend.dm.DataManager;
import backend.parser.statement;
import backend.tbm.BeginRes;
import backend.tbm.TableManager;
import backend.tm.TransactionManager;
import backend.vm.VersionManager;
import backend.vm.VersionManagerImpl;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * JTxBase 性能压测工具
 * 
 * 测试内容：
 * 1. 索引查询 vs 全表扫描（10万条数据，P95延迟 + QPS）
 * 2. 并发事务吞吐量（10线程，P95延迟 + TPS）
 */
public class PerformanceBenchmark {

    private static final String TEST_DB_PATH = "benchmark_test_db";
    private static final int RECORD_COUNT = 100_000; // 10万条
    private static final int QUERY_COUNT = 1000; // 查询次数
    private static final int THREAD_COUNT = 10; // 并发线程数
    private static final int OPS_PER_THREAD = 1000; // 每线程操作数

    private TableManager tbm;
    private TransactionManager tm;
    private DataManager dm;

    public static void main(String[] args) {
        PerformanceBenchmark benchmark = new PerformanceBenchmark();
        try {
            benchmark.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws Exception {
        System.out.println("========== JTxBase Performance Benchmark ==========\n");

        // 清理旧的测试数据库
        cleanupTestDB();

        // 创建测试数据库
        createTestDB();

        // 打开数据库
        openTestDB();

        try {
            // 测试1：点查询 vs 范围查询
            benchmarkPointVsRangeQuery();

            // 测试2：并发事务吞吐量
            benchmarkConcurrentTransactions();

        } finally {
            tbm.close();
            System.out.println("\n========== Benchmark Complete ==========");
        }

        // 清理测试数据库
        cleanupTestDB();
    }

    private void cleanupTestDB() {
        String[] extensions = { ".xid", ".db", ".log", ".bt" };
        for (String ext : extensions) {
            File f = new File(TEST_DB_PATH + ext);
            if (f.exists())
                f.delete();
        }
    }

    private void createTestDB() {
        TransactionManager tm = TransactionManager.create(TEST_DB_PATH);
        DataManager dm = DataManager.create(TEST_DB_PATH, (1 << 20) * 64, tm); // 64MB
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager.create(TEST_DB_PATH, vm, dm);
        tm.close();
        dm.close();
        System.out.println("[Setup] Test database created: " + TEST_DB_PATH);
    }

    private void openTestDB() {
        tm = TransactionManager.open(TEST_DB_PATH);
        dm = DataManager.open(TEST_DB_PATH, (1 << 20) * 64, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        tbm = TableManager.open(TEST_DB_PATH, vm, dm);
        System.out.println("[Setup] Test database opened\n");
    }

    /**
     * 测试1：点查询 vs 范围查询（均在索引字段上）
     */
    private void benchmarkPointVsRangeQuery() throws Exception {
        System.out.println("[Test 1] Point Query vs Range Query (" + RECORD_COUNT + " records)");

        // 创建测试表：id (indexed), name, value
        statement.Begin begin = new statement.Begin();
        begin.level = 0; // READ COMMITTED
        BeginRes beginRes = tbm.begin(begin);
        long xid = beginRes.xid;

        statement.Create create = new statement.Create();
        create.tableName = "benchmark_table";
        create.fieldName = new String[] { "id", "name", "value" };
        create.fieldType = new String[] { "int32", "string", "int32" };
        create.index = new String[] { "id" }; // id 字段有索引
        tbm.create(xid, create);
        tbm.commit(xid);

        System.out.println("  - Created table with indexed field 'id'");

        // 插入10万条数据
        System.out.print("  - Inserting " + RECORD_COUNT + " records... ");
        long insertStart = System.currentTimeMillis();

        int batchSize = 1000;
        for (int batch = 0; batch < RECORD_COUNT / batchSize; batch++) {
            beginRes = tbm.begin(begin);
            xid = beginRes.xid;

            for (int i = 0; i < batchSize; i++) {
                int id = batch * batchSize + i;
                statement.Insert insert = new statement.Insert();
                insert.tableName = "benchmark_table";
                insert.values = new String[] { String.valueOf(id), "name_" + id, String.valueOf(id * 10) };
                tbm.insert(xid, insert);
            }
            tbm.commit(xid);

            // 进度显示
            if ((batch + 1) % 20 == 0) {
                System.out.print((batch + 1) * batchSize / 1000 + "k ");
            }
        }
        long insertEnd = System.currentTimeMillis();
        System.out.println("\n  - Insert completed in " + (insertEnd - insertStart) + " ms");
        System.out.printf("  - Insert TPS: %.0f%n", RECORD_COUNT * 1000.0 / (insertEnd - insertStart));

        // 点查询测试（返回1条记录）
        System.out.println("  - Running point queries (WHERE id = X)...");
        List<Long> pointLatencies = new ArrayList<>();
        Random rand = new Random(42);

        for (int i = 0; i < QUERY_COUNT; i++) {
            int targetId = rand.nextInt(RECORD_COUNT);

            beginRes = tbm.begin(begin);
            xid = beginRes.xid;

            statement.Select select = new statement.Select();
            select.tableName = "benchmark_table";
            select.fields = new String[] { "*" };
            select.where = new statement.Where();
            select.where.singleExp1 = new statement.SingleExpression();
            select.where.singleExp1.field = "id";
            select.where.singleExp1.compareOp = "=";
            select.where.singleExp1.value = String.valueOf(targetId);

            long start = System.nanoTime();
            tbm.read(xid, select);
            long end = System.nanoTime();

            pointLatencies.add((end - start) / 1_000); // 转换为微秒
            tbm.commit(xid);
        }

        // 范围查询测试（返回约100条记录，减少查询次数以加快测试）
        int rangeQueryCount = 100; // 范围查询较慢，减少次数
        System.out
                .println("  - Running range queries (WHERE id > X AND id < X+100)... " + rangeQueryCount + " queries");
        List<Long> rangeLatencies = new ArrayList<>();

        for (int i = 0; i < rangeQueryCount; i++) {
            int startId = rand.nextInt(RECORD_COUNT - 100);

            beginRes = tbm.begin(begin);
            xid = beginRes.xid;

            statement.Select select = new statement.Select();
            select.tableName = "benchmark_table";
            select.fields = new String[] { "*" };
            select.where = new statement.Where();
            select.where.singleExp1 = new statement.SingleExpression();
            select.where.singleExp1.field = "id";
            select.where.singleExp1.compareOp = ">";
            select.where.singleExp1.value = String.valueOf(startId);
            select.where.logicOp = "and";
            select.where.singleExp2 = new statement.SingleExpression();
            select.where.singleExp2.field = "id";
            select.where.singleExp2.compareOp = "<";
            select.where.singleExp2.value = String.valueOf(startId + 100);

            long start = System.nanoTime();
            tbm.read(xid, select);
            long end = System.nanoTime();

            rangeLatencies.add((end - start) / 1_000); // 转换为微秒
            tbm.commit(xid);

            // 进度显示
            if ((i + 1) % 20 == 0) {
                System.out.print((i + 1) + " ");
            }
        }
        System.out.println();

        // 计算统计数据
        double pointP95 = calculateP95(pointLatencies);
        double rangeP95 = calculateP95(rangeLatencies);
        double pointAvg = calculateAvg(pointLatencies);
        double rangeAvg = calculateAvg(rangeLatencies);
        long pointQPS = pointAvg > 0 ? (long) (1000.0 / pointAvg) : 0;
        long rangeQPS = rangeAvg > 0 ? (long) (1000.0 / rangeAvg) : 0;

        // 转换为毫秒显示
        System.out.println("\n  Point Query Results (1 record):");
        System.out.printf("    - Avg Latency: %.3f ms (%.0f µs)%n", pointAvg / 1000.0, pointAvg);
        System.out.printf("    - P95 Latency: %.3f ms (%.0f µs)%n", pointP95 / 1000.0, pointP95);
        System.out.printf("    - QPS: %.0f%n", 1_000_000.0 / pointAvg);

        System.out.println("  Range Query Results (~100 records):");
        System.out.printf("    - Avg Latency: %.2f ms%n", rangeAvg / 1000.0);
        System.out.printf("    - P95 Latency: %.2f ms%n", rangeP95 / 1000.0);
        System.out.printf("    - QPS: %.1f%n", 1_000_000.0 / rangeAvg);
    }

    /**
     * 测试2：并发事务吞吐量
     */
    private void benchmarkConcurrentTransactions() throws Exception {
        System.out.println(
                "[Test 2] Concurrent Transactions (" + THREAD_COUNT + " threads × " + OPS_PER_THREAD + " ops)");

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<List<Long>>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                List<Long> latencies = new ArrayList<>();
                statement.Begin begin = new statement.Begin();
                begin.level = 0;

                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    long opStart = System.nanoTime();

                    BeginRes beginRes = tbm.begin(begin);
                    long xid = beginRes.xid;

                    // INSERT
                    int id = RECORD_COUNT + threadId * OPS_PER_THREAD + i;
                    statement.Insert insert = new statement.Insert();
                    insert.tableName = "benchmark_table";
                    insert.values = new String[] { String.valueOf(id), "concurrent_" + id, String.valueOf(id) };
                    tbm.insert(xid, insert);

                    // SELECT
                    statement.Select select = new statement.Select();
                    select.tableName = "benchmark_table";
                    select.fields = new String[] { "*" };
                    select.where = new statement.Where();
                    select.where.singleExp1 = new statement.SingleExpression();
                    select.where.singleExp1.field = "id";
                    select.where.singleExp1.compareOp = "=";
                    select.where.singleExp1.value = String.valueOf(id);
                    tbm.read(xid, select);

                    tbm.commit(xid);

                    long opEnd = System.nanoTime();
                    latencies.add((opEnd - opStart) / 1_000_000);
                }
                return latencies;
            }));
        }

        // 收集所有延迟数据
        List<Long> allLatencies = new ArrayList<>();
        for (Future<List<Long>> future : futures) {
            allLatencies.addAll(future.get());
        }

        long endTime = System.currentTimeMillis();
        executor.shutdown();

        // 计算统计
        long totalOps = (long) THREAD_COUNT * OPS_PER_THREAD;
        long totalTime = endTime - startTime;
        double tps = totalOps * 1000.0 / totalTime;
        double p95 = calculateP95(allLatencies);
        double avg = calculateAvg(allLatencies);

        System.out.printf("  - Total operations: %d%n", totalOps);
        System.out.printf("  - Total time: %d ms%n", totalTime);
        System.out.printf("  - TPS: %.0f%n", tps);
        System.out.printf("  - Avg Latency: %.2f ms%n", avg);
        System.out.printf("  - P95 Latency: %.2f ms%n", p95);
    }

    private double calculateP95(List<Long> latencies) {
        if (latencies.isEmpty())
            return 0;
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int p95Index = (int) (sorted.size() * 0.95);
        return sorted.get(Math.min(p95Index, sorted.size() - 1));
    }

    private double calculateAvg(List<Long> latencies) {
        if (latencies.isEmpty())
            return 0;
        long sum = 0;
        for (Long l : latencies)
            sum += l;
        return (double) sum / latencies.size();
    }
}
