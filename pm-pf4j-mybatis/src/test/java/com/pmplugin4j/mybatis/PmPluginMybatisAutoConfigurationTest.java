package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.pmplugin4j.mybatis.db.AuditRecordMapper;
import com.pmplugin4j.mybatis.model.AuditRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PmPluginMybatisAutoConfigurationTest {

    @Test
    void fillsPluginAuditFieldsUsingHostHandler() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 9, 10, 0);
        LocalDateTime updated = created.plusHours(1);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:plugin-audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table audit_record (id bigint primary key, create_time timestamp not null, "
                + "update_time timestamp)");
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext()) {
            host.getBeanFactory().registerSingleton("dataSource", dataSource);
            host.getBeanFactory().registerSingleton("auditHandler", new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject metaObject) {
                    strictInsertFill(metaObject, "createTime", LocalDateTime.class, created);
                }

                @Override
                public void updateFill(MetaObject metaObject) {
                    strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, updated);
                }
            });
            host.register(PmPluginMybatisAutoConfiguration.class);
            host.refresh();
            plugin.setParent(host);
            plugin.setId("audit-plugin");
            PluginMybatisSqlSessionManager manager = host.getBean(PluginMybatisSqlSessionManager.class);
            manager.initializeMyBatisForPlugin(plugin.getId(), "com.pmplugin4j.mybatis", plugin);
            plugin.refresh();

            AuditRecordMapper mapper = plugin.getBean(AuditRecordMapper.class);
            AuditRecord record = new AuditRecord();
            record.setId(1L);
            assertEquals(1, mapper.insert(record));
            assertEquals(created, mapper.selectById(1L).getCreateTime());
            assertEquals(1, mapper.updateById(record));
            assertEquals(updated, mapper.selectById(1L).getUpdateTime());
            assertEquals(created,
                    jdbc.queryForObject("select create_time from audit_record where id = 1", LocalDateTime.class));
            manager.cleanupPluginResources(plugin.getId());
            assertEquals(0, manager.trackedPluginCount());
        }
    }

    @Test
    void exposesMybatisLifecycleRegistrarWhenModuleIsPresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PmPluginMybatisAutoConfiguration.class)) {
            assertNotNull(context.getBean(PluginMybatisRegistrar.class));
        }
    }

    @Test
    void createsSessionManagerWhenHostProvidesDataSource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("dataSource", new DriverManagerDataSource("jdbc:test"));
            context.register(PmPluginMybatisAutoConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(PluginMybatisSqlSessionManager.class));
        }
    }
}
