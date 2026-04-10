package com.exam.dao;

import com.exam.config.DBConnection;
import com.exam.model.Allocation;

import java.sql.*;
import java.util.List;

/**
 * AllocationDAO — database operations for the allocations table.
 *
 * WHAT CHANGED:
 *   Original bulkSaveAllocations() did a plain INSERT with no guard against
 *   duplicate runs. Running "Allocate" twice on the same exam hit the UNIQUE
 *   constraints on (exam_id, seat_id) and (exam_id, student_id), causing
 *   a SQLException and leaving the data in a partially saved state.
 *
 *   FIX: replaceAllocations() deletes existing allocations for the exam
 *   BEFORE inserting new ones, inside a single transaction.
 *   This makes the operation idempotent — you can run allocation as many
 *   times as you want and always get a clean, consistent result.
 */
public class AllocationDAO {

    /**
     * Atomically replaces all allocations for an exam.
     *
     * Transaction sequence:
     *   1. DELETE existing allocations for this examId
     *   2. Batch INSERT all new allocations
     *   3. COMMIT — both steps succeed or neither does (ROLLBACK on error)
     *
     * @param examId      The exam whose allocations are being replaced
     * @param allocations The new allocations to save
     * @return true on success, false on failure
     */
    public boolean replaceAllocations(long examId, List<Allocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            System.err.println("[AllocationDAO] Nothing to save — empty allocations list.");
            return false;
        }

        String sqlDelete = "DELETE FROM allocations WHERE exam_id = ?";
        String sqlInsert = "INSERT INTO allocations (exam_id, student_id, seat_id) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // Begin transaction

            try (PreparedStatement psDelete = conn.prepareStatement(sqlDelete);
                 PreparedStatement psInsert = conn.prepareStatement(sqlInsert)) {

                // Step 1: Clear previous results for this exam
                psDelete.setLong(1, examId);
                int deleted = psDelete.executeUpdate();
                if (deleted > 0) {
                    System.out.println("[AllocationDAO] Cleared " + deleted + " previous allocations for exam " + examId);
                }

                // Step 2: Batch insert new allocations
                for (Allocation alloc : allocations) {
                    psInsert.setLong(1, alloc.getExamId());
                    psInsert.setLong(2, alloc.getStudentId());
                    psInsert.setLong(3, alloc.getSeatId());
                    psInsert.addBatch();
                }

                int[] results = psInsert.executeBatch();
                conn.commit();

                System.out.println("[AllocationDAO] Committed " + results.length + " allocations.");
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[AllocationDAO] Transaction failed, rolled back. Error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

        } catch (SQLException e) {
            System.err.println("[AllocationDAO] Could not get DB connection: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}