package com.exam.dao;

import com.exam.config.DBConnection;
import com.exam.model.Allocation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class AllocationDAO {

    public boolean bulkSaveAllocations(List<Allocation> allocations) {
        // We use ON DUPLICATE KEY UPDATE to prevent crashing if we run it twice for the same exam
        // (Optional optimization, but good for testing)
        String sql = "INSERT INTO allocations (exam_id, student_id, seat_id) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // Start Transaction

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                for (Allocation alloc : allocations) {
                    pstmt.setLong(1, alloc.getExamId());
                    pstmt.setLong(2, alloc.getStudentId());
                    pstmt.setLong(3, alloc.getSeatId());
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit(); // Commit Transaction
                System.out.println("Transaction Committed: Saved " + allocations.size() + " allocations.");
                return true;

            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                System.err.println("Transaction Failed! Rolling back.");
                System.err.println("SQL Error: " + e.getMessage());
                return false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}