package com.exam.dao;

import com.exam.config.DBConnection;
import com.exam.model.Student;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StudentDAO {

    // CAPABILITY 1: Add a student
    public void addStudent(String rollNo, String name, String email, String branch) {
        String sql = "INSERT INTO students (roll_no, name, email, branch) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, rollNo);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, branch);

            pstmt.executeUpdate();
            System.out.println("Student " + name + " added.");

        } catch (SQLException e) {
            // We just print error, we don't crash.
            System.err.println("Error adding student: " + e.getMessage());
        }
    }

    // CAPABILITY 2: Find a student
    public Student getStudentByRollNo(String rollNo) {
        String sql = "SELECT * FROM students WHERE roll_no = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, rollNo);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Student(
                        rs.getLong("id"),
                        rs.getString("roll_no"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("branch")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}