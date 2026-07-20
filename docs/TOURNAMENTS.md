# Турниры, NPC и голограммы

## Жизненный цикл

1. Организатор создаёт турнир и выбирает режим кита.
2. До старта игроки регистрируются через `/tour join`, `/tour menu` или NPC.
3. `/tour start` формирует олимпийскую сетку минимум из двух участников.
4. `/tour nextmatch` запускает готовые пары по очереди.
5. Победитель проходит в следующий раунд, проигравший отмечается выбывшим.
6. После финала турнир получает статус завершённого, а GUI и голограммы
   показывают победителя и результаты вместо следующего матча.

Игрок не может одновременно состоять в двух незавершённых турнирах. Для матча
используются итоговый выбранный кит и одна из разрешённых арен.

## Создание через меню

Основной рабочий интерфейс организатора: `/tour menu`. Из него доступны создание,
выбор турнира, участники, киты, арены, запуск матчей, NPC и голограммы.
Консольные команды сохранены для диагностики и точного управления.

Пример через команды:

```text
/tour create CrystalCup choice
/tour kits CrystalCup toggle crystal
/tour kits CrystalCup toggle anchor
/tour arenas CrystalCup toggle crystal_arena
/tour setlobby
```

`fixed` использует фиксированный кит. `choice` открывает игрокам выбор из
разрешённых китов; матч запускается только с итоговым китом системы.

## Привязка произвольного NPC

Установите FancyNpcs и создайте NPC с любым удобным именем. Плагин не требует
префикса `tour_`:

```text
/npc create CrystalMaster --type PLAYER
/tour npc bind CrystalCup CrystalMaster
/tour npc list
```

Можно передать имя или ID FancyNPC. Один NPC привязывается к одному турниру,
а разные турниры могут иметь разных NPC:

```text
/tour npc bind CrystalCup CrystalMaster
/tour npc bind SwordCup SwordMaster
/tour npc unbind CrystalMaster
```

При клике открывается турнирное меню со статусом, регистрацией, отменой участия,
правилами и наблюдением. Для завершённого турнира показываются результаты; для
выбывшего игрока отображается состояние выбытия.

Привязки сохраняются в `config.yml` по пути
`tournament.fancy-npcs.npc-tournaments`.

## Отключение и возврат игрока

По умолчанию `tournament.disconnect-loss: false`. Если участник выходит во время
матча, пара переходит в ожидание на `tournament.auto-forfeit-minutes` минут
(стандартно 2). GUI и `/tour mymatch` показывают прошедшее и оставшееся время.

- если игрок вернулся до истечения срока, плагин сразу возвращает его в матч;
- если онлайн только один участник, после таймера победа засчитывается ему;
- если отсутствуют оба участника, оба выбывают;
- при `disconnect-loss: true` выход из активного боя означает немедленное поражение.

## CMI-голограммы

Требуется CMI и `cmi-holograms.enabled: true`.

```text
/tour holo create CrystalCup
/tour holo update CrystalCup
/tour holo page CrystalCup 1
/tour holo round CrystalCup 2
/tour holo direction CrystalCup auto
/tour holo remove CrystalCup
```

Шаблоны, интервалы и направления находятся в разделах `cmi-holograms`,
`hologram` и `hologram-template` файла `config.yml`. Состояние сетки хранится в
`tournaments.yml` и восстанавливается после запуска плагина.
