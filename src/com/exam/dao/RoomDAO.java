package com.exam.dao;

import com.exam.config.DBConnection;
import com.exam.model.Room;
import com.exam.model.Seat;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * RoomDAO — database operations for rooms and seats tables.
 *
 * WHAT CHANGED:
 *   Added getAllRooms() — required by the new multi-room AllocationService.
 *   Original code had no way to fetch all rooms; AllocationHandler was
 *   hardcoding "SELECT id FROM rooms LIMIT 1" inline in the handler itself.
 */
public class RoomDAO {

    /**
     * Fetches all rooms from the database.
     * Used by AllocationService to distribute students across rooms.
     *
     * @return List of all rooms, ordered by id (insertion order)
     */
    public List<Room> getAllRooms() {
        List<Room> rooms = new ArrayList<>();
        String sql = "SELECT id, room_number, capacity, rows_count, cols_count FROM rooms ORDER BY id ASC";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {

            while (rs.next()) {
                rooms.add(new Room(
                        rs.getLong("id"),
                        rs.getString("room_number"),
                        rs.getInt("capacity"),
                        rs.getInt("rows_count"),
                        rs.getInt("cols_count")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[RoomDAO] Error fetching all rooms: " + e.getMessage());
            e.printStackTrace();
        }
        return rooms;
    }

    /**
     * Fetches all seats for a specific room, ordered by seat_number.
     * The order matters — Hill Climbing fills seats sequentially by index.
     */
    public List<Seat> getSeatsByRoomId(long roomId) {
        List<Seat> seats = new ArrayList<>();
        String sql = "SELECT id, room_id, seat_number, row_num, col_num " +
                "FROM seats WHERE room_id = ? ORDER BY seat_number ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, roomId);
            ResultSet rs = ps.executeQuery();

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
            System.err.println("[RoomDAO] Error fetching seats for room " + roomId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return seats;
    }

    /**
     * Adds a room and generates all its seats in one transaction.
     * Called by BatchRoomHandler directly — kept here for the App.java
     * console flow and for completeness.
     */
    public void addRoom(String roomNumber, int rows, int cols) {
        int capacity = rows * cols;
        String sqlRoom = "INSERT INTO rooms (room_number, capacity, rows_count, cols_count) VALUES (?, ?, ?, ?)";
        String sqlSeat = "INSERT INTO seats (room_id, seat_number, row_num, col_num) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long roomId;
                try (PreparedStatement psRoom = conn.prepareStatement(sqlRoom, Statement.RETURN_GENERATED_KEYS)) {
                    psRoom.setString(1, roomNumber);
                    psRoom.setInt(2, capacity);
                    psRoom.setInt(3, rows);
                    psRoom.setInt(4, cols);
                    psRoom.executeUpdate();
                    ResultSet keys = psRoom.getGeneratedKeys();
                    if (!keys.next()) throw new SQLException("Room insert returned no ID");
                    roomId = keys.getLong(1);
                }

                try (PreparedStatement psSeat = conn.prepareStatement(sqlSeat)) {
                    int seatNum = 1;
                    for (int r = 1; r <= rows; r++) {
                        for (int c = 1; c <= cols; c++) {
                            psSeat.setLong(1, roomId);
                            psSeat.setInt(2, seatNum++);
                            psSeat.setInt(3, r);
                            psSeat.setInt(4, c);
                            psSeat.addBatch();
                        }
                    }
                    psSeat.executeBatch();
                }

                conn.commit();
                System.out.println("[RoomDAO] Room '" + roomNumber + "' added with " + capacity + " seats.");

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[RoomDAO] Transaction failed, rolled back: " + e.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}