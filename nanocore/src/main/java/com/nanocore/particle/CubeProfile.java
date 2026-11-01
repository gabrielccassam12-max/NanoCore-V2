package com.nanocore.particle;

/**
 * One kind of 3D cube particle. Colours go young -> middle -> old (0xRRGGBB).
 * gravity: positive falls, negative rises. shrink: size multiplier per tick. flat: height scale of the cube (1 = cube).
 */
public record CubeProfile(int c0, int c1, int c2, float size, float sizeVar, int life, int lifeVar,
                          float gravity, float drag, float shrink, float spin, float flat, float alpha, boolean inner) {
}
