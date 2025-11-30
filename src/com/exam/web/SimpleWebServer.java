package com.exam.web;

import com.exam.config.DBConnection;
import com.exam.service.AllocationService;
import com.exam.strategy.HillClimbingStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;

public class SimpleWebServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Core Files
        server.createContext("/", new StaticFileHandler());

        // API - Management
        server.createContext("/api/reset", new ClearDataHandler());
        server.createContext("/api/rooms/batch", new BatchRoomHandler());
        server.createContext("/api/students/batch", new BatchStudentHandler());
        server.createContext("/api/allocate", new AllocationHandler());

        // API - Read Data
        server.createContext("/api/view", new ViewHandler());
        server.createContext("/api/stats", new StatsHandler()); // NEW: Analytics

        server.setExecutor(null);
        System.out.println("🚀 Portfolio App Started: http://localhost:" + port);
        server.start();
    }

    // --- NEW: DASHBOARD STATS ---
    static class StatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try (Connection c = DBConnection.getConnection(); Statement s = c.createStatement()) {
                // 1. Total Rooms
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM rooms");
                rs.next(); int rooms = rs.getInt(1);

                // 2. Total Capacity
                rs = s.executeQuery("SELECT COALESCE(SUM(capacity),0) FROM rooms");
                rs.next(); int capacity = rs.getInt(1);

                // 3. Registered Students
                rs = s.executeQuery("SELECT COUNT(*) FROM students");
                rs.next(); int students = rs.getInt(1);

                // 4. Allocated Seats
                rs = s.executeQuery("SELECT COUNT(*) FROM allocations");
                rs.next(); int allocated = rs.getInt(1);

                String json = String.format(
                        "{\"rooms\":%d, \"capacity\":%d, \"students\":%d, \"allocated\":%d}",
                        rooms, capacity, students, allocated
                );
                sendJson(ex, json);
            } catch (Exception e) { e.printStackTrace(); sendJson(ex, "{}"); }
        }
    }

    // --- EXISTING HANDLERS (Optimized) ---
    static class ClearDataHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) return;
            try (Connection c = DBConnection.getConnection(); Statement s = c.createStatement()) {
                s.execute("SET FOREIGN_KEY_CHECKS=0");
                s.execute("TRUNCATE TABLE allocations"); s.execute("TRUNCATE TABLE exam_students");
                s.execute("TRUNCATE TABLE seats"); s.execute("TRUNCATE TABLE rooms");
                s.execute("TRUNCATE TABLE students"); s.execute("SET FOREIGN_KEY_CHECKS=1");
                s.executeUpdate("INSERT IGNORE INTO courses VALUES (1, 'GEN', 'General')");
                s.executeUpdate("INSERT IGNORE INTO exams VALUES (99, 1, CURDATE(), '09:00:00', 180)");
                sendJson(ex, "{\"status\":\"success\"}");
            } catch (Exception e) { sendJson(ex, "{\"status\":\"error\"}"); }
        }
    }

    static class BatchRoomHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = DBConnection.getConnection()) {
                c.setAutoCommit(false);
                String sqlR = "INSERT INTO rooms (room_number, capacity, rows_count, cols_count) VALUES (?, ?, ?, ?)";
                String sqlS = "INSERT INTO seats (room_id, seat_number, row_num, col_num) VALUES (?, ?, ?, ?)";
                try (PreparedStatement psR = c.prepareStatement(sqlR, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement psS = c.prepareStatement(sqlS)) {
                    for(String r : body.split(";")) {
                        String[] p = r.split(",");
                        int rows=Integer.parseInt(p[1]), cols=Integer.parseInt(p[2]);
                        psR.setString(1, p[0]); psR.setInt(2, rows*cols); psR.setInt(3, rows); psR.setInt(4, cols);
                        psR.executeUpdate();
                        ResultSet rs = psR.getGeneratedKeys();
                        if(rs.next()) {
                            long rid = rs.getLong(1);
                            int sn=1;
                            for(int i=1; i<=rows; i++) for(int j=1; j<=cols; j++) {
                                psS.setLong(1, rid); psS.setInt(2, sn++); psS.setInt(3, i); psS.setInt(4, j); psS.addBatch();
                            }
                        }
                    }
                    psS.executeBatch(); c.commit(); sendJson(ex, "{\"status\":\"success\"}");
                } catch(Exception e) { c.rollback(); throw e; }
            } catch(Exception e) { sendJson(ex, "{\"status\":\"error\"}"); }
        }
    }

    static class BatchStudentHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = DBConnection.getConnection()) {
                c.setAutoCommit(false);
                String sqlSt = "INSERT INTO students (roll_no, name, email, branch) VALUES (?, ?, ?, ?)";
                String sqlRg = "INSERT INTO exam_students (exam_id, student_id) VALUES (99, ?)";
                try (PreparedStatement psS = c.prepareStatement(sqlSt, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement psR = c.prepareStatement(sqlRg)) {
                    for(String b : body.split(";")) {
                        String[] p = b.split(",");
                        String br=p[0]; int s=Integer.parseInt(p[1]), e=Integer.parseInt(p[2]);
                        for(int i=s; i<=e; i++) {
                            psS.setString(1, br+"-"+i); psS.setString(2, "Student "+i); psS.setString(3, "u@t.com"); psS.setString(4, br);
                            psS.executeUpdate();
                            ResultSet rs = psS.getGeneratedKeys();
                            if(rs.next()) { psR.setLong(1, rs.getLong(1)); psR.addBatch(); }
                        }
                    }
                    psR.executeBatch(); c.commit(); sendJson(ex, "{\"status\":\"success\"}");
                } catch(Exception e) { c.rollback(); throw e; }
            } catch(Exception e) { sendJson(ex, "{\"status\":\"error\"}"); }
        }
    }

    static class AllocationHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                AllocationService service = new AllocationService();
                long roomId = 1; // Default
                try(Connection c=DBConnection.getConnection(); ResultSet rs=c.createStatement().executeQuery("SELECT id FROM rooms LIMIT 1")) {
                    if(rs.next()) roomId = rs.getLong(1);
                }
                service.generateSeating(99, roomId, new HillClimbingStrategy());
                sendJson(ex, "{\"status\":\"success\"}");
            } catch (Exception e) { e.printStackTrace(); sendJson(ex, "{\"status\":\"error\"}"); }
        }
    }

    static class ViewHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder json = new StringBuilder("[");
            try(Connection c=DBConnection.getConnection(); ResultSet rs=c.createStatement().executeQuery(
                    "SELECT s.row_num, s.col_num, st.name, st.branch, st.roll_no, r.room_number FROM allocations a JOIN seats s ON a.seat_id=s.id JOIN students st ON a.student_id=st.id JOIN rooms r ON s.room_id=r.id")) {
                boolean f=true;
                while(rs.next()){
                    if(!f) json.append(",");
                    json.append(String.format("{\"row\":%d,\"col\":%d,\"name\":\"%s\",\"branch\":\"%s\",\"roll\":\"%s\",\"room\":\"%s\"}",
                            rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
                    f=false;
                }
            } catch(Exception e){}
            json.append("]");
            sendJson(ex, json.toString());
        }
    }

    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";
            File f = new File("web" + path);
            if (f.exists()) {
                ex.sendResponseHeaders(200, f.length());
                Files.copy(f.toPath(), ex.getResponseBody());
            } else ex.sendResponseHeaders(404, 0);
            ex.getResponseBody().close();
        }
    }
    private static void sendJson(HttpExchange ex, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, json.length());
        ex.getResponseBody().write(json.getBytes());
        ex.getResponseBody().close();
    }
}