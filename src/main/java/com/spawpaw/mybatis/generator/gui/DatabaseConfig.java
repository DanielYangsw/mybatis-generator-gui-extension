package com.spawpaw.mybatis.generator.gui;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.spawpaw.mybatis.generator.gui.annotations.Config;
import com.spawpaw.mybatis.generator.gui.annotations.ConfigType;
import com.spawpaw.mybatis.generator.gui.controls.ControlsFactory;
import com.spawpaw.mybatis.generator.gui.entity.TableColumnMetaData;
import com.spawpaw.mybatis.generator.gui.enums.DatabaseType;
import com.spawpaw.mybatis.generator.gui.util.Constants;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

/**
 * Created By spawpaw@hotmail.com  2018-01-30
 *
 * @author BenBenShang spawpaw@hotmail.com
 */
public class DatabaseConfig implements Serializable {
    transient Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    public transient Map<String, List<TableColumnMetaData>> tableConfigs;
    @Config(bundle = "database.savedName")
    public SimpleStringProperty savedName = new SimpleStringProperty("untitled");

    @Config(bundle = "database.databaseType", testRegex = "MySQL|Oracle_SID|Oracle_ServiceName|Oracle_TNSName|Oracle_TNSEntryString|PostgreSQL|SQLServer|SQLServer_InstanceBased", type = ConfigType.ChoiceBox)
    public SimpleStringProperty databaseType = new SimpleStringProperty("MySQL");
    @Config(bundle = "database.dbName")
    public SimpleStringProperty dbName = new SimpleStringProperty("");
    @Config(bundle = "database.tableNamePattern")
    public SimpleStringProperty tableNamePattern = new SimpleStringProperty("%");
    @Config(bundle = "database.host")
    public SimpleStringProperty host = new SimpleStringProperty("localhost");
    @Config(bundle = "database.port")
    public SimpleStringProperty port = new SimpleStringProperty("3306");
    @Config(bundle = "database.userName")
    public SimpleStringProperty userName = new SimpleStringProperty("root");
    @Config(bundle = "database.password")
    public SimpleStringProperty password = new SimpleStringProperty("123456");
    @Config(bundle = "database.encoding", testRegex = "utf8|latin1", type = ConfigType.ChoiceBox)
    public SimpleStringProperty encoding = new SimpleStringProperty("utf8");
    private transient TreeItem<String> rootItem;

    //table->tableDDL
    public Map<String, String> tableDDLs = new HashMap<>();

    public String driver() {
        return DatabaseType.valueOf(databaseType.getValue()).getDriverClazzName();
    }

    public String catalog() throws SQLException {
        log.info("catalog:{}", getConnection().getCatalog());
        return getConnection().getCatalog();
    }

    public String schema() throws SQLException {
        log.info("schema:{}", getConnection().getSchema());
        return getConnection().getSchema();
    }

    public String connectionUrl() {
        return DatabaseType.valueOf(databaseType.getValue()).getConnectStr(
                host.getValue(),
                port.getValue(),
                dbName.getValue(),
                encoding.getValue()
        );
    }

    private Connection getConnection() throws SQLException {
        //加载驱动
        Driver driver = DatabaseType.valueOf(databaseType.getValue()).getDriver();
        //获得数据库连接
        Properties p = new Properties();
        p.put("user", userName.getValue());
        p.put("password", password.getValue());
        p.put("useInformationSchema", "true"); //获取表注释
        log.info("using connection url:{}", connectionUrl());
        return driver.connect(connectionUrl(), p);
//        return DriverManager.getConnection(connectionUrl(), userName.getValue(), password.getValue());
    }

    public void test() throws SQLException {
        Connection connection = getConnection();
        connection.close();
    }

    public TreeItem<String> getRootTreeItem() {
        if (rootItem == null)
            rootItem = new TreeItem<>();
        rootItem.setValue(String.format(
                "%s(%s@%s:%s/%s)",
                savedName.getValue(),
                userName.getValue(),
                host.getValue(),
                port.getValue(),
                dbName.getValue()
                )
        );
        return rootItem;
    }

    /**
     * 连接数据库，初始化表信息
     */
    public void connect() throws SQLException {
        if (tableConfigs != null && tableConfigs.size() != 0) return;
        tableConfigs = new Hashtable<>();
        if (tableNamePattern.getValue().isEmpty())
            tableNamePattern.setValue("%");
        ArrayList<String> tableNamePatterns = Lists.newArrayList();
        if (tableNamePattern.getValue().contains(",")) {
            tableNamePatterns.addAll(Splitter.on(",").splitToList(tableNamePattern.getValue()));
        } else {
            tableNamePatterns.add(tableNamePattern.getValue());
        }
        tableNamePatterns.forEach(this::fetchTables);
    }

