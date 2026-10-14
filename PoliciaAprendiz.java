package com.example.policia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

// Aprendiz del herrero: un NPC con skin propia que se queda en tu taller,
// mantiene los hornos prendidos con el combustible que le das, te avisa
// cuando terminan de fundir y te junta lo fundido. También te puede seguir.
public class PoliciaAprendiz {
    public static final RegistryObject<EntityType<Apprentice>> APR = PoliciaRefuerzo.ENTITIES.register("aprendiz", () -> EntityType.Builder.<Apprentice>of(Apprentice::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).build("policia:aprendiz"));
    public static final RegistryObject<Item> CONTRATO = PoliciaShield.ITEMS.register("contrato_aprendiz", () -> new Contract(new Item.Properties().stacksTo(1)));
    static final int R = 8;

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaAprendiz::attrs);
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(APR.get(), Apprentice.attrs().build());
    }

    static Apprentice mine(ServerPlayer p) {
        for (ServerLevel sl : p.server.getAllLevels()) {
            for (var e : sl.getAllEntities()) {
                if (e instanceof Apprentice a && a.isAlive() && p.getUUID().equals(a.owner)) {
                    return a;
                }
            }
        }
        return null;
    }

    static boolean fuel(ItemStack s) {
        return ForgeHooks.getBurnTime(s, null) > 0 && !s.is(Items.LAVA_BUCKET);
    }

    public static class Contract extends Item {
        public Contract(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack it, Level lv, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("§7Herrero: clic derecho en el suelo de tu taller"));
            tip.add(Component.literal("§7para contratar un aprendiz (uno por herrero)"));
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer p)) {
                return InteractionResult.SUCCESS;
            }
            if (!PoliciaHerrero.isHerrero(p)) {
                PoliciaMod.msg(p, "Solo un herrero puede contratar un aprendiz");
                return InteractionResult.FAIL;
            }
            BlockPos at = c.getClickedPos().above();
            Apprentice a = PoliciaAprendiz.mine(p);
            if (a != null) {
                a.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                a.home = at;
                PoliciaMod.msg(p, "Tu aprendiz vino a trabajar acá");
                return InteractionResult.SUCCESS;
            }
            a = new Apprentice(APR.get(), c.getLevel());
            a.owner = p.getUUID();
            a.home = at;
            a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180.0f, 0.0f);
            a.setCustomName(Component.literal("Aprendiz de " + p.getName().getString()));
            a.setCustomNameVisible(true);
            c.getLevel().addFreshEntity(a);
            c.getItemInHand().shrink(1);
            c.getLevel().playSound(null, at, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.2f);
            p.sendSystemMessage(Component.literal("§6<Aprendiz> ¡A sus órdenes, maestro! Dame carbón y yo cuido los hornos. (clic derecho para hablarme)"));
            return InteractionResult.SUCCESS;
        }
    }

    public static class Apprentice extends PathfinderMob {
        UUID owner;
        BlockPos home;
        boolean follow;
        final SimpleContainer stock = new SimpleContainer(9);
        BlockPos workAt;
        int workT;

        public Apprentice(EntityType<? extends Apprentice> t, Level l) {
            super(t, l);
            this.setPersistenceRequired();
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(PoliciaPlaca.MARTILLO.get()));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32.0);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        }

        @Override
        public boolean removeWhenFarAway(double d) {
            return false;
        }

        @Override
        public boolean hurt(DamageSource s, float a) {
            if (s.getEntity() instanceof Player) {
                return false;
            }
            return super.hurt(s, a);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            if (this.owner != null) t.putUUID("owner", this.owner);
            if (this.home != null) t.putLong("home", this.home.asLong());
            t.putBoolean("follow", this.follow);
            ListTag l = new ListTag();
            for (int i = 0; i < this.stock.getContainerSize(); ++i) {
                ItemStack s = this.stock.getItem(i);
                if (s.isEmpty()) continue;
                CompoundTag e = new CompoundTag();
                e.putInt("Slot", i);
                s.save(e);
                l.add(e);
            }
            t.put("stock", l);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            if (t.hasUUID("owner")) this.owner = t.getUUID("owner");
            if (t.contains("home")) this.home = BlockPos.of(t.getLong("home"));
            this.follow = t.getBoolean("follow");
            for (Tag e : t.getList("stock", 10)) {
                CompoundTag c = (CompoundTag) e;
                this.stock.setItem(c.getInt("Slot"), ItemStack.of(c));
            }
        }

        ServerPlayer ownerPlayer() {
            return this.owner == null || this.level().isClientSide ? null : ((ServerLevel) this.level()).getServer().getPlayerList().getPlayer(this.owner);
        }

        int fuelCount() {
            int n = 0;
            for (int i = 0; i < this.stock.getContainerSize(); ++i) n += this.stock.getItem(i).getCount();
            return n;
        }

        List<AbstractFurnaceBlockEntity> furnaces() {
            List<AbstractFurnaceBlockEntity> l = new ArrayList<AbstractFurnaceBlockEntity>();
            BlockPos c = this.follow || this.home == null ? this.blockPosition() : this.home;
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-R, -3, -R), c.offset(R, 3, R))) {
                BlockEntity be = this.level().getBlockEntity(bp);
                if (be instanceof AbstractFurnaceBlockEntity f) l.add(f);
            }
            return l;
        }

        void work(BlockPos at) {
            this.workAt = at.immutable();
            this.workT = 40;
            this.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.8);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.level().isClientSide) {
                return;
            }
            ServerLevel sl = (ServerLevel) this.level();
            ServerPlayer o = this.ownerPlayer();
            if (this.follow && o != null && o.level() == sl) {
                double d = this.distanceToSqr(o);
                if (d > 24 * 24) {
                    this.teleportTo(o.getX(), o.getY(), o.getZ());
                } else if (d > 9.0 && this.tickCount % 10 == 0) {
                    this.getNavigation().moveTo(o, 1.0);
                }
            } else if (!this.follow && this.home != null && this.workT <= 0 && this.tickCount % 40 == 0 && this.distanceToSqr(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5) > 4.0) {
                this.getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 0.7);
            }
            if (this.workT > 0) {
                --this.workT;
                if (this.workAt != null) {
                    this.getLookControl().setLookAt(this.workAt.getX() + 0.5, this.workAt.getY() + 0.5, this.workAt.getZ() + 0.5);
                    if (this.workT % 10 == 0 && this.distanceToSqr(this.workAt.getX() + 0.5, this.workAt.getY(), this.workAt.getZ() + 0.5) < 6.0) {
                        this.swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
            if (this.tickCount % 40 != 0) {
                return;
            }
            // martillar en un yunque cercano cuando no hay nada que hacer
            if (this.workT <= 0 && this.random.nextInt(4) == 0) {
                for (BlockPos bp : BlockPos.betweenClosed(this.blockPosition().offset(-2, -1, -2), this.blockPosition().offset(2, 1, 2))) {
                    if (sl.getBlockState(bp).getBlock() instanceof AnvilBlock) {
                        this.getLookControl().setLookAt(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                        this.swing(InteractionHand.MAIN_HAND);
                        sl.playSound(null, bp, SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.35f, 1.3f);
                        sl.sendParticles(ParticleTypes.LAVA, bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 2, 0.1, 0.0, 0.1, 0.0);
                        break;
                    }
                }
            }
            for (AbstractFurnaceBlockEntity f : this.furnaces()) {
                BlockPos fp = f.getBlockPos();
                ItemStack in = f.getItem(0);
                ItemStack fu = f.getItem(1);
                ItemStack out = f.getItem(2);
                boolean lit = sl.getBlockState(fp).hasProperty(BlockStateProperties.LIT) && sl.getBlockState(fp).getValue(BlockStateProperties.LIT);
                // cargar combustible si hay algo para fundir y no tiene
                if (!in.isEmpty() && fu.isEmpty() && !lit) {
                    for (int i = 0; i < this.stock.getContainerSize(); ++i) {
                        ItemStack s = this.stock.getItem(i);
                        if (s.isEmpty()) continue;
                        int n = Math.min(s.getCount(), Math.max(1, in.getCount() / 8 + 1));
                        f.setItem(1, s.split(n));
                        f.setChanged();
                        this.work(fp);
                        sl.playSound(null, fp, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 1.2f);
                        sl.sendParticles(ParticleTypes.FLAME, fp.getX() + 0.5, fp.getY() + 0.8, fp.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
                        if (this.fuelCount() == 0 && o != null) {
                            PoliciaMod.msg(o, "§6<Aprendiz> ¡Maestro, me quedé sin carbón!");
                        }
                        break;
                    }
                }
                // avisar cuando terminó de fundir
                CompoundTag pd = f.getPersistentData();
                if (in.isEmpty() && !out.isEmpty()) {
                    int sig = out.getCount() * 31 + Item.getId(out.getItem());
                    if (pd.getInt("apr_n") != sig) {
                        pd.putInt("apr_n", sig);
                        if (o != null) {
                            o.sendSystemMessage(Component.literal("§6<Aprendiz> ¡El horno en " + fp.getX() + ", " + fp.getY() + ", " + fp.getZ() + " terminó: " + out.getCount() + " x " + out.getHoverName().getString() + "!"));
                            o.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 0.8f, 1.6f);
                        }
                    }
                } else if (pd.contains("apr_n") && (out.isEmpty() || !in.isEmpty())) {
                    pd.remove("apr_n");
                }
            }
        }

        @Override
        protected InteractionResult mobInteract(Player pl, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!(pl instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            if (this.owner == null || !this.owner.equals(sp.getUUID())) {
                PoliciaMod.msg(sp, "<Aprendiz> Trabajo para otro maestro, perdón.");
                return InteractionResult.SUCCESS;
            }
            this.menu(sp);
            return InteractionResult.SUCCESS;
        }

        void menu(ServerPlayer sp) {
            SimpleContainer c = new SimpleContainer(27);
            for (int i = 0; i < 27; ++i) {
                c.setItem(i, PoliciaBoss.pane());
            }
            int hornos = this.furnaces().size();
            c.setItem(11, PoliciaBoss.named(Items.COAL, "Darle combustible", "Le pasás todo el carbón, leña y bloques de carbón", "de tu inventario. Tiene: " + this.fuelCount()));
            c.setItem(13, PoliciaBoss.named(Items.FURNACE, "Hornos a su cargo: " + hornos, this.follow ? "Te está siguiendo" : "Trabaja en este taller", "Los prende cuando tienen algo para fundir", "y te avisa cuando terminan"));
            c.setItem(15, this.follow ? PoliciaBoss.named(Items.CAMPFIRE, "Quedate acá", "Se queda trabajando en este lugar") : PoliciaBoss.named(Items.LEAD, "Seguime", "Te acompaña a todos lados"));
            c.setItem(22, PoliciaBoss.named(Items.IRON_INGOT, "Juntar lo fundido", "Te trae todo lo que salió de los hornos"));
            c.setItem(26, PoliciaBoss.named(Items.BARRIER, "Despedirlo", "Se va y te devuelve el combustible"));
            PoliciaBoss.openMenu3(sp, "Aprendiz", c, (p, slot) -> {
                if (slot == 11) {
                    int moved = 0;
                    for (int i = 0; i < p.getInventory().items.size(); ++i) {
                        ItemStack s = p.getInventory().items.get(i);
                        if (s.isEmpty() || !PoliciaAprendiz.fuel(s)) continue;
                        int before = s.getCount();
                        ItemStack rest = this.stock.addItem(s.copy());
                        moved += before - rest.getCount();
                        p.getInventory().items.set(i, rest);
                    }
                    p.closeContainer();
                    PoliciaMod.msg(p, moved > 0 ? "<Aprendiz> ¡Gracias! Ahora tengo " + this.fuelCount() + " de combustible" : "<Aprendiz> No tenés combustible encima (o ya estoy lleno)");
                } else if (slot == 15) {
                    this.follow = !this.follow;
                    if (!this.follow) {
                        this.home = this.blockPosition();
                    }
                    p.closeContainer();
                    PoliciaMod.msg(p, this.follow ? "<Aprendiz> ¡Voy con usted, maestro!" : "<Aprendiz> Me quedo cuidando este taller");
                } else if (slot == 22) {
                    int n = 0;
                    for (AbstractFurnaceBlockEntity f : this.furnaces()) {
                        ItemStack out = f.getItem(2);
                        if (out.isEmpty()) continue;
                        n += out.getCount();
                        PoliciaBoss.give(p, out.copy());
                        f.setItem(2, ItemStack.EMPTY);
                        f.setChanged();
                    }
                    p.closeContainer();
                    this.swing(InteractionHand.MAIN_HAND);
                    PoliciaMod.msg(p, n > 0 ? "<Aprendiz> Acá tiene: " + n + " cosas fundidas" : "<Aprendiz> Los hornos todavía no sacaron nada");
                } else if (slot == 26) {
                    p.closeContainer();
                    for (int i = 0; i < this.stock.getContainerSize(); ++i) {
                        ItemStack s = this.stock.getItem(i);
                        if (!s.isEmpty()) PoliciaBoss.give(p, s);
                    }
                    this.stock.clearContent();
                    ((ServerLevel) this.level()).sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                    this.discard();
                    PoliciaMod.msg(p, "<Aprendiz> Fue un honor, maestro.");
                }
            });
        }
    }
}
