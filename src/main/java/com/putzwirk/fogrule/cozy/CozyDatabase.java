package com.putzwirk.fogrule.cozy;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;


public class CozyDatabase {
    private static final String URL = "jdbc:sqlite:fogrule_cozy.db";
    private static final ConcurrentLinkedQueue<ChunkDiff> diffQueue = new ConcurrentLinkedQueue<>();
    private static Thread worker;

    private record ChunkDiff(int x, int z, IntOpenHashSet positions) {}

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(URL);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS chunks (x INTEGER, z INTEGER, data BLOB, PRIMARY KEY (x, z))");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        worker = new Thread(() -> {
            while (true) {
                if (!diffQueue.isEmpty()) {
                    try (Connection conn = new org.sqlite.JDBC().connect(URL, new Properties())) {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO chunks (x, z, data) VALUES (?, ?, ?)")) {
                            ChunkDiff diff;
                            while ((diff = diffQueue.poll()) != null) {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                DataOutputStream dos = new DataOutputStream(baos);
                                dos.writeInt(diff.positions().size());
                                for (int i : diff.positions()) {
                                    dos.writeInt(i);
                                }
                                ps.setInt(1, diff.x());
                                ps.setInt(2, diff.z());
                                ps.setBytes(3, baos.toByteArray());
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                        conn.commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        }, "FogRule-DB-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public static void scheduleFlush(int x, int z, IntOpenHashSet positions) {
        diffQueue.add(new ChunkDiff(x, z, positions));
    }

    public static IntOpenHashSet loadPositions(int x, int z) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT data FROM chunks WHERE x = ? AND z = ?")) {
                ps.setInt(1, x);
                ps.setInt(2, z);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] blob = rs.getBytes("data");
                        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(blob));
                        int size = dis.readInt();
                        IntOpenHashSet set = new IntOpenHashSet(size);
                        for (int i = 0; i < size; i++) {
                            set.add(dis.readInt());
                        }
                        return set;
                    }
                }
            }
        } catch (Exception e) {
            // ignored
        }
        return new IntOpenHashSet();
    }
}