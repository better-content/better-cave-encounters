package com.bettercontent.bettercaveencounters;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class CaveNativeRootBridgeTest {
 @Test void absentContextForwardsOriginalArgumentsOnce() {
  var calls=new AtomicInteger();var position=new BlockPos(12,-40,30);
  Operation<Entity> original=args->{calls.incrementAndGet();assertEquals(4,args.length);assertNull(args[0]);assertNull(args[1]);assertSame(position,args[2]);assertSame(MobSpawnType.NATURAL,args[3]);return null;};
  assertNull(CaveNativeRootBridge.nativeSpawn(null,null,position,MobSpawnType.NATURAL,null,original));assertEquals(1,calls.get());
 }
 @Test void absentContextPreservesOriginalExceptionWithoutRetry() {
  var calls=new AtomicInteger();var cause=new IllegalStateException("native call");
  assertSame(cause,assertThrows(IllegalStateException.class,()->CaveNativeRootBridge.nativeSpawn(null,null,BlockPos.ZERO,
      MobSpawnType.MOB_SUMMONED,null,args->{calls.incrementAndGet();throw cause;})));assertEquals(1,calls.get());
 }
 @Test void absentContextPreservesNativeErrorAndRemainsAbsentAfterThrow() {
  var cause=new AssertionError("native error");
  assertSame(cause,assertThrows(AssertionError.class,()->CaveNativeRootBridge.nativeSpawn(null,null,BlockPos.ZERO,
      MobSpawnType.EVENT,null,args->{throw cause;})));
  var calls=new AtomicInteger();CaveNativeRootBridge.nativeSpawn(null,null,BlockPos.ZERO,MobSpawnType.EVENT,null,args->{calls.incrementAndGet();return null;});assertEquals(1,calls.get());
 }
}
