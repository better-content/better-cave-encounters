package com.bettercontent.bettercaveencounters;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Version1 encoder/decoder, ordinary NBT only: no world, Mob construction or registry bootstrap. */
class DirectorLineagePersistenceTest {
    private static final UUID ORIGINAL=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTUAL=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENCOUNTER=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FAMILY=UUID.fromString("00000000-0000-0000-0000-000000000004");
    @Test void pendingRootAndCandidateNeverPersist() {
        var data=new DirectorLineages();
        var family=data.beginFamily(ORIGINAL,ENCOUNTER,FAMILY,GeologicalDefinition.Offspring.NONE,true);
        assertTrue(data.save(new CompoundTag()).getList("Lineages",Tag.TAG_COMPOUND).isEmpty());
        family.replaceInitialRoot(ACTUAL,true);
        assertTrue(data.save(new CompoundTag()).getList("Lineages",Tag.TAG_COMPOUND).isEmpty());
    }
    @Test void versionOnePreservesStableFamilyAndActualRootMembership() {
        var data=new DirectorLineages();
        var family=data.beginFamily(ORIGINAL,ENCOUNTER,FAMILY,GeologicalDefinition.Offspring.NONE,true);
        family.replaceInitialRoot(ACTUAL,true); family.pending=false;
        CompoundTag saved=data.save(new CompoundTag());
        assertEquals(1,saved.getInt("Version"));
        CompoundTag entry=saved.getList("Lineages",Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(ORIGINAL,entry.getUUID("Id")); assertEquals(ENCOUNTER,entry.getUUID("Encounter"));
        assertEquals(FAMILY,entry.getUUID("EncounterFamily"));
        assertEquals(ACTUAL,entry.getList("Members",Tag.TAG_COMPOUND).getCompound(0).getUUID("Id"));
        assertEquals(1,entry.getList("Members",Tag.TAG_COMPOUND).size());
        assertEquals(ACTUAL,entry.getList("Heavies",Tag.TAG_COMPOUND).getCompound(0).getUUID("Id"));
        assertEquals(0,entry.getInt("Funded")); assertEquals(0,entry.getInt("Remaining"));
        assertEquals(saved,DirectorLineages.load(saved).save(new CompoundTag()));
    }
    @Test void actualCorpseSurvivesRoundTripWithoutRecreatingOriginal() {
        var data=new DirectorLineages();
        var family=data.beginFamily(ORIGINAL,ENCOUNTER,FAMILY,GeologicalDefinition.Offspring.NONE,false);
        family.replaceInitialRoot(ACTUAL,false); family.dead.add(ACTUAL); family.pending=false;
        CompoundTag saved=data.save(new CompoundTag());
        var restored=DirectorLineages.load(saved).save(new CompoundTag());
        assertEquals(saved,restored);
        var entry=restored.getList("Lineages",Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(ACTUAL,entry.getList("DeadMembers",Tag.TAG_COMPOUND).getCompound(0).getUUID("Id"));
        assertTrue(entry.getList("Heavies",Tag.TAG_COMPOUND).isEmpty());
    }
    @Test void transferRemovesOriginalHeavyAndDeadAliases() {
        var data=new DirectorLineages();
        var family=data.beginFamily(ORIGINAL,ENCOUNTER,FAMILY,GeologicalDefinition.Offspring.NONE,true);
        family.dead.add(ORIGINAL); family.replaceInitialRoot(ACTUAL,false);
        assertEquals(java.util.Set.of(ACTUAL),family.members);
        assertTrue(family.heavies.isEmpty()); assertTrue(family.dead.isEmpty());
        assertEquals(ORIGINAL,family.id);
    }
    @Test void committedFamilyCannotBeReplacedAgain() {
        var data=new DirectorLineages();
        var family=data.beginFamily(ORIGINAL,ENCOUNTER,FAMILY,GeologicalDefinition.Offspring.NONE,false);
        family.pending=false;
        assertThrows(IllegalStateException.class,()->family.replaceInitialRoot(ACTUAL,false));
    }
    @Test void replacingWithOriginalIsRejected() {
        var data=new DirectorLineages();
        var family=data.beginFamily(ORIGINAL,ENCOUNTER,FAMILY,GeologicalDefinition.Offspring.NONE,false);
        assertThrows(IllegalStateException.class,()->family.replaceInitialRoot(ORIGINAL,false));
    }
}
