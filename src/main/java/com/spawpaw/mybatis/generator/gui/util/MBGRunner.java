package com.spawpaw.mybatis.generator.gui.util;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.spawpaw.mybatis.generator.gui.DatabaseConfig;
import com.spawpaw.mybatis.generator.gui.ProjectConfig;
import com.spawpaw.mybatis.generator.gui.annotations.EnablePlugin;
import com.spawpaw.mybatis.generator.gui.annotations.ExportToPlugin;
import com.spawpaw.mybatis.generator.gui.entity.TableColumnMetaData;
import com.spawpaw.mybatis.generator.gui.enums.DatabaseType;
import com.spawpaw.mybatis.generator.gui.enums.DeclaredPlugins;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.GeneratedXmlFile;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.config.*;
import org.mybatis.generator.exception.InvalidConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created By spawpaw@hotmail.com 2018.1.20
 * Description:
 *
 * @author BenBenShang spawpaw@hotmail.com
 */
public class MBGRunner {
    Logger log = LoggerFactory.getLogger(MBGRunner.class);
    private ProjectConfig projectConfig;
    private DatabaseConfig databaseConfig;

    private Set<String> enabledPlugins = new HashSet<>();
    private HashMap<String, HashMap<String, String>> pluginConfigs = new HashMap<>();
    private HashMap<String, String> oldFileStrMap = new HashMap<>();
    private HashMap<String, List<String>> oldMethodMap = new HashMap<>();
    private Configuration config;
    private Context context;
    private final String BUILT_IN_IDS = "BaseResultMap,ResultMapWithBLOBs,Example_Where_Clause,Update_By_Example_Where_Clause,Base_Column_List,Blob_Column_List,selectByExampleWithBLOBs,selectByExample,selectByPrimaryKey,deleteByPrimaryKey,deleteByExample,insert,insertSelective,countByExample,updateByExampleSelective,updateByExampleWithBLOBs,updateByExample,updateByPrimaryKeySelective,updateByPrimaryKeyWithBLOBs,updateByPrimaryKey,batchInsert,batchInsertSelective";
    private final Predicate<SimpleStringProperty> notEmptyStringPropertyPredicate = simpleStringProperty -> !simpleStringProperty.getValue().isEmpty();

    public MBGRunner(ProjectConfig projectConfig, DatabaseConfig databaseConfig) {
        this.projectConfig = projectConfig;
        this.databaseConfig = databaseConfig;
    }


