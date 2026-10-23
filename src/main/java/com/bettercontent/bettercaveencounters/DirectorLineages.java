package com.bettercontent.bettercaveencounters;

import com.bettercontent.bettercompatfixes.api.event.MobOffspringAdmissionEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Durable finite credits. Encounter retirement, unload and reload never mint new credits. */
public final class DirectorLineages extends SavedData {
    private static final String NAME = "better_cave_encounters_lineages";
    private static final String LINEAGE = "DepthDirectorLineage";
    private static final String PARENT = "DepthDirectorParent";
    private static final UUID NONE = new UUID(0, 0);
    private static final ThreadLocal<Owner> OWNER = new ThreadLocal<>();
    private final Map<UUID, Family> lineages = new LinkedHashMap<>();
    private int pendingRoots;
    private int pendingChildren;
    private String lastFailure = "none";

    static DirectorLineages get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DirectorLineages::load, DirectorLineages::new, NAME);
    }

    static <T> T ownedBy(UUID encounter, UUID family, Supplier<T> action) {
        Owner previous = OWNER.get();
        OWNER.set(new Owner(encounter, family));
        try { return action.get(); }
        finally { if (previous == null) OWNER.remove(); else OWNER.set(previous); }
    }

    static int reservation(ResourceLocation entity) { return GeologicalRegistry.INSTANCE.offspring(entity).credits(); }

    static GeologicalDefinition.Offspring policy(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        GeologicalDefinition.Offspring declared = GeologicalRegistry.INSTANCE.offspring(id);
        if (!declared.equals(GeologicalDefinition.Offspring.NONE) || !(mob instanceof Slime)) return declared;
        // Preserve the bounded native fallback for other ordinary Slime subclasses.
        return new GeologicalDefinition.Offspring(GeologyWeights.slimeEnvelope(4), 4, Map.of(id, 20));
    }

    static RootAuthority rootAuthority() {
        Owner owner = OWNER.get();
        return DirectorRuntime.INSTANCE.rootAuthority(owner == null ? null : owner.encounter(), owner == null ? null : owner.family());
    }

    static RootAdmission beginRoot(Mob root, EcologyDefinition.Role role, GeologicalDefinition.Offspring policy, int baseCost) {
        return beginRoot(root, role, policy, baseCost, rootAuthority());
    }

    static RootAdmission beginRoot(Mob root, EcologyDefinition.Role role, GeologicalDefinition.Offspring policy,
                                   int baseCost, RootAuthority authority) {
        ServerLevel level = (ServerLevel) root.level();
        DirectorLineages data = get(level.getServer());
        Owner owner = OWNER.get();
        DirectorRuntime.RootCharge charge = authority.reserve(baseCost + policy.credits());
        if (charge == null) return null;
        Family lineage = data.beginFamily(root.getUUID(), owner == null ? NONE : owner.encounter(),
                owner == null ? root.getUUID() : owner.family(), policy, role == EcologyDefinition.Role.HEAVY);
        mark(root, lineage.id);
        data.setDirty();
        return new RootAdmission(data, lineage, root, charge, authority, role == EcologyDefinition.Role.HEAVY);
    }

    /** Pure membership initialization; the charge is already held by beginRoot. No new authority. */
    Family beginFamily(UUID root, UUID encounter, UUID encounterFamily, GeologicalDefinition.Offspring policy, boolean heavy) {
        Family lineage = new Family(root, encounter, encounterFamily, policy, policy.credits());
        lineage.members.add(root);
        if (heavy) lineage.heavies.add(root);
        lineages.put(lineage.id, lineage);
        pendingRoots++;
        return lineage;
    }

    static void observe(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !SpawnLocator.isDirectorMob(mob)) return;
        DirectorLineages data = get(level.getServer());
        Family lineage = data.find(mob);
        if (lineage == null && DirectorRuntime.INSTANCE.restored()) lineage = data.adoptLegacy(mob);
        if (lineage != null) {
            lineage.members.add(mob.getUUID());
            if (mob.getHealth() <= 0) lineage.dead.add(mob.getUUID()); else lineage.dead.remove(mob.getUUID());
            data.setDirty();
        }
    }

    static UUID encounterOf(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return null;
        Family family = get(level.getServer()).find(mob);
        return family == null || family.encounter.equals(NONE) ? null : family.encounter;
    }

    static boolean hasHeavy(MinecraftServer server, UUID encounterFamily) {
        return get(server).lineages.values().stream().filter(f -> f.encounterFamily.equals(encounterFamily))
                .anyMatch(f -> f.heavies.stream().anyMatch(id -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        Entity entity = level.getEntity(id);
                        if (entity != null) return entity.isAlive();
                    }
                    return !f.dead.contains(id); // Unloaded is not dead; a saved corpse is.
                }));
    }

    static int pending(MinecraftServer server) {
        DirectorLineages data = get(server);
        return data.pendingRoots + data.pendingChildren;
    }

    static void died(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !SpawnLocator.isDirectorMob(mob)) return;
        DirectorLineages data = get(level.getServer());
        Family family = data.find(mob);
        // Keep corpse provenance. Loaded liveness remains authoritative if another mod cancels death.
        if (family != null) { family.dead.add(mob.getUUID()); data.setDirty(); }
    }

    static void removed(Entity entity) {
        if (!(entity instanceof Mob mob) || !SpawnLocator.isDirectorMob(mob)
                || !(entity.level() instanceof ServerLevel level) || entity.getRemovalReason() == null) return;
        DirectorLineages data = get(level.getServer());
        Family family = data.find(entity);
        if (family == null) return;
        if (mob.getHealth() <= 0) family.dead.add(entity.getUUID()); else family.dead.remove(entity.getUUID());
        if (!entity.getRemovalReason().shouldDestroy() || entity.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
            data.setDirty(); return;
        }
        family.members.remove(entity.getUUID());
        family.heavies.remove(entity.getUUID());
        family.dead.remove(entity.getUUID());
        if (!family.pending && family.members.isEmpty()) data.lineages.remove(family.id);
        data.setDirty();
    }

    static String inspect(MinecraftServer server) {
        DirectorLineages data = get(server);
        int credits = data.lineages.values().stream().mapToInt(f -> f.credits.remaining()).sum();
        int used = data.lineages.values().stream().mapToInt(f -> f.credits.funded() - f.credits.remaining()).sum();
        return " lineage_families=" + data.lineages.size() + " lineage_remaining=" + credits
                + " lineage_used=" + used + " lineage_capacity_limited="
                + data.lineages.values().stream().filter(f -> f.credits.funded() < f.policy.credits()).count()
                + " lineage_failure=" + data.lastFailure;
    }

    @SubscribeEvent(receiveCanceled = true) public static void offspring(MobOffspringAdmissionEvent event) {
        if (CaveNativeRootBridge.admit(event)) return;
        if (event.isCanceled() || !(event.parent() instanceof Mob parent) || !SpawnLocator.isDirectorMob(parent)
                || !(parent.level() instanceof ServerLevel level)) return;
        DirectorLineages data = get(level.getServer());
        Family family = data.find(parent);
        if (family == null) family = data.adoptLegacy(parent);
        if (family.rollingBack) { event.setCanceled(true); return; }
        ResourceLocation childType = ForgeRegistries.ENTITY_TYPES.getKey(event.child().getType());
        int maximum = family.policy.children().getOrDefault(childType, 0);
        if (maximum == 0 || event.child().getType().is(SpawnLocator.DENIED)) {
            data.lastFailure = "unsupported_child:" + childType;
            DepthDirectorMod.LOGGER.error("Unsupported Director offspring {} from {}; insertion denied", childType,
                    ForgeRegistries.ENTITY_TYPES.getKey(parent.getType()));
            event.setCanceled(true);
            return;
        }
        if (data.lineages.values().stream().anyMatch(f -> f.members.contains(event.child().getUUID()))) {
            event.setCanceled(true);
            return;
        }
        if (!family.credits.canReserve(childType) || !DirectorRuntime.INSTANCE.reserveAdmission(level.getServer())) {
            data.lastFailure = "offspring_capacity:" + childType;
            event.setCanceled(true);
            return;
        }
        Family admitted = family;
        Mob child = event.child();
        CompoundTag previous = child.getPersistentData().copy();
        Set<String> previousTags = Set.copyOf(child.getTags());
        data.pendingChildren++;
        family.credits.reserve(childType);
        mark(child, family.id);
        child.getPersistentData().putUUID(PARENT, parent.getUUID());
        if (parent.getPersistentData().hasUUID(SpawnLocator.TARGET_NBT))
            child.getPersistentData().putUUID(SpawnLocator.TARGET_NBT, parent.getPersistentData().getUUID(SpawnLocator.TARGET_NBT));
        data.setDirty();
        event.onCompletion(inserted -> {
            data.pendingChildren--;
            if (inserted) {
                admitted.members.add(child.getUUID());
                DirectorRuntime.INSTANCE.descendantAdded(child, admitted.encounter);
                SpawnLocator.restoreDirectorMob(child);
                data.lastFailure = "none";
            } else {
                admitted.credits.rollback(childType);
                admitted.members.remove(child.getUUID());
                admitted.dead.remove(child.getUUID());
                admitted.heavies.remove(child.getUUID());
                DirectorRuntime.INSTANCE.removeMob(child.getUUID());
                child.getPersistentData().getAllKeys().stream().filter(k -> !previous.contains(k)).toList()
                        .forEach(child.getPersistentData()::remove);
                child.getPersistentData().merge(previous);
                if (!previousTags.contains(SpawnLocator.PROVENANCE_TAG)) child.removeTag(SpawnLocator.PROVENANCE_TAG);
            }
            data.setDirty();
        });
    }

    private Family adoptLegacy(Mob mob) {
        Family existing = find(mob);
        if (existing != null) return existing;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        GeologicalDefinition.Offspring policy = policy(mob);
        DirectorRuntime.LegacyOwner owner = DirectorRuntime.INSTANCE.reserveLegacy(mob, policy.credits());
        Family family = new Family(mob.getUUID(), owner.encounter(), owner.family(), policy, owner.credits());
        family.pending = false;
        family.members.add(mob.getUUID());
        if (owner.heavy()) family.heavies.add(mob.getUUID());
        if (mob.getHealth() <= 0) family.dead.add(mob.getUUID());
        lineages.put(family.id, family);
        mark(mob, family.id);
        if (owner.credits() < policy.credits()) {
            lastFailure = "capacity_limited_legacy:" + type;
            DepthDirectorMod.LOGGER.warn("Legacy Director lineage {} funded {}/{} credits", mob.getUUID(), owner.credits(), policy.credits());
        }
        setDirty();
        return family;
    }

    private Family find(Entity member) {
        return member.getPersistentData().hasUUID(LINEAGE) ? lineages.get(member.getPersistentData().getUUID(LINEAGE)) : null;
    }

    private static void mark(Mob mob, UUID family) {
        mob.addTag(SpawnLocator.PROVENANCE_TAG);
        mob.getPersistentData().putBoolean(SpawnLocator.PROVENANCE_NBT, true);
        mob.getPersistentData().putUUID(LINEAGE, family);
    }

    public static DirectorLineages load(CompoundTag root) {
        DirectorLineages data = new DirectorLineages();
        if (!root.isEmpty() && root.getInt("Version") != 1)
            throw new IllegalArgumentException("Unsupported Director lineage save version");
        for (Tag element : root.getList("Lineages", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) element;
            Map<ResourceLocation, Integer> allowed = new LinkedHashMap<>();
            CompoundTag children = t.getCompound("Children");
            children.getAllKeys().forEach(k -> allowed.put(new ResourceLocation(k), children.getInt(k)));
            GeologicalDefinition.Offspring policy = new GeologicalDefinition.Offspring(t.getInt("Envelope"),
                    t.getInt("MaximumSlimeSize"), allowed);
            int funded = t.getInt("Funded");
            if (funded > policy.credits()) throw new IllegalArgumentException("Overfunded lineage");
            Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
            CompoundTag savedCounts = t.getCompound("Counts");
            savedCounts.getAllKeys().forEach(k -> counts.put(new ResourceLocation(k), savedCounts.getInt(k)));
            LineageCredits<ResourceLocation> account = LineageCredits.restore(policy.children(), funded, t.getInt("Remaining"), counts);
            Family family = new Family(t.getUUID("Id"), t.getUUID("Encounter"), t.getUUID("EncounterFamily"), policy, account);
            family.pending = false;
            for (Tag m : t.getList("Members", Tag.TAG_COMPOUND)) family.members.add(((CompoundTag) m).getUUID("Id"));
            for (Tag m : t.getList("DeadMembers", Tag.TAG_COMPOUND)) {
                UUID id = ((CompoundTag) m).getUUID("Id");
                if (family.members.contains(id)) family.dead.add(id);
            }
            for (Tag m : t.getList("Heavies", Tag.TAG_COMPOUND)) {
                UUID heavy = ((CompoundTag) m).getUUID("Id");
                if (family.members.contains(heavy)) family.heavies.add(heavy);
            }
            if (data.lineages.putIfAbsent(family.id, family) != null) throw new IllegalArgumentException("Duplicate lineage id");
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        root.putInt("Version", 1);
        ListTag list = new ListTag();
        lineages.values().forEach(f -> {
            if (f.pending) return; // In-flight admissions cannot survive outside their server-thread call.
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", f.id); t.putUUID("Encounter", f.encounter); t.putUUID("EncounterFamily", f.encounterFamily);
            t.putInt("Envelope", f.policy.credits()); t.putInt("MaximumSlimeSize", f.policy.maximumSlimeSize());
            t.putInt("Funded", f.credits.funded()); t.putInt("Remaining", f.credits.remaining());
            CompoundTag children = new CompoundTag(); f.policy.children().forEach((id, n) -> children.putInt(id.toString(), n)); t.put("Children", children);
            CompoundTag counts = new CompoundTag(); f.credits.counts().forEach((id, n) -> counts.putInt(id.toString(), n)); t.put("Counts", counts);
            t.put("Members", uuids(f.members)); t.put("DeadMembers", uuids(f.dead));
            t.put("Heavies", uuids(f.heavies)); list.add(t);
        });
        root.put("Lineages", list);
        return root;
    }

    private static ListTag uuids(Set<UUID> ids) {
        ListTag list = new ListTag(); ids.stream().sorted().forEach(id -> {
            CompoundTag t = new CompoundTag(); t.putUUID("Id", id); list.add(t);
        }); return list;
    }

    private record Owner(UUID encounter, UUID family) {}
    static final class Family {
        final UUID id, encounter, encounterFamily;
        final GeologicalDefinition.Offspring policy;
        final LineageCredits<ResourceLocation> credits;
        boolean pending = true, rollingBack;
        final Set<UUID> members = new HashSet<>(), heavies = new HashSet<>(), dead = new HashSet<>();
        void replaceInitialRoot(UUID actual, boolean heavy) {
            if (!pending || actual.equals(id)) throw new IllegalStateException("Not a pending distinct root replacement");
            members.remove(id); heavies.remove(id); dead.remove(id);
            members.add(actual);
            if (heavy) heavies.add(actual);
        }
        Family(UUID id, UUID encounter, UUID encounterFamily, GeologicalDefinition.Offspring policy, int funded) {
            this(id, encounter, encounterFamily, policy, new LineageCredits<>(policy.children(), funded));
        }
        Family(UUID id, UUID encounter, UUID encounterFamily, GeologicalDefinition.Offspring policy, LineageCredits<ResourceLocation> credits) {
            this.id = id; this.encounter = encounter; this.encounterFamily = encounterFamily;
            this.policy = policy; this.credits = credits;
        }
    }

    static final class RootAdmission implements AutoCloseable {
        private final DirectorLineages data;
        private final Family family;
        private final Mob root;
        private final DirectorRuntime.RootCharge charge;
        private final RootAuthority authority;
        private final boolean heavy;
        private final RootReplacementAttempt<Mob> replacement;
        private Predicate<Mob> beforeInsert = mob -> false, afterInsert = mob -> false;
        private Consumer<Mob> initialize = mob -> {};
        private RootReplacementBody.MemberSnapshot snapshot;
        private Entity nativeProduct;
        private EntityType<?> expectedNativeType;
        private boolean committed, closed;
        RootAdmission(DirectorLineages data, Family family, Mob root, DirectorRuntime.RootCharge charge,
                      RootAuthority authority, boolean heavy) {
            this.data = data; this.family = family; this.root = root; this.charge = charge;
            this.authority = authority; this.heavy = heavy; replacement = new RootReplacementAttempt<>(root);
        }
        void prepareReplacement(Predicate<Mob> beforeInsert, Predicate<Mob> afterInsert, Consumer<Mob> initialize) {
            this.beforeInsert = beforeInsert; this.afterInsert = afterInsert; this.initialize = initialize;
        }
        boolean allowsNativeCall(EntityType<?> type, ServerLevel level, BlockPos position, MobSpawnType reason, Entity parent) {
            ResourceLocation rootType = ForgeRegistries.ENTITY_TYPES.getKey(root.getType());
            ResourceLocation variant = ForgeRegistries.ENTITY_TYPES.getKey(type);
            return !closed && !committed && parent == root && root.level() == level && !root.isRemoved()
                    && reason == MobSpawnType.MOB_SUMMONED && position.equals(root.blockPosition())
                    && rootType != null && RootReplacementAttempt.DAMNED.equals(rootType.toString())
                    && variant != null && RootReplacementAttempt.VARIANTS.contains(variant.toString())
                    && authority.allowsInitialReplacement() && authority.stillValid();
        }
        void beginNative(EntityType<?> type) {
            replacement.beginNative(ForgeRegistries.ENTITY_TYPES.getKey(type).toString());
            expectedNativeType = type;
        }
        void nativeReturned(Entity value) {
            nativeProduct = value;
            replacement.returned(value instanceof Mob mob ? mob : null);
        }
        boolean provisional(Mob mob) { return !closed && !committed && replacement.candidate() == mob; }
        boolean admitReplacement(MobOffspringAdmissionEvent event) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(event.child().getType());
            if (id == null || event.child().getType() != expectedNativeType
                    || !replacement.matches(event.parent() instanceof Mob parent ? parent : null, id.toString())) return false;
            Mob child = event.child();
            replacement.claim(child);
            event.onCompletion(inserted -> replacement.complete(child, inserted));
            if (event.isCanceled()) return true; // Never undo an earlier veto or modify its candidate.
            if (child.level() != root.level() || !child.isAlive() || child.isRemoved() || child.isSpawnCancelled()
                    || child.getType().is(SpawnLocator.DENIED) || !authority.stillValid()
                    || !DirectorRuntime.INSTANCE.reservedRootSlotStillFits(((ServerLevel) root.level()).getServer())) {
                event.setCanceled(true); return true;
            }
            snapshot = new RootReplacementBody.MemberSnapshot(child);
            mark(child, family.id);
            initialize.accept(child);
            if (!beforeInsert.test(child)) event.setCanceled(true);
            return true;
        }
        boolean replacementObserved() { return replacement.observed(); }
        Mob commitReplacement() {
            Mob actual = replacement.candidate();
            boolean discarded = root.getRemovalReason() == Entity.RemovalReason.DISCARDED;
            // Nonnull native returns can follow vetoed insertion. They must never reach final initialization.
            if (!replacement.canCommit(discarded, true)) return null;
            boolean valid = actual != null && actual.isAlive() && !actual.isRemoved() && !actual.isSpawnCancelled()
                    && validFinalizedSize() && actual.level() == root.level() && authority.stillValid()
                    && DirectorRuntime.INSTANCE.reservedRootSlotStillFits(((ServerLevel) root.level()).getServer())
                    && afterInsert.test(actual)
                    && actual.isAlive() && !actual.isRemoved() && !actual.isSpawnCancelled()
                    && actual.level() == root.level() && authority.stillValid()
                    && DirectorRuntime.INSTANCE.reservedRootSlotStillFits(((ServerLevel) root.level()).getServer());
            if (!replacement.canCommit(discarded, valid)) return null;
            replacement.commit(discarded, valid);
            family.replaceInitialRoot(actual.getUUID(), heavy);
            commit();
            return actual;
        }
        boolean validFinalizedSize() {
            return !(root instanceof Slime slime) || (family.policy.maximumSlimeSize() > 0
                    && slime.getSize() <= family.policy.maximumSlimeSize()
                    && GeologyWeights.slimeEnvelope(slime.getSize()) <= family.policy.credits());
        }
        void commit() { charge.commit(); committed = true; family.pending = false; data.setDirty(); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            data.pendingRoots--;
            replacement.close();
            if (committed) { charge.close(); return; }
            family.rollingBack = true;
            List<Entity> rollback = new ArrayList<>(); rollback.add(root);
            if (replacement.candidate() != null) rollback.add(replacement.candidate());
            if (nativeProduct != null && !rollback.contains(nativeProduct)) rollback.add(nativeProduct);
            for (UUID id : List.copyOf(family.members))
                for (ServerLevel level : ((ServerLevel) root.level()).getServer().getAllLevels()) {
                    Entity child = level.getEntity(id);
                    if (child != null && child != root && !rollback.contains(child)) rollback.add(child);
                }
            RootRollback.removeAll(rollback, Entity::isRemoved, Entity::discard, failure -> {
                // Never refund pressure while a broken native remover may have left live
                // children. Keep the paid frozen account and surface a qualification failure.
                family.pending = false; family.rollingBack = false; charge.commit(); charge.close();
                data.lastFailure = "native_rollback_failure"; data.setDirty();
                DepthDirectorMod.LOGGER.error("Director root rollback could not remove every native entity", failure);
                if (replacement.observed()) {
                    // Keep paid pressure even for a returned body with a vetoed/unobserved callback.
                    // This remains a fatal failure, never permission for another native spawn.
                    if (root.isRemoved()) {
                        family.members.remove(root.getUUID());
                        family.heavies.remove(root.getUUID());
                        family.dead.remove(root.getUUID());
                    }
                    for (Entity entity : rollback) if (entity instanceof Mob survivor && !survivor.isRemoved()) {
                        mark(survivor, family.id);
                        family.members.add(survivor.getUUID());
                        if (heavy) family.heavies.add(survivor.getUUID());
                        DirectorRuntime.INSTANCE.registerMob(survivor);
                    }
                }
            });
            for (Entity entity : rollback) DirectorRuntime.INSTANCE.removeMob(entity.getUUID());
            data.lineages.remove(family.id);
            root.getPersistentData().remove(LINEAGE);
            root.getPersistentData().remove(SpawnLocator.PROVENANCE_NBT);
            root.removeTag(SpawnLocator.PROVENANCE_TAG);
            data.setDirty();
            if (snapshot != null) RootRollback.restoreAndRefund(snapshot::restore, charge);
            else charge.close();
        }
    }
}
