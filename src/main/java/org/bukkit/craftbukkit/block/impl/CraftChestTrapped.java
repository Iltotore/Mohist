/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftChestTrapped extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.Chest, org.bukkit.block.data.Directional, org.bukkit.block.data.Waterlogged {

    public CraftChestTrapped() {
        super();
    }

    public CraftChestTrapped(net.minecraft.block.BlockState state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftChest

    private static final net.minecraft.state.PropertyEnum<?> TYPE = getEnum(net.minecraft.block.ChestBlockTrapped.class, "type");

    @Override
    public Type getType() {
        return get(TYPE, Type.class);
    }

    @Override
    public void setType(Type type) {
        set(TYPE, type);
    }

    // org.bukkit.craftbukkit.block.data.CraftDirectional

    private static final net.minecraft.state.PropertyEnum<?> FACING = getEnum(net.minecraft.block.ChestBlockTrapped.class, "facing");

    @Override
    public org.bukkit.block.BlockFace getFacing() {
        return get(FACING, org.bukkit.block.BlockFace.class);
    }

    @Override
    public void setFacing(org.bukkit.block.BlockFace facing) {
        set(FACING, facing);
    }

    @Override
    public java.util.Set<org.bukkit.block.BlockFace> getFaces() {
        return getValues(FACING, org.bukkit.block.BlockFace.class);
    }

    // org.bukkit.craftbukkit.block.data.CraftWaterlogged

    private static final net.minecraft.state.PropertyBoolean WATERLOGGED = getBoolean(net.minecraft.block.ChestBlockTrapped.class, "waterlogged");

    @Override
    public boolean isWaterlogged() {
        return get(WATERLOGGED);
    }

    @Override
    public void setWaterlogged(boolean waterlogged) {
        set(WATERLOGGED, waterlogged);
    }
}
