package org.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private static final String URL = "jdbc:mysql://localhost:3308/pokedex";
    private static final String USER = "root";
    private static final String PASSWORD = "root";

    private final Map<Integer, Pokemon> pokemonCache = new HashMap<>();

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean registerUser(String username, String password) {
        String query = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashPassword(password));
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int authenticateUser(String username, String password) {
        // Updated to match your exported schema
        String query = "SELECT user_id FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashPassword(password));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("user_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public int getTotalPokemonCount() {
        String query = "SELECT COUNT(*) FROM pokemon";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query); ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<String> getAllPokemonNames() {
        List<String> names = new ArrayList<>();
        String query = "SELECT name FROM pokemon";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query); ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) names.add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
        return names;
    }

    public Pokemon getPokemonByName(String searchName) {
        String query = "SELECT * FROM pokemon WHERE name = ?";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, searchName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapResultSetToPokemon(rs);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public Pokemon getAdjacentPokemon(int currentId, boolean isNext) {
        String operator = isNext ? ">" : "<"; String order = isNext ? "ASC" : "DESC";
        String query = "SELECT * FROM pokemon WHERE number " + operator + " ? ORDER BY number " + order + " LIMIT 1";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setInt(1, currentId);
            try (ResultSet rs = pstmt.executeQuery()) { if (rs.next()) return mapResultSetToPokemon(rs); }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<Pokemon> searchPokemonWithFilters(String namePart, String type) {
        List<Pokemon> results = new ArrayList<>();
        StringBuilder query = new StringBuilder("SELECT * FROM pokemon WHERE 1=1");
        List<Object> parameters = new ArrayList<>();

        if (namePart != null && !namePart.trim().isEmpty()) {
            query.append(" AND name LIKE ?"); parameters.add("%" + namePart.trim() + "%");
        }
        if (type != null && !type.equals("All")) {
            query.append(" AND (type_1 = ? OR type_2 = ?)"); parameters.add(type); parameters.add(type);
        }
        query.append(" ORDER BY number ASC");

        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query.toString())) {
            for (int i = 0; i < parameters.size(); i++) pstmt.setObject(i + 1, parameters.get(i));
            try (ResultSet rs = pstmt.executeQuery()) { while (rs.next()) results.add(mapResultSetToPokemon(rs)); }
        } catch (SQLException e) { e.printStackTrace(); }
        return results;
    }

    private Pokemon mapResultSetToPokemon(ResultSet rs) throws SQLException {
        int id = rs.getInt("number");
        if (pokemonCache.containsKey(id)) return pokemonCache.get(id);
        Pokemon p = new Pokemon(id, rs.getString("name"), rs.getString("type_1"), rs.getString("type_2"), rs.getString("description"), rs.getInt("hp"), rs.getInt("attack"), rs.getInt("defense"), rs.getInt("speed"), rs.getDouble("height"), rs.getDouble("weight"));
        pokemonCache.put(id, p); return p;
    }

    public static class Team {
        private int id; private String name;
        public Team(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; } public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    public static class TeamAnalytics {
        public int teamSize; public int totalBST;
        public double avgAttack; public double avgDefense; public double avgSpeed;
    }

    public List<Team> getAllTeams(int userId) {
        List<Team> teams = new ArrayList<>();
        String query = "SELECT * FROM teams WHERE user_id = ? ORDER BY team_name ASC";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setInt(1, userId); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) teams.add(new Team(rs.getInt("team_id"), rs.getString("team_name")));
        } catch (SQLException e) { e.printStackTrace(); }
        return teams;
    }

    public boolean createTeam(int userId, String teamName) {
        String query = "INSERT INTO teams (team_name, user_id) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, teamName); pstmt.setInt(2, userId); return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public String addPokemonToTeam(int teamId, int pokemonId) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM team_members WHERE team_id = ?")) {
            pstmt.setInt(1, teamId); ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) >= 6) return "Team is full! (Max 6)";
        } catch (SQLException e) { return "Database error."; }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("INSERT INTO team_members (team_id, pokemon_id) VALUES (?, ?)")) {
            pstmt.setInt(1, teamId); pstmt.setInt(2, pokemonId); pstmt.executeUpdate(); return "Success";
        } catch (SQLException e) { return "Database error."; }
    }

    public List<Pokemon> getTeamMembers(int teamId) {
        List<Pokemon> members = new ArrayList<>();
        String query = "SELECT p.* FROM pokemon p JOIN team_members tm ON p.number = tm.pokemon_id WHERE tm.team_id = ?";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setInt(1, teamId);
            try (ResultSet rs = pstmt.executeQuery()) { while (rs.next()) members.add(mapResultSetToPokemon(rs)); }
        } catch (SQLException e) { e.printStackTrace(); }
        return members;
    }

    public boolean removePokemonFromTeam(int teamId, int pokemonId) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ? AND pokemon_id = ? LIMIT 1")) {
            pstmt.setInt(1, teamId); pstmt.setInt(2, pokemonId); return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean deleteTeam(int teamId) {
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM teams WHERE team_id = ?")) {
            pstmt.setInt(1, teamId); return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public TeamAnalytics getTeamAnalytics(int teamId) {
        TeamAnalytics stats = new TeamAnalytics();
        String query = "SELECT COUNT(p.number) as team_size, SUM(p.hp + p.attack + p.defense + p.speed) as total_bst, " +
                "AVG(p.attack) as avg_attack, AVG(p.defense) as avg_defense, AVG(p.speed) as avg_speed " +
                "FROM pokemon p JOIN team_members tm ON p.number = tm.pokemon_id WHERE tm.team_id = ?";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setInt(1, teamId); ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                stats.teamSize = rs.getInt("team_size"); stats.totalBST = rs.getInt("total_bst");
                stats.avgAttack = rs.getDouble("avg_attack"); stats.avgDefense = rs.getDouble("avg_defense"); stats.avgSpeed = rs.getDouble("avg_speed");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return stats;
    }

    public String getTeamTypeDistribution(int teamId) {
        StringBuilder dist = new StringBuilder();
        String query = "SELECT type_1, COUNT(*) as count FROM pokemon p JOIN team_members tm ON p.number = tm.pokemon_id " +
                "WHERE tm.team_id = ? GROUP BY type_1 ORDER BY count DESC";
        try (Connection con = getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setInt(1, teamId); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) dist.append(rs.getString("type_1")).append(": ").append(rs.getInt("count")).append("   ");
        } catch (SQLException e) { e.printStackTrace(); }
        return dist.toString();
    }
}