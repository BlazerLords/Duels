# Команды и права

Встроенная справка является основным актуальным источником:

```text
/duels help
/tour help
/tour help player
/tour help admin
/tour help match
/tour help npc
/tour help holo
/tour help spec
```

## Команды игроков

| Команда | Назначение |
| --- | --- |
| `/duel` или `/1v1` | меню и вызов на дуэль |
| `/queue` | очередь дуэлей |
| `/party` | управление группой |
| `/spectate` | наблюдение за обычной дуэлью |
| `/kit` | доступные действия с китами |
| `/tour menu` | меню турниров |
| `/tour join <турнир>` | регистрация до старта |
| `/tour leave <турнир>` | отмена регистрации до старта |
| `/tour mymatch` | текущая пара, ожидание или личный результат |
| `/tour info <турнир>` | статус, участники и сетка |
| `/tour spec <турнир>` | наблюдение за активным матчем |

Основные права игрока: `duels.duel`, `duels.queue`, `duels.spectate`,
`tournament.join`, `tournament.spectate`. Стандартный набор объединён в
`duels.default`.

## Настройка китов и арен

| Команда | Назначение |
| --- | --- |
| `/duels savekit <kit>` | сохранить инвентарь как кит |
| `/duels loadkit <kit>` | загрузить кит в инвентарь |
| `/duels deletekit <kit>` | удалить кит |
| `/duels setitem <kit>` | установить иконку кита предметом в руке |
| `/duels options <kit>` | открыть настройки кита |
| `/duels create <arena>` | создать арену |
| `/duels delete <arena>` | удалить арену |
| `/duels set <arena> <1|2>` | установить точки появления |
| `/duels setarenaitem <arena>` | установить иконку арены предметом в руке |
| `/duels bind <kit>` | открыть привязку кита к аренам |
| `/duels enable <arena>` | включить арену |
| `/duels disable <arena>` | выключить арену |
| `/duels toggle <arena>` | переключить доступность |
| `/duels teleport <arena>` | телепортироваться на арену |
| `/duels info <arena>` | показать сведения об арене |
| `/duels setbounds <arena> <min|max>` | установить границы разрушения |

Названия с пробелами в административных командах передавайте через дефисы,
если конкретная команда не принимает остаток строки напрямую.

## Диагностика разрушаемых арен

| Команда | Назначение |
| --- | --- |
| `/duels kitdebug <kit>` | все настройки разрушения кита |
| `/duels arena debug <arena>` | bounds, матч, кит, состояние и счётчики |
| `/duels arena restorestatus <arena>` | ход восстановления |
| `/duels arena restore <arena>` | принудительно начать восстановление |

Эти команды требуют административного доступа Duels.

## Управление турниром

| Команда | Назначение |
| --- | --- |
| `/tour create <name> <fixed|choice>` | создать турнир без заданного кита |
| `/tour create <name> <kit> [fixed|choice]` | создать с китом |
| `/tour kitmode <name> <fixed|choice>` | режим выбора кита |
| `/tour kits <name> list|toggle|clear [kit]` | разрешённые киты |
| `/tour arenas <name> list|toggle|clear [arena]` | разрешённые арены |
| `/tour add <name> <player>` | добавить участника |
| `/tour remove <name> <player>` | убрать участника до старта |
| `/tour start <name>` | сформировать сетку |
| `/tour nextmatch <name>` | запустить следующую готовую пару |
| `/tour match start <name> <round> <match>` | запустить конкретную пару |
| `/tour win <name> <player>` | вручную назначить победителя |
| `/tour replay <name> <round> <match>` | вернуть матч на переигровку |
| `/tour cancel <name>` | отменить без удаления |
| `/tour reset <name>` | вернуть регистрацию, сохранив участников |
| `/tour finish <name>` | принудительно завершить |
| `/tour delete <name>` | удалить турнир |
| `/tour setlobby` | установить турнирное лобби |

Административный корневой permission: `tournament.admin`. Детальные права
объявлены в `duels-plugin/src/main/resources/plugin.yml`.
