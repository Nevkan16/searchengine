package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.Table;
import javax.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityTableUtil {

    private final EntityManager entityManager;

    public List<String> getEntityTableNames() {
        List<String> tableNames = new ArrayList<>();
        // Получаем метамодель всех сущностей
        for (EntityType<?> entityType : entityManager.getMetamodel().getEntities()) {
            // Имя таблицы из аннотации @Table
            String tableName = entityType.getName();
            Table tableAnnotation = entityType.getJavaType().getAnnotation(Table.class);
            if (tableAnnotation != null) {
                tableName = tableAnnotation.name();
            }
            tableNames.add(tableName);
        }
        if (tableNames.isEmpty()) {
            log.warn("Таблицы не найдены");
            return Collections.emptyList();
        }
        return tableNames;
    }

    @Transactional
    public void resetAutoIncrement(String tableName) {
        try {
            entityManager.createNativeQuery("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1").executeUpdate();
            log.info("Автоинкремент сброшен для таблицы: " + tableName);
        } catch (Exception e) {
            log.error("Ошибка при сбросе автоинкремента для таблицы " + tableName + ": " + e.getMessage());
        }
    }
    @Transactional
    public void resetAutoIncrementForAllTables() {
        List<String> tableNames = getEntityTableNames();

        if (tableNames.isEmpty()) {
            log.warn("Нет таблиц для сброса автоинкремента");
            return;
        }

        log.info("Сброс автоинкремента для всех таблиц...");
        tableNames.forEach(this::resetAutoIncrement);
        log.info("Сброс автоинкремента завершён.");
    }


    public void print() {
        List<String> printedTableName = getEntityTableNames();
        for (String table : printedTableName) {
            System.out.println(table);
        }
    }
}
