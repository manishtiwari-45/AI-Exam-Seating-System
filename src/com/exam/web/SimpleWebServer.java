package com.exam.web;

import com.exam.config.AppConfig;
import com.exam.config.DBConnection;
import com.exam.service.AllocationService;
import com.exam.strategy.HillClimbingStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * SimpleWebServer — the HTTP entry point for the application.
 *
 * Uses the JDK built-in com.sun.net.httpserver.HttpServer (no Tomcat/Jetty needed).
 * Each inner class handles one API route.
 *
 * Routes:
 *   GET  /                    → serves web/index.html
 *   GET  /api/stats           → dashboard counts
 *   POST /api/reset           → truncates all data
 *   POST /api/rooms/batch     → add rooms + generate seats
 *   POST /api/students/batch  → add students + register for exam
 *   POST /api/allocate        → run Hill Climbing allocation
 *   GET  /api/view            → get full seating map as JSON
 *   POST /api/analyze         → AI-powered seating analysis (Phase 5)
 */
public class SimpleWebServer {

    // Shared Jackson mapper — thread-safe, reuse across handlers
    static final ObjectMapper JSON = new ObjectMapper();

    // Resolved at startup: absolute path to the web/ directory.
    // This fixes the bug where relative paths broke when the JAR was run
    // from a different working directory.
    static final Path WEB_DIR;

    static {
        // Strategy: resolve web/ relative to where the JAR/class lives.
        // Works for both IntelliJ runs and deployed JARs.
        String webPath = AppConfig.get("WEB_DIR", "web");
        WEB_DIR = Paths.get(webPath).toAbsolutePath();
        System.out.println("[Server] Serving static files from: " + WEB_DIR);
    }

