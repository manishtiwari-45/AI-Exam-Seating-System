package com.exam.dao;

import com.exam.model.Seat;
import java.util.ArrayList;
import java.util.List;
import com.exam.config.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class RoomDAO {

    public void addRoom(String roomNumber, int rows, int cols) {
        int capacity = rows * cols;
        String sqlRoom = "INSERT INTO rooms (room_number, capacity, rows_count, cols_count) VALUES (?, ?, ?, ?)";
        String sqlSeat = "INSERT INTO seats (room_id, seat_number, row_num, col_num) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Start Transaction
            conn.setAutoCommit(false);

            try {
                // 2. Insert Room
                long roomId = -1;
                try (PreparedStatement pstmt = conn.prepareStatement(sqlRoom, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, roomNumber);
                    pstmt.setInt(2, capacity);
                    pstmt.setInt(3, rows);
                    pstmt.setInt(4, cols);
                    pstmt.executeUpdate();

                    ResultSet rs = pstmt.getGeneratedKeys();
                    if (rs.next()) {
                        roomId = rs.getLong(1);
                    }
                }

                if (roomId == -1) throw new SQLException("Creating room failed, no ID obtained.");

                // 3. Generate Seats for this Room
                try (PreparedStatement seatStmt = conn.prepareStatement(sqlSeat)) {
                    int seatCount = 1;
                    for (int r = 1; r <= rows; r++) {
                        for (int c = 1; c <= cols; c++) {
                            seatStmt.setLong(1, roomId);
                            seatStmt.setInt(2, seatCount++);
                            seatStmt.setInt(3, r);
                            seatStmt.setInt(4, c);
                            seatStmt.addBatch(); // Batch insert for performance
                        }
                    }
                    seatStmt.executeBatch();
                }

                // 4. Commit Transaction
                conn.commit();
                System.out.println("Room " + roomNumber + " and " + capacity + " seats added successfully.");

            } catch (SQLException e) {
                // 5. Rollback on failure
                conn.rollback();
                System.err.println("Transaction failed! Rolling back changes.");
                e.printStackTrace();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Seat> getSeatsByRoomId(long roomId) {
        List<Seat> seats = new ArrayList<>();
        // We order by seat_number so the strategy fills them in order (1, 2, 3...)
        String sql = "SELECT * FROM seats WHERE room_id = ? ORDER BY seat_number ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, roomId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                seats.add(new Seat(
                        rs.getLong("id"),
                        rs.getLong("room_id"),
                        rs.getInt("seat_number"),
                        rs.getInt("row_num"),
                        rs.getInt("col_num")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return seats;
    }
}