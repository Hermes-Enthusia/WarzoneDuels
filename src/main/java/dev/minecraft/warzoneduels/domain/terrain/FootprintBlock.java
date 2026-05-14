package dev.minecraft.warzoneduels.domain.terrain;

public record FootprintBlock(int x, int y, int z) {
    public long packedKey() {
        return (((long) (x & 0x3FFFFFF)) << 38)
            | (((long) (z & 0x3FFFFFF)) << 12)
            | (y & 0xFFF);
    }
}
