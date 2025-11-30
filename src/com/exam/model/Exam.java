package com.exam.model;

public class Exam {
    private long id;
    private long courseId;
    private String courseCode; // Convenience field (joined from courses table)

    public Exam(long id, long courseId, String courseCode) {
        this.id = id;
        this.courseId = courseId;
        this.courseCode = courseCode;
    }

    public long getId() { return id; }
    public long getCourseId() { return courseId; }
    public String getCourseCode() { return courseCode; }
}