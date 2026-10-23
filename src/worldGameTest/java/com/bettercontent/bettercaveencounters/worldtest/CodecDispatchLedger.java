package com.bettercontent.bettercaveencounters.worldtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded scalars only. Every row needs an original registered selection AND original root return. */
public final class CodecDispatchLedger {
    private record Row(String id, String codec, String digest, String body) {}
    private static final class Frame {
        List<String> ids;
        final List<Row> rows = new ArrayList<>();
        String id, codec, digest, body;
        int selected, returned;
        boolean parsing;
    }
    private final ThreadLocal<Frame> current = new ThreadLocal<>();
    private volatile Thread owner;
    private String fault;
    private int starts;
    public synchronized void fail(String reason) { if (fault == null) fault = reason; }
    public void begin(List<String> ids) {
        if (owner != null || ++starts > 4 || ids.isEmpty() || ids.size() > 72
                || ids.stream().distinct().count() != ids.size() || ids.stream().anyMatch(s -> s.length() > 160)) {
            fail("APPLY_BOUNDS"); current.remove(); return;
        }
        Frame f = new Frame(); f.ids = List.copyOf(ids); current.set(f); owner = Thread.currentThread();
    }
    public boolean active() {
        if (owner != Thread.currentThread()) return false; // no inactive ThreadLocal.get/bootstrap entries
        Frame f = current.get(); return f != null && f.parsing;
    }
    public String codec() { return active() ? current.get().codec : null; }
    public String row() { return active() ? current.get().id : null; }
    public void before(String id, String codec, String digest) {
        Frame f = current.get();
        if (f == null || f.parsing || f.rows.size() >= f.ids.size() || !f.ids.get(f.rows.size()).equals(id)
                || codec == null || codec.isBlank() || codec.length() > 160 || digest == null || digest.length() != 64) {
            fail("ROW_INPUT_ORDER"); return;
        }
        f.id = id; f.codec = codec; f.digest = digest; f.body = null;
        f.selected = f.returned = 0; f.parsing = true;
    }
    public void selected(boolean originalRegisteredIdentity, boolean fullSuccess, String key) {
        Frame f = current.get();
        if (!active()) { fail("NO_ACTIVE_SELECTION"); return; }
        if (!originalRegisteredIdentity || !fullSuccess || !Objects.equals(f.codec, key) || ++f.selected != 1)
            fail("REGISTERED_IDENTITY");
    }
    public void returned(boolean exactRootAndOps, boolean fullSuccess, String digest, String body) {
        Frame f = current.get();
        if (!active()) { fail("NO_ACTIVE_ROOT"); return; }
        if (!exactRootAndOps || !fullSuccess || !Objects.equals(f.digest, digest) || body == null
                || body.length() > 256 || f.selected != 1 || ++f.returned != 1) fail("ORIGINAL_ROOT_RETURN");
        f.body = body;
    }
    public void after(String id) {
        Frame f = current.get();
        if (!active() || !Objects.equals(f.id, id) || f.selected != 1 || f.returned != 1) {
            fail("INCOMPLETE_ROW"); return;
        }
        f.rows.add(new Row(f.id, f.codec, f.digest, f.body)); f.parsing = false;
    }
    public void requireLoaded(int ordinal, String id, String codec, String digest, String implementation) {
        Frame f = current.get();
        if (fault != null || f == null || f.parsing || f.rows.size() != f.ids.size() || ordinal < 0 || ordinal >= f.ids.size())
            throw new IllegalStateException("Incomplete original codec proof: " + fault);
        Row row = f.rows.get(ordinal);
        if (!row.equals(new Row(id, codec, digest, implementation)))
            throw new IllegalStateException("Original codec input/body/order differs at " + ordinal + ": " + id
                + "; original=" + row + "; loaded=" + new Row(id, codec, digest, implementation));
    }
    public void end() { current.remove(); owner = null; }
}