    private void fetchTables(String tableNamePattern) {
        try {
            Connection connection = getConnection();
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet rs;
            String _catalog = null;
            String _schemaPattern = null;
            String _tableNamePattern = null;
            String[] types = {"TABLE", "VIEW"};
            String sql;
            //获取表列表
            switch (DatabaseType.valueOf(databaseType.getValue())) {
                case MySQL:
                    _catalog = connection.getCatalog();
                    _schemaPattern = dbName.getValue().isEmpty() ? null : dbName.getValue();
                    _tableNamePattern = tableNamePattern;
                    rs = meta.getTables(_catalog, _schemaPattern, _tableNamePattern, types);
                    break;
                case Oracle:
                case Oracle_SID:
                case Oracle_ServiceName:
                case Oracle_TNSName:
                case Oracle_TNSEntryString:
                    _catalog = null;
                    _schemaPattern = userName.getValue().toUpperCase();
                    _tableNamePattern = tableNamePattern;
                    rs = meta.getTables(_catalog, _schemaPattern, _tableNamePattern, types);
                    break;
                case SQLServer:
                case SQLServer_InstanceBased:
                    _catalog = dbName.getValue();
                    _tableNamePattern = tableNamePattern;
                    sql = "select name as TABLE_NAME from sysobjects  where xtype='u' or xtype='v' ";
                    rs = connection.createStatement().executeQuery(sql);
                    break;
                case PostgreSQL:
                    _catalog = null;
                    _schemaPattern = "%";
                    _tableNamePattern = tableNamePattern;
                    rs = meta.getTables(_catalog, _schemaPattern, _tableNamePattern, types);
                    break;
                case DB2MF:
                case DB2:
                    _catalog = null;
                    _schemaPattern = "jence_user";
                    _tableNamePattern = tableNamePattern;
                    rs = meta.getTables(_catalog, _schemaPattern, _tableNamePattern, types);
                    break;
                case SYBASE:
                case INFORMIX:
                    _catalog = null;
                    _schemaPattern = null;
                    _tableNamePattern = tableNamePattern;
                    rs = meta.getTables(_catalog, _schemaPattern, _tableNamePattern, types);
                    break;
                default:
                    throw new RuntimeException(Constants.getI18nStr("msg.unsupportedDatabase"));
            }
            List<String> tmpList = readTables(connection, rs);
            tmpList.sort(Comparator.naturalOrder());
            //获取每个表中的字段信息
            for (String tableName : tmpList) {
                //生成表的基本信息（每个字段的名称、类型）
                rs = meta.getColumns(_catalog, _schemaPattern, tableName, null);
                while (rs.next()) {
                    TableColumnMetaData columnMetaData = new TableColumnMetaData();
                    columnMetaData.setColumnName(rs.getString("COLUMN_NAME"));
                    columnMetaData.setJdbcType(rs.getString("TYPE_NAME"));
                    tableConfigs.get(tableName).add(columnMetaData);
                }
                //生成TreeView
                TreeItem<String> item = new TreeItem<>(tableName);
                rootItem.getChildren().add(item);
                rootItem.setExpanded(true);
            }
            connection.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    private ArrayList<String> readTables(Connection connection, ResultSet rs) throws SQLException {
        ArrayList<String> tables = Lists.newArrayList();
        while (rs.next()) {
            String table = rs.getString("TABLE_NAME");
            tables.add(table);
            tableConfigs.put(table, new ArrayList<>());
            //针对MYSQL，增加获取DDL的功能
            if (DatabaseType.MySQL.equals(DatabaseType.valueOf(databaseType.getValue()))) {
                ResultSet tableNameRs = connection.createStatement().executeQuery("SHOW CREATE TABLE " + rs.getString("TABLE_NAME") + ";");
                while (tableNameRs.next()) {
                    if (existsColumn(tableNameRs, "table")) {
                        String tableName = tableNameRs.getString("table");
                        String tableDDL = tableNameRs.getString("create table");
                        tableDDLs.put(tableName, tableDDL);
                        log.info("table:{};  tableDDLs:{}", tableName, tableDDL);
                    } else if (existsColumn(tableNameRs, "view")) {
                        String viewName = tableNameRs.getString("view");
                        String viewDDL = tableNameRs.getString("create view");
                        tableDDLs.put(viewName, viewDDL);
                        log.info("viewName:{};  view DDL:{}", viewName, viewDDL);
                    } else {
                        log.error("the row is neither table nor view.");
                    }
                }
            }
        }
        return tables;
    }

    private boolean existsColumn(ResultSet rs, String columnName) {
        try {
            if (rs.findColumn(columnName) > 0) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    //关闭连接,清空ListView
    public void close() {
        if (rootItem != null)
            rootItem.getChildren().clear();
        tableConfigs = null;
    }


    //获取数据库连接的配置表单
    public VBox getLayout() {
        VBox vBox = new VBox();
        try {
            for (Field field : DatabaseConfig.class.getFields()) {
                if (field.getAnnotation(Config.class) != null && field.get(this) instanceof Property) {
                    vBox.getChildren().addAll(ControlsFactory.getLayout(field.getAnnotation(Config.class), (Property) field.get(this)));
                } else if (field.getAnnotation(Config.class) != null && !(field.get(this) instanceof Property)) {
                    log.info(Constants.getI18nStr("msg.dbConfigInvalid"));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return vBox;
    }
}