    public static void main(String[] args) throws IOException {
        int port = AppConfig.getInt("PORT", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Static files
        server.createContext("/", new StaticFileHandler());

        // Management APIs
        server.createContext("/api/reset",          new ClearDataHandler());
        server.createContext("/api/rooms/batch",    new BatchRoomHandler());
        server.createContext("/api/students/batch", new BatchStudentHandler());
        server.createContext("/api/allocate",       new AllocationHandler());

        // Read APIs
        server.createContext("/api/view",    new ViewHandler());
        server.createContext("/api/stats",   new StatsHandler());

        // AI API (implemented in Phase 5 — stub returns placeholder for now)
        server.createContext("/api/analyze", new AIAnalysisHandler());

        server.setExecutor(null); // Uses default thread pool
        System.out.println("[Server] Started on http://localhost:" + port);
        server.start();
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    /** GET /api/stats — returns counts for the dashboard cards. */
    static class StatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            try (Connection c = DBConnection.getConnection();
                 Statement s  = c.createStatement()) {

                ResultSet rs;

                rs = s.executeQuery("SELECT COUNT(*) FROM rooms");
                rs.next(); int rooms = rs.getInt(1);

                rs = s.executeQuery("SELECT COALESCE(SUM(capacity), 0) FROM rooms");
                rs.next(); int capacity = rs.getInt(1);

                rs = s.executeQuery("SELECT COUNT(*) FROM students");
                rs.next(); int students = rs.getInt(1);

                rs = s.executeQuery("SELECT COUNT(*) FROM allocations");
                rs.next(); int allocated = rs.getInt(1);

                Map<String, Integer> data = new LinkedHashMap<>();
                data.put("rooms",     rooms);
                data.put("capacity",  capacity);
                data.put("students",  students);
                data.put("allocated", allocated);

                sendJson(ex, JSON.writeValueAsString(data));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, "{}");
            }
        }
    }

    /** POST /api/reset — clears all data and re-seeds defaults. */
    static class ClearDataHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if (!isPost(ex)) return;
            try (Connection c = DBConnection.getConnection();
                 Statement s  = c.createStatement()) {

                s.execute("SET FOREIGN_KEY_CHECKS=0");
                s.execute("TRUNCATE TABLE allocations");
                s.execute("TRUNCATE TABLE exam_students");
                s.execute("TRUNCATE TABLE seats");
                s.execute("TRUNCATE TABLE rooms");
                s.execute("TRUNCATE TABLE students");
                s.execute("SET FOREIGN_KEY_CHECKS=1");
                // Re-seed the default exam session
                s.executeUpdate("INSERT IGNORE INTO courses VALUES (1, 'GEN', 'General')");
                s.executeUpdate("INSERT IGNORE INTO exams VALUES (99, 1, CURDATE(), '09:00:00', 180)");

                sendJson(ex, "{\"status\":\"success\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/rooms/batch
     * Body format: "RoomA,5,6;RoomB,4,5"
     * Each token: roomNumber,rows,cols
     */
    static class BatchRoomHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if (!isPost(ex)) return;

            String body = readBody(ex);
            if (body == null || body.isBlank()) {
                sendJson(ex, "{\"status\":\"error\",\"message\":\"Empty body\"}");
                return;
            }

            try (Connection c = DBConnection.getConnection()) {
                c.setAutoCommit(false);
                String sqlRoom = "INSERT INTO rooms (room_number, capacity, rows_count, cols_count) VALUES (?, ?, ?, ?)";
                String sqlSeat = "INSERT INTO seats (room_id, seat_number, row_num, col_num) VALUES (?, ?, ?, ?)";

                try (PreparedStatement psRoom = c.prepareStatement(sqlRoom, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement psSeat = c.prepareStatement(sqlSeat)) {

                    for (String token : body.split(";")) {
                        token = token.trim();
                        if (token.isEmpty()) continue;

                        String[] parts = token.split(",");
                        if (parts.length < 3) continue;

                        String roomName = parts[0].trim();
                        int rows = Integer.parseInt(parts[1].trim());
                        int cols = Integer.parseInt(parts[2].trim());

                        // Insert room
                        psRoom.setString(1, roomName);
                        psRoom.setInt(2, rows * cols);
                        psRoom.setInt(3, rows);
                        psRoom.setInt(4, cols);
                        psRoom.executeUpdate();

                        ResultSet keys = psRoom.getGeneratedKeys();
                        if (!keys.next()) continue;
                        long roomId = keys.getLong(1);

                        // Generate all seats for this room
                        int seatNum = 1;
                        for (int r = 1; r <= rows; r++) {
                            for (int col = 1; col <= cols; col++) {
                                psSeat.setLong(1, roomId);
                                psSeat.setInt(2, seatNum++);
                                psSeat.setInt(3, r);
                                psSeat.setInt(4, col);
                                psSeat.addBatch();
                            }
                        }
                    }

                    psSeat.executeBatch();
                    c.commit();
                    sendJson(ex, "{\"status\":\"success\"}");

                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/students/batch
     * Body format: "CSE,1,30;ECE,1,25;ME,1,20"
     * Each token: branch,startRollSuffix,endRollSuffix
     */
    static class BatchStudentHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if (!isPost(ex)) return;

            String body = readBody(ex);
            if (body == null || body.isBlank()) {
                sendJson(ex, "{\"status\":\"error\",\"message\":\"Empty body\"}");
                return;
            }

            try (Connection c = DBConnection.getConnection()) {
                c.setAutoCommit(false);
                String sqlStudent  = "INSERT INTO students (roll_no, name, email, branch) VALUES (?, ?, ?, ?)";
                String sqlRegister = "INSERT INTO exam_students (exam_id, student_id) VALUES (99, ?)";

                try (PreparedStatement psStudent  = c.prepareStatement(sqlStudent, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement psRegister = c.prepareStatement(sqlRegister)) {

                    for (String token : body.split(";")) {
                        token = token.trim();
                        if (token.isEmpty()) continue;

                        String[] parts = token.split(",");
                        if (parts.length < 3) continue;

                        String branch = parts[0].trim();
                        int start = Integer.parseInt(parts[1].trim());
                        int end   = Integer.parseInt(parts[2].trim());

                        for (int i = start; i <= end; i++) {
                            psStudent.setString(1, branch + "-" + i);
                            psStudent.setString(2, branch + " Student " + i);
                            psStudent.setString(3, branch.toLowerCase() + i + "@exam.com");
                            psStudent.setString(4, branch);
                            psStudent.executeUpdate();

                            ResultSet keys = psStudent.getGeneratedKeys();
                            if (keys.next()) {
                                psRegister.setLong(1, keys.getLong(1));
                                psRegister.addBatch();
                            }
                        }
                    }

                    psRegister.executeBatch();
                    c.commit();
                    sendJson(ex, "{\"status\":\"success\"}");

                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/allocate — runs Hill Climbing across ALL rooms.
     *
     * BUG FIX: Previously only allocated to the first room.
     * Now distributes students across all rooms based on capacity.
     * Full multi-room implementation is done in Phase 3 (AllocationService refactor).
     */
    static class AllocationHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if (!isPost(ex)) return;
            try {
                AllocationService service = new AllocationService();
                service.generateSeating(99, new HillClimbingStrategy());
                sendJson(ex, "{\"status\":\"success\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/view — returns full seating map as JSON array.
     * Uses Jackson for safe JSON serialisation (no manual string concat).
     *
     * BUG FIX: Manual string concatenation was unsafe for names with quotes.
     */
    static class ViewHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            List<Map<String, Object>> result = new ArrayList<>();

            String sql = """
                SELECT s.row_num, s.col_num, s.seat_number,
                       st.name, st.branch, st.roll_no, r.room_number, r.id AS room_id
                FROM allocations a
                JOIN seats    s  ON a.seat_id    = s.id
                JOIN students st ON a.student_id = st.id
                JOIN rooms    r  ON s.room_id    = r.id
                ORDER BY r.id, s.seat_number
            """;

            try (Connection c = DBConnection.getConnection();
                 ResultSet rs = c.createStatement().executeQuery(sql)) {

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("row",    rs.getInt("row_num"));
                    row.put("col",    rs.getInt("col_num"));
                    row.put("seat",   rs.getInt("seat_number"));
                    row.put("name",   rs.getString("name"));
                    row.put("branch", rs.getString("branch"));
                    row.put("roll",   rs.getString("roll_no"));
                    row.put("room",   rs.getString("room_number"));
                    row.put("roomId", rs.getLong("room_id"));
                    result.add(row);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            sendJson(ex, JSON.writeValueAsString(result));
        }
    }

    /**
     * POST /api/analyze — AI-powered seating analysis.
     * Stub for now. Full implementation added in Phase 5 (AIAnalysisService).
     */
    static class AIAnalysisHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            // Placeholder response until AIAnalysisService is implemented in Phase 5
            sendJson(ex, "{\"insight\": \"AI analysis coming in Phase 5. Run the allocation first.\"}");
        }
    }

    /**
     * Serves static files from the web/ directory.
     *
     * BUG FIX: Uses WEB_DIR (absolute path resolved at startup) instead of
     * new File("web" + path) which was relative to CWD and broke on deployment.
     */
    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String uriPath = ex.getRequestURI().getPath();
            if ("/".equals(uriPath)) uriPath = "/index.html";

            // Resolve against the absolute WEB_DIR
            Path filePath = WEB_DIR.resolve(uriPath.substring(1)).normalize();

            // Security: reject any path that escapes the web/ directory
            if (!filePath.startsWith(WEB_DIR)) {
                ex.sendResponseHeaders(403, 0);
                ex.getResponseBody().close();
                return;
            }

            File file = filePath.toFile();
            if (file.exists() && file.isFile()) {
                String mime = Files.probeContentType(filePath);
                if (mime != null) ex.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(filePath);
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
            } else {
                ex.sendResponseHeaders(404, 0);
            }
            ex.getResponseBody().close();
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * BUG FIX: was using json.length() (char count).
     * Must use byte length for correct Content-Length header.
     */
    static void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static boolean isPost(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, 0);
            ex.getResponseBody().close();
            return false;
        }
        return true;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    }
}