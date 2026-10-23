package com.bettercontent.bettercaveencounters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CatalogueRootAccountTest {
 @Test void finiteAccountActuallyDebitsAndCommitsTheExistingCharge() {
  var a=new CatalogueRootAccount(64);var c=a.reserve(7);assertEquals(57,a.remaining());assertEquals(7,a.spent());
  c.commit();c.close();c.close();assertEquals(57,a.remaining());assertEquals(7,a.spent());
 }
 @Test void realChargeFailureRefundsExactlyOnce() {
  var a=new CatalogueRootAccount(64);var c=a.reserve(7);c.close();c.close();assertEquals(64,a.remaining());assertEquals(0,a.spent());
 }
 @Test void spentAccountIsNotResetForAnotherTry() {
  var a=new CatalogueRootAccount(64);var c=a.reserve(60);c.commit();c.close();assertNull(a.reserve(5));assertEquals(4,a.remaining());
 }
 @Test void accountCannotExceedOriginalMaximumCost() {
  assertThrows(IllegalArgumentException.class,()->new CatalogueRootAccount(65));assertThrows(IllegalArgumentException.class,()->new CatalogueRootAccount(0));
 }
 @Test void originalCostIncludingExistingCreditsIsPrepaidTogether() {
  var a=new CatalogueRootAccount(64);assertNull(a.reserve(65));assertEquals(64,a.remaining());var c=a.reserve(64);assertEquals(0,a.remaining());c.close();assertEquals(64,a.remaining());
 }
 @Test void invalidCostCannotMintBudget() {
  var a=new CatalogueRootAccount(64);assertThrows(IllegalArgumentException.class,()->a.reserve(-1));assertThrows(IllegalArgumentException.class,()->a.reserve(0));assertEquals(64,a.remaining());
 }
 @Test void closedAccountCannotAuthorizeAnotherRoot() {
  var a=new CatalogueRootAccount(64);a.close();assertFalse(a.allowsInitialReplacement());assertFalse(a.stillValid());assertThrows(IllegalStateException.class,()->a.reserve(1));
 }
 @Test void closingScopeDoesNotRefundAPaidCommittedRoot() {
  var a=new CatalogueRootAccount(64);var c=a.reserve(4);c.commit();c.close();a.close();assertEquals(60,a.remaining());assertEquals(4,a.spent());
 }
}
