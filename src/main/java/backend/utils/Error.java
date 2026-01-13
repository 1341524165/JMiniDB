package backend.utils;

public class Error {
    // common
    public static final Exception FileExistsException = new RuntimeException("File already exists!");
    public static final Exception FileCannotRWException = new RuntimeException("File cannot read or write!");
    public static final Exception CacheFullException = new RuntimeException("Cache is full!");
    public static final Exception FileNotExistsException = new RuntimeException("File does not exists!");

    // dm
    public static final Exception BadLogFileException = new RuntimeException("Bad log file!");
    public static final Exception MemTooSmallException = new RuntimeException("Memory too small!");
    public static final Exception DataTooLargeException = new RuntimeException("Data too large!");
    public static final Exception DatabaseBusyException = new RuntimeException("Database is busy!");

    // tm
    public static final Exception BadXIDFileException = new RuntimeException("Bad XID file!");

    // vm
    public static final Exception NullEntryException = new RuntimeException("Entry is null!");
    public static final Exception ConcurrentUpdateException = new RuntimeException("Concurrent update issue!");
    public static final Exception DeadlockException = new RuntimeException("Deadlock detected!");

    // parser
    public static final Exception InvalidCommandException = new RuntimeException("Invalid command!");

    // transport
    public static final Exception InvalidPkgDataException = new RuntimeException("Invalid package data!");
}