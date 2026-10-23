package com.bettercontent.bettercaveencounters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RootScopeTest {
 @Test void restoresNestedContext() {
  var s=new RootScope<Object>(); Object outer=new Object(), inner=new Object();
  s.call(outer,()->{assertSame(outer,s.current());s.call(inner,()->{assertSame(inner,s.current());return null;});assertSame(outer,s.current());return null;});
  assertNull(s.current());
 }
 @Test void naturalUnownedScopeMasksRatherThanBorrows() {
  var s=new RootScope<Object>();Object outer=new Object();
  s.call(outer,()->{s.call(null,()->{assertNull(s.current());return null;});assertSame(outer,s.current());return null;});assertNull(s.current());
 }
 @Test void exceptionalNestedExitRestoresAndDoesNotSwallowCause() {
  var s=new RootScope<Object>();Object outer=new Object();var cause=new IllegalStateException("native");
  s.call(outer,()->{assertSame(cause,assertThrows(IllegalStateException.class,()->s.call(new Object(),()->{throw cause;})));assertSame(outer,s.current());return null;});assertNull(s.current());
 }
 @Test void exceptionalOuterExitClearsScope() {
  var s=new RootScope<Object>();assertThrows(AssertionError.class,()->s.call(new Object(),()->{throw new AssertionError("native");}));assertNull(s.current());
 }
 @Test void contextsDoNotLeakBetweenThreads() throws Exception {
  var s=new RootScope<Object>();var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  s.call(new Object(),()->{var t=new Thread(()->{try{assertNull(s.current());}catch(Throwable e){failure.set(e);}});t.start();try{t.join();}catch(InterruptedException e){throw new AssertionError(e);}return null;});
  assertNull(failure.get());assertNull(s.current());
 }
}