    public String generate() throws SQLException {
        config = new Configuration();
        //default model type
        if (projectConfig.defaultModelType.getValue().equalsIgnoreCase("CONDITIONAL"))
            context = new Context(ModelType.CONDITIONAL);
        else if (projectConfig.defaultModelType.getValue().equalsIgnoreCase("FLAT"))
            context = new Context(ModelType.FLAT);
        else context = new Context(ModelType.HIERARCHICAL);

        context.setId("mybatis generator gui extension");//id
        context.setTargetRuntime("MyBatis3");//targetRuntime
        context.addProperty("javaFileEncoding", projectConfig.javaFileEncoding.getValue());
        for (Map.Entry<String, String> tableDDL : databaseConfig.tableDDLs.entrySet()) {
            context.addProperty("ddls." + tableDDL.getKey(), tableDDL.getValue());
            log.info("add config [{}:{}] to context", "ddls." + tableDDL.getKey(), tableDDL.getValue());
        }

        //=====================================================================================================加载插件
        //initialize plugin data
        initPluginConfigs();
        addPlugins();

        //====================================================================================================注释生成器
        if (projectConfig.enableComment.getValue()) {
            CommentGeneratorConfiguration commentGeneratorConfiguration = new CommentGeneratorConfiguration();
            commentGeneratorConfiguration.setConfigurationType(DeclaredPlugins.CommentPlugin);
            if (pluginConfigs.containsKey(DeclaredPlugins.CommentPlugin)) {
                HashMap<String, String> pluginProperties = pluginConfigs.get(DeclaredPlugins.CommentPlugin);
                for (String key : pluginProperties.keySet())
                    commentGeneratorConfiguration.addProperty(key, pluginProperties.get(key));
            }
            context.setCommentGeneratorConfiguration(commentGeneratorConfiguration);
        } else {
            CommentGeneratorConfiguration commentGeneratorConfiguration = new CommentGeneratorConfiguration();
            commentGeneratorConfiguration.addProperty("suppressDate", "true");
            commentGeneratorConfiguration.addProperty("suppressAllComments", "true");
            context.setCommentGeneratorConfiguration(commentGeneratorConfiguration);
        }

        //==============================================================================================jdbc connection
        JDBCConnectionConfiguration jdbcConnectionConfiguration = new JDBCConnectionConfiguration();
        jdbcConnectionConfiguration.setDriverClass(databaseConfig.driver());
        jdbcConnectionConfiguration.setConnectionURL(databaseConfig.connectionUrl());
        jdbcConnectionConfiguration.setUserId(databaseConfig.userName.getValue());
        jdbcConnectionConfiguration.setPassword(databaseConfig.password.getValue());
        //手动添加获取表注释的参数，这里仅找到了这几个数据库的获取方式，如有有获取其他数据库表注释的方法请提issue
        switch (DatabaseType.valueOf(databaseConfig.databaseType.get())) {
            case MySQL:
                jdbcConnectionConfiguration.addProperty("useInformationSchema", "true");//获取Mysql的表注释
                break;
            case Oracle:
            case Oracle_SID:
            case Oracle_ServiceName:
            case Oracle_TNSEntryString:
            case Oracle_TNSName:
                jdbcConnectionConfiguration.addProperty("remarksReporting", "true");//获取Oracle的表注释
                break;
            default:
                jdbcConnectionConfiguration.addProperty("remarksReporting", "true");
                jdbcConnectionConfiguration.addProperty("useInformationSchema", "true");
                break;
        }
        context.setJdbcConnectionConfiguration(jdbcConnectionConfiguration);

        //=============================================================================================javaTypeResolver
        JavaTypeResolverConfiguration javaTypeResolverConfiguration = new JavaTypeResolverConfiguration();
        javaTypeResolverConfiguration.addProperty("forceBigDecimals", "false");
        context.setJavaTypeResolverConfiguration(javaTypeResolverConfiguration);

        //========================================================================================================model
        JavaModelGeneratorConfiguration javaModelGeneratorConfiguration = new JavaModelGeneratorConfiguration();
        javaModelGeneratorConfiguration.setTargetPackage(projectConfig.entityPackage.getValue().replace(" ", ""));
        javaModelGeneratorConfiguration.setTargetProject(projectDir() + projectConfig.entityDir.getValue());
        javaModelGeneratorConfiguration.addProperty("enableSubPackages", "true");
        javaModelGeneratorConfiguration.addProperty("useActualColumnNames", projectConfig.useActualColumnNames.getValue().toString());
        javaModelGeneratorConfiguration.addProperty("trimStrings", projectConfig.trimStrings.getValue().toString());
        javaModelGeneratorConfiguration.addProperty("shardingTable", projectConfig.shardingTable.getValue().toString());
        if (!projectConfig.entityRootClass.getValue().isEmpty())
            javaModelGeneratorConfiguration.addProperty("rootClass", projectConfig.entityRootClass.getValue());
        context.setJavaModelGeneratorConfiguration(javaModelGeneratorConfiguration);

        //=======================================================================================================mapper
        SqlMapGeneratorConfiguration sqlMapGeneratorConfiguration = new SqlMapGeneratorConfiguration();
        sqlMapGeneratorConfiguration.setTargetProject(projectDir() + projectConfig.mapperDir.getValue());
        sqlMapGeneratorConfiguration.setTargetPackage(projectConfig.mapperPackage.getValue());
        sqlMapGeneratorConfiguration.addProperty("useActualColumnNames", projectConfig.useActualColumnNames.getValue().toString());
        sqlMapGeneratorConfiguration.addProperty("enableSubPackages", "true");
        context.setSqlMapGeneratorConfiguration(sqlMapGeneratorConfiguration);

        //==========================================================================================================dao
        JavaClientGeneratorConfiguration javaClientGeneratorConfiguration = new JavaClientGeneratorConfiguration();
        javaClientGeneratorConfiguration.setConfigurationType(projectConfig.javaClientMapperType.getValue());
        javaClientGeneratorConfiguration.setTargetProject(projectDir() + projectConfig.daoDir.getValue());
        javaClientGeneratorConfiguration.setTargetPackage(projectConfig.daoPackage.getValue());
        sqlMapGeneratorConfiguration.addProperty("useActualColumnNames", projectConfig.useActualColumnNames.getValue().toString());
        sqlMapGeneratorConfiguration.addProperty("enableSubPackages", "true");
        context.setJavaClientGeneratorConfiguration(javaClientGeneratorConfiguration);

        //========================================================================================================table
        TableConfiguration tableConfiguration = new TableConfiguration(context);
        tableConfiguration.setCatalog(databaseConfig.catalog());
        tableConfiguration.setSchema(databaseConfig.schema());
        String tableName = projectConfig.selectedTable.getValue();
        Boolean isShardingTable = BooleanUtils.isTrue(projectConfig.shardingTable.getValue());
        log.info("shardingTable:{}", isShardingTable);
        String logicTableName = null;
        if (isShardingTable) {
            logicTableName = tableName.replaceAll("_\\d+$", "");
        }
        tableConfiguration.setTableName(tableName);
        tableConfiguration.setDomainObjectName(projectConfig.entityObjName.getValue().replace(" ", ""));
        tableConfiguration.setMapperName(projectConfig.daoObjName.getValue().replace(" ", ""));

        tableConfiguration.setInsertStatementEnabled(projectConfig.enableInsert.getValue());
        tableConfiguration.setSelectByPrimaryKeyStatementEnabled(projectConfig.enableSelectByPrimaryKey.getValue());
        tableConfiguration.setSelectByExampleStatementEnabled(projectConfig.enableSelectByExample.getValue());
        if (projectConfig.selectByPrimaryKeyQueryId.getValue().isEmpty())
            tableConfiguration.setSelectByPrimaryKeyQueryId(projectConfig.selectByPrimaryKeyQueryId.getValue());
        if (!projectConfig.selectByExampleQueryId.getValue().isEmpty())
            tableConfiguration.setSelectByExampleQueryId(projectConfig.selectByExampleQueryId.getValue());
        tableConfiguration.setUpdateByPrimaryKeyStatementEnabled(projectConfig.enableUpdateByPrimaryKey.getValue());
        tableConfiguration.setUpdateByExampleStatementEnabled(projectConfig.enableUpdateByExample.getValue());
        tableConfiguration.setDeleteByPrimaryKeyStatementEnabled(projectConfig.enableDeleteByPrimaryKey.getValue());
        tableConfiguration.setDeleteByExampleStatementEnabled(projectConfig.enableDeleteByExample.getValue());
        tableConfiguration.setCountByExampleStatementEnabled(projectConfig.enableCountByExample.getValue());
        tableConfiguration.addProperty("useActualColumnNames", projectConfig.useActualColumnNames.getValue().toString());//使用小骆驼峰替代原列名
        tableConfiguration.addProperty("ignoreQualifiersAtRuntime", "true");//使用小骆驼峰替代原列名
        Optional.of(projectConfig.tableAlias).filter(notEmptyStringPropertyPredicate)
                .ifPresent((tableAlias) -> {
                    tableConfiguration.setAlias(tableAlias.getValue());
                });
        if (!projectConfig.enableVirtualPrimaryKeyPlugin.getValue().isEmpty())
            tableConfiguration.addProperty("virtualKeyColumns", projectConfig.enableVirtualPrimaryKeyPlugin.getValue());

        //see http://www.mybatis.org/generator/configreference/generatedKey.html  ,JDBC is a database independent method of obtaining the value from identity columns,only for Mybatis3+
        if (!projectConfig.primaryKey.getValue().isEmpty()) {
            String sqlStatement = DatabaseType.valueOf(databaseConfig.databaseType.getValue()).getSqlStatement();
            if (!projectConfig.lastInsertIdSqlStatement.getValue().trim().isEmpty())//如果指定了获取自增主键的sql，则覆盖默认的配置
                sqlStatement = projectConfig.lastInsertIdSqlStatement.getValue();
            tableConfiguration.setGeneratedKey(new GeneratedKey(projectConfig.primaryKey.getValue(), sqlStatement, true, null));
        }

        //添加忽略列/列覆写
        for (TableColumnMetaData column : databaseConfig.tableConfigs.get(projectConfig.selectedTable.getValue())) {
            if (!column.getChecked()) {
                log.info("忽略列：{}", column.getColumnName());
                tableConfiguration.addIgnoredColumn(new IgnoredColumn(column.getColumnName()));
            } else {
                ColumnOverride columnOverride = new ColumnOverride(column.getColumnName());
                columnOverride.setJavaProperty(column.getPropertyName());
                columnOverride.setJavaType(column.getJavaType());
//                columnOverride.setJdbcType(column.getJdbcType());
                columnOverride.setTypeHandler(column.getTypeHandler());
                tableConfiguration.addColumnOverride(columnOverride);
            }
        }

        context.addTableConfiguration(tableConfiguration);


        if (!projectConfig.autoDelimitKeywords.getValue().trim().isEmpty()) {
            context.addProperty("autoDelimitKeywords", "true");
            context.addProperty("beginningDelimiter", projectConfig.autoDelimitKeywords.getValue());
            context.addProperty("endingDelimiter", projectConfig.autoDelimitKeywords.getValue());
            tableConfiguration.setDelimitIdentifiers(true);
//            tableConfiguration.setAllColumnDelimitingEnabled(true);//将此行取消注释即可delimit所有字段
        }

        config.addContext(context);
        List<String> warnings = new ArrayList<>();
        MyShellCallback callback = new MyShellCallback(projectConfig.overwrite.getValue());
        try {
            MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
            myBatisGenerator.generate(new ProgressCallback() {
                @Override
                public void introspectionStarted(int i) {

                }

                @Override
                public void generationStarted(int i) {

                }

                @Override
                public void saveStarted(int j) {
                    List<GeneratedXmlFile> generatedXmlFiles = myBatisGenerator.getGeneratedXmlFiles();
                    generatedXmlFiles.forEach(f -> {
                        String name = sqlMapGeneratorConfiguration.getTargetProject() + "/" + projectConfig.mapperPackage.getValue() + "/" + f.getFileName();
                        try {
                            File file = new File(name);
                            String fileAsStr = FileUtil.readFileAsStr(file);
                            if (!file.exists()) {
                                return;
                            }
                            oldFileStrMap.put(name, fileAsStr);
                            oldMethodMap.put(name, new ArrayList<>());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }

                @Override
                public void startTask(String s) {

                }

                @Override
                public void done() {

                }

                @Override
                public void checkCancel() throws InterruptedException {

                }
            });
            // 逻辑表替换
            String finalLogicTableName = logicTableName;
            List<GeneratedJavaFile> generatedJavaFiles = myBatisGenerator.getGeneratedJavaFiles();
            generatedJavaFiles.forEach(f -> {
                String name = f.getTargetProject() + "/" + f.getTargetPackage().replaceAll("\\.", "/") + "/" + f.getFileName();
                String formattedContent = f.getFormattedContent();
                if (isShardingTable) {
                    formattedContent = formattedContent.replaceAll(tableName, finalLogicTableName);
                }
                try {
                    FileUtil.writeStringToFile(name, formattedContent);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });


            List<GeneratedXmlFile> generatedXmlFiles = myBatisGenerator.getGeneratedXmlFiles();
            generatedXmlFiles.forEach(f -> {
                String name = sqlMapGeneratorConfiguration.getTargetProject() + "/" + projectConfig.mapperPackage.getValue() + "/" + f.getFileName();
                if (oldFileStrMap.containsKey(name)) {
                    String fileAsStr = oldFileStrMap.get(name);
                    String[] split = fileAsStr.split("\n");
                    split[1] = "";
                    fileAsStr = Joiner.on("\n").join(split);
                    log.info("callback save started:{},{}", name, fileAsStr);
                    SAXReader reader = new SAXReader();
                    reader.setValidation(false);
                    // 解析XML文件，生成一个Document对象
                    try {
                        Document document = DocumentHelper.parseText(fileAsStr);
                        // 获取根元素
                        Element root = document.getRootElement();
                        // 遍历根元素的子元素
                        for (Object child : root.elements()) {
                            Element cast = (Element) child;
                            // 在这里可以对子元素进行操作
                            if (!BUILT_IN_IDS.contains(cast.attributeValue("id"))) {
                                System.out.println("OLD METHOD ID：" + cast.attributeValue("id"));
                                oldMethodMap.get(name).add(cast.asXML());
                            }
                        }
                    } catch (DocumentException e) {
                        throw new RuntimeException(e);
                    }
                }
                String formattedContent = f.getFormattedContent();
                Set<Map.Entry<String, String>> entries = Constants.MAPPER_TIMES_REPLACEMENT.entrySet();
                for (Map.Entry<String, String> next : entries) {
                    formattedContent = formattedContent.replace(next.getKey(), next.getValue());
                }
                if (CollectionUtils.isNotEmpty(oldMethodMap.get(name))) {
                    formattedContent = formattedContent.replace(
                            "</mapper>",
                            Joiner.on("\n").join(oldMethodMap.get(name)) + "\n</mapper>"
                    );
                }
                if (isShardingTable) {
                    formattedContent = formattedContent.replaceAll(tableName, finalLogicTableName);
                }
                try {
                    FileUtil.writeStringToFile(name, formattedContent);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (InvalidConfigurationException | InterruptedException | SQLException | IOException e) {
            e.printStackTrace();
        }
        StringBuilder w = new StringBuilder();
        for (String warning : warnings) {
            w.append(warning).append("\n");
        }
        return w.toString();
    }

    /**
     * 获取项目的绝对路径前缀
     */

    private String projectDir() {
        return projectConfig.projectDir.getValue().isEmpty() ? "" : projectConfig.projectDir.getValue() + "/";
    }

    /**
     * 初始化与Plugin相关的配置
     */
    private void initPluginConfigs() {
        try {
            for (Field field : ProjectConfig.class.getFields()) {
                String valueOfField;
                //获取配置项的值
                if (field.get(projectConfig) instanceof Property)
                    valueOfField = ((Property) field.get(projectConfig)).getValue().toString();
                else {
                    log.info("initPluginConfigs:不支持的配置:{},类型不是Property", field.getName());
                    continue;
                }
                //如果该配置项设置了启动某个Plugin的Trigger，将启用指定的plugin
                for (EnablePlugin enablePlugin : field.getAnnotationsByType(EnablePlugin.class)) {
                    if (!valueOfField.isEmpty() && !valueOfField.equals("false")) {
                        enabledPlugins.add(enablePlugin.value());
                    }
                }
                //将配置项的值加入到plugin的properties中,如果没有指定key，则使用变量名称
                for (ExportToPlugin exportToPlugin : field.getAnnotationsByType(ExportToPlugin.class)) {
                    log.info("配置:{},值：{},,plugin:{}   key:{}", field.getName(), valueOfField, exportToPlugin.plugin(), exportToPlugin.key());
                    pluginConfigs.putIfAbsent(exportToPlugin.plugin(), new HashMap<>());
                    pluginConfigs.get(exportToPlugin.plugin()).put(exportToPlugin.key().isEmpty() ? field.getName() : exportToPlugin.key(), valueOfField);
                }
            }
            //如果方法上包含ExportToPlugin注释，则使用返回值的toString方法
            for (Method method : ProjectConfig.class.getMethods()) {
                for (ExportToPlugin exportToPlugin : method.getAnnotationsByType(ExportToPlugin.class)) {
                    pluginConfigs.putIfAbsent(exportToPlugin.plugin(), new HashMap<>());
                    pluginConfigs.get(exportToPlugin.plugin()).put(exportToPlugin.key().isEmpty() ? method.getName() : exportToPlugin.key(), method.invoke(projectConfig).toString());
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void addPlugins() {
        for (String enabledPluginType : enabledPlugins) {
            log.info("启用插件：{}", enabledPluginType);
            HashMap<String, String> pluginProperties = pluginConfigs.get(enabledPluginType);
            PluginConfiguration pluginConfiguration = new PluginConfiguration();
            pluginConfiguration.setConfigurationType(enabledPluginType);
            if (pluginProperties != null)
                for (String key : pluginProperties.keySet()) {
                    pluginConfiguration.addProperty(key, pluginProperties.get(key));
                }
            context.addPluginConfiguration(pluginConfiguration);
        }
    }
}
