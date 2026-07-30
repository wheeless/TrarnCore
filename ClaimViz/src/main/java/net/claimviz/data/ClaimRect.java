package net.claimviz.data;

public record ClaimRect(
    int minX, int maxX,
    int minZ, int maxZ,
    int color,        // packed 0xAARRGGBB
    String owner,
    String dimension  // SquareMap format: "minecraft_overworld" etc.
) {
    public boolean isNear(double px, double pz, double threshold) {
        return px >= minX - threshold && px <= maxX + threshold
            && pz >= minZ - threshold && pz <= maxZ + threshold;
    }

    public boolean contains(double px, double pz) {
        return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
    }

    public double centerX() { return (minX + maxX) / 2.0; }
    public double centerZ() { return (minZ + maxZ) / 2.0; }
}
