package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect real compiled production classes; no reflective target access or world bootstrap. */
class RootAbiTest {
 private static final String PREFIX="com/bettercontent/bettercaveencounters/";
 private ClassNode read(String name) throws Exception {
  try(var in=getClass().getClassLoader().getResourceAsStream(PREFIX+name+".class")) {
   assertNotNull(in,name);var node=new ClassNode();new ClassReader(in).accept(node,0);return node;
  }
 }
 private MethodNode method(ClassNode node,String name) {return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
 private long calls(MethodNode node,String owner,String name) {
  long count=0;for(var instruction:node.instructions)if(instruction instanceof MethodInsnNode call &&call.owner.equals(owner)&&call.name.equals(name))count++;return count;
 }
 @Test void publicInternalBridgeHasOnlyApprovedPublicDescriptorAndOneOriginalCallsite() throws Exception {
  var node=read("CaveNativeRootBridge");assertNotEquals(0,node.access&Opcodes.ACC_PUBLIC);
  var bridge=method(node,"nativeSpawn");assertEquals(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,bridge.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC));
  assertEquals("(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/Entity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Lnet/minecraft/world/entity/Entity;",bridge.desc);
  assertEquals(1,calls(bridge,"com/llamalad7/mixinextras/injector/wrapoperation/Operation","call"));
  for(var m:node.methods)if(!m.name.equals("nativeSpawn"))assertEquals(0,m.access&Opcodes.ACC_PUBLIC,m.name);
 }
 @Test void authorityAndTokensAreNotPublicApis() throws Exception {
  for(String name:List.of("RootAuthority","RootScope","RootReplacementAttempt","DirectorLineages$RootAdmission"))
   assertEquals(0,read(name).access&Opcodes.ACC_PUBLIC,name);
  var frame=read("CaveNativeRootBridge$Frame");assertEquals(0,frame.access&Opcodes.ACC_PUBLIC);
 }
 @Test void externalMergedHandlerIsPrivateStaticAndDelegatesExactlyOnce() throws Exception {
  var node=read("mixin/DamnedInitialRootMixin");var handler=method(node,"betterCave$initialRoot");
  assertEquals(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,handler.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED));
  assertEquals(1,calls(handler,PREFIX+"CaveNativeRootBridge","nativeSpawn"));
  assertEquals(0,calls(handler,"com/llamalad7/mixinextras/injector/wrapoperation/Operation","call"));
  var annotations=new java.util.ArrayList<AnnotationNode>();
  if(handler.visibleAnnotations!=null)annotations.addAll(handler.visibleAnnotations);
  if(handler.invisibleAnnotations!=null)annotations.addAll(handler.invisibleAnnotations);
  var wrap=annotations.stream().filter(a->a.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")).findFirst().orElseThrow();
  for(String key:List.of("require","expect","allow")) {int index=wrap.values.indexOf(key);assertTrue(index>=0);assertEquals(4,wrap.values.get(index+1));}
 }
 @Test void geometryDirectCallsDoNotMoveRotateGroundWriteTerrainOrDrawRandom() throws Exception {
  Set<String> forbidden=Set.of("moveTo","setPos","setXRot","setYRot","setOnGround","setDeltaMovement","setBlock","destroyBlock","nextFloat","nextDouble","nextInt","random");
  for(var pair:List.of(new String[]{"RootReplacementBody","spatialTarget"},new String[]{"RootReplacementBody","finalAdmission"},new String[]{"SpawnLocator","isFloor"},new String[]{"SpawnLocator","visibleToAny"}))
   for(var instruction:method(read(pair[0]),pair[1]).instructions)
    if(instruction instanceof MethodInsnNode call)assertFalse(forbidden.contains(call.name)&&!(pair[1].equals("finalAdmission")&&call.name.equals("setOnGround")),pair[0]+"."+pair[1]+" -> "+call.name);
 }
 @Test void provisionalGeometryHasNoGroundingOrPathAndFinalAdmissionRetainsBothInOrder() throws Exception {
  var body=read("RootReplacementBody");var provisional=method(body,"provisionalGeometry");var end=method(body,"finalAdmission");
  assertEquals(0,calls(provisional,"net/minecraft/world/entity/Mob","setOnGround"));
  assertEquals(0,calls(provisional,"net/minecraft/world/entity/ai/navigation/PathNavigation","createPath"));
  assertEquals(1,calls(provisional,PREFIX+"RootReplacementBody","spatialTarget"));
  assertEquals(1,calls(end,"net/minecraft/world/entity/Mob","setOnGround"));
  assertEquals(1,calls(end,"net/minecraft/world/entity/ai/navigation/PathNavigation","createPath"));
  assertEquals(1,calls(end,"net/minecraft/world/level/pathfinder/Path","canReach"));
  int spatial=-1,ground=-1,path=-1,index=0;
  for(var instruction:end.instructions) {if(instruction instanceof MethodInsnNode call) {
   if(call.name.equals("spatialTarget"))spatial=index;if(call.name.equals("setOnGround"))ground=index;if(call.name.equals("createPath"))path=index;
  }index++;}
  assertTrue(spatial>=0&&ground>spatial&&path>ground);
  var snapshot=read("RootReplacementBody$MemberSnapshot");
  assertEquals(1,calls(method(snapshot,"<init>"),"net/minecraft/world/entity/Mob","onGround"));
  assertEquals(1,calls(method(snapshot,"restore"),"net/minecraft/world/entity/Mob","setOnGround"));
 }
 @Test void explicitCavePositionCheckIsFinalAfterPathNotAnExtraProvisionalCallback() throws Exception {
  var locator=read("SpawnLocator");
  var provisional=locator.methods.stream().filter(m->calls(m,PREFIX+"RootReplacementBody","provisionalGeometry")==1).findFirst().orElseThrow();
  var end=locator.methods.stream().filter(m->calls(m,PREFIX+"RootReplacementBody","finalAdmission")==1).findFirst().orElseThrow();
  assertEquals(0,calls(provisional,PREFIX+"SpawnLocator","eventSpawnPositionAllowed"));
  assertEquals(1,calls(end,PREFIX+"SpawnLocator","eventSpawnPositionAllowed"));
  assertEquals(1,calls(method(locator,"finishSpawn"),PREFIX+"SpawnLocator","eventSpawnPositionAllowed"));
  int admission=-1,position=-1,index=0;
  for(var instruction:end.instructions) {if(instruction instanceof MethodInsnNode call) {
   if(call.name.equals("finalAdmission"))admission=index;if(call.name.equals("eventSpawnPositionAllowed"))position=index;
  }index++;}assertTrue(admission>=0&&position>admission);
 }
 @Test void trueCompletionAndReturnEligibilityPrecedeAnyFinalBodyMutation() throws Exception {
  var commit=method(read("DirectorLineages$RootAdmission"),"commitReplacement");int eligible=-1,geometry=-1,index=0;
  for(var instruction:commit.instructions) {if(instruction instanceof MethodInsnNode call) {
   if(call.name.equals("canCommit")&&eligible<0)eligible=index;
   if(call.owner.equals("java/util/function/Predicate")&&call.name.equals("test"))geometry=index;
  }index++;}assertTrue(eligible>=0&&geometry>eligible);
 }
 @Test void preliminaryVetoIsObservedWithoutChangingTheOrdinaryCanceledPath() throws Exception {
  var listener=method(read("DirectorLineages"),"offspring");var annotation=listener.visibleAnnotations.stream()
      .filter(a->a.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")).findFirst().orElseThrow();
  int at=annotation.values.indexOf("receiveCanceled");assertTrue(at>=0);assertEquals(Boolean.TRUE,annotation.values.get(at+1));
  int bridge=-1,canceled=-1,index=0;
  for(var instruction:listener.instructions) {if(instruction instanceof MethodInsnNode call) {
    if(call.owner.equals(PREFIX+"CaveNativeRootBridge")&&call.name.equals("admit"))bridge=index;
    if(call.name.equals("isCanceled"))canceled=index;
   }index++;}
  assertTrue(bridge>=0&&canceled>bridge,"Exact root scope observes veto; ordinary path still returns canceled");
 }
 @Test void frozenAccountIsPaidBeforeNativeSurvivorBookkeepingCanFail() throws Exception {
  var freeze=read("DirectorLineages$RootAdmission").methods.stream().filter(m->m.name.startsWith("lambda$close$")
      &&calls(m,PREFIX+"DirectorRuntime$RootCharge","commit")==1).findFirst().orElseThrow();
  int commit=-1,register=-1,index=0;
  for(var instruction:freeze.instructions) {if(instruction instanceof MethodInsnNode call) {
    if(call.owner.equals(PREFIX+"DirectorRuntime$RootCharge")&&call.name.equals("commit"))commit=index;
    if(call.owner.equals(PREFIX+"DirectorRuntime")&&call.name.equals("registerMob"))register=index;
   }index++;}
  assertTrue(commit>=0&&register>commit,"Retain payment before any surviving-native-body registration");
 }
 @Test void noSecondAdmissionOrDescendantChargeInsideReplacementProtocol() throws Exception {
  for(String name:List.of("admitReplacement","commitReplacement"))
   for(var instruction:method(read("DirectorLineages$RootAdmission"),name).instructions)
    if(instruction instanceof MethodInsnNode call)assertFalse(Set.of("reserveAdmission","reserveRootCost","descendantAdded").contains(call.name),name+" -> "+call.name);
 }
}
