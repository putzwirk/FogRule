package com.putzwirk.fogrule.cozy;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.io.*;
import java.sql.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CozyDatabase {

    private static final String URL = "jdbc:sqlite:fogrule_cozy.db";
    private static final ConcurrentLinkedQueue<ChunkDiff> diffQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Thread worker;

    private record ChunkDiff(int x, int z, IntOpenHashSet positions) {}

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS chunks (x INTEGER, z INTEGER, data BLOB, PRIMARY KEY (x, z))");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        worker = new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(URL)) {
                conn.setAutoCommit(false);

                while (running.get()) {
                    if (!diffQueue.isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT OR REPLACE INTO chunks (x, z, data) VALUES (?, ?, ?)")) {

                            ChunkDiff diff;
                            int batchCount = 0;
                            while ((diff = diffQueue.poll()) != null) {
                                try {
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    try (DataOutputStream dos = new DataOutputStream(baos)) {
                                        dos.writeInt(diff.positions().size());
                                        for (int pos : diff.positions()) {
                                            dos.writeInt(pos);
                                        }
                                    }
                                    ps.setInt(1, diff.x());
                                    ps.setInt(2, diff.z());
                                    ps.setBytes(3, baos.toByteArray());
                                    ps.addBatch();
                                    batchCount++;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                if (batchCount >= 100) {
                                    ps.executeBatch();
                                    conn.commit();
                                    batchCount = 0;
                                }
                            }
                            if (batchCount > 0) {
                                ps.executeBatch();
                                conn.commit();
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                            try { conn.rollback(); } catch (SQLException ignored) {}
                        }
                    } else {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, "FogRule-DB-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public static void shutdown() {
        running.set(false);
        flushSync(); // write all remaining diffs synchronously
        if (worker != null) {
            worker.interrupt();
        }
    }

    /**
     * Synchronously writes all pending diffs to the database.
     */
    public static void flushSync() {
        if (diffQueue.isEmpty()) return;
        try (Connection conn = DriverManager.getConnection(URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO chunks (x, z, data) VALUES (?, ?, ?)")) {
                ChunkDiff diff;
                while ((diff = diffQueue.poll()) != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (DataOutputStream dos = new DataOutputStream(baos)) {
                        dos.writeInt(diff.positions().size());
                        for (int pos : diff.positions()) {
                            dos.writeInt(pos);
                        }
                    }
                    ps.setInt(1, diff.x());
                    ps.setInt(2, diff.z());
                    ps.setBytes(3, baos.toByteArray());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException | IOException e) {
                e.printStackTrace();
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void scheduleFlush(int x, int z, IntOpenHashSet positions) {
        IntOpenHashSet copy = new IntOpenHashSet(positions);
        diffQueue.add(new ChunkDiff(x, z, copy));
    }

    public static IntOpenHashSet loadPositions(int x, int z) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("SELECT data FROM chunks WHERE x = ? AND z = ?")) {
            ps.setInt(1, x);
            ps.setInt(2, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] blob = rs.getBytes("data");
                    try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(blob))) {
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
            e.printStackTrace();
        }
        return new IntOpenHashSet();
    }
}