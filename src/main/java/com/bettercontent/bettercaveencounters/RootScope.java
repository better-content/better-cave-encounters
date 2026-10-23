package com.bettercontent.bettercaveencounters;

import java.util.function.Supplier;

/** Private-to-Cave transient context; nested/unowned calls mask rather than borrow an outer root. */
final class RootScope<T> {
    private final ThreadLocal<T> active = new ThreadLocal<>();
    T current() { return active.get(); }
    <R> R call(T value, Supplier<R> action) {
        T previous = active.get();
        if (value == null) active.remove(); else active.set(value);
        try { return action.get(); }
        finally { if (previous == null) active.remove(); else active.set(previous); }
    }
}
