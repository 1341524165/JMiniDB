package backend.dm;

import backend.dm.dataItem.DataItem;
import backend.tm.TransactionManager;

public interface DataManager {
    DataItem read(long uid) throws Exception;

    long insert(long xid, byte[] data) throws Exception;

    void close();

    public static DataManager create(String path, long mem, TransactionManager tm) {
        return DataManagerImpl.create(path, mem, tm);
    }

    public static DataManager open(String path, long mem, TransactionManager tm) {
        return DataManagerImpl.open(path, mem, tm);
    }
}