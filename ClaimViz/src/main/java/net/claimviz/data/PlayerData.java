package net.claimviz.data;

public record PlayerData(
    String name,
    String uuid,   // no dashes, matches players.json format
    double x, double y, double z,
    float yaw,
    int health,    // 0–20
    int armor,     // 0–20
    String world   // SquareMap format: "minecraft_overworld" etc.
) {}
