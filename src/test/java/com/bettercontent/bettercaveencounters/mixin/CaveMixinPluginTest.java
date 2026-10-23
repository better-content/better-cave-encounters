package com.bettercontent.bettercaveencounters.mixin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CaveMixinPluginTest {
 @Test void absentNativeTargetIsOptional() {assertFalse(CaveMixinPlugin.selected(null));}
 @Test void exactApprovedNativeVersionIsSelected() {assertTrue(CaveMixinPlugin.selected("1.98.1"));}
 @Test void differentNativeVersionRequiresActualAbiRequalification() {assertThrows(IllegalStateException.class,()->CaveMixinPlugin.selected("1.98.2"));}
 @Test void emptyInventoryVersionDoesNotSilentlyEnable() {assertThrows(IllegalStateException.class,()->CaveMixinPlugin.selected(""));}
}
