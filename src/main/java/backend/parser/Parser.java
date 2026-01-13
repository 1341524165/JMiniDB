package backend.parser;

import backend.utils.Error;

public class Parser {
    public static Object Parse(byte[] statement) throws Exception {
        Tokenizer tokenizer = new Tokenizer(statement);
        String token = tokenizer.peek();
        tokenizer.pop();
        Object stat = null;
        Exception statErr = null;
        try {
            switch (token) {
                case "begin":
                    stat = parseBegin(tokenizer);
                    break;
                case "commit":
                    stat = parseCommit(tokenizer);
                    break;
                case "abort":
                    stat = parseAbort(tokenizer);
                    break;
                case "create":
                    stat = parseCreate(tokenizer);
                    break;
                case "drop":
                    stat = parseDrop(tokenizer);
                    break;
                case "select":
                    stat = parseSelect(tokenizer);
                    break;
                case "insert":
                    stat = parseInsert(tokenizer);
                    break;
                case "delete":
                    stat = parseDelete(tokenizer);
                    break;
                case "update":
                    stat = parseUpdate(tokenizer);
                    break;
                case "show":
                    stat = parseShow(tokenizer);
                    break;
                default:
                    throw Error.InvalidCommandException;
            }
        } catch (Exception e) {
            statErr = e;
        }
        if (statErr != null)
            throw statErr;
        return stat;
    }

    private static statement.Begin parseBegin(Tokenizer tokenizer) throws Exception {
        String isolation = tokenizer.peek();
        statement.Begin begin = new statement.Begin();
        if ("".equals(isolation)) {
            begin.level = 0;
            return begin;
        }
        if (!"isolation".equals(isolation))
            throw Error.InvalidCommandException;
        tokenizer.pop();
        if (!"level".equals(tokenizer.peek()))
            throw Error.InvalidCommandException;
        tokenizer.pop();
        String tmp1 = tokenizer.peek();
        tokenizer.pop();
        String tmp2 = tokenizer.peek();
        tokenizer.pop();
        String levelStr = tmp1 + " " + tmp2;
        if ("read committed".equals(levelStr)) {
            begin.level = 0;
        } else if ("repeatable read".equals(levelStr)) {
            begin.level = 1;
        } else {
            throw Error.InvalidCommandException;
        }
        if (!"".equals(tokenizer.peek()))
            throw Error.InvalidCommandException;
        return begin;
    }

    // ... 其他 parse 方法省略，参考完整实现
    private static statement.Commit parseCommit(Tokenizer t) throws Exception {
        return new statement.Commit();
    }

    private static statement.Abort parseAbort(Tokenizer t) throws Exception {
        return new statement.Abort();
    }

    private static statement.Show parseShow(Tokenizer t) throws Exception {
        return new statement.Show();
    }

