package com.pmplugin4j.jpa;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pm.plugin.jpa")
public class PluginJpaProperties {

    private String ddlAuto = "none";
    private boolean showSql;
    private boolean formatSql = true;
    private String databasePlatform;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public Map<String, Object> asJpaProperties() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hibernate.hbm2ddl.auto", ddlAuto);
        result.put("hibernate.show_sql", showSql);
        result.put("hibernate.format_sql", formatSql);
        if (databasePlatform != null && !databasePlatform.isBlank()) {
            result.put("hibernate.dialect", databasePlatform);
        }
        result.putAll(properties);
        return result;
    }

    public void setDdlAuto(String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }

    public void setFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
    }

    public void setDatabasePlatform(String databasePlatform) {
        this.databasePlatform = databasePlatform;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
