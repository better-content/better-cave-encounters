package com.bettercontent.bettercaveencounters;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DirectorLineagesSavedDataTest {
    private static CompoundTag savedFamily() {
        UUID root = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CompoundTag family = new CompoundTag();
        family.putUUID("Id", root);
        family.putUUID("Encounter", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        family.putUUID("EncounterFamily", UUID.fromString("00000000-0000-0000-0000-000000000003"));
        family.putInt("Envelope", 2); family.putInt("MaximumSlimeSize", 0);
        family.putInt("Funded", 2); family.putInt("Remaining", 1);
        CompoundTag allowed = new CompoundTag(); allowed.putInt("fixture:maggot", 2); family.put("Children", allowed);
        CompoundTag counts = new CompoundTag(); counts.putInt("fixture:maggot", 1); family.put("Counts", counts);
        CompoundTag member = new CompoundTag(); member.putUUID("Id", root);
        ListTag members = new ListTag(); members.add(member); family.put("Members", members);
        family.put("DeadMembers", new ListTag());
        ListTag heavies = new ListTag(); heavies.add(member.copy()); family.put("Heavies", heavies);
        return family;
    }
    private static CompoundTag saved(CompoundTag family) {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 1);
        ListTag list = new ListTag(); list.add(family); root.put("Lineages", list); return root;
    }
    @Test void versionedRoundTripKeepsFrozenPolicyCreditsUnloadedMembersAndFamilyHeavyIdentity() {
        CompoundTag root = saved(savedFamily());
        assertEquals(root, DirectorLineages.load(root).save(new CompoundTag()));
    }
    @Test void capacityLimitedLegacyRecordRetainsItsUnfundedLimitWithoutMintingCredit() {
        CompoundTag family = savedFamily(); family.putInt("Funded", 1); family.putInt("Remaining", 0);
        CompoundTag root = saved(family);
        assertEquals(root, DirectorLineages.load(root).save(new CompoundTag()));
    }
    @Test void corpseIdentityAndUnspentCreditsSurviveUnloadWithoutReactivatingAHeavy() {
        CompoundTag family = savedFamily(); family.put("DeadMembers", family.get("Members").copy());
        CompoundTag root = saved(family);
        assertEquals(root, DirectorLineages.load(root).save(new CompoundTag()));
    }
    @Test void corruptVersionAndCreditConservationFailClosed() {
        CompoundTag version = saved(savedFamily()); version.putInt("Version", 2);
        assertThrows(IllegalArgumentException.class, () -> DirectorLineages.load(version));
        CompoundTag family = savedFamily(); family.putInt("Remaining", 2);
        assertThrows(IllegalArgumentException.class, () -> DirectorLineages.load(saved(family)));
    }
}
