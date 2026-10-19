package com.mindsetalliance.core.cnn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Lance en parallèle les lectures indépendantes de la base Colis na Nga.
 * Chaque requête traverse le Cloud SQL Auth Proxy : en série, quelques dizaines d'allers-retours
 * prenaient plusieurs secondes. Un bloc en échec vaut null sans bloquer les autres.
 */
public final class CnnParallel {

    private static final Logger log = LoggerFactory.getLogger(CnnParallel.class);
    /** Aligné sur la taille du pool de connexions Colis na Nga. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "cnn-read-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private final Map<String, CompletableFuture<Object>> tasks = new LinkedHashMap<>();

    public CnnParallel add(String key, Supplier<?> supplier) {
        tasks.put(key, CompletableFuture.supplyAsync(() -> {
            try {
                return (Object) supplier.get();
            } catch (Exception e) {
                log.warn("Lecture Colis na Nga : bloc « {} » illisible : {}", key, e.getMessage());
                return null;
            }
        }, POOL));
        return this;
    }

    /** Attend tous les blocs (au plus 25 s) et les ajoute à {@code body} dans l'ordre d'ajout. */
    public Map<String, Object> into(Map<String, Object> body) {
        for (Map.Entry<String, CompletableFuture<Object>> e : tasks.entrySet()) {
            Object value;
            try {
                value = e.getValue().get(25, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.warn("Lecture Colis na Nga : bloc « {} » trop lent ou en échec : {}", e.getKey(), ex.getMessage());
                value = null;
            }
            body.put(e.getKey(), value);
        }
        return body;
    }
}
