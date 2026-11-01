package com.example.desertvanguard.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Desert Vanguard - zombie-like AI: wanders, chases the nearest player and stabs him with the glaive. */
public class DesertVanguardEntity extends PathfinderMob {

    /** Walk animation strength (0 = standing, 1 = full stride), smoothed; used only by the renderer. */
    public float walkAmp, walkAmpO;

    /** Ticks since the stab animation started, -1 = not stabbing. Runs on both sides (the swing packet reaches the client). */
    public int stabTime = -1;
    /** Length of the stab animation in ticks (1.125 s). */
    public static final int STAB_TICKS = 23;

    public DesertVanguardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    @Override
    public void tick() {
        super.tick();
        this.walkAmpO = this.walkAmp;
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float target = Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F);
        this.walkAmp += (target - this.walkAmp) * 0.4F;
        if (this.stabTime >= 0 && ++this.stabTime > STAB_TICKS) this.stabTime = -1;
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        this.stabTime = 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new StabGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Melee attack like the zombie's, but the damage lands in the middle of the stab animation instead of instantly. */
    private static class StabGoal extends MeleeAttackGoal {
        private static final int HIT_DELAY = 9; // ticks after the swing starts (the glaive thrust is at ~0.45 s)
        private int windup = -1;

        StabGoal(PathfinderMob mob, double speed) {
            super(mob, speed, false);
        }

        @Override
        public void stop() {
            super.stop();
            this.windup = -1;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
            if (this.windup >= 0) {
                if (--this.windup == 0) {
                    if (target.isAlive() && this.mob.distanceToSqr(target) <= this.getAttackReachSqr(target) * 1.6D) {
                        this.mob.doHurtTarget(target);
                    }
                    this.windup = -1;
                }
                return;
            }
            if (distToTargetSqr <= this.getAttackReachSqr(target) && this.isTimeToAttack()) {
                this.resetAttackCooldown();
                this.mob.swing(InteractionHand.MAIN_HAND);
                this.windup = HIT_DELAY;
            }
        }
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 1.74F;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.SKELETON_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SKELETON_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.SKELETON_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SKELETON_STEP, 0.15F, 1.0F);
    }
}
