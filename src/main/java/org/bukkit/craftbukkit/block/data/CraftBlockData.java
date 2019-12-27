package org.bukkit.craftbukkit.block.data;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.command.arguments.BlockStateParser;
import net.minecraft.block.Block;
import net.minecraft.state.StateHolder;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.util.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.state.IProperty;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.registry.Registry;
import net.minecraft.nbt.CompoundNBT;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;

public class CraftBlockData implements BlockData {

    private IBlockData state;
    private Map<IBlockState<?>, Comparable<?>> parsedStates;

    protected CraftBlockData() {
        throw new AssertionError("Template Constructor");
    }

    protected CraftBlockData(IBlockData state) {
        this.state = state;
    }

    @Override
    public Material getMaterial() {
        return CraftMagicNumbers.getMaterial(state.getBlock());
    }

    public IBlockData getState() {
        return state;
    }

    /**
     * Get a given BlockStateEnum's value as its Bukkit counterpart.
     *
     * @param nms the NMS state to convert
     * @param bukkit the Bukkit class
     * @param <B> the type
     * @return the matching Bukkit type
     */
    protected <B extends Enum<B>> B get(BlockStateEnum<?> nms, Class<B> bukkit) {
        return toBukkit(state.get(nms), bukkit);
    }

    /**
     * Convert all values from the given BlockStateEnum to their appropriate
     * Bukkit counterpart.
     *
     * @param nms the NMS state to get values from
     * @param bukkit the bukkit class to convert the values to
     * @param <B> the bukkit class type
     * @return an immutable Set of values in their appropriate Bukkit type
     */
    @SuppressWarnings("unchecked")
    protected <B extends Enum<B>> Set<B> getValues(BlockStateEnum<?> nms, Class<B> bukkit) {
        ImmutableSet.Builder<B> values = ImmutableSet.builder();

        for (Enum<?> e : nms.getValues()) {
            values.add(toBukkit(e, bukkit));
        }

        return values.build();
    }

    /**
     * Set a given {@link BlockStateEnum} with the matching enum from Bukkit.
     *
     * @param nms the NMS BlockStateEnum to set
     * @param bukkit the matching Bukkit Enum
     * @param <B> the Bukkit type
     * @param <N> the NMS type
     */
    protected <B extends Enum<B>, N extends Enum<N> & INamable> void set(BlockStateEnum<N> nms, Enum<B> bukkit) {
        this.parsedStates = null;
        this.state = this.state.set(nms, toNMS(bukkit, nms.b()));
    }

    @Override
    public BlockData merge(BlockData data) {
        CraftBlockData craft = (CraftBlockData) data;
        Preconditions.checkArgument(craft.parsedStates != null, "Data not created via string parsing");
        Preconditions.checkArgument(this.state.getBlock() == craft.state.getBlock(), "States have different types (got %s, expected %s)", data, this);

        CraftBlockData clone = (CraftBlockData) this.clone();
        clone.parsedStates = null;

        for (IBlockState parsed : craft.parsedStates.keySet()) {
            clone.state = clone.state.set(parsed, craft.state.get(parsed));
        }

        return clone;
    }

    @Override
    public boolean matches(BlockData data) {
        if (data == null) {
            return false;
        }
        if (!(data instanceof CraftBlockData)) {
            return false;
        }

        CraftBlockData craft = (CraftBlockData) data;
        if (this.state.getBlock() != craft.state.getBlock()) {
            return false;
        }

        // Fastpath an exact match
        boolean exactMatch = this.equals(data);

        // If that failed, do a merge and check
        if (!exactMatch && craft.parsedStates != null) {
            return this.merge(data).equals(this);
        }

        return exactMatch;
    }

    private static final Map<Class, BiMap<Enum<?>, Enum<?>>> classMappings = new HashMap<>();

