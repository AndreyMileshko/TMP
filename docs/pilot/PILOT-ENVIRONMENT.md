# TMP PILOT — изолированная среда

Краткое руководство по пилотной среде на одной рабочей станции Windows.

## Текущая модель окружений

| | PILOT (постоянная) | Package launcher | Автотесты |
|---|---|---|---|
| База | `tmp_pilot` | Только явный `TMP_DB_URL` (без default) | Изолированные disposable БД (Testcontainers и аналоги) |
| Роль приложения | `tmp_pilot_app` (не superuser) | Задаётся вызывающим (`TMP_DB_USERNAME`) | Локальные test credentials |
| Запуск | `scripts/pilot/run-tmp-pilot.ps1` | `scripts/run-tmp-package.ps1` + обязательный `TMP_DB_URL` | Maven / Testcontainers |
| Учётные данные | `%LOCALAPPDATA%\TMP-Pilot\credentials.env` | Env vars на время запуска | Не используют `tmp_pilot` / stage |

`tmp_pilot` — **постоянная** пилотная база для разработки и acceptance.

`tmp_gui_stage5` — **legacy** имя базы. Оно остаётся в запрещённых targets пилотных скриптов. После отдельного утверждённого шага удаления живая БД считается retired; до DROP она может ещё существовать на общем контейнере, но **не** является default target.

В выводе пилотного скрипта всегда есть баннер **TMP PILOT**.

Контейнер PostgreSQL общий: `tmp-stage5-pg`, порт хоста `55432`.

## Учётные данные

Файл (вне Git, ACL только для текущего пользователя):

`%LOCALAPPDATA%\TMP-Pilot\credentials.env`

Обязательные переменные:

- `TMP_DB_URL` — только `jdbc:postgresql://localhost:55432/tmp_pilot`
- `TMP_DB_USERNAME` — только `tmp_pilot_app`
- `TMP_DB_PASSWORD`
- `TMP_SECURITY_BOOTSTRAP_*` — логин/отображаемое имя/пароль первого администратора TMP

Пароли не хранятся в репозитории и не печатаются скриптами.

**Перед загрузкой реальных данных пилота** смените пароль администратора приложения, если он был сгенерирован только для технической инициализации, и не используйте пароли вроде `admin` / stage-defaults.

## Запуск TMP PILOT

Из корня репозитория:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pilot\run-tmp-pilot.ps1
```

Скрипт:

- проверяет обязательные настройки БД;
- отклоняет любое имя базы, кроме `tmp_pilot`;
- отклоняет forbidden legacy URL (`tmp_gui_stage5`) / пользователя `tmp`;
- **не** завершает уже запущенные процессы TMP;
- по умолчанию **не** применяет новые Flyway-миграции (`SPRING_FLYWAY_ENABLED=false`).

Логин в приложение — значение `TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN` из `credentials.env` (при первом старте на пустой БД создаётся администратор).

## Package launcher (явная цель обязательна)

```powershell
$env:TMP_DB_URL = "jdbc:postgresql://localhost:55432/<explicit-database>"
powershell -ExecutionPolicy Bypass -File .\scripts\run-tmp-package.ps1
```

`scripts/run-tmp-package.ps1` **не** подставляет default ни на `tmp_pilot`, ни на `tmp_gui_stage5`. Без `TMP_DB_URL` скрипт завершается с ошибкой. Для постоянного пилота используйте `run-tmp-pilot.ps1`.

## Инициализация / обновление схемы

Первичная инициализация схемы (явная):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pilot\run-tmp-pilot.ps1 -ApplyMigrations
```

Обычные последующие запуски — **без** `-ApplyMigrations`, чтобы смена пакета TMP не применяла миграции молча.

Ограничение текущего пакета: при `SPRING_FLYWAY_ENABLED=false` Flyway не выполняет и validate-only. Контролируемое обновление схемы — снова явный `-ApplyMigrations` после решения о допуске новой версии пакета.

Не копируйте схему/данные из legacy stage (`tmp_gui_stage5`).

## Резервное копирование

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pilot\backup-tmp-pilot.ps1
```

- Формат: `pg_dump` custom (`-Fc`)
- Каталог: `%LOCALAPPDATA%\TMP-Pilot\backups\`
- Имя: `tmp_pilot_yyyyMMdd_HHmmss.dump`
- Перезапись существующего файла запрещена
- Дампы **не** кладутся в репозиторий / `scripts/reconciliation/backups`

## Проверка, что бэкап восстанавливается

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pilot\verify-tmp-pilot-backup.ps1
```

или с явным файлом:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pilot\verify-tmp-pilot-backup.ps1 -DumpFile "C:\Users\<you>\AppData\Local\TMP-Pilot\backups\tmp_pilot_....dump"
```

Процедура создаёт только `tmp_pilot_restore_check`, восстанавливает туда дамп, проверяет Flyway/схемы и удаляет **только** эту временную базу.  
Никогда не восстанавливает поверх `tmp_pilot` или forbidden legacy name `tmp_gui_stage5`. Если `tmp_pilot_restore_check` уже есть — остановка без перезаписи.  
Живая legacy stage DB **не** требуется для успешной проверки.

## Ручное восстановление в отдельную базу

1. Создайте новую пустую БД с **уникальным** именем (не `tmp_pilot`, не `tmp_gui_stage5`).
2. `pg_restore` только в эту новую БД.
3. Не используйте автоматический destructive restore в пилотных скриптах — его нет намеренно.

## Безопасное обновление пилотного приложения

1. Сделайте бэкап `backup-tmp-pilot.ps1`.
2. Проверьте бэкап `verify-tmp-pilot-backup.ps1`.
3. Соберите пакет:  
   `mvn -pl :tmp-bootstrap-app -am pre-integration-test -Ppackage -DskipTests`  
   Не перезаписывайте `dist\jpackage\TMP`, если этот каталог сейчас использует работающий процесс пользователя.
4. Запустите пилот **без** миграций и убедитесь, что приложение стартует.
5. Только по решению — один контролируемый запуск с `-ApplyMigrations`.
6. Снова бэкап после успешной миграции.

## Запрещено

- Подключать пилот к `tmp_gui_stage5` или подставлять stage-пароли
- Запускать пилот под ролью `tmp` / `postgres`
- Клонировать/копировать данные из legacy stage в `tmp_pilot` без отдельной задачи
- `DROP` защищённых баз (`tmp_pilot`; `tmp_gui_stage5` — только отдельным утверждённым retirement-шагом)
- Восстанавливать дамп поверх пилота или legacy stage
- Коммитить `credentials.env`, дампы, пароли
- Импортировать реальные заказы / создавать остатки в рамках только инфраструктурной подготовки (отдельные задачи)
- Менять порты/volumes/auth контейнера `tmp-stage5-pg` или пересоздавать его ради пилота
