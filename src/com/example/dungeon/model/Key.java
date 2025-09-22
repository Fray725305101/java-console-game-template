package com.example.dungeon.model;

public class Key extends Item {
    private final int type;

    public Key(String name, int type) {
        super(name);
        this.type = type;
    }

    public int getType() {
        return type;
    }

    @Override
    public void apply(GameState ctx) {
        Room currentRoom = ctx.getCurrent();
        boolean foundMatchingLock = currentRoom.getNeighbors().values().stream()
                .anyMatch(room -> room.getLocked() == this.type);
        if (foundMatchingLock) {
            currentRoom.getNeighbors().values().stream()
                    .filter(room -> room.getLocked() == this.type)
                    .forEach(room -> room.setLocked(0));
            System.out.println("Дверь открыта ключом: "+getName());
        } else {
            System.out.println("Нет подходящей двери");
        }
    }
}
