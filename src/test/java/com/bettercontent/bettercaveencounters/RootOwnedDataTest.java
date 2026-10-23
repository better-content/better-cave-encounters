package com.bettercontent.bettercaveencounters;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RootOwnedDataTest {
 @Test void restoresExactlyTypedOwnKeysAndKeepsUnrelatedChanges() {
  var data=new CompoundTag();data.putInt("DepthDirectorSpawned",7);data.putString("DepthDirectorLineage","previous");
  data.putInt("OtherModCounter",1);var before=data.copy();var snapshot=new RootReplacementBody.OwnedDataSnapshot(data);
  data.putBoolean("DepthDirectorSpawned",true);data.putString("DepthDirectorTarget","new");data.remove("DepthDirectorLineage");
  data.putString("DepthDirectorSpawnHabitat","new");data.putInt("OtherModCounter",2);data.putInt("OtherNewCounter",3);
  snapshot.restore(data);assertEquals(before.get("DepthDirectorSpawned"),data.get("DepthDirectorSpawned"));
  assertEquals(before.get("DepthDirectorLineage"),data.get("DepthDirectorLineage"));assertFalse(data.contains("DepthDirectorTarget"));
  assertFalse(data.contains("DepthDirectorSpawnHabitat"));assertEquals(2,data.getInt("OtherModCounter"));assertEquals(3,data.getInt("OtherNewCounter"));
 }
 @Test void restoresCopiesNotSharedMutableNbt() {
  var data=new CompoundTag();var value=new CompoundTag();value.putInt("prior",1);data.put("DepthDirectorTarget",value);
  var snapshot=new RootReplacementBody.OwnedDataSnapshot(data);value.putInt("prior",2);snapshot.restore(data);
  assertEquals(1,data.getCompound("DepthDirectorTarget").getInt("prior"));data.getCompound("DepthDirectorTarget").putInt("prior",3);
  snapshot.restore(data);assertEquals(1,data.getCompound("DepthDirectorTarget").getInt("prior"));
 }
 @Test void emptyProvenanceIsRemovedWithoutInventingKeys() {
  var data=new CompoundTag();var snapshot=new RootReplacementBody.OwnedDataSnapshot(data);
  data.putBoolean("DepthDirectorSpawned",true);snapshot.restore(data);assertTrue(data.isEmpty());
 }
}
