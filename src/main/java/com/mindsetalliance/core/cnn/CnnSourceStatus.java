package com.mindsetalliance.core.cnn;

import org.springframework.stereotype.Component;

/** État de la source Colis na Nga, sans identifiant ni URL. */
@Component
public class CnnSourceStatus {

    private volatile boolean reachable;
    private volatile boolean readOnlyVerified;

    public boolean reachable() {
        return reachable;
    }

    public boolean readOnlyVerified() {
        return readOnlyVerified;
    }

    public void mark(boolean reachable, boolean readOnlyVerified) {
        this.reachable = reachable;
        this.readOnlyVerified = readOnlyVerified;
    }
}
