package com.bettercontent.bettercaveencounters;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Original archive bytes and actual AP output only; never loads/initializes a vendor entity. */
class NativeInitialRootAbiTest {
 private static final String PROCEDURE="net/mcreator/thedeepvoid/procedures/DamnedOnInitialEntitySpawnProcedure";
 private static final String DESC="(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;";
 private static Path input(String property) {String value=System.getProperty(property);assertNotNull(value,"Missing authoritative input "+property);return Path.of(value);}
 private ClassNode read(String resource) throws Exception {
  try(var archive=new ZipFile(input("bc.deepVoidOriginalArchive").toFile())) {
   var entry=archive.getEntry(resource+".class");assertNotNull(entry,resource);
   var node=new ClassNode();new ClassReader(archive.getInputStream(entry)).accept(node,0);return node;
  }
 }
 @Test void exactOriginalArchiveNotAnUnreviewedNativeProcedureVersion() throws Exception {
  byte[] bytes=Files.readAllBytes(input("bc.deepVoidOriginalArchive"));
  assertEquals("faa95b5af7eaf398f109c3abab054a0036a18022",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
 }
 @Test void originalProcedureHasFourSequentialExclusiveNativeReplacementBranches() throws Exception {
  var node=read(PROCEDURE);var method=node.methods.stream().filter(m->m.name.equals("execute")
      &&m.desc.equals("(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V")).findFirst().orElseThrow();
  List<Integer> random=new ArrayList<>(),spawn=new ArrayList<>(),discard=new ArrayList<>();List<String> variants=new ArrayList<>();
  List<LabelNode> exits=new ArrayList<>();boolean guarded=false;int index=0;
  for(var instruction:method.instructions) {
   if(instruction instanceof TypeInsnNode type &&type.getOpcode()==Opcodes.INSTANCEOF&&type.desc.equals("net/mcreator/thedeepvoid/entity/DamnedEntity"))guarded=true;
   if(instruction instanceof FieldInsnNode field &&field.owner.equals("net/mcreator/thedeepvoid/init/TheDeepVoidModEntities")
       &&List.of("CROSS_EYES","BIG_EYE","MULTIPLE_EYES","FOUR_EYES").contains(field.name))variants.add(field.name);
   if(instruction instanceof MethodInsnNode call) {
    if(call.owner.equals("java/lang/Math")&&call.name.equals("random"))random.add(index);
    if(call.owner.equals("net/minecraft/world/entity/EntityType")&&call.name.equals("m_262496_")) {assertEquals(DESC,call.desc);spawn.add(index);}
    if(call.owner.equals("net/minecraft/world/entity/Entity")&&call.name.equals("m_146870_")) {
     discard.add(index);var next=instruction.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
     assertTrue(next instanceof JumpInsnNode&&next.getOpcode()==Opcodes.GOTO,"Native conversion must leave, never fall through to another branch");exits.add(((JumpInsnNode)next).label);
    }
   }
   index++;
  }
  assertTrue(guarded);assertEquals(4,random.size());assertEquals(4,spawn.size());assertEquals(4,discard.size());
  assertEquals(List.of("CROSS_EYES","BIG_EYE","MULTIPLE_EYES","FOUR_EYES"),variants);
  for(int i=0;i<4;i++) {assertTrue(random.get(i)<spawn.get(i)&&spawn.get(i)<discard.get(i));assertSame(exits.get(0),exits.get(i));if(i<3)assertTrue(discard.get(i)<random.get(i+1));}
 }
 @Test void fourNativeVariantClassesAreNotDamnedSubclasses() throws Exception {
  for(String variant:List.of("CrossEyesEntity","BigEyeEntity","MultipleEyesEntity","FourEyesEntity")) {
   var node=read("net/mcreator/thedeepvoid/entity/"+variant);assertEquals("net/minecraft/world/entity/monster/Monster",node.superName);
  }
 }
 @Test void originalAndAllFourBodiesInheritNativeNavigationAndPathfindingCallbacks() throws Exception {
  for(String variant:List.of("DamnedEntity","CrossEyesEntity","BigEyeEntity","MultipleEyesEntity","FourEyesEntity")) {
   var node=read("net/mcreator/thedeepvoid/entity/"+variant);
   assertEquals("net/minecraft/world/entity/monster/Monster",node.superName);
   for(var method:node.methods) {
    assertNotEquals("(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/ai/navigation/PathNavigation;",method.desc,variant);
    assertFalse(List.of("onPathfindingStart","onPathfindingDone").contains(method.name),variant);
   }
  }
 }
 @Test void pinnedBaseMobCallbacksAreNopButNativeGroundNavigationIsNotAReadonlyGuarantee() throws Exception {
  ClassNode mob=new ClassNode(),ground=new ClassNode();
  try(var in=getClass().getClassLoader().getResourceAsStream("net/minecraft/world/entity/Mob.class")) {
   assertNotNull(in);new ClassReader(in).accept(mob,0);
  }
  try(var in=getClass().getClassLoader().getResourceAsStream("net/minecraft/world/entity/ai/navigation/GroundPathNavigation.class")) {
   assertNotNull(in);new ClassReader(in).accept(ground,0);
  }
  for(String name:List.of("onPathfindingStart","onPathfindingDone")) {
   var method=mob.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals("()V")).findFirst().orElseThrow();
   List<Integer> operations=new ArrayList<>();for(var instruction:method.instructions)if(instruction.getOpcode()>=0)operations.add(instruction.getOpcode());
   assertEquals(List.of(Opcodes.RETURN),operations);
  }
  var method=ground.methods.stream().filter(m->m.name.equals("canUpdatePath")).findFirst().orElseThrow();
  List<String> calls=new ArrayList<>();for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)calls.add(call.name);
  assertTrue(calls.containsAll(List.of("onGround","isInLiquid","isPassenger")));
 }
 @Test void actualGeneratedRefmapBindsTheProductionInvocationInBothMappingViews() throws Exception {
  var root=JsonParser.parseString(Files.readString(input("bc.initialRootRefmap"))).getAsJsonObject();
  String mixin="com/bettercontent/bettercaveencounters/mixin/DamnedInitialRootMixin";
  String named="Lnet/minecraft/world/entity/EntityType;spawn"+DESC,production="Lnet/minecraft/world/entity/EntityType;m_262496_"+DESC;
  assertEquals(production,root.getAsJsonObject("mappings").getAsJsonObject(mixin).get(named).getAsString());
  assertEquals(production,root.getAsJsonObject("data").getAsJsonObject("searge").getAsJsonObject(mixin).get(named).getAsString());
 }
}
