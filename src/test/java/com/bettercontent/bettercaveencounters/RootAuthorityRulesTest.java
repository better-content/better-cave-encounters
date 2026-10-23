package com.bettercontent.bettercaveencounters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RootAuthorityRulesTest {
 private final UUID family=UUID.fromString("00000000-0000-0000-0000-000000000001");
 @Test void onlySameLiveSurgePacketAuthorizes() {assertTrue(RootAuthorityRules.liveOwner(true,family,family,DirectorPolicy.Phase.SURGE,-1,1));}
 @Test void everyOtherPhaseIsRejected() {for(var phase:DirectorPolicy.Phase.values())if(phase!=DirectorPolicy.Phase.SURGE)assertFalse(RootAuthorityRules.liveOwner(true,family,family,phase,-1,1));}
 @Test void replacedOrRetiredEncounterCannotBorrowAuthority() {assertFalse(RootAuthorityRules.liveOwner(false,family,family,DirectorPolicy.Phase.SURGE,-1,1));}
 @Test void wrongFamilyCannotBorrowAuthority() {assertFalse(RootAuthorityRules.liveOwner(true,new UUID(0,2),family,DirectorPolicy.Phase.SURGE,-1,1));}
 @Test void absentFamilyCannotAuthorize() {assertFalse(RootAuthorityRules.liveOwner(true,null,null,DirectorPolicy.Phase.SURGE,-1,1));}
 @Test void telegraphStillActiveCannotAuthorize() {assertFalse(RootAuthorityRules.liveOwner(true,family,family,DirectorPolicy.Phase.SURGE,0,1));}
 @Test void emptyQueueCannotAuthorize() {assertFalse(RootAuthorityRules.liveOwner(true,family,family,DirectorPolicy.Phase.SURGE,-1,0));}
 @Test void originalHeldSlotAtCapIsNotChargedTwice() {assertTrue(RootAuthorityRules.heldSlotFits(9,1,10,1,1,5,5));}
 @Test void overGlobalCapFails() {assertFalse(RootAuthorityRules.heldSlotFits(10,1,10,1,1,5,5));}
 @Test void missingReservationFails() {assertFalse(RootAuthorityRules.heldSlotFits(9,0,10,1,1,5,5));}
 @Test void overTickRateFails() {assertFalse(RootAuthorityRules.heldSlotFits(9,1,10,2,1,5,5));}
 @Test void overSecondRateFails() {assertFalse(RootAuthorityRules.heldSlotFits(9,1,10,1,1,6,5));}
 @Test void corruptNegativePendingCannotMintCapacity() {assertFalse(RootAuthorityRules.heldSlotFits(9,-1,10,1,1,5,5));}
 @Test void largeCountsCannotOverflowIntoAllowedCapacity() {assertFalse(RootAuthorityRules.heldSlotFits(Integer.MAX_VALUE,1,Integer.MAX_VALUE,1,1,5,5));}
}