    /**
     * Convert an NMS Enum (usually a BlockStateEnum) to its appropriate Bukkit
     * enum from the given class.
     *
     * @throws IllegalStateException if the Enum could not be converted
     */
    @SuppressWarnings("unchecked")
    private static <B extends Enum<B>> B toBukkit(Enum<?> nms, Class<B> bukkit) {
        Enum<?> converted;
        BiMap<Enum<?>, Enum<?>> nmsToBukkit = classMappings.get(nms.getClass());

        if (nmsToBukkit != null) {
            converted = nmsToBukkit.get(nms);
            if (converted != null) {
                return (B) converted;
            }
        }

        if (nms instanceof EnumDirection) {
            converted = CraftBlock.notchToBlockFace((EnumDirection) nms);
        } else {
            converted = bukkit.getEnumConstants()[nms.ordinal()];
        }

        Preconditions.checkState(converted != null, "Could not convert enum %s->%s", nms, bukkit);

        if (nmsToBukkit == null) {
            nmsToBukkit = HashBiMap.create();
            classMappings.put(nms.getClass(), nmsToBukkit);
        }

        nmsToBukkit.put(nms, converted);

        return (B) converted;
    }

    /**
     * Convert a given Bukkit enum to its matching NMS enum type.
     *
     * @param bukkit the Bukkit enum to convert
     * @param nms the NMS class
     * @return the matching NMS type
     * @throws IllegalStateException if the Enum could not be converted
     */
    @SuppressWarnings("unchecked")
    private static <N extends Enum<N> & INamable> N toNMS(Enum<?> bukkit, Class<N> nms) {
        Enum<?> converted;
        BiMap<Enum<?>, Enum<?>> nmsToBukkit = classMappings.get(nms);

        if (nmsToBukkit != null) {
            converted = nmsToBukkit.inverse().get(bukkit);
            if (converted != null) {
                return (N) converted;
            }
        }

        if (bukkit instanceof BlockFace) {
            converted = CraftBlock.blockFaceToNotch((BlockFace) bukkit);
        } else {
            converted = nms.getEnumConstants()[bukkit.ordinal()];
        }

        Preconditions.checkState(converted != null, "Could not convert enum %s->%s", nms, bukkit);

        if (nmsToBukkit == null) {
            nmsToBukkit = HashBiMap.create();
            classMappings.put(nms, nmsToBukkit);
        }

        nmsToBukkit.put(converted, bukkit);

        return (N) converted;
    }

    /**
     * Get the current value of a given state.
     *
     * @param ibs the state to check
     * @param <T> the type
     * @return the current value of the given state
     */
    protected <T extends Comparable<T>> T get(IBlockState<T> ibs) {
        // Straight integer or boolean getter
        return this.state.get(ibs);
    }

    /**
     * Set the specified state's value.
     *
     * @param ibs the state to set
     * @param v the new value
     * @param <T> the state's type
     * @param <V> the value's type. Must match the state's type.
     */
    public <T extends Comparable<T>, V extends T> void set(IBlockState<T> ibs, V v) {
        // Straight integer or boolean setter
        this.parsedStates = null;
        this.state = this.state.set(ibs, v);
    }

    @Override
    public String getAsString() {
        return toString(((BlockDataAbstract) state).getStateMap());
    }

    @Override
    public String getAsString(boolean hideUnspecified) {
        return (hideUnspecified && parsedStates != null) ? toString(parsedStates) : getAsString();
    }

    @Override
    public BlockData clone() {
        try {
            return (BlockData) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError("Clone not supported", ex);
        }
    }

    @Override
    public String toString() {
        return "CraftBlockData{" + state.toString() + "}";
    }

    // Mimicked from BlockDataAbstract#toString()
    public String toString(Map<IBlockState<?>, Comparable<?>> states) {
        StringBuilder stateString = new StringBuilder(IRegistry.BLOCK.getKey(state.getBlock()).toString());

        if (!states.isEmpty()) {
            stateString.append('[');
            stateString.append(states.entrySet().stream().map(BlockDataAbstract.STATE_TO_VALUE).collect(Collectors.joining(",")));
            stateString.append(']');
        }

        return stateString.toString();
    }

