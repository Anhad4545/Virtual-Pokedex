package org.example;

public class Pokemon {
    private int id;
    private String name;
    private String type1;
    private String type2;
    private String description;
    private int hp;
    private int attack;
    private int defense;
    private int speed;
    private double height;
    private double weight;

    public Pokemon(int id, String name, String type1, String type2, String description,
                   int hp, int attack, int defense, int speed, double height, double weight) {
        this.id = id;
        this.name = name;
        this.type1 = type1;
        this.type2 = type2;
        this.description = description;
        this.hp = hp;
        this.attack = attack;
        this.defense = defense;
        this.speed = speed;
        this.height = height;
        this.weight = weight;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getType1() { return type1; }
    public String getType2() { return type2; }
    public String getDescription() { return description; }
    public int getHp() { return hp; }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public int getSpeed() { return speed; }
    public double getHeight() { return height; }
    public double getWeight() { return weight; }
}