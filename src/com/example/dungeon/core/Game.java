package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));
        //Эта команда выводит информацию по памяти Runtime environment JVM
        //Возвращает информацию по использованной памяти, свободной и доступной в байтах
        //Ниже сделал команду для демонстрации работы GC
        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            System.out.println("Память: used=" + used + " free=" + free + " total=" + total);
        });
        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));
        commands.put("move", (ctx, args) -> {
            //Проверка на кол-во аргументов:
            if (args.isEmpty()) {
                throw new InvalidCommandException("Неверно задано направление");
            }
            String direction = String.join(" ", args).toLowerCase();
            //Получаем следующую комнату из карты соседей
            Room currentRoom = ctx.getCurrent();
            Room nextRoom = currentRoom.getNeighbors().get(direction);
            //Если нашли, то перемещаемся
            if (nextRoom != null) {
                //Проверяем, не закрыта ли дверь
                if (nextRoom.isLocked()) {
                    System.out.println("Дверь заперта. Нужен ключ");
                    return;
                }
                //Добавляем начисление очков за новую комнату
                //Добавим флаг visited в класс Room
                if (!nextRoom.isVisited()) {
                    ctx.addScore(1); //Очко уходит в зрительский зал ©ЧГК
                    nextRoom.setVisited(true);
                }
                ctx.setCurrent(nextRoom);
                System.out.println("Вы перешли в: "+nextRoom.getName());
                //Показываем описание
                System.out.println(nextRoom.describe());
            } else { //Исключение при неправильном пути
                throw new InvalidCommandException("Нет пути: "+direction);
            }
        });
        commands.put("take", (ctx, args) -> {
            //Проверка на кол-во аргументов:
            if (args.isEmpty()) {
                throw new InvalidCommandException("Не указан предмет");
            }
            String itemName = String.join(" ", args);
            Room currentRoom = ctx.getCurrent();
            //Ищем предмет в комнате по имени
            Optional<Item> foundItem = currentRoom.getItems().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(itemName))
                    .findFirst();
            if (foundItem.isPresent()) {
                Item item = foundItem.get();
                //Удаляем из комнаты
                currentRoom.getItems().remove(item);
                //Добавляем в инвентарь игрока
                ctx.getPlayer().getInventory().add(item);
                System.out.println("Взято: "+item.getName());
            } else {
                throw new InvalidCommandException("Предмет "+itemName+" не найден");
            }
        });
        commands.put("inventory", (ctx, args) -> {
            List<Item> inventory = ctx.getPlayer().getInventory();
            //Тут всё понятно
            if (inventory.isEmpty()) {
                System.out.println("Инвентарь пуст");
                return;
            }
            //Группировка предметов по классам
            Map<String, List<Item>> groupedItems = inventory.stream()
                    .collect(Collectors.groupingBy(item -> item.getClass().getSimpleName()));
            //Проходим по каждой группе и выводим инфу
            groupedItems.forEach((type, items) -> {
                //Сортировка по имени в группе
                List<String> itemNames = items.stream()
                        .map(Item::getName)
                        .sorted()
                        .toList();
                System.out.println("- "+type+" ("+items.size()+"): "+String.join(", ", itemNames));
            });
        });
        commands.put("use", (ctx, args) -> {
            //Тут всё понятно
            if (args.isEmpty()) {
                throw new InvalidCommandException("Не указан предмет");
            }
            //Объединяем в одну строку аргументы
            String itemName = String.join(" ", args);
            Player player = ctx.getPlayer();
            //Ищем в инвентаре
            Optional<Item> foundItem = player.getInventory().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(itemName))
                    .findFirst();
            if (foundItem.isPresent()) {
                Item item = foundItem.get();
                item.apply(ctx);
            } else {
                throw new InvalidCommandException("В вашем инвентаре нет "+itemName);
            }
        });
        commands.put("fight", (ctx, args) -> {
            Room currentRoom = ctx.getCurrent();
            Monster monster = currentRoom.getMonster();
            Player player = ctx.getPlayer();
            //Проверяем, что есть монстр
            if (monster == null) {
                throw new InvalidCommandException("Здесь не с кем сражаться");
            }
            //Бой>
            //Ход игрока
            monster.setHp(monster.getHp() - player.getAttack());
            System.out.println("Вы бьёте "+monster.getName()+" на "+player.getAttack()+". HP монстра: "+monster.getHp());
            //Проверяем, жив ли монстр
            if (monster.getHp() <= 0) {
                System.out.println("Вы победили "+monster.getName()+" (ур. "+monster.getLevel()+")");
                //Убираем монстра из комнаты. По структуре класса видно, что private Monster monster;
                //Это 1 монстр, а не коллекция, так что с чистой совестью просто зачищаем поле
                currentRoom.setMonster(null);
                //ЛУУУУУУТ!!!111!!1!
                Item loot = monster.getLoot();
                if (loot != null) {
                    currentRoom.getItems().add(loot);
                    System.out.println(monster.getName()+" (ур. "+monster.getLevel()+")"+" оставил после себя "+loot.getName());
                }
                int scoreForWin = monster.getLevel(); //Начисляем кол-во очков = уровню монстра
                ctx.addScore(scoreForWin); //Фиксиуем
            } else {
                //Ход монстра
                //Принимаем урон монстра = уровень монстра
                int monsterDamage = monster.getLevel();
                player.setHp(player.getHp()-monsterDamage);
                System.out.println("Монстр отвечает на "+monsterDamage+". Ваше HP: "+player.getHp());

                if (player.getHp() <= 0) {
                    System.out.println("Вы потерпели поражение");
                    System.exit(0);
                }
            }
        });
        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });
        commands.put("alloc-demo", (ctx, args) -> {
            System.out.println("Демонстрация работы GC...");
            //До аллокации
            Runtime rt = Runtime.getRuntime();
            long memoryBefore = rt.totalMemory() - rt.freeMemory();
            System.out.println("Память до: " + memoryBefore + " байт");
            //Создаем много объектов для демонстрации работы GC
            List<String> tempList = new ArrayList<>();
            for (int i = 0; i < 100000; i++) {
                tempList.add("Временный объект " + i + " " + new Date());
            }
            long memoryDuring = rt.totalMemory() - rt.freeMemory();
            System.out.println("Память во время: " + memoryDuring + " байт");
            System.out.println("Выделено: " + (memoryDuring - memoryBefore) + " байт");
            //Освобождаем ссылки - объекты становятся кандидатами на GC
            tempList = null;
            //Выполняем сборку мусора (но это только suggestion)
            System.gc();
            //Даем время GC поработать
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            long memoryAfter = rt.totalMemory() - rt.freeMemory();
            System.out.println("Память после: " + memoryAfter + " байт");
            System.out.println("Освобождено GC: " + (memoryDuring - memoryAfter) + " байт");
        });
    }

    private void bootstrapWorld() {
        Player hero = new Player("Mario", 20, 5);
        state.setPlayer(hero);

        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        square.setVisited(true); //За площадь очки не начисляем

        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        Room tower = new Room("Башня", "Неприступная башня высится серой глыбой");
        Room hall = new Room("Зал", "Гулкий и мрачный зал");
        Room dungeon = new Room("Подземелье", "Thank you Mario! But our princess is in another castle!");
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        forest.getNeighbors().put("west", tower);
        cave.getNeighbors().put("west", forest);
        tower.getNeighbors().put("east", forest);
        tower.getNeighbors().put("into the tower", hall);
        hall.getNeighbors().put("outside", tower);
        hall.getNeighbors().put("down", dungeon);
        dungeon.getNeighbors().put("up", hall);

        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.setMonster(new Monster("Волк", 1, 8, new Potion("Среднее зелье", 10)));
        tower.setMonster(new Monster("Орк-стражник", 2, 15, new Key("Ключ от башни", 1)));
        cave.getItems().add(new Key("Старинный ключ", 2));
        hall.setLocked(1); //Заперли комнату
        dungeon.setLocked(2); //И эту тоже
        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    //state.addScore(1); //Переписываем логику начисления очков
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}
