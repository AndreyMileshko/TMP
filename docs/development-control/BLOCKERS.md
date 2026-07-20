# TMP Development Blockers

## `BLK-001` — `Shell execution environment unavailable`

**Status:** RESOLVED  
**Task:** `STAGE0-001`  
**Detected:** 2026-07-17

### Reason

Команды в Shell не возвращают exit status, из-за чего невозможно достоверно выполнить обязательную verification-команду задачи.

### Evidence

- Document/file/check: `mvn -q -DskipTests validate` -> "The shell command returned no exit status, so its result is unknown".
- Document/file/check: `dir` -> "The shell command returned no exit status, so its result is unknown".

### Options

1. Перезапустить/восстановить терминальное окружение Cursor и продолжить с текущего состояния файлов.
2. Разрешить временную ручную верификацию вне Shell tool (с локальным подтверждением пользователя).

### Recommendation

Вариант 1: восстановить Shell execution в среде Cursor и продолжить автоматический цикл, сохранив строгую трассируемость verification.

### Required user decision

Можете перезапустить/восстановить терминальную среду Cursor и подтвердить, что можно повторно запустить `mvn -q -DskipTests validate`?

---

# Blocker Template

## `<BLOCKER-ID>` — `<title>`

**Status:** OPEN | RESOLVED  
**Task:** `<TASK-ID>`  
**Detected:** YYYY-MM-DD

### Reason

...

### Evidence

- Document/file/check: ...

### Options

1. ...
2. ...

### Recommendation

...

### Required user decision

Один конкретный вопрос.

### Resolution

Терминальная среда восстановлена; проверка выполнена локальным portable Maven (`.tools/apache-maven-3.9.9/bin/mvn.cmd`), `validate` прошёл успешно.

## `BLK-002` — `JDK 21 compiler not available in environment`

**Status:** RESOLVED  
**Task:** `STAGE0-004`  
**Detected:** 2026-07-17

### Reason

Для Stage 0 и текущего parent build baseline требуется Java 21, но доступен только JRE 21 и JDK 17. Компиляция тестов невозможна в требуемом окружении.

### Evidence

- Document/file/check: `docs/TMP/TMP-Discussion-Checklist.md` указывает Java 21 LTS как целевую версию.
- Document/file/check: `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` -> "No compiler is provided in this environment".
- Document/file/check: запуск с `JAVA_HOME` на JDK 17 -> fail enforcer rule: Java version not in `[21,)`.

### Options

1. Установить JDK 21 и повторить `STAGE0-004` verification.
2. Ослабить Java baseline до 17 (потребует изменения утвержденных документов/архитектурного решения).

### Recommendation

Вариант 1: установить JDK 21 и продолжить Stage 0 без отклонений от утвержденных требований.

### Required user decision

Можете установить/подключить JDK 21 в среде (или указать путь к уже установленному JDK 21), чтобы я продолжил `STAGE0-004`?

### Resolution

Установлен portable JDK 21 в `.tools/jdk-21.0.11+10`; `mvn test` выполнен успешно с `JAVA_HOME` на JDK 21.

## `BLK-003` — `Docker environment unavailable for Testcontainers`

**Status:** RESOLVED  
**Task:** `STAGE0-009`  
**Detected:** 2026-07-17

### Reason

Интеграционные тесты Testcontainers не могут быть выполнены: Docker environment не найден в системе.

### Evidence

- Document/file/check: `mvn -q -pl :tmp-infra-db verify` -> `Could not find a valid Docker environment`.
- Document/file/check: Testcontainers log reports `docker-machine executable was not found on PATH`.

### Options

1. Установить и запустить Docker Desktop (или другой Docker engine), затем повторить `STAGE0-009` verification.
2. Временно пропустить containerized integration tests (нарушает acceptance criteria Stage 0).

### Recommendation

Вариант 1: установить/запустить Docker и продолжить автопроход без изменения спецификации.

### Required user decision

Можете установить и запустить Docker, чтобы я завершил `STAGE0-009` и продолжил Stage 0?

### Resolution

Docker Desktop установлен и запущен; WSL2 kernel обновлён; Testcontainers обновлён до 1.21.4 с `api.version=1.44` для совместимости с Docker Engine 29. Verification `mvn -q -pl :tmp-infra-db verify` PASSED.
