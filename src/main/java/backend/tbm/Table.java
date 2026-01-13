package backend.tbm;

import backend.parser.statement;
import backend.tm.TransactionManagerImpl;
import backend.utils.Bytes;
import backend.utils.Panic;
import backend.utils.Parser;

import java.util.*;

/**
 * Table 表示数据库中的一张表
 * 二进制格式：[TableName][NextTableUid][Field1Uid][Field2Uid]...[FieldNUid]
 */
public class Table {
    TableManager tbm; // 表管理器引用
    long uid; // 表在 VM 中的 UID
    String name; // 表名
    byte status; // 表状态
    long nextUid; // 下一张表的 UID（链表结构）
    List<Field> fields = new ArrayList<>(); // 字段列表

    public Table(TableManager tbm, long uid) {
        this.tbm = tbm;
        this.uid = uid;
    }

    public Table(TableManager tbm, String tableName, long nextUid) {
        this.tbm = tbm;
        this.name = tableName;
        this.nextUid = nextUid;
    }

    /**
     * 从 VM 中加载表
     */
    public static Table loadTable(TableManager tbm, long uid) {
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e) {
            Panic.panic(e);
        }
        assert raw != null;
        Table tb = new Table(tbm, uid);
        return tb.parseSelf(raw);
    }

    /**
     * 解析表的二进制数据
     */
    private Table parseSelf(byte[] raw) {
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        name = res.str;
        position += res.next;
        nextUid = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
        position += 8;

        // 解析所有字段 UID
        while (position < raw.length) {
            long uid = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
            position += 8;
            fields.add(Field.loadField(this, uid));
        }
        return this;
    }

    /**
     * 创建新表
     */
    public static Table createTable(TableManager tbm, long nextUid, long xid,
            statement.Create create) throws Exception {
        Table tb = new Table(tbm, create.tableName, nextUid);
        for (int i = 0; i < create.fieldName.length; i++) {
            String fieldName = create.fieldName[i];
            String fieldType = create.fieldType[i];
            boolean indexed = false;
            for (String indexName : create.index) {
                if (fieldName.equals(indexName)) {
                    indexed = true;
                    break;
                }
            }
            tb.fields.add(Field.createField(tb, xid, fieldName, fieldType, indexed));
        }
        return tb.persistSelf(xid);
    }

    /**
     * 持久化表到 VM
     */
    private Table persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(name);
        byte[] nextRaw = Parser.long2Byte(nextUid);
        byte[] fieldRaw = new byte[0];
        for (Field f : fields) {
            fieldRaw = Bytes.concat(fieldRaw, Parser.long2Byte(f.uid));
        }
        uid = ((TableManagerImpl) tbm).vm.insert(xid, Bytes.concat(nameRaw, nextRaw, fieldRaw));
        return this;
    }

    /**
     * 插入数据
     */
    public void insert(long xid, statement.Insert insert) throws Exception {
        Map<String, Object> entry = string2Entry(insert.values);
        byte[] raw = entry2Raw(entry);
        long uid = ((TableManagerImpl) tbm).vm.insert(xid, raw);
        for (Field f : fields) {
            if (f.isIndexed()) {
                f.insert(entry.get(f.fieldName), uid);
            }
        }
    }

    /**
     * 将值字符串数组转为 Map
     */
    private Map<String, Object> string2Entry(String[] values) throws Exception {
        if (values.length != fields.size()) {
            throw new RuntimeException("Values count doesn't match fields count");
        }
        Map<String, Object> entry = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Object v = parseValue(f.fieldType, values[i]);
            entry.put(f.fieldName, v);
        }
        return entry;
    }

    /**
     * 解析值字符串
     */
    private Object parseValue(String fieldType, String valueStr) {
        switch (fieldType) {
            case "int32":
                return Integer.parseInt(valueStr);
            case "int64":
                return Long.parseLong(valueStr);
            case "string":
                return valueStr;
        }
        return null;
    }

    /**
     * 将 Map 序列化为二进制
     */
    private byte[] entry2Raw(Map<String, Object> entry) {
        byte[] raw = new byte[0];
        for (Field f : fields) {
            raw = Bytes.concat(raw, f.value2Raw(entry.get(f.fieldName)));
        }
        return raw;
    }

    /**
     * 删除数据
     */
    public int delete(long xid, statement.Delete delete) throws Exception {
        List<Long> uids = parseWhere(delete.where);
        int count = 0;
        for (Long uid : uids) {
            if (((TableManagerImpl) tbm).vm.delete(xid, uid)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 更新数据
     */
    public int update(long xid, statement.Update update) throws Exception {
        List<Long> uids = parseWhere(update.where);
        Field fd = null;
        for (Field f : fields) {
            if (f.fieldName.equals(update.fieldName)) {
                fd = f;
                break;
            }
        }
        if (fd == null) {
            throw new RuntimeException("Field not found: " + update.fieldName);
        }
        Object value = parseValue(fd.fieldType, update.value);
        int count = 0;
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (raw == null)
                continue;
            ((TableManagerImpl) tbm).vm.delete(xid, uid);
            Map<String, Object> entry = parseEntry(raw);
            entry.put(fd.fieldName, value);
            byte[] newRaw = entry2Raw(entry);
            long newUid = ((TableManagerImpl) tbm).vm.insert(xid, newRaw);
            // 更新索引
            for (Field f : fields) {
                if (f.isIndexed()) {
                    f.insert(entry.get(f.fieldName), newUid);
                }
            }
            count++;
        }
        return count;
    }

    /**
     * 查询数据
     */
    public String read(long xid, statement.Select select) throws Exception {
        List<Long> uids = parseWhere(select.where);
        StringBuilder sb = new StringBuilder();
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (raw == null)
                continue;
            Map<String, Object> entry = parseEntry(raw);
            sb.append(printEntry(entry, select.fields)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析 WHERE 条件
     */
    private List<Long> parseWhere(statement.Where where) throws Exception {
        long l0 = 0, r0 = 0, l1 = 0, r1 = 0;
        boolean single = false;
        Field fd = null;
        if (where == null) {
            // 无条件，查询所有
            for (Field f : fields) {
                if (f.isIndexed()) {
                    fd = f;
                    break;
                }
            }
            l0 = 0;
            r0 = Long.MAX_VALUE;
            single = true;
        } else {
            for (Field f : fields) {
                if (f.fieldName.equals(where.singleExp1.field)) {
                    if (!f.isIndexed()) {
                        throw new RuntimeException("Field not indexed: " + f.fieldName);
                    }
                    fd = f;
                    break;
                }
            }
            if (fd == null) {
                throw new RuntimeException("Field not found: " + where.singleExp1.field);
            }
            FieldCalRes res = fd.calExp(where.singleExp1);
            l0 = res.left;
            r0 = res.right;
            if (where.singleExp2 == null) {
                single = true;
            } else {
                res = fd.calExp(where.singleExp2);
                l1 = res.left;
                r1 = res.right;
            }
        }
        List<Long> uids = fd.search(l0, r0);
        if (!single) {
            List<Long> uids1 = fd.search(l1, r1);
            if ("or".equals(where.logicOp)) {
                uids = mergeLists(uids, uids1);
            } else {
                uids = intersectLists(uids, uids1);
            }
        }
        return uids;
    }

    /**
     * 解析二进制为 Map
     */
    private Map<String, Object> parseEntry(byte[] raw) {
        int pos = 0;
        Map<String, Object> entry = new HashMap<>();
        for (Field f : fields) {
            Object v = f.parseValue(Arrays.copyOfRange(raw, pos, raw.length));
            entry.put(f.fieldName, v);
            pos += getRawLen(f, v);
        }
        return entry;
    }

    /**
     * 获取值的字节长度
     */
    private int getRawLen(Field f, Object v) {
        switch (f.fieldType) {
            case "int32":
                return 4;
            case "int64":
                return 8;
            case "string":
                return 4 + ((String) v).getBytes().length;
        }
        return 0;
    }

    /**
     * 打印条目
     */
    private String printEntry(Map<String, Object> entry, String[] selectFields) {
        StringBuilder sb = new StringBuilder("[");
        String[] fieldsToShow = selectFields;
        if (selectFields.length == 1 && "*".equals(selectFields[0])) {
            fieldsToShow = new String[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                fieldsToShow[i] = fields.get(i).fieldName;
            }
        }
        for (int i = 0; i < fieldsToShow.length; i++) {
            String fn = fieldsToShow[i];
            sb.append(fn).append("=").append(entry.get(fn));
            if (i < fieldsToShow.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 合并两个列表（OR）
     */
    private List<Long> mergeLists(List<Long> l1, List<Long> l2) {
        Set<Long> set = new HashSet<>(l1);
        set.addAll(l2);
        return new ArrayList<>(set);
    }

    /**
     * 取两个列表的交集（AND）
     */
    private List<Long> intersectLists(List<Long> l1, List<Long> l2) {
        Set<Long> set = new HashSet<>(l1);
        set.retainAll(l2);
        return new ArrayList<>(set);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append(name).append(": ");
        for (int i = 0; i < fields.size(); i++) {
            sb.append(fields.get(i).toString());
            if (i < fields.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
