package backend.tbm;

import backend.im.BPlusTree;
import backend.parser.statement;
import backend.tm.TransactionManagerImpl;
import backend.utils.Panic;
import backend.utils.Parser;
import backend.utils.Bytes;

import java.util.Arrays;
import java.util.List;

/**
 * Field 表示表中的一个字段
 * 二进制格式：[FieldName][TypeName][IndexUid]
 * 其中字符串格式为：[StringLength(4字节)][StringData]
 */
public class Field {
    long uid; // 字段在 VM 中的 UID
    private Table tb; // 所属表
    String fieldName; // 字段名
    String fieldType; // 字段类型: int32, int64, string
    private long index; // 索引 B+ 树的 UID，0 表示无索引
    private BPlusTree bt; // B+ 树索引对象

    public Field(long uid, Table tb) {
        this.uid = uid;
        this.tb = tb;
    }

    public Field(Table tb, String fieldName, String fieldType, long index) {
        this.tb = tb;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
    }

    /**
     * 从 VM 中加载字段
     */
    public static Field loadField(Table tb, long uid) {
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) tb.tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e) {
            Panic.panic(e);
        }
        assert raw != null;
        return new Field(uid, tb).parseSelf(raw);
    }

    /**
     * 解析字段的二进制数据
     */
    private Field parseSelf(byte[] raw) {
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        fieldName = res.str;
        position += res.next;
        res = Parser.parseString(Arrays.copyOfRange(raw, position, raw.length));
        fieldType = res.str;
        position += res.next;
        this.index = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
        if (index != 0) {
            try {
                bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            } catch (Exception e) {
                Panic.panic(e);
            }
        }
        return this;
    }

    /**
     * 创建字段并持久化
     */
    public static Field createField(Table tb, long xid, String fieldName, String fieldType, boolean indexed)
            throws Exception {
        typeCheck(fieldType);
        Field f = new Field(tb, fieldName, fieldType, 0);
        if (indexed) {
            long index = BPlusTree.create(((TableManagerImpl) tb.tbm).dm);
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            f.index = index;
            f.bt = bt;
        }
        f.persistSelf(xid);
        return f;
    }

    /**
     * 持久化字段到 VM
     */
    private void persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(fieldName);
        byte[] typeRaw = Parser.string2Byte(fieldType);
        byte[] indexRaw = Parser.long2Byte(index);
        this.uid = ((TableManagerImpl) tb.tbm).vm.insert(xid, Bytes.concat(nameRaw, typeRaw, indexRaw));
    }

    /**
     * 检查字段类型是否合法
     */
    private static void typeCheck(String fieldType) throws Exception {
        if (!"int32".equals(fieldType) && !"int64".equals(fieldType) && !"string".equals(fieldType)) {
            throw new RuntimeException("Invalid field type: " + fieldType);
        }
    }

    /**
     * 判断是否有索引
     */
    public boolean isIndexed() {
        return index != 0;
    }

    /**
     * 向索引中插入键值对
     */
    public void insert(Object key, long uid) throws Exception {
        long uKey = value2Uid(key);
        bt.insert(uKey, uid);
    }

    /**
     * 在索引中搜索
     */
    public List<Long> search(long left, long right) throws Exception {
        return bt.searchRange(left, right);
    }

    /**
     * 将值转换为 long 类型（用于索引）
     */
    public long value2Uid(Object key) {
        long uid = 0;
        switch (fieldType) {
            case "string":
                uid = Parser.str2Uid((String) key);
                break;
            case "int32":
                uid = (int) key;
                break;
            case "int64":
                uid = (long) key;
                break;
        }
        return uid;
    }

    /**
     * 将二进制数据解析为值
     */
    public Object parseValue(byte[] raw) {
        switch (fieldType) {
            case "int32":
                return Parser.parseInt(Arrays.copyOf(raw, 4));
            case "int64":
                return Parser.parseLong(Arrays.copyOf(raw, 8));
            case "string":
                return Parser.parseString(raw).str;
        }
        return null;
    }

    /**
     * 将值序列化为二进制
     */
    public byte[] value2Raw(Object v) {
        switch (fieldType) {
            case "int32":
                return Parser.int2Byte((int) v);
            case "int64":
                return Parser.long2Byte((long) v);
            case "string":
                return Parser.string2Byte((String) v);
        }
        return null;
    }

    /**
     * 计算单个表达式的范围
     */
    public FieldCalRes calExp(statement.SingleExpression exp) throws Exception {
        Object v = null;
        FieldCalRes res = new FieldCalRes();
        switch (fieldType) {
            case "int32":
                v = Integer.parseInt(exp.value);
                break;
            case "int64":
                v = Long.parseLong(exp.value);
                break;
            case "string":
                v = exp.value;
                break;
        }
        long uid = value2Uid(v);
        switch (exp.compareOp) {
            case "<":
                res.left = 0;
                res.right = uid;
                if (res.right > 0)
                    res.right--;
                break;
            case "=":
                res.left = uid;
                res.right = uid;
                break;
            case ">":
                res.left = uid + 1;
                res.right = Long.MAX_VALUE;
                break;
        }
        return res;
    }

    /**
     * 字段值转字符串（打印用）
     */
    public String printValue(Object v) {
        return String.valueOf(v);
    }

    @Override
    public String toString() {
        return fieldName + " (" + fieldType + ")" + (isIndexed() ? " [indexed]" : "");
    }
}

/**
 * 字段计算结果，表示一个范围
 */
class FieldCalRes {
    long left;
    long right;
}