    private static statement.Drop parseDrop(Tokenizer t) throws Exception {
        // drop table <tableName>
        if (!"table".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();
        statement.Drop drop = new statement.Drop();
        drop.tableName = t.peek();
        t.pop();
        if (!"".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        return drop;
    }

    /**
     * 解析 CREATE TABLE 语句
     * 格式: create table <tableName> (<fieldName> <fieldType> [index], ...)
     */
    private static statement.Create parseCreate(Tokenizer t) throws Exception {
        // create table <tableName>
        if (!"table".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        statement.Create create = new statement.Create();
        create.tableName = t.peek();
        t.pop();

        if (!"(".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        java.util.List<String> fieldNames = new java.util.ArrayList<>();
        java.util.List<String> fieldTypes = new java.util.ArrayList<>();
        java.util.List<String> indexes = new java.util.ArrayList<>();

        while (true) {
            String fieldName = t.peek();
            t.pop();
            String fieldType = t.peek();
            t.pop();

            fieldNames.add(fieldName);
            fieldTypes.add(fieldType);

            String next = t.peek();
            if ("index".equals(next)) {
                indexes.add(fieldName);
                t.pop();
                next = t.peek();
            }
            if (")".equals(next)) {
                t.pop();
                break;
            }
            if (",".equals(next)) {
                t.pop();
            }
        }

        if (!"".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }

        create.fieldName = fieldNames.toArray(new String[0]);
        create.fieldType = fieldTypes.toArray(new String[0]);
        create.index = indexes.toArray(new String[0]);
        return create;
    }

    /**
     * 解析 SELECT 语句
     * 格式: select <fields> from <tableName> [where <condition>]
     */
    private static statement.Select parseSelect(Tokenizer t) throws Exception {
        statement.Select select = new statement.Select();

        java.util.List<String> fields = new java.util.ArrayList<>();
        String field = t.peek();
        while (!"from".equals(field) && !"".equals(field)) {
            if (!",".equals(field)) {
                fields.add(field);
            }
            t.pop();
            field = t.peek();
        }
        select.fields = fields.toArray(new String[0]);

        if (!"from".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        select.tableName = t.peek();
        t.pop();

        String next = t.peek();
        if ("".equals(next)) {
            select.where = null;
            return select;
        }
        if (!"where".equals(next)) {
            throw Error.InvalidCommandException;
        }
        t.pop();
        select.where = parseWhere(t);
        return select;
    }

    /**
     * 解析 INSERT 语句
     * 格式: insert into <tableName> values <value1> <value2> ...
     */
    private static statement.Insert parseInsert(Tokenizer t) throws Exception {
        statement.Insert insert = new statement.Insert();

        if (!"into".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        insert.tableName = t.peek();
        t.pop();

        if (!"values".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        java.util.List<String> values = new java.util.ArrayList<>();
        while (!"".equals(t.peek())) {
            values.add(t.peek());
            t.pop();
        }
        insert.values = values.toArray(new String[0]);
        return insert;
    }

    /**
     * 解析 DELETE 语句
     * 格式: delete from <tableName> where <condition>
     */
    private static statement.Delete parseDelete(Tokenizer t) throws Exception {
        statement.Delete delete = new statement.Delete();

        if (!"from".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        delete.tableName = t.peek();
        t.pop();

        if (!"where".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        delete.where = parseWhere(t);
        return delete;
    }

    /**
     * 解析 UPDATE 语句
     * 格式: update <tableName> set <fieldName> = <value> where <condition>
     */
    private static statement.Update parseUpdate(Tokenizer t) throws Exception {
        statement.Update update = new statement.Update();

        update.tableName = t.peek();
        t.pop();

        if (!"set".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        update.fieldName = t.peek();
        t.pop();

        if (!"=".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        update.value = t.peek();
        t.pop();

        if (!"where".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        t.pop();

        update.where = parseWhere(t);
        return update;
    }

    /**
     * 解析 WHERE 子句
     * 格式: <field> <op> <value> [and|or <field> <op> <value>]
     */
    private static statement.Where parseWhere(Tokenizer t) throws Exception {
        statement.Where where = new statement.Where();

        where.singleExp1 = parseSingleExp(t);

        String logicOp = t.peek();
        if ("".equals(logicOp)) {
            where.logicOp = null;
            where.singleExp2 = null;
            return where;
        }
        if (!"and".equals(logicOp) && !"or".equals(logicOp)) {
            throw Error.InvalidCommandException;
        }
        where.logicOp = logicOp;
        t.pop();

        where.singleExp2 = parseSingleExp(t);

        if (!"".equals(t.peek())) {
            throw Error.InvalidCommandException;
        }
        return where;
    }

    /**
     * 解析单个表达式
     * 格式: <field> <op> <value>
     */
    private static statement.SingleExpression parseSingleExp(Tokenizer t) throws Exception {
        statement.SingleExpression exp = new statement.SingleExpression();
        exp.field = t.peek();
        t.pop();
        exp.compareOp = t.peek();
        t.pop();
        exp.value = t.peek();
        t.pop();
        return exp;
    }
}