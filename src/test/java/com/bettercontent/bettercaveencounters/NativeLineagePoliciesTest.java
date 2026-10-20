package com.bettercontent.bettercaveencounters;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLineagePoliciesTest {
    @Test void unmappedFallbackStillPrepaysExistingCorpseAndSlimeActors() {
        assertEquals(2, NativeLineagePolicies.get(new ResourceLocation("born_in_chaos_v1:door_knight")).credits());
        assertEquals(4, NativeLineagePolicies.get(new ResourceLocation("born_in_chaos_v1:zombie_bruiser")).credits());
        assertEquals(20, NativeLineagePolicies.get(new ResourceLocation("tconstruct:ender_slime")).credits());
        assertEquals(20, NativeLineagePolicies.get(new ResourceLocation("goety:crypt_slime")).credits());
        assertEquals(GeologicalDefinition.Offspring.NONE, NativeLineagePolicies.get(new ResourceLocation("absent_optional_mod:unknown")));
    }
    @Test void dreadAllowanceIsOneSharedFiniteAccountAcrossAllDeclaredNativeConversionTypes() {
        var policy = NativeLineagePolicies.get(new ResourceLocation("iceandfire:dread_thrall"));
        assertEquals(4, policy.credits());
        assertEquals(5, policy.children().size());
        LineageCredits<ResourceLocation> account = new LineageCredits<>(policy.children(), policy.credits());
        int admitted = 0;
        for (ResourceLocation child : policy.children().keySet()) if (account.reserve(child)) admitted++;
        assertEquals(4, admitted);
        assertEquals(0, account.remaining());
    }
}
