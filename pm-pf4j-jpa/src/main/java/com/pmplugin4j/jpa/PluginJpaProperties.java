package com.pmplugin4j.jpa;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pm.pf4j.jpa")
public class PluginJpaProperties {

    private String ddlAuto = "none";
    private boolean showSql;
    private boolean formatSql = true;
    private String databasePlatform;
    private boolean generateStatistics;
    private final Map<String, Object> extraProperties = new LinkedHashMap<>();

    public String getDdlAuto() {
        return ddlAuto;
    }

    public Map<String, Object> toJpaPropertyMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hibernate.hbm2ddl.auto", ddlAuto);
        result.put("hibernate.show_sql", showSql);
        result.put("hibernate.format_sql", formatSql);
        result.put("hibernate.generate_statistics", generateStatistics);
        result.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        if (databasePlatform != null && !databasePlatform.isBlank()) {
            result.put("hibernate.dialect", databasePlatform);
        }
        result.putAll(extraProperties);
        return result;
    }

    public void setDdlAuto(String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    public boolean isShowSql() {
        return showSql;
    }

    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }

    public boolean isFormatSql() {
        return formatSql;
    }

    public void setFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
    }

    public String getDatabasePlatform() {
        return databasePlatform;
    }

    public void setDatabasePlatform(String databasePlatform) {
        this.databasePlatform = databasePlatform;
    }

    public boolean isGenerateStatistics() {
        return generateStatistics;
    }

    public void setGenerateStatistics(boolean generateStatistics) {
        this.generateStatistics = generateStatistics;
    }

    public Map<String, Object> getExtraProperties() {
        return extraProperties;
    }
}
