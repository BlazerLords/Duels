# Установка и первичная настройка

## 1. Установка

1. Установите Java 25 и Paper.
2. Поместите собранный `Duels-Optimised-7.3.jar` в каталог `plugins/`.
3. Установите нужные дополнительные плагины до первого запуска.
4. Полностью запустите сервер и проверьте отсутствие `ERROR`/`SEVERE` в консоли.

Рекомендуемые дополнения:

| Плагин | Назначение |
| --- | --- |
| Vault + экономика | денежные ставки |
| PlaceholderAPI | плейсхолдеры |
| FancyNpcs | NPC регистрации в турнирах |
| HeadDB | декоративные головы в турнирных GUI |
| CMI | турнирные голограммы |

Это soft-зависимости. Их отсутствие не должно блокировать загрузку Duels.

## 2. Сборка из исходников

```bash
./gradlew :duels-plugin:test :duels-plugin:shadowJar
```

Результат: `out/Duels-Optimised-7.3.jar`.

`libs/HeadDB-6.0.0-rc.2.jar` используется только как compile-time API и не
попадает в итоговый JAR. На сервере HeadDB устанавливается отдельно.

## 3. Базовая настройка дуэлей

Выполняйте команды от администратора в игровом мире:

```text
/duels setlobby
/duels savekit crystal
/duels setitem crystal
/duels create crystal_arena
/duels set crystal_arena 1
/duels set crystal_arena 2
/duels setarenaitem crystal_arena
/duels bind crystal
/duels enable crystal_arena
```

`/duels setitem` и `/duels setarenaitem` используют предмет в основной руке.
`/duels bind <kit>` открывает GUI привязки кита к аренам.

Для разрушаемой арены дополнительно встаньте в двух противоположных углах
кубоида и выполните:

```text
/duels setbounds crystal_arena min
/duels setbounds crystal_arena max
/duels options crystal
```

В меню включите `Разрушаемая арена` и настройте разрешённые механики.
Порядок установки `min` и `max` не важен: координаты нормализуются.

## 4. Проверка

1. Выполните `/duels info crystal_arena` и убедитесь, что обе точки появления заданы.
2. Выполните `/duels arena debug crystal_arena` и проверьте границы.
3. Проведите обычную `/1v1` на Sword-ките.
4. Проведите бой на разрушаемом ките и дождитесь восстановления.
5. Создайте тестовый турнир по [инструкции турниров](TOURNAMENTS.md).

Не используйте `/reload` Bukkit. Для обновления JAR выполняйте полный restart.