    public NBTTagCompound toStates() {
        NBTTagCompound compound = new NBTTagCompound();

        for (Map.Entry<IBlockState<?>, Comparable<?>> entry : state.getStateMap().entrySet()) {
            IBlockState iblockstate = (IBlockState) entry.getKey();

            compound.setString(iblockstate.a(), iblockstate.a(entry.getValue()));
        }

        return compound;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CraftBlockData && state.equals(((CraftBlockData) obj).state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    protected static BlockStateBoolean getBoolean(String name) {
        throw new AssertionError("Template Method");
    }

    protected static BlockStateBoolean getBoolean(String name, boolean optional) {
        throw new AssertionError("Template Method");
    }

    protected static BlockStateEnum<?> getEnum(String name) {
        throw new AssertionError("Template Method");
    }

    protected static BlockStateInteger getInteger(String name) {
        throw new AssertionError("Template Method");
    }

    protected static BlockStateBoolean getBoolean(Class<? extends Block> block, String name) {
        return (BlockStateBoolean) getState(block, name, false);
    }

    protected static BlockStateBoolean getBoolean(Class<? extends Block> block, String name, boolean optional) {
        return (BlockStateBoolean) getState(block, name, optional);
    }

    protected static BlockStateEnum<?> getEnum(Class<? extends Block> block, String name) {
        return (BlockStateEnum<?>) getState(block, name, false);
    }

    protected static BlockStateInteger getInteger(Class<? extends Block> block, String name) {
        return (BlockStateInteger) getState(block, name, false);
    }

    /**
     * Get a specified {@link IBlockState} from a given block's class with a
     * given name
     *
     * @param block the class to retrieve the state from
     * @param name the name of the state to retrieve
     * @param optional if the state can be null
     * @return the specified state or null
     * @throws IllegalStateException if the state is null and {@code optional}
     * is false.
     */
    private static IBlockState<?> getState(Class<? extends Block> block, String name, boolean optional) {
        IBlockState<?> state = null;

        for (Block instance : IRegistry.BLOCK) {
            if (instance.getClass() == block) {
                if (state == null) {
                    state = instance.getStates().a(name);
                } else {
                    IBlockState<?> newState = instance.getStates().a(name);

                    Preconditions.checkState(state == newState, "State mistmatch %s,%s", state, newState);
                }
            }
        }

        Preconditions.checkState(optional || state != null, "Null state for %s,%s", block, name);

        return state;
    }

    /**
     * Get the minimum value allowed by the BlockStateInteger.
     *
     * @param state the state to check
     * @return the minimum value allowed
     */
    protected static int getMin(BlockStateInteger state) {
        return state.min;
    }

    /**
     * Get the maximum value allowed by the BlockStateInteger.
     *
     * @param state the state to check
     * @return the maximum value allowed
     */
    protected static int getMax(BlockStateInteger state) {
        return state.max;
    }

    //
    private static final Map<Class<? extends Block>, Function<IBlockData, CraftBlockData>> MAP = new HashMap<>();

    static {
        //<editor-fold desc="CraftBlockData Registration" defaultstate="collapsed">
        register(net.minecraft.block.BlockAnvil.class, org.bukkit.craftbukkit.block.impl.CraftAnvil::new);
        register(net.minecraft.block.BlockBamboo.class, org.bukkit.craftbukkit.block.impl.CraftBamboo::new);
        register(net.minecraft.block.BlockBanner.class, org.bukkit.craftbukkit.block.impl.CraftBanner::new);
        register(net.minecraft.block.BlockBannerWall.class, org.bukkit.craftbukkit.block.impl.CraftBannerWall::new);
        register(net.minecraft.block.BlockBarrel.class, org.bukkit.craftbukkit.block.impl.CraftBarrel::new);
        register(net.minecraft.block.BlockBed.class, org.bukkit.craftbukkit.block.impl.CraftBed::new);
        register(net.minecraft.block.BlockBeehive.class, org.bukkit.craftbukkit.block.impl.CraftBeehive::new);
        register(net.minecraft.block.BlockBeetroot.class, org.bukkit.craftbukkit.block.impl.CraftBeetroot::new);
        register(net.minecraft.block.BellBlock.class, org.bukkit.craftbukkit.block.impl.CraftBell::new);
        register(net.minecraft.block.BlockBlastFurnace.class, org.bukkit.craftbukkit.block.impl.CraftBlastFurnace::new);
        register(net.minecraft.block.BrewingStandBlock.class, org.bukkit.craftbukkit.block.impl.CraftBrewingStand::new);
        register(net.minecraft.block.BlockBubbleColumn.class, org.bukkit.craftbukkit.block.impl.CraftBubbleColumn::new);
        register(net.minecraft.block.BlockCactus.class, org.bukkit.craftbukkit.block.impl.CraftCactus::new);
        register(net.minecraft.block.BlockCake.class, org.bukkit.craftbukkit.block.impl.CraftCake::new);
        register(net.minecraft.block.BlockCampfire.class, org.bukkit.craftbukkit.block.impl.CraftCampfire::new);
        register(net.minecraft.block.BlockCarrots.class, org.bukkit.craftbukkit.block.impl.CraftCarrots::new);
        register(net.minecraft.block.CauldronBlock.class, org.bukkit.craftbukkit.block.impl.CraftCauldron::new);
        register(net.minecraft.block.ChestBlock.class, org.bukkit.craftbukkit.block.impl.CraftChest::new);
        register(net.minecraft.block.ChestBlockTrapped.class, org.bukkit.craftbukkit.block.impl.CraftChestTrapped::new);
        register(net.minecraft.block.BlockChorusFlower.class, org.bukkit.craftbukkit.block.impl.CraftChorusFlower::new);
        register(net.minecraft.block.BlockChorusFruit.class, org.bukkit.craftbukkit.block.impl.CraftChorusFruit::new);
        register(net.minecraft.block.BlockCobbleWall.class, org.bukkit.craftbukkit.block.impl.CraftCobbleWall::new);
        register(net.minecraft.block.BlockCocoa.class, org.bukkit.craftbukkit.block.impl.CraftCocoa::new);
        register(net.minecraft.block.BlockCommand.class, org.bukkit.craftbukkit.block.impl.CraftCommand::new);
        register(net.minecraft.block.ComposterBlock.class, org.bukkit.craftbukkit.block.impl.CraftComposter::new);
        register(net.minecraft.block.BlockConduit.class, org.bukkit.craftbukkit.block.impl.CraftConduit::new);
        register(net.minecraft.block.CoralBlockDead.class, org.bukkit.craftbukkit.block.impl.CraftCoralDead::new);
        register(net.minecraft.block.CoralBlockFan.class, org.bukkit.craftbukkit.block.impl.CraftCoralFan::new);
        register(net.minecraft.block.CoralBlockFanAbstract.class, org.bukkit.craftbukkit.block.impl.CraftCoralFanAbstract::new);
        register(net.minecraft.block.CoralBlockFanWall.class, org.bukkit.craftbukkit.block.impl.CraftCoralFanWall::new);
        register(net.minecraft.block.CoralBlockFanWallAbstract.class, org.bukkit.craftbukkit.block.impl.CraftCoralFanWallAbstract::new);
        register(net.minecraft.block.CoralBlockPlant.class, org.bukkit.craftbukkit.block.impl.CraftCoralPlant::new);
        register(net.minecraft.block.BlockCrops.class, org.bukkit.craftbukkit.block.impl.CraftCrops::new);
        register(net.minecraft.block.BlockDaylightDetector.class, org.bukkit.craftbukkit.block.impl.CraftDaylightDetector::new);
        register(net.minecraft.block.BlockDirtSnow.class, org.bukkit.craftbukkit.block.impl.CraftDirtSnow::new);
        register(net.minecraft.block.DispenserBlock.class, org.bukkit.craftbukkit.block.impl.CraftDispenser::new);
        register(net.minecraft.block.DoorBlock.class, org.bukkit.craftbukkit.block.impl.CraftDoor::new);
        register(net.minecraft.block.BlockDropper.class, org.bukkit.craftbukkit.block.impl.CraftDropper::new);
        register(net.minecraft.block.EndRodBlock.class, org.bukkit.craftbukkit.block.impl.CraftEndRod::new);
        register(net.minecraft.block.EnderChestBlock.class, org.bukkit.craftbukkit.block.impl.CraftEnderChest::new);
        register(net.minecraft.block.EndPortalFrameBlock.class, org.bukkit.craftbukkit.block.impl.CraftEnderPortalFrame::new);
        register(net.minecraft.block.BlockFence.class, org.bukkit.craftbukkit.block.impl.CraftFence::new);
        register(net.minecraft.block.FenceGateBlock.class, org.bukkit.craftbukkit.block.impl.CraftFenceGate::new);
        register(net.minecraft.block.FireBlock.class, org.bukkit.craftbukkit.block.impl.CraftFire::new);
        register(net.minecraft.block.StandingSignBlock.class, org.bukkit.craftbukkit.block.impl.CraftFloorSign::new);
        register(net.minecraft.block.BlockFluids.class, org.bukkit.craftbukkit.block.impl.CraftFluids::new);
        register(net.minecraft.block.FurnaceBlock.class, org.bukkit.craftbukkit.block.impl.CraftFurnaceFurace::new);
        register(net.minecraft.block.BlockGlazedTerracotta.class, org.bukkit.craftbukkit.block.impl.CraftGlazedTerracotta::new);
        register(net.minecraft.block.BlockGrass.class, org.bukkit.craftbukkit.block.impl.CraftGrass::new);
        register(net.minecraft.block.BlockGrindstone.class, org.bukkit.craftbukkit.block.impl.CraftGrindstone::new);
        register(net.minecraft.block.BlockHay.class, org.bukkit.craftbukkit.block.impl.CraftHay::new);
        register(net.minecraft.block.BlockHopper.class, org.bukkit.craftbukkit.block.impl.CraftHopper::new);
        register(net.minecraft.block.BlockHugeMushroom.class, org.bukkit.craftbukkit.block.impl.CraftHugeMushroom::new);
        register(net.minecraft.block.BlockIceFrost.class, org.bukkit.craftbukkit.block.impl.CraftIceFrost::new);
        register(net.minecraft.block.BlockIronBars.class, org.bukkit.craftbukkit.block.impl.CraftIronBars::new);
        register(net.minecraft.block.JigsawBlock.class, org.bukkit.craftbukkit.block.impl.CraftJigsaw::new);
        register(net.minecraft.block.JukeboxBlock.class, org.bukkit.craftbukkit.block.impl.CraftJukeBox::new);
        register(net.minecraft.block.BlockKelp.class, org.bukkit.craftbukkit.block.impl.CraftKelp::new);
        register(net.minecraft.block.BlockLadder.class, org.bukkit.craftbukkit.block.impl.CraftLadder::new);
        register(net.minecraft.block.BlockLantern.class, org.bukkit.craftbukkit.block.impl.CraftLantern::new);
        register(net.minecraft.block.BlockLeaves.class, org.bukkit.craftbukkit.block.impl.CraftLeaves::new);
        register(net.minecraft.block.LecternBlock.class, org.bukkit.craftbukkit.block.impl.CraftLectern::new);
        register(net.minecraft.block.BlockLever.class, org.bukkit.craftbukkit.block.impl.CraftLever::new);
        register(net.minecraft.block.BlockLogAbstract.class, org.bukkit.craftbukkit.block.impl.CraftLogAbstract::new);
        register(net.minecraft.block.LoomBlock.class, org.bukkit.craftbukkit.block.impl.CraftLoom::new);
        register(net.minecraft.block.BlockMinecartDetector.class, org.bukkit.craftbukkit.block.impl.CraftMinecartDetector::new);
        register(net.minecraft.block.BlockMinecartTrack.class, org.bukkit.craftbukkit.block.impl.CraftMinecartTrack::new);
        register(net.minecraft.block.BlockMycel.class, org.bukkit.craftbukkit.block.impl.CraftMycel::new);
        register(net.minecraft.block.NetherWartBlock.class, org.bukkit.craftbukkit.block.impl.CraftNetherWart::new);
        register(net.minecraft.block.BlockNote.class, org.bukkit.craftbukkit.block.impl.CraftNote::new);
        register(net.minecraft.block.BlockObserver.class, org.bukkit.craftbukkit.block.impl.CraftObserver::new);
        register(net.minecraft.block.BlockPiston.class, org.bukkit.craftbukkit.block.impl.CraftPiston::new);
        register(net.minecraft.block.BlockPistonExtension.class, org.bukkit.craftbukkit.block.impl.CraftPistonExtension::new);
        register(net.minecraft.block.MovingPistonBlock.class, org.bukkit.craftbukkit.block.impl.CraftPistonMoving::new);
        register(net.minecraft.block.BlockPortal.class, org.bukkit.craftbukkit.block.impl.CraftPortal::new);
        register(net.minecraft.block.BlockPotatoes.class, org.bukkit.craftbukkit.block.impl.CraftPotatoes::new);
        register(net.minecraft.block.RedstoneBlockRail.class, org.bukkit.craftbukkit.block.impl.CraftPoweredRail::new);
        register(net.minecraft.block.PressurePlateBlock.class, org.bukkit.craftbukkit.block.impl.CraftPressurePlateBinary::new);
        register(net.minecraft.block.BlockPressurePlateWeighted.class, org.bukkit.craftbukkit.block.impl.CraftPressurePlateWeighted::new);
        register(net.minecraft.block.CarvedPumpkinBlock.class, org.bukkit.craftbukkit.block.impl.CraftPumpkinCarved::new);
        register(net.minecraft.block.ComparatorBlock.class, org.bukkit.craftbukkit.block.impl.CraftRedstoneComparator::new);
        register(net.minecraft.block.RedstoneLampBlock.class, org.bukkit.craftbukkit.block.impl.CraftRedstoneLamp::new);
        register(net.minecraft.block.BlockRedstoneOre.class, org.bukkit.craftbukkit.block.impl.CraftRedstoneOre::new);
        register(net.minecraft.block.BlockRedstoneTorch.class, org.bukkit.craftbukkit.block.impl.CraftRedstoneTorch::new);
        register(net.minecraft.block.BlockRedstoneTorchWall.class, org.bukkit.craftbukkit.block.impl.CraftRedstoneTorchWall::new);
        register(net.minecraft.block.RedstoneWireBlock.class, org.bukkit.craftbukkit.block.impl.CraftRedstoneWire::new);
        register(net.minecraft.block.BlockReed.class, org.bukkit.craftbukkit.block.impl.CraftReed::new);
        register(net.minecraft.block.BlockRepeater.class, org.bukkit.craftbukkit.block.impl.CraftRepeater::new);
        register(net.minecraft.block.BlockRotatable.class, org.bukkit.craftbukkit.block.impl.CraftRotatable::new);
        register(net.minecraft.block.BlockSapling.class, org.bukkit.craftbukkit.block.impl.CraftSapling::new);
        register(net.minecraft.block.BlockScaffolding.class, org.bukkit.craftbukkit.block.impl.CraftScaffolding::new);
        register(net.minecraft.block.SeaPickleBlock.class, org.bukkit.craftbukkit.block.impl.CraftSeaPickle::new);
        register(net.minecraft.block.BlockShulkerBox.class, org.bukkit.craftbukkit.block.impl.CraftShulkerBox::new);
        register(net.minecraft.block.SkullBlock.class, org.bukkit.craftbukkit.block.impl.CraftSkull::new);
        register(net.minecraft.block.SkullPlayerBlock.class, org.bukkit.craftbukkit.block.impl.CraftSkullPlayer::new);
        register(net.minecraft.block.SkullPlayerBlockWall.class, org.bukkit.craftbukkit.block.impl.CraftSkullPlayerWall::new);
        register(net.minecraft.block.SkullBlockWall.class, org.bukkit.craftbukkit.block.impl.CraftSkullWall::new);
        register(net.minecraft.block.BlockSmoker.class, org.bukkit.craftbukkit.block.impl.CraftSmoker::new);
        register(net.minecraft.block.BlockSnow.class, org.bukkit.craftbukkit.block.impl.CraftSnow::new);
        register(net.minecraft.block.FarmlandBlock.class, org.bukkit.craftbukkit.block.impl.CraftSoil::new);
        register(net.minecraft.block.StainedGlassBlockPane.class, org.bukkit.craftbukkit.block.impl.CraftStainedGlassPane::new);
        register(net.minecraft.block.BlockStairs.class, org.bukkit.craftbukkit.block.impl.CraftStairs::new);
        register(net.minecraft.block.StemBlock.class, org.bukkit.craftbukkit.block.impl.CraftStem::new);
        register(net.minecraft.block.AttachedStemBlock.class, org.bukkit.craftbukkit.block.impl.CraftStemAttached::new);
        register(net.minecraft.block.BlockStepAbstract.class, org.bukkit.craftbukkit.block.impl.CraftStepAbstract::new);
        register(net.minecraft.block.BlockStoneButton.class, org.bukkit.craftbukkit.block.impl.CraftStoneButton::new);
        register(net.minecraft.block.StonecutterBlock.class, org.bukkit.craftbukkit.block.impl.CraftStonecutter::new);
        register(net.minecraft.block.BlockStructure.class, org.bukkit.craftbukkit.block.impl.CraftStructure::new);
        register(net.minecraft.block.BlockSweetBerryBush.class, org.bukkit.craftbukkit.block.impl.CraftSweetBerryBush::new);
        register(net.minecraft.block.TNTBlock.class, org.bukkit.craftbukkit.block.impl.CraftTNT::new);
        register(net.minecraft.block.FourWayBlockPlant.class, org.bukkit.craftbukkit.block.impl.CraftTallPlant::new);
        register(net.minecraft.block.FourWayBlockPlantFlower.class, org.bukkit.craftbukkit.block.impl.CraftTallPlantFlower::new);
        register(net.minecraft.block.TallSeaGrassBlock.class, org.bukkit.craftbukkit.block.impl.CraftTallSeaGrass::new);
        register(net.minecraft.block.TorchBlockWall.class, org.bukkit.craftbukkit.block.impl.CraftTorchWall::new);
        register(net.minecraft.block.BlockTrapdoor.class, org.bukkit.craftbukkit.block.impl.CraftTrapdoor::new);
        register(net.minecraft.block.BlockTripwire.class, org.bukkit.craftbukkit.block.impl.CraftTripwire::new);
        register(net.minecraft.block.BlockTripwireHook.class, org.bukkit.craftbukkit.block.impl.CraftTripwireHook::new);
        register(net.minecraft.block.BlockTurtleEgg.class, org.bukkit.craftbukkit.block.impl.CraftTurtleEgg::new);
        register(net.minecraft.block.BlockVine.class, org.bukkit.craftbukkit.block.impl.CraftVine::new);
        register(net.minecraft.block.BlockWallSign.class, org.bukkit.craftbukkit.block.impl.CraftWallSign::new);
        register(net.minecraft.block.BlockWitherSkull.class, org.bukkit.craftbukkit.block.impl.CraftWitherSkull::new);
        register(net.minecraft.block.BlockWitherSkullWall.class, org.bukkit.craftbukkit.block.impl.CraftWitherSkullWall::new);
        register(net.minecraft.block.WoodButtonBlock.class, org.bukkit.craftbukkit.block.impl.CraftWoodButton::new);
        //</editor-fold>
    }

    private static void register(Class<? extends Block> nms, Function<IBlockData, CraftBlockData> bukkit) {
        Preconditions.checkState(MAP.put(nms, bukkit) == null, "Duplicate mapping %s->%s", nms, bukkit);
    }

    public static CraftBlockData newData(Material material, String data) {
        Preconditions.checkArgument(material == null || material.isBlock(), "Cannot get data for not block %s", material);

        IBlockData blockData;
        Block block = CraftMagicNumbers.getBlock(material);
        Map<IBlockState<?>, Comparable<?>> parsed = null;

        // Data provided, use it
        if (data != null) {
            try {
                // Material provided, force that material in
                if (block != null) {
                    data = IRegistry.BLOCK.getKey(block) + data;
                }

                StringReader reader = new StringReader(data);
                ArgumentBlock arg = new ArgumentBlock(reader, false).a(false);
                Preconditions.checkArgument(!reader.canRead(), "Spurious trailing data: " + data);

                blockData = arg.getBlockData();
                parsed = arg.getStateMap();
            } catch (CommandSyntaxException ex) {
                throw new IllegalArgumentException("Could not parse data: " + data, ex);
            }
        } else {
            blockData = block.getBlockData();
        }

        CraftBlockData craft = fromData(blockData);
        craft.parsedStates = parsed;
        return craft;
    }

    public static CraftBlockData fromData(IBlockData data) {
        return MAP.getOrDefault(data.getBlock().getClass(), CraftBlockData::new).apply(data);
    }
}
