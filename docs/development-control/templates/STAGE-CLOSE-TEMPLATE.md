# Stage Close Gate

Stage получает `COMPLETED` только если:

- все задачи Stage имеют DONE;
- отсутствуют OPEN blockers Stage;
- полный Maven verify успешен;
- обязательные integration и architecture tests успешны;
- ручные сценарии Stage успешны;
- документация соответствует коду;
- public APIs зафиксированы;
- отсутствуют TODO/FIXME/HACK и временные решения;
- следующий Stage Manifest может использовать только стабильные публичные контракты.
