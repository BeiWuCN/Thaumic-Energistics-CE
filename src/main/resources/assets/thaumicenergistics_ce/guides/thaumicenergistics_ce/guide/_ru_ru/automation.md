---
navigation:
  title: Автоматизация эссенции и магического создания
  position: 50
  parent: thaumicenergistics_ce:index.md
  icon: thaumicenergistics_ce:alchemy_provider
item_ids:
- thaumicenergistics_ce:alchemy_provider
- thaumicenergistics_ce:arcane_assembler
- thaumicenergistics_ce:knowledge_core
- thaumicenergistics_ce:knowledge_inscriber
---

# Автоматизация эссенции и магического создания

Сначала решите, какую часть процесса должна взять на себя МЭ-сеть.

- [Перенос эссенции через МЭ-шины](essentia-buses.md)
- [Автоматизация наполнения](infusion-automation.md)
- [Шаблоны дистилляции](distillation-patterns.md)

## Алхимия

<ItemLink id="thaumicenergistics_ce:alchemy_provider" /> подаёт эссенцию по запросу соседнего совместимого устройства. При смене рецепта не нужно перенастраивать фильтр экспорта. Если подача остановилась, проверьте запас запрошенного аспекта в терминале.

## Магическая сборка

Запишите изученный рецепт в ядро знаний через <ItemLink id="thaumicenergistics_ce:knowledge_inscriber" />. Вставьте ядро, разложите ингредиенты как на магическом верстаке, проверьте результат и сохраните запись. Перенесите ядро в <ItemLink id="thaumicenergistics_ce:arcane_assembler" />, наладьте подачу предметов и vis, затем запустите задание.
