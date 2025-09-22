package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SaveLoad {
    private static final Path SAVE = Paths.get("save.txt");
    private static final Path SCORES = Paths.get("scores.csv");

    public static void save(GameState s) {
        try (BufferedWriter w = Files.newBufferedWriter(SAVE)) {
            Player p = s.getPlayer();
            w.write("player;" + p.getName() + ";" + p.getHp() + ";" + p.getAttack());
            w.newLine();
            //String inv = p.getInventory().stream().map(i -> i.getClass().getSimpleName() + ":" + i.getName()).collect(Collectors.joining(","));
            String inv = p.getInventory().stream()
                            .map(item -> {
                                if (item instanceof Potion potion) {
                                    return "Potion:"+potion.getName()+":"+potion.getHeal();
                                } else if (item instanceof Key key) {
                                    return "Key:"+key.getName()+":"+key.getType();
                                }
                                return "";
                            })
                            .filter(str -> !str.isEmpty())
                            .collect(Collectors.joining(","));
            w.write("inventory;" + inv);
            w.newLine();
            w.write("current_room;" + s.getCurrent().getName());
            w.newLine();
            //Сохраняем состояние комнат
            for (Room room : s.getAllRooms()) {
                StringBuilder roomLine = new StringBuilder();
                roomLine.append("room_state;")
                        .append(room.getName()).append(";")
                        .append(room.isVisited()).append(";")
                        .append(room.getLocked()).append(";")
                        .append(room.getMonster() != null ? "1" : "0").append(";");
                String roomItems = room.getItems().stream()
                        .map(item -> {
                            if (item instanceof Potion potion) {
                                return "Potion:"+potion.getName()+":"+potion.getHeal();
                            } else if (item instanceof Key key) {
                                return "Key:"+key.getName()+":"+key.getType();
                            }
                            return "";
                        })
                        .filter(str -> !str.isEmpty())
                        .collect(Collectors.joining("|"));
                roomLine.append(roomItems);
                w.write(roomLine.toString());
                w.newLine();
            }
            System.out.println("Сохранено в " + SAVE.toAbsolutePath());
            writeScore(p.getName(), s.getScore());
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить игру", e);
        }
    }

    //Ошибка в методе загрузки. Лечим.
    public static void load(GameState s) {
        if (!Files.exists(SAVE)) {
            System.out.println("Сохранение не найдено.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SAVE)) {
            Map<String, String> map = new HashMap<>();
            List<String> roomStates = new ArrayList<>(); //Храним состояние комнат
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("room_state;")) {
                    roomStates.add(line);
                } else {
                    String[] parts = line.split(";", 2);
                    if (parts.length == 2) map.put(parts[0], parts[1]);
                }
            }
            Player p = s.getPlayer();
            String[] pp = map.getOrDefault("player", "player;Hero;10;3").split(";");
            //Вот здесь косяк
            p.setName(pp[0]); //Нумерация шла с 1
            p.setHp(Integer.parseInt(pp[1])); //И падало ArrayIndexOutOfBoundsException
            p.setAttack(Integer.parseInt(pp[2])); //Вот здесь. Логично, ведь в массиве всего 3 значения
            p.getInventory().clear();
            String inv = map.getOrDefault("inventory", "");
            if (!inv.isBlank()) for (String tok : inv.split(",")) {
                String[] t = tok.split(":", 3);
                if (t.length < 2) continue;
                switch (t[0]) {
                    //Исправлена загрузка зелья. Раньше по умолчанию всегда предмету прописывалось 5
                    case "Potion" -> {
                        int heal = Integer.parseInt(t[2]);
                        p.getInventory().add(new Potion(t[1], heal));
                    }
                    //Исправили загрузку ключа с типом
                    case "Key" -> {
                        int type = Integer.parseInt(t[2]);
                        p.getInventory().add(new Key(t[1], type));
                    }
                    case "Weapon" -> p.getInventory().add(new Weapon(t[1], 3));
                    default -> {
                    }
                }
            }
            String currentRoomName = map.getOrDefault("current_room", "Площадь");
            for (String roomState : roomStates) {
                String[] parts = roomState.split(";");
                if (parts.length >= 6) {
                    String roomName = parts[1];
                    boolean visited = Boolean.parseBoolean(parts[2]);
                    int locked = Integer.parseInt(parts[3]);
                    boolean hasMonster = "1".equals(parts[4]);
                    String itemsData = parts.length > 5 ? parts[5] : "";

                    // Находим комнату по имени
                    Room room = s.getAllRooms().stream()
                            .filter(r -> r.getName().equals(roomName))
                            .findFirst()
                            .orElse(null);

                    if (room != null) {
                        // Восстанавливаем состояние комнаты
                        room.setVisited(visited);
                        room.setLocked(locked);

                        // Очищаем предметы и добавляем сохраненные
                        room.getItems().clear();
                        if (!itemsData.isBlank()) {
                            for (String itemStr : itemsData.split("\\|")) {
                                String[] itemParts = itemStr.split(":");
                                if (itemParts.length >= 3) {
                                    switch (itemParts[0]) {
                                        case "Potion" -> {
                                            int heal = Integer.parseInt(itemParts[2]);
                                            room.getItems().add(new Potion(itemParts[1], heal));
                                        }
                                        case "Key" -> {
                                            int type = Integer.parseInt(itemParts[2]);
                                            room.getItems().add(new Key(itemParts[1], type));
                                        }
                                    }
                                }
                            }
                        }

                        // Убираем монстра если он был убит
                        if (!hasMonster) {
                            room.setMonster(null);
                        }

                        // Устанавливаем текущую комнату
                        if (roomName.equals(currentRoomName)) {
                            s.setCurrent(room);
                        }
                    }
                }
            }
            System.out.println("Игра загружена (упрощённо).");
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить игру", e);
        }
    }

    public static void printScores() {
        if (!Files.exists(SCORES)) {
            System.out.println("Пока нет результатов.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SCORES)) {
            System.out.println("Таблица лидеров (топ-10):");
            r.lines().skip(1).map(l -> l.split(",")).map(a -> new Score(a[1], Integer.parseInt(a[2])))
                    .sorted(Comparator.comparingInt(Score::score).reversed()).limit(10)
                    .forEach(s -> System.out.println(s.player() + " — " + s.score()));
        } catch (IOException e) {
            System.err.println("Ошибка чтения результатов: " + e.getMessage());
        }
    }

    private static void writeScore(String player, int score) {
        try {
            boolean header = !Files.exists(SCORES);
            try (BufferedWriter w = Files.newBufferedWriter(SCORES, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (header) {
                    w.write("ts,player,score");
                    w.newLine();
                }
                w.write(LocalDateTime.now() + "," + player + "," + score);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать очки: " + e.getMessage());
        }
    }

    private record Score(String player, int score) {
    }
}
