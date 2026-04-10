-- Turfzy Database Initialization
-- Full schema will be added incrementally as we build each feature

CREATE DATABASE IF NOT EXISTS turfzy_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE turfzy_db;

-- We'll add tables starting Day 2 via JPA @Entity classes
-- This file seeds the DB with the database + encoding only for now
SELECT 'Turfzy DB initialized successfully' AS status;