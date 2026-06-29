package org.example;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import java.io.File;

public class PokedexUI extends JFrame {

    private static final Color POKEDEX_RED = new Color(220, 10, 45);
    private static final Color POKEDEX_BLUE = new Color(50, 100, 200);

    private JTextField searchField;
    private JPopupMenu suggestionMenu;

    private Timer mainSearchTimer, compareP1Timer, compareP2Timer;

    private JComboBox<String> typeFilterCombo;
    private JLabel nameLabel, typeLabel, imageLabel, totalCountLabel, userWelcomeLabel;
    private JLabel hpLabel, attackLabel, defenseLabel;
    private JTextArea descriptionArea;
    private JButton searchButton, prevButton, nextButton;
    private TypeBackgroundPanel screenPanel;

    private DatabaseManager db;
    private PokemonTrie trie;
    private Pokemon currentPokemon;
    private List<Pokemon> currentSearchResults;
    private int currentResultIndex = -1;

    private int loggedInUserId = -1;
    private String loggedInUsername = "";

    private JComboBox<DatabaseManager.Team> teamComboBox;
    private JButton createTeamBtn, addToTeamBtn, viewTeamBtn, compareBtn;

    public PokedexUI() {
        db = new DatabaseManager();

        if (!promptAuthentication()) System.exit(0);

        trie = new PokemonTrie();
        List<String> allNames = db.getAllPokemonNames();
        for (String name : allNames) {
            trie.insert(name);
        }

        setTitle("Java Pokedex ");
        setSize(400, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(15, 15, 15));

        userWelcomeLabel = new JLabel("Trainer: " + loggedInUsername);
        userWelcomeLabel.setForeground(Color.GREEN);
        userWelcomeLabel.setBounds(10, 5, 200, 20);
        add(userWelcomeLabel);

        JLabel titleLabel = new JLabel("POKÉDEX");
        titleLabel.setForeground(POKEDEX_RED); titleLabel.setFont(new Font("Impact", Font.BOLD, 36));
        titleLabel.setBounds(130, 20, 200, 40); add(titleLabel);

        totalCountLabel = new JLabel("Total Pokémon: " + allNames.size());
        totalCountLabel.setForeground(Color.LIGHT_GRAY); totalCountLabel.setBounds(100, 60, 200, 20);
        totalCountLabel.setHorizontalAlignment(SwingConstants.CENTER); add(totalCountLabel);

        searchField = new JTextField();
        searchField.setBounds(40, 90, 120, 35);
        add(searchField);

        suggestionMenu = new JPopupMenu();
        suggestionMenu.setFocusable(false);

        mainSearchTimer = new Timer(250, e -> updateMainSuggestions());
        mainSearchTimer.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { triggerDebounce(mainSearchTimer); }
            public void removeUpdate(DocumentEvent e) { triggerDebounce(mainSearchTimer); }
            public void changedUpdate(DocumentEvent e) { triggerDebounce(mainSearchTimer); }
        });

        String[] types = {"All", "Normal", "Fire", "Water", "Grass", "Electric", "Ice", "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon"};
        typeFilterCombo = new JComboBox<>(types);
        typeFilterCombo.setBounds(170, 90, 90, 35);
        add(typeFilterCombo);

        searchButton = new JButton("GO");
        searchButton.setBounds(270, 90, 80, 35);
        searchButton.setBackground(POKEDEX_RED); searchButton.setForeground(Color.WHITE);
        add(searchButton);

        screenPanel = new TypeBackgroundPanel(); screenPanel.setBounds(40, 140, 310, 460); screenPanel.setLayout(null); screenPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 3)); add(screenPanel);

        nameLabel = new JLabel("Search a Pokémon!"); nameLabel.setBounds(0, 15, 310, 30); nameLabel.setHorizontalAlignment(SwingConstants.CENTER); nameLabel.setFont(new Font("Arial", Font.BOLD, 22)); screenPanel.add(nameLabel);
        imageLabel = new JLabel(); imageLabel.setBounds(55, 60, 200, 200); screenPanel.add(imageLabel);

        prevButton = new JButton("<"); prevButton.setBounds(10, 140, 35, 35); prevButton.setBackground(new Color(20, 20, 20)); prevButton.setForeground(Color.WHITE); screenPanel.add(prevButton);
        nextButton = new JButton(">"); nextButton.setBounds(265, 140, 35, 35); nextButton.setBackground(new Color(20, 20, 20)); nextButton.setForeground(Color.WHITE); screenPanel.add(nextButton);

        typeLabel = new JLabel(""); typeLabel.setBounds(0, 45, 310, 20); typeLabel.setHorizontalAlignment(SwingConstants.CENTER); screenPanel.add(typeLabel);
        hpLabel = new JLabel("HP: -"); hpLabel.setBounds(30, 280, 100, 20); screenPanel.add(hpLabel);
        attackLabel = new JLabel("Atk: -"); attackLabel.setBounds(120, 280, 100, 20); screenPanel.add(attackLabel);
        defenseLabel = new JLabel("Def: -"); defenseLabel.setBounds(210, 280, 100, 20); screenPanel.add(defenseLabel);
        descriptionArea = new JTextArea(); descriptionArea.setBounds(30, 310, 250, 120); descriptionArea.setLineWrap(true); descriptionArea.setWrapStyleWord(true); descriptionArea.setEditable(false); descriptionArea.setOpaque(false); screenPanel.add(descriptionArea);

        JLabel teamLabel = new JLabel("Team:"); teamLabel.setBounds(40, 620, 40, 30); teamLabel.setForeground(Color.WHITE); add(teamLabel);
        teamComboBox = new JComboBox<>(); teamComboBox.setBounds(85, 620, 100, 30); refreshTeamDropdown(); add(teamComboBox);
        createTeamBtn = new JButton("+"); createTeamBtn.setBounds(190, 620, 45, 30); createTeamBtn.setBackground(POKEDEX_BLUE); createTeamBtn.setForeground(Color.WHITE); add(createTeamBtn);
        addToTeamBtn = new JButton("Add"); addToTeamBtn.setBounds(240, 620, 60, 30); add(addToTeamBtn);

        viewTeamBtn = new JButton("View"); viewTeamBtn.setBounds(305, 620, 65, 30); add(viewTeamBtn);

        compareBtn = new JButton("Compare");
        compareBtn.setBounds(40, 660, 330, 35);

        compareBtn.setBackground(new Color(255, 170, 1));
        compareBtn.setForeground(Color.WHITE);
        compareBtn.setFont(new Font("Arial", Font.BOLD, 14));
        add(compareBtn);

        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());
        prevButton.addActionListener(e -> navigateResults(false)); nextButton.addActionListener(e -> navigateResults(true));
        createTeamBtn.addActionListener(e -> createNewTeam()); addToTeamBtn.addActionListener(e -> addCurrentToTeam()); viewTeamBtn.addActionListener(e -> viewCurrentTeam());

        compareBtn.addActionListener(e -> showCompareDialog());

        setVisible(true);
        performSearch();
    }

    private void showCompareDialog() {
        JDialog dialog = new JDialog(this, "Versus Arena", true);
        dialog.setSize(500, 450);
        dialog.setLayout(null);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(new Color(20, 20, 20));

        JLabel title = new JLabel("VS", SwingConstants.CENTER);
        title.setFont(new Font("Impact", Font.ITALIC, 36));
        title.setForeground(Color.YELLOW);
        title.setBounds(200, 20, 100, 40);
        dialog.add(title);

        JTextField p1Field = new JTextField(); p1Field.setBounds(30, 20, 150, 30); dialog.add(p1Field);
        JTextField p2Field = new JTextField(); p2Field.setBounds(310, 20, 150, 30); dialog.add(p2Field);

        JPopupMenu p1Menu = new JPopupMenu(); p1Menu.setFocusable(false);
        JPopupMenu p2Menu = new JPopupMenu(); p2Menu.setFocusable(false);

        compareP1Timer = new Timer(250, e -> updateGenericSuggestions(p1Field, p1Menu));
        compareP1Timer.setRepeats(false);

        compareP2Timer = new Timer(250, e -> updateGenericSuggestions(p2Field, p2Menu));
        compareP2Timer.setRepeats(false);

        p1Field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { triggerDebounce(compareP1Timer); }
            public void removeUpdate(DocumentEvent e) { triggerDebounce(compareP1Timer); }
            public void changedUpdate(DocumentEvent e) { triggerDebounce(compareP1Timer); }
        });

        p2Field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { triggerDebounce(compareP2Timer); }
            public void removeUpdate(DocumentEvent e) { triggerDebounce(compareP2Timer); }
            public void changedUpdate(DocumentEvent e) { triggerDebounce(compareP2Timer); }
        });

        JButton fightBtn = new JButton("FIGHT!");
        fightBtn.setBounds(200, 70, 100, 30);

        fightBtn.setBackground(new Color(255, 140, 0));
        fightBtn.setForeground(Color.WHITE);
        dialog.add(fightBtn);

        JPanel statsPanel = new JPanel(new GridLayout(6, 3, 10, 10));
        statsPanel.setBounds(30, 120, 430, 200);
        statsPanel.setBackground(new Color(20, 20, 20));
        dialog.add(statsPanel);

        fightBtn.addActionListener(e -> {
            statsPanel.removeAll();
            p1Menu.setVisible(false); p2Menu.setVisible(false);

            Pokemon p1 = db.getPokemonByName(p1Field.getText().trim());
            Pokemon p2 = db.getPokemonByName(p2Field.getText().trim());

            if (p1 == null || p2 == null) {
                JOptionPane.showMessageDialog(dialog, "Could not find one or both Pokémon. Check spelling!");
                return;
            }

            statsPanel.add(createStatLabel(p1.getName(), Color.WHITE, true));
            statsPanel.add(createStatLabel("STAT", Color.LIGHT_GRAY, true));
            statsPanel.add(createStatLabel(p2.getName(), Color.WHITE, true));

            addStatRow(statsPanel, "HP", p1.getHp(), p2.getHp());
            addStatRow(statsPanel, "Attack", p1.getAttack(), p2.getAttack());
            addStatRow(statsPanel, "Defense", p1.getDefense(), p2.getDefense());
            addStatRow(statsPanel, "Speed", p1.getSpeed(), p2.getSpeed());

            int p1Total = p1.getHp() + p1.getAttack() + p1.getDefense() + p1.getSpeed();
            int p2Total = p2.getHp() + p2.getAttack() + p2.getDefense() + p2.getSpeed();
            addStatRow(statsPanel, "TOTAL BST", p1Total, p2Total);

            statsPanel.revalidate();
            statsPanel.repaint();
        });

        dialog.setVisible(true);
    }

    private void addStatRow(JPanel panel, String statName, int val1, int val2) {
        Color p1Color = (val1 > val2) ? Color.GREEN : (val1 == val2) ? Color.WHITE : Color.RED;
        Color p2Color = (val2 > val1) ? Color.GREEN : (val1 == val2) ? Color.WHITE : Color.RED;
        panel.add(createStatLabel(String.valueOf(val1), p1Color, false));
        panel.add(createStatLabel(statName, Color.LIGHT_GRAY, true));
        panel.add(createStatLabel(String.valueOf(val2), p2Color, false));
    }

    private JLabel createStatLabel(String text, Color color, boolean isBold) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(color);
        label.setFont(new Font("Arial", isBold ? Font.BOLD : Font.PLAIN, 16));
        return label;
    }


    private void triggerDebounce(Timer timer) {
        if (timer.isRunning()) timer.restart();
        else timer.start();
    }

    private void updateMainSuggestions() {
        updateGenericSuggestions(searchField, suggestionMenu);
    }

    private void updateGenericSuggestions(JTextField field, JPopupMenu menu) {
        SwingUtilities.invokeLater(() -> {
            String query = field.getText().trim();
            menu.setVisible(false);
            menu.removeAll();
            if (query.isEmpty()) return;

            List<String> suggestions = trie.getSuggestions(query);
            if (suggestions.isEmpty()) return;

            for (String suggestion : suggestions) {
                JMenuItem item = new JMenuItem(suggestion);
                item.addActionListener(e -> {
                    field.setText(suggestion);
                    menu.setVisible(false);
                    if (field == searchField) performSearch();
                });
                menu.add(item);
            }
            menu.show(field, 0, field.getHeight());
            field.requestFocusInWindow();
        });
    }

    private class PokemonTrie {
        class TrieNode {
            Map<Character, TrieNode> children = new TreeMap<>();
            boolean isEndOfWord = false;
        }
        private final TrieNode root = new TrieNode();
        public void insert(String word) {
            TrieNode current = root;
            for (char ch : word.toLowerCase().toCharArray()) {
                current.children.putIfAbsent(ch, new TrieNode());
                current = current.children.get(ch);
            }
            current.isEndOfWord = true;
        }
        public List<String> getSuggestions(String prefix) {
            List<String> results = new ArrayList<>();
            TrieNode current = root;
            String lowerPrefix = prefix.toLowerCase();
            for (char ch : lowerPrefix.toCharArray()) {
                if (!current.children.containsKey(ch)) return results;
                current = current.children.get(ch);
            }
            dfs(current, lowerPrefix, results);
            return results;
        }
        private void dfs(TrieNode node, String currentWord, List<String> results) {
            if (results.size() >= 8) return;
            if (node.isEndOfWord) results.add(capitalize(currentWord));
            for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
                dfs(entry.getValue(), currentWord + entry.getKey(), results);
            }
        }
        private String capitalize(String str) {
            if (str == null || str.isEmpty()) return str;
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }

    private boolean promptAuthentication() {
        while (true) {
            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            JTextField userField = new JTextField(); JPasswordField passField = new JPasswordField();
            panel.add(new JLabel("Username:")); panel.add(userField); panel.add(new JLabel("Password:")); panel.add(passField);
            String[] options = {"Login", "Register", "Cancel"};
            int result = JOptionPane.showOptionDialog(null, panel, "Pokédex Network Login", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (result == 2 || result == JOptionPane.CLOSED_OPTION) return false;
            String user = userField.getText().trim(); String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(null, "Credentials cannot be empty."); continue; }
            if (result == 0) {
                int id = db.authenticateUser(user, pass);
                if (id != -1) { loggedInUserId = id; loggedInUsername = user; return true; }
                else JOptionPane.showMessageDialog(null, "Invalid username or password.", "Error", JOptionPane.ERROR_MESSAGE);
            } else if (result == 1) {
                if (db.registerUser(user, pass)) JOptionPane.showMessageDialog(null, "Registration successful! You can now log in.");
                else JOptionPane.showMessageDialog(null, "Username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void refreshTeamDropdown() {
        teamComboBox.removeAllItems();
        List<DatabaseManager.Team> teams = db.getAllTeams(loggedInUserId);
        for (DatabaseManager.Team t : teams) teamComboBox.addItem(t);
    }
    private void createNewTeam() {
        String name = JOptionPane.showInputDialog(this, "Enter a name for your new team:");
        if (name != null && !name.trim().isEmpty()) {
            if (db.createTeam(loggedInUserId, name.trim())) { refreshTeamDropdown(); JOptionPane.showMessageDialog(this, "Team '" + name + "' created!"); }
            else JOptionPane.showMessageDialog(this, "Failed. Database error.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void addCurrentToTeam() {
        if (currentPokemon == null) return;
        DatabaseManager.Team selectedTeam = (DatabaseManager.Team) teamComboBox.getSelectedItem();
        if (selectedTeam == null) return;
        String result = db.addPokemonToTeam(selectedTeam.getId(), currentPokemon.getId());
        if (result.equals("Success")) JOptionPane.showMessageDialog(this, "Added to " + selectedTeam.getName() + "!");
        else JOptionPane.showMessageDialog(this, result, "Failed", JOptionPane.ERROR_MESSAGE);
    }

    private void viewCurrentTeam() {
        DatabaseManager.Team selectedTeam = (DatabaseManager.Team) teamComboBox.getSelectedItem();
        if (selectedTeam == null) return;
        List<Pokemon> members = db.getTeamMembers(selectedTeam.getId());
        DefaultListModel<Pokemon> model = new DefaultListModel<>();
        for (Pokemon p : members) model.addElement(p);
        JList<Pokemon> list = new JList<>(model);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Pokemon) { Pokemon p = (Pokemon) value; setText("#" + p.getId() + " - " + p.getName() + " (" + p.getType1() + ")"); }
                return this;
            }
        });
        JPanel panel = new JPanel(new BorderLayout()); panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JButton analyzeBtn = new JButton("Analyze"); analyzeBtn.setBackground(new Color(40, 160, 80)); analyzeBtn.setForeground(Color.WHITE);
        JButton removeBtn = new JButton("Remove");
        JButton deleteTeamBtn = new JButton("Delete"); deleteTeamBtn.setBackground(POKEDEX_RED); deleteTeamBtn.setForeground(Color.WHITE);
        JPanel btnPanel = new JPanel(); btnPanel.add(analyzeBtn); btnPanel.add(removeBtn); btnPanel.add(deleteTeamBtn); panel.add(btnPanel, BorderLayout.SOUTH);
        JDialog dialog = new JDialog(this, selectedTeam.getName() + " - Roster", true); dialog.setContentPane(panel); dialog.setSize(320, 400); dialog.setLocationRelativeTo(this);

        analyzeBtn.addActionListener(e -> {
            DatabaseManager.TeamAnalytics analytics = db.getTeamAnalytics(selectedTeam.getId());
            if (analytics.teamSize == 0) return;
            String report = String.format("Size: %d/6\nTotal Power: %d\nAvg Atk: %.1f\nAvg Def: %.1f\nAvg Spd: %.1f\n\nTypes:\n%s",
                    analytics.teamSize, analytics.totalBST, analytics.avgAttack, analytics.avgDefense, analytics.avgSpeed, db.getTeamTypeDistribution(selectedTeam.getId()));
            JOptionPane.showMessageDialog(dialog, report, "Analytics", JOptionPane.INFORMATION_MESSAGE);
        });
        removeBtn.addActionListener(e -> {
            Pokemon selected = list.getSelectedValue();
            if (selected != null && db.removePokemonFromTeam(selectedTeam.getId(), selected.getId())) model.removeElement(selected);
        });
        deleteTeamBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(dialog, "Delete team?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION && db.deleteTeam(selectedTeam.getId())) { dialog.dispose(); refreshTeamDropdown(); }
        });
        dialog.setVisible(true);
    }
    private void performSearch() {
        String namePart = searchField.getText().trim(); String typeFilter = (String) typeFilterCombo.getSelectedItem();
        currentSearchResults = db.searchPokemonWithFilters(namePart, typeFilter);
        suggestionMenu.setVisible(false);
        if (currentSearchResults != null && !currentSearchResults.isEmpty()) {
            currentResultIndex = 0; updateUI(currentSearchResults.get(currentResultIndex)); loadImage(currentSearchResults.get(currentResultIndex).getId());
        } else { nameLabel.setText("Not Found"); imageLabel.setIcon(null); typeLabel.setText(""); }
    }
    private void navigateResults(boolean isNext) {
        if (currentPokemon == null) return;
        boolean hasActiveFilters = !searchField.getText().trim().isEmpty() || !"All".equals(typeFilterCombo.getSelectedItem());
        if (hasActiveFilters && currentSearchResults != null && !currentSearchResults.isEmpty()) {
            currentResultIndex = isNext ? (currentResultIndex + 1) % currentSearchResults.size() : (currentResultIndex - 1 + currentSearchResults.size()) % currentSearchResults.size();
            Pokemon p = currentSearchResults.get(currentResultIndex); updateUI(p); loadImage(p.getId());
        } else {
            Pokemon adjacent = db.getAdjacentPokemon(currentPokemon.getId(), isNext);
            if (adjacent != null) { updateUI(adjacent); loadImage(adjacent.getId()); }
        }
    }
    private void updateUI(Pokemon p) {
        currentPokemon = p; nameLabel.setText("#" + p.getId() + " - " + p.getName());
        typeLabel.setText((p.getType2() != null && !p.getType2().trim().isEmpty()) ? p.getType1() + " / " + p.getType2() : p.getType1());
        hpLabel.setText("HP: " + p.getHp()); attackLabel.setText("Atk: " + p.getAttack()); defenseLabel.setText("Def: " + p.getDefense());
        descriptionArea.setText(p.getDescription() != null ? p.getDescription() : "No info."); screenPanel.setTypeBackground(p.getType1());
    }
    private void loadImage(int id) {
        SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                return new ImageIcon(ImageIO.read(new URL("https://raw.githubusercontent.com/poketwo/data/master/images/" + id + ".png")).getScaledInstance(200, 200, Image.SCALE_SMOOTH));
            }
            @Override protected void done() { try { imageLabel.setIcon(get()); } catch (Exception e) {} }
        }; worker.execute();
    }
}

class TypeBackgroundPanel extends JPanel {
    private Image bg;
    public void setTypeBackground(String type) {
        try { bg = ImageIO.read(new File("backgrounds/" + type.toLowerCase() + ".png")); } catch (Exception e) { bg = null; } repaint();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(bg != null) g.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
        g.setColor(new Color(0,0,0,150)); g.fillRect(0,0,getWidth(),getHeight());
    }
}