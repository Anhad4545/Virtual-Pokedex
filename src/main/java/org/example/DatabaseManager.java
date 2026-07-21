package org.example;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class DatabaseManager {
    private static volatile HikariDataSource dataSource;
    private static synchronized HikariDataSource getPool() throws SQLException {
        if (dataSource == null) {
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://localhost:3308/pokedex?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
                config.setUsername("root");
                config.setPassword("root");
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(10_000);  
                config.setIdleTimeout(600_000);
                config.setMaxLifetime(1_800_000);
                config.setConnectionTestQuery("SELECT 1");
                dataSource = new HikariDataSource(config);
            } catch (Exception e) {
                throw new SQLException("Cannot connect to database: " + e.getMessage(), e);
            }
        }
        return dataSource;
    }
    private Connection getConnection() throws SQLException {
        return getPool().getConnection();
    }
    private final Map<Integer, Pokemon> pokemonCache = new HashMap<>();
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    public boolean registerUser(String username, String password) {
        final String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public int authenticateUser(String username, String password) {
        final String sql = "SELECT user_id FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
    public int getTotalPokemonCount() {
        final String sql = "SELECT COUNT(*) FROM pokemon";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    public List<String> getAllPokemonNames() {
        List<String> names = new ArrayList<>();
        final String sql = "SELECT name FROM pokemon ORDER BY number ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) names.add(rs.getString("name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return names;
    }
    public Pokemon getPokemonByName(String searchName) {
        final String sql = "SELECT * FROM pokemon WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, searchName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToPokemon(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public Pokemon getAdjacentPokemon(int currentId, boolean isNext) {
        String operator = isNext ? ">" : "<";
        String order    = isNext ? "ASC" : "DESC";
        final String sql = "SELECT * FROM pokemon WHERE number " + operator + " ? ORDER BY number " + order + " LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToPokemon(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public List<Pokemon> searchPokemonWithFilters(String namePart, String type) {
        List<Pokemon> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM pokemon WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (namePart != null && !namePart.trim().isEmpty()) {
            sql.append(" AND name LIKE ?");
            params.add("%" + namePart.trim() + "%");
        }
        if (type != null && !type.equals("All")) {
            sql.append(" AND (type_1 = ? OR type_2 = ?)");
            params.add(type);
            params.add(type);
        }
        sql.append(" ORDER BY number ASC");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapResultSetToPokemon(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }
    public static class PokemonTypeRank {
        public int typeRank;
        public int totalInType;
        public int overallRank;
        public int totalOverall;
    }
    public PokemonTypeRank getTypeRankingsWindowFunction(int pokemonNumber) {
        PokemonTypeRank rank = new PokemonTypeRank();
        final String sql =
                "WITH ranked_pokemon AS (" +
                "    SELECT number, " +
                "           RANK() OVER (PARTITION BY type_1 ORDER BY (hp + attack + defense + speed) DESC) AS type_rank, " +
                "           COUNT(*) OVER (PARTITION BY type_1) AS total_in_type, " +
                "           RANK() OVER (ORDER BY (hp + attack + defense + speed) DESC) AS overall_rank, " +
                "           COUNT(*) OVER () AS total_overall " +
                "    FROM pokemon" +
                ") " +
                "SELECT * FROM ranked_pokemon WHERE number = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pokemonNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rank.typeRank     = rs.getInt("type_rank");
                    rank.totalInType  = rs.getInt("total_in_type");
                    rank.overallRank  = rs.getInt("overall_rank");
                    rank.totalOverall = rs.getInt("total_overall");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rank;
    }
    public static class EvolutionInfo {
        public String baseName;
        public String evolvedName;
        public int    minLevel;
    }
    public List<EvolutionInfo> getEvolutionChain(int pokemonNumber) {
        List<EvolutionInfo> chain = new ArrayList<>();
        final String sql =
                "SELECT p1.name AS base_name, p2.name AS evo_name, e.minimum_level " +
                "FROM evolution e " +
                "JOIN pokemon p1 ON e.number = p1.number " +
                "JOIN pokemon p2 ON e.evolved_species_id = p2.number " +
                "WHERE e.number = ? OR e.evolved_species_id = ? " +
                "ORDER BY e.minimum_level ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pokemonNumber);
            ps.setInt(2, pokemonNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EvolutionInfo info = new EvolutionInfo();
                    info.baseName    = rs.getString("base_name");
                    info.evolvedName = rs.getString("evo_name");
                    info.minLevel    = rs.getInt("minimum_level");
                    chain.add(info);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return chain;
    }
    public boolean duplicateTeamTransaction(int userId, int sourceTeamId, String newTeamName) {
        final String createTeamSql = "INSERT INTO teams (team_name, user_id) VALUES (?, ?)";
        final String copyMembersSql = "INSERT INTO team_members (team_id, pokemon_id) SELECT ?, pokemon_id FROM team_members WHERE team_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); 
            int newTeamId = -1;
            try (PreparedStatement ps = conn.prepareStatement(createTeamSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, newTeamName);
                ps.setInt(2, userId);
                if (ps.executeUpdate() <= 0) {
                    conn.rollback();
                    return false;
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) newTeamId = keys.getInt(1);
                }
            }
            if (newTeamId != -1) {
                try (PreparedStatement ps2 = conn.prepareStatement(copyMembersSql)) {
                    ps2.setInt(1, newTeamId);
                    ps2.setInt(2, sourceTeamId);
                    ps2.executeUpdate();
                }
            }
            conn.commit(); 
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }
    public List<Pokemon> getTopPokemonByStat(String statName, int limit) {
        List<Pokemon> results = new ArrayList<>();
        String column = "hp";
        if (statName.equalsIgnoreCase("Attack")) column = "attack";
        else if (statName.equalsIgnoreCase("Defense")) column = "defense";
        else if (statName.equalsIgnoreCase("Speed")) column = "speed";
        else if (statName.equalsIgnoreCase("Weight")) column = "weight";
        final String sql = "SELECT * FROM pokemon ORDER BY " + column + " DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToPokemon(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }
    private Pokemon mapResultSetToPokemon(ResultSet rs) throws SQLException {
        int id = rs.getInt("number");
        if (pokemonCache.containsKey(id)) return pokemonCache.get(id);
        Pokemon p = new Pokemon(
                id,
                rs.getString("name"),
                rs.getString("type_1"),
                rs.getString("type_2"),
                rs.getString("description"),
                rs.getInt("hp"),
                rs.getInt("attack"),
                rs.getInt("defense"),
                rs.getInt("speed"),
                rs.getDouble("height"),
                rs.getDouble("weight")
        );
        pokemonCache.put(id, p);
        return p;
    }
    public static class Team {
        private final int id;
        private final String name;
        public Team(int id, String name) { this.id = id; this.name = name; }
        public int getId()    { return id; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
    }
    public static class TeamAnalytics {
        public int    teamSize;
        public int    totalBST;
        public double avgAttack;
        public double avgDefense;
        public double avgSpeed;
    }
    public List<Team> getAllTeams(int userId) {
        List<Team> teams = new ArrayList<>();
        final String sql = "SELECT * FROM teams WHERE user_id = ? ORDER BY team_name ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) teams.add(new Team(rs.getInt("team_id"), rs.getString("team_name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return teams;
    }
    public boolean createTeam(int userId, String teamName) {
        final String sql = "INSERT INTO teams (team_name, user_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamName);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
    public String addPokemonToTeam(int teamId, int pokemonId) {
        final String countSql  = "SELECT COUNT(*) FROM team_members WHERE team_id = ?";
        final String insertSql = "INSERT INTO team_members (team_id, pokemon_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement countPs = conn.prepareStatement(countSql)) {
            countPs.setInt(1, teamId);
            try (ResultSet rs = countPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= 6) return "Team is full! (Max 6)";
            }
        } catch (SQLException e) {
            return "Database error.";
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, pokemonId);
            ps.executeUpdate();
            return "Success";
        } catch (SQLException e) {
            return "Database error.";
        }
    }
    public List<Pokemon> getTeamMembers(int teamId) {
        List<Pokemon> members = new ArrayList<>();
        final String sql =
                "SELECT p.* FROM pokemon p " +
                "JOIN team_members tm ON p.number = tm.pokemon_id " +
                "WHERE tm.team_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) members.add(mapResultSetToPokemon(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }
    public boolean removePokemonFromTeam(int teamId, int pokemonId) {
        final String sql = "DELETE FROM team_members WHERE team_id = ? AND pokemon_id = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, pokemonId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
    public boolean deleteTeam(int teamId) {
        final String sql = "DELETE FROM teams WHERE team_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
    public TeamAnalytics getTeamAnalytics(int teamId) {
        TeamAnalytics stats = new TeamAnalytics();
        final String sql =
                "SELECT COUNT(p.number) AS team_size, " +
                "       SUM(p.hp + p.attack + p.defense + p.speed) AS total_bst, " +
                "       AVG(p.attack)  AS avg_attack, " +
                "       AVG(p.defense) AS avg_defense, " +
                "       AVG(p.speed)   AS avg_speed " +
                "FROM pokemon p " +
                "JOIN team_members tm ON p.number = tm.pokemon_id " +
                "WHERE tm.team_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.teamSize   = rs.getInt("team_size");
                    stats.totalBST   = rs.getInt("total_bst");
                    stats.avgAttack  = rs.getDouble("avg_attack");
                    stats.avgDefense = rs.getDouble("avg_defense");
                    stats.avgSpeed   = rs.getDouble("avg_speed");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }
    public Map<String, Integer> getTeamTypeDistributionMap(int teamId) {
        Map<String, Integer> dist = new java.util.LinkedHashMap<>();
        final String sql =
                "SELECT type_1, COUNT(*) AS count " +
                "FROM pokemon p " +
                "JOIN team_members tm ON p.number = tm.pokemon_id " +
                "WHERE tm.team_id = ? " +
                "GROUP BY type_1 ORDER BY count DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) dist.put(rs.getString("type_1"), rs.getInt("count"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dist;
    }
}