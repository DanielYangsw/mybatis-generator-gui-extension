package com.spawpaw.mybatis.generator.gui.util;

import com.spawpaw.mybatis.generator.gui.controls.AbstractControl;
import javafx.geometry.Insets;

import java.util.*;

/**
 * Created By spawpaw@hotmail.com 2018.1.20
 * Description:
 * 常量池
 *
 * @author BenBenShang spawpaw@hotmail.com
 */
public class Constants {
    //保存配置的目录
    public static final String CONFIG_SAVE_PATH = "~/data/config/";
    public static final String CONNECTION_SAVE_PATH = "~/data/connection/";
    private static Locale SPECIFIED_LOCALE = Locale.getDefault();
    //缓存Res
    private static Map<Locale, ResourceBundle> resourceBundles = new HashMap<>();

    //当前系统的语言，为国际化准备
    public static void setLocale(Locale locale) {
        SPECIFIED_LOCALE = locale;
        AbstractControl.refreshLabels();
    }

    public static String getI18nStr(String s) {
        try {
            if (!getResourcesBundle().getString(s).isEmpty())
                return getResourcesBundle().getString(s);
        } catch (Exception e) {
            return s;
        }
        return s;
    }

    public static ResourceBundle getResourcesBundle() {
        if (SPECIFIED_LOCALE.equals(Locale.CHINA)
                || SPECIFIED_LOCALE.equals(Locale.CHINESE)
                || SPECIFIED_LOCALE.equals(Locale.SIMPLIFIED_CHINESE)
                || SPECIFIED_LOCALE.equals(Locale.PRC)
                || SPECIFIED_LOCALE.equals(Locale.TRADITIONAL_CHINESE)
                || SPECIFIED_LOCALE.equals(Locale.TAIWAN)
        ) {
            if (resourceBundles.get(Locale.CHINA) == null)
                resourceBundles.put(Locale.CHINA, ResourceBundle.getBundle("i18n.locale", Locale.CHINA));
            return resourceBundles.get(Locale.CHINA);
        } else {
            if (resourceBundles.get(Locale.ENGLISH) == null)
                resourceBundles.put(Locale.ENGLISH, ResourceBundle.getBundle("i18n.locale", Locale.ENGLISH));
            return resourceBundles.get(Locale.ENGLISH);
        }
    }

    //选项卡，当配置过多时，将分为多个选项卡显示，在这里统一管理选项卡的名称
    public static class tabs {
        public static final String SHORTCUT = "ui.tab.0.SHORTCUT";
        public static final String BASIC_SETTINGS = "ui.tab.1.BASIC_SETTINGS";
        public static final String DATA_ACCESS_OBJECT = "ui.tab.2.DATA_ACCESS_OBJECT";
        public static final String DOMAIN_OBJECT = "ui.tab.3.DOMAIN_OBJECT";
        public static final String MVC = "ui.tab.4.MVC";
        public static final String COMMENT = "ui.tab.5.COMMENT";
        public static final String CACHE = "ui.tab.6.CACHE";
    }

    //与UI有关的常量
    public static class ui {
        public static final String MAIN_WINDOW_TITLE = "GUI extension for mybatis-generator";
        public static final int MIN_TEXT_FIELD_WIDTH = 360;
        public static final Insets DEFAULT_CTL_INSETS = (new Insets(8, 0, 8, 0));
        public static final Insets DEFAULT_LAYOUT_INSETS = (new Insets(8, 8, 8, 8));
    }

    //    CURRENT_TIMESTAMP(),
//      UTC_TIMESTAMP(),
    public static final Map<String, String> MAPPER_TIMES_REPLACEMENT = new LinkedHashMap<String, String>() {
        {
            put("<if test=\"createTimeLocal != null\">\n" +
                    "        create_time_local,\n" +
                    "      </if>", "create_time_local,");
            put("<if test=\"createTimeUtc != null\">\n" +
                    "        create_time_utc,\n" +
                    "      </if>", "create_time_utc,");
            put("<if test=\"updateTimeLocal != null\">\n" +
                    "        update_time_local,\n" +
                    "      </if>", "update_time_local,");
            put("<if test=\"updateTimeUtc != null\">\n" +
                    "        update_time_utc,\n" +
                    "      </if>", "update_time_utc,");

            put("<if test=\"createTimeLocal != null\">\n" +
                    "        #{createTimeLocal,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "CURRENT_TIMESTAMP(),");
            put("<if test=\"createTimeUtc != null\">\n" +
                    "        #{createTimeUtc,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "UTC_TIMESTAMP(),");
            put("<if test=\"updateTimeLocal != null\">\n" +
                    "        #{updateTimeLocal,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "CURRENT_TIMESTAMP(),");
            put("<if test=\"updateTimeUtc != null\">\n" +
                    "        #{updateTimeUtc,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "UTC_TIMESTAMP(),");

            put("<if test=\"record.updateTimeLocal != null\">\n" +
                    "        update_time_local = #{record.updateTimeLocal,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "update_time_local = CURRENT_TIMESTAMP(),");
            put("<if test=\"record.updateTimeUtc != null\">\n" +
                    "        update_time_utc = #{record.updateTimeUtc,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "update_time_utc = UTC_TIMESTAMP(),");

            put("<if test=\"updateTimeLocal != null\">\n" +
                    "        update_time_local = #{updateTimeLocal,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "update_time_local = CURRENT_TIMESTAMP(),");
            put("<if test=\"updateTimeUtc != null\">\n" +
                    "        update_time_utc = #{updateTimeUtc,jdbcType=TIMESTAMP},\n" +
                    "      </if>", "update_time_utc = UTC_TIMESTAMP(),");
            put(
                    "<foreach collection=\"selective\" item=\"column\" separator=\",\">\n" +
                            "      ${column.escapedColumnName}\n" +
                            "    </foreach>",
                    "create_time_local, create_time_utc, update_time_local,update_time_utc,\n" +
                            "    <foreach collection=\"selective\" item=\"column\" separator=\",\">\n" +
                            "      ${column.escapedColumnName}\n" +
                            "    </foreach>"
            );
            put(
                    "<foreach collection=\"list\" item=\"item\" separator=\",\">\n" +
                            "      (\n" +
                            "      <foreach collection=\"selective\" item=\"column\" separator=\",\">",
                    "<foreach collection=\"list\" item=\"item\" separator=\",\">\n" +
                            "      (CURRENT_TIMESTAMP(),UTC_TIMESTAMP(),CURRENT_TIMESTAMP(),UTC_TIMESTAMP(),\n" +
                            "      <foreach collection=\"selective\" item=\"column\" separator=\",\">"
            );
            put(
                    "        <if test=\"'create_time_local'.toString() == column.value\">\n" +
                            "          #{item.createTimeLocal,jdbcType=TIMESTAMP}\n" +
                            "        </if>\n",
                    ""
            );
            put(
                    "        <if test=\"'create_time_utc'.toString() == column.value\">\n" +
                            "          #{item.createTimeUtc,jdbcType=TIMESTAMP}\n" +
                            "        </if>\n",
                    ""
            );
            put(
                    "        <if test=\"'update_time_local'.toString() == column.value\">\n" +
                            "          #{item.updateTimeLocal,jdbcType=TIMESTAMP}\n" +
                            "        </if>\n",
                    ""
            );
            put(
                    "        <if test=\"'update_time_utc'.toString() == column.value\">\n" +
                            "          #{item.updateTimeUtc,jdbcType=TIMESTAMP}\n" +
                            "        </if>\n",
                    ""
            );
        }
    };


}
