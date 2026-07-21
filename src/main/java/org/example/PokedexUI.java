package org.example;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
public class PokedexUI extends JFrame {
    private static final Color POKEDEX_RED   = new Color(220, 10, 45);
    private static final Color POKEDEX_BLUE  = new Color(50, 100, 200);
    private static final Color BG_DARK       = new Color(15, 15, 15);
    private static final Color CARD_BG       = new Color(28, 28, 35);
    private static final Color ACCENT_GOLD   = new Color(255, 190, 0);
    private static final Font  FONT_TITLE    = new Font("Impact", Font.BOLD, 36);
    private static final Font  FONT_BODY     = new Font("Arial", Font.PLAIN, 14);
    private static final Font  FONT_BOLD     = new Font("Arial", Font.BOLD, 14);
    private JTextField searchField;
    private JPopupMenu suggestionMenu;
    private Timer      mainSearchTimer;
    private Timer      compareP1Timer;
    private Timer      compareP2Timer;
    private JComboBox<String>             typeFilterCombo;
    private JLabel                        nameLabel, typeLabel, imageLabel;
    private JLabel                        totalCountLabel, userWelcomeLabel;
    private JLabel                        hpLabel, attackLabel, defenseLabel;
    private JTextArea                     descriptionArea;
    private JButton                       searchButton, prevButton, nextButton;
    private TypeBackgroundPanel           screenPanel;
    private JComboBox<DatabaseManager.Team> teamComboBox;
    private JButton createTeamBtn, addToTeamBtn, dashboardBtn, compareBtn, analyticsBtn;
    private DatabaseManager  db;
    private PokemonTrie      trie;
    private Pokemon          currentPokemon;
    private List<Pokemon>    currentSearchResults;
    private int              currentResultIndex = -1;
    private int              loggedInUserId     = -1;
    private String           loggedInUsername   = "";
    public PokedexUI() {
        db = new DatabaseManager();
        if (!promptAuthentication()) System.exit(0);
        JWindow splash = buildSplash();
        splash.setVisible(true);
        buildStartupWorker(splash).execute();
    }
    private JWindow buildSplash() {
        JWindow splash = new JWindow();
        splash.setSize(340, 160);
        splash.setLocationRelativeTo(null);
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        panel.setBackground(CARD_BG);
        JLabel logo = new JLabel("POKÉDEX", SwingConstants.CENTER);
        logo.setFont(FONT_TITLE);
        logo.setForeground(POKEDEX_RED);
        panel.add(logo, BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar(0, 3);
        bar.setStringPainted(true);
        bar.setString("Connecting to database...");
        bar.setForeground(POKEDEX_RED);
        bar.setBackground(new Color(40, 40, 50));
        panel.add(bar, BorderLayout.CENTER);
        splash.setContentPane(panel);
        splash.getRootPane().putClientProperty("progressBar", bar);
        return splash;
    }
    private SwingWorker<List<String>, Integer> buildStartupWorker(JWindow splash) {
        JProgressBar bar = (JProgressBar) splash.getRootPane().getClientProperty("progressBar");
        return new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                publish(1);
                db.getTotalPokemonCount(); 
                publish(2);
                List<String> names = db.getAllPokemonNames();
                publish(3);
                trie = new PokemonTrie();
                for (String name : names) trie.insert(name);
                return names;
            }
            @Override
            protected void process(List<Integer> chunks) {
                int step = chunks.get(chunks.size() - 1);
                bar.setValue(step);
                switch (step) {
                    case 1 -> bar.setString("Warming up connection pool...");
                    case 2 -> bar.setString("Loading 1 000+ Pokémon records...");
                    case 3 -> bar.setString("Building prefix-tree index...");
                }
            }
            @Override
            protected void done() {
                try {
                    List<String> names = get();
                    splash.dispose();
                    buildMainUI(names.size());
                    setVisible(true);
                    performSearch();
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Failed to load data: " + e.getMessage());
                    System.exit(1);
                }
            }
        };
    }
    private void buildMainUI(int totalCount) {
        setTitle("Virtual Pokédex");
        setSize(420, 760);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        userWelcomeLabel = new JLabel("Trainer: " + loggedInUsername);
        userWelcomeLabel.setForeground(new Color(80, 220, 100));
        userWelcomeLabel.setFont(FONT_BODY);
        userWelcomeLabel.setBounds(10, 6, 200, 20);
        add(userWelcomeLabel);
        JLabel titleLabel = new JLabel("POKÉDEX");
        titleLabel.setForeground(POKEDEX_RED);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setBounds(130, 18, 210, 40);
        add(titleLabel);
        totalCountLabel = new JLabel("Total Pokémon: " + totalCount);
        totalCountLabel.setForeground(Color.LIGHT_GRAY);
        totalCountLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        totalCountLabel.setBounds(100, 58, 220, 18);
        totalCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(totalCountLabel);
        searchField = new JTextField();
        searchField.setBounds(40, 88, 120, 34);
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
        String[] types = {
            "All", "Normal", "Fire", "Water", "Grass", "Electric", "Ice",
            "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
            "Rock", "Ghost", "Dragon"
        };
        typeFilterCombo = new JComboBox<>(types);
        typeFilterCombo.setBounds(170, 88, 90, 34);
        add(typeFilterCombo);
        searchButton = new JButton("GO");
        searchButton.setBounds(270, 88, 80, 34);
        searchButton.setBackground(POKEDEX_RED);
        searchButton.setForeground(Color.WHITE);
        searchButton.setFont(FONT_BOLD);
        add(searchButton);
        screenPanel = new TypeBackgroundPanel();
        screenPanel.setBounds(40, 138, 330, 460);
        screenPanel.setLayout(null);
        screenPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 70), 2));
        add(screenPanel);
        nameLabel = new JLabel("Select a Pokémon!");
        nameLabel.setBounds(0, 14, 330, 30);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 22));
        nameLabel.setForeground(Color.WHITE);
        screenPanel.add(nameLabel);
        typeLabel = new JLabel("");
        typeLabel.setBounds(0, 44, 330, 20);
        typeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        typeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        typeLabel.setForeground(new Color(110, 210, 255)); 
        screenPanel.add(typeLabel);
        imageLabel = new JLabel();
        imageLabel.setBounds(65, 68, 200, 200);
        screenPanel.add(imageLabel);
        prevButton = new JButton("◀");
        prevButton.setBounds(8, 138, 38, 38);
        prevButton.setBackground(new Color(30, 30, 40));
        prevButton.setForeground(Color.WHITE);
        screenPanel.add(prevButton);
        nextButton = new JButton("▶");
        nextButton.setBounds(284, 138, 38, 38);
        nextButton.setBackground(new Color(30, 30, 40));
        nextButton.setForeground(Color.WHITE);
        screenPanel.add(nextButton);
        hpLabel      = new JLabel("HP: —");
        hpLabel.setBounds(25, 276, 90, 20);
        hpLabel.setForeground(Color.WHITE);
        hpLabel.setFont(FONT_BOLD);
        screenPanel.add(hpLabel);
        attackLabel  = new JLabel("Atk: —");
        attackLabel.setBounds(120, 276, 90, 20);
        attackLabel.setForeground(Color.WHITE);
        attackLabel.setFont(FONT_BOLD);
        screenPanel.add(attackLabel);
        defenseLabel = new JLabel("Def: —");
        defenseLabel.setBounds(215, 276, 90, 20);
        defenseLabel.setForeground(Color.WHITE);
        defenseLabel.setFont(FONT_BOLD);
        screenPanel.add(defenseLabel);
        descriptionArea = new JTextArea();
        descriptionArea.setBounds(25, 305, 280, 140);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);
        descriptionArea.setFont(FONT_BODY);
        descriptionArea.setForeground(new Color(245, 245, 245));
        screenPanel.add(descriptionArea);
        JLabel teamLabel = new JLabel("Team:");
        teamLabel.setBounds(40, 618, 40, 30);
        teamLabel.setForeground(Color.WHITE);
        add(teamLabel);
        teamComboBox = new JComboBox<>();
        teamComboBox.setBounds(85, 618, 100, 30);
        refreshTeamDropdown();
        add(teamComboBox);
        createTeamBtn = new JButton("+");
        createTeamBtn.setBounds(192, 618, 40, 30);
        createTeamBtn.setBackground(POKEDEX_BLUE);
        createTeamBtn.setForeground(Color.WHITE);
        add(createTeamBtn);
        addToTeamBtn = new JButton("Add");
        addToTeamBtn.setBounds(238, 618, 55, 30);
        add(addToTeamBtn);
        dashboardBtn = new JButton("Dashboard");
        dashboardBtn.setBounds(298, 618, 90, 30);
        add(dashboardBtn);
        compareBtn = new JButton("⚔  Compare Pokémon");
        compareBtn.setBounds(40, 658, 170, 36);
        compareBtn.setBackground(new Color(79, 70, 229)); 
        compareBtn.setForeground(Color.WHITE);
        compareBtn.setFont(new Font("Arial", Font.BOLD, 14));
        compareBtn.setFocusPainted(false);
        add(compareBtn);
        analyticsBtn = new JButton("📈 Analytics");
        analyticsBtn.setBounds(218, 658, 170, 36);
        analyticsBtn.setBackground(new Color(20, 160, 140)); 
        analyticsBtn.setForeground(Color.WHITE);
        analyticsBtn.setFont(new Font("Arial", Font.BOLD, 14));
        analyticsBtn.setFocusPainted(false);
        add(analyticsBtn);
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());
        prevButton.addActionListener(e -> navigateResults(false));
        nextButton.addActionListener(e -> navigateResults(true));
        createTeamBtn.addActionListener(e -> createNewTeam());
        addToTeamBtn.addActionListener(e -> addCurrentToTeam());
        dashboardBtn.addActionListener(e -> openTeamDashboard());
        compareBtn.addActionListener(e -> showCompareDialog());
        analyticsBtn.addActionListener(e -> showAnalyticsDialog());
    }
    private void performSearch() {
        String namePart   = searchField.getText().trim();
        String typeFilter = (String) typeFilterCombo.getSelectedItem();
        suggestionMenu.setVisible(false);
        new SwingWorker<List<Pokemon>, Void>() {
            @Override
            protected List<Pokemon> doInBackground() {
                return db.searchPokemonWithFilters(namePart, typeFilter);
            }
            @Override
            protected void done() {
                try {
                    currentSearchResults = get();
                    if (currentSearchResults != null && !currentSearchResults.isEmpty()) {
                        currentResultIndex = 0;
                        Pokemon p = currentSearchResults.get(0);
                        updateDisplayedPokemon(p);
                    } else {
                        nameLabel.setText("Not Found");
                        imageLabel.setIcon(null);
                        typeLabel.setText("");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }
    private void navigateResults(boolean isNext) {
        if (currentPokemon == null) return;
        boolean hasFilters = !searchField.getText().trim().isEmpty()
                             || !"All".equals(typeFilterCombo.getSelectedItem());
        if (hasFilters && currentSearchResults != null && !currentSearchResults.isEmpty()) {
            currentResultIndex = isNext
                    ? (currentResultIndex + 1) % currentSearchResults.size()
                    : (currentResultIndex - 1 + currentSearchResults.size()) % currentSearchResults.size();
            updateDisplayedPokemon(currentSearchResults.get(currentResultIndex));
        } else {
            new SwingWorker<Pokemon, Void>() {
                @Override protected Pokemon doInBackground() {
                    return db.getAdjacentPokemon(currentPokemon.getId(), isNext);
                }
                @Override protected void done() {
                    try { Pokemon p = get(); if (p != null) updateDisplayedPokemon(p); }
                    catch (Exception ex) { ex.printStackTrace(); }
                }
            }.execute();
        }
    }
    private void updateDisplayedPokemon(Pokemon p) {
        currentPokemon = p;
        nameLabel.setText("#" + p.getId() + "  " + p.getName());
        String typeText = (p.getType2() != null && !p.getType2().trim().isEmpty())
                ? p.getType1() + "  /  " + p.getType2()
                : p.getType1();
        typeLabel.setText(typeText);
        typeLabel.setForeground(typeColor(p.getType1()));
        hpLabel.setText("HP: " + p.getHp());
        attackLabel.setText("Atk: " + p.getAttack());
        defenseLabel.setText("Def: " + p.getDefense());
        descriptionArea.setText(p.getDescription() != null ? p.getDescription() : "No data available.");
        screenPanel.setTypeBackground(p.getType1());
        loadImageAsync(p.getId());
        new SwingWorker<DatabaseManager.PokemonTypeRank, Void>() {
            @Override
            protected DatabaseManager.PokemonTypeRank doInBackground() {
                return db.getTypeRankingsWindowFunction(p.getId());
            }
            @Override
            protected void done() {
                try {
                    DatabaseManager.PokemonTypeRank rank = get();
                    if (rank != null && rank.totalInType > 0) {
                        String baseType = (p.getType2() != null && !p.getType2().trim().isEmpty())
                                ? p.getType1() + " / " + p.getType2()
                                : p.getType1();
                        typeLabel.setText(baseType + "  •  Rank #" + rank.typeRank + "/" + rank.totalInType + " (" + p.getType1() + ")");
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }
    private void loadImageAsync(int id) {
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                URL url = new URL("https://raw.githubusercontent.com/poketwo/data/master/images/" + id + ".png");
                Image img = ImageIO.read(url).getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                return new ImageIcon(img);
            }
            @Override
            protected void done() {
                try { imageLabel.setIcon(get()); }
                catch (Exception ignored) { imageLabel.setIcon(null); }
            }
        }.execute();
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
    private boolean promptAuthentication() {
        while (true) {
            JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
            JTextField    userField = new JTextField();
            JPasswordField passField = new JPasswordField();
            panel.add(new JLabel("Username:")); panel.add(userField);
            panel.add(new JLabel("Password:")); panel.add(passField);
            String[] options = {"Login", "Register", "Cancel"};
            int result = JOptionPane.showOptionDialog(
                    null, panel, "Pokédex Network Login",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);
            if (result == 2 || result == JOptionPane.CLOSED_OPTION) return false;
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Credentials cannot be empty.");
                continue;
            }
            try {
                if (result == 0) { 
                    int id = db.authenticateUser(user, pass);
                    if (id != -1) { loggedInUserId = id; loggedInUsername = user; return true; }
                    JOptionPane.showMessageDialog(null, "Invalid username or password.", "Error", JOptionPane.ERROR_MESSAGE);
                } else { 
                    if (db.registerUser(user, pass))
                        JOptionPane.showMessageDialog(null, "Registration successful! You can now log in.");
                    else
                        JOptionPane.showMessageDialog(null, "Username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Database connection failed.\nMake sure Docker is running on port 3308.\n\n" + ex.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }
    private void refreshTeamDropdown() {
        teamComboBox.removeAllItems();
        for (DatabaseManager.Team t : db.getAllTeams(loggedInUserId)) teamComboBox.addItem(t);
    }
    private void createNewTeam() {
        String name = JOptionPane.showInputDialog(this, "Enter a name for your new team:");
        if (name != null && !name.trim().isEmpty()) {
            if (db.createTeam(loggedInUserId, name.trim())) {
                refreshTeamDropdown();
                JOptionPane.showMessageDialog(this, "Team '" + name + "' created!");
            } else {
                JOptionPane.showMessageDialog(this, "Failed — name may already exist.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void addCurrentToTeam() {
        if (currentPokemon == null) return;
        DatabaseManager.Team selected = (DatabaseManager.Team) teamComboBox.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No team selected. Please create a team first using the '+' button!", "No Team Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String result = db.addPokemonToTeam(selected.getId(), currentPokemon.getId());
        if (result.equals("Success"))
            JOptionPane.showMessageDialog(this, currentPokemon.getName() + " added to " + selected.getName() + "!");
        else
            JOptionPane.showMessageDialog(this, result, "Failed", JOptionPane.ERROR_MESSAGE);
    }
    private void openTeamDashboard() {
        DatabaseManager.Team selected = (DatabaseManager.Team) teamComboBox.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No teams created right now. Please create a team first using the '+' button!", "No Teams Created", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JDialog dialog = new JDialog(this, selected.getName() + "  —  Dashboard", true);
        dialog.setSize(440, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(0, 0));
        dialog.getContentPane().setBackground(BG_DARK);
        List<Pokemon> members = db.getTeamMembers(selected.getId());
        DefaultListModel<Pokemon> listModel = new DefaultListModel<>();
        members.forEach(listModel::addElement);
        JList<Pokemon> rosterList = new JList<>(listModel);
        rosterList.setBackground(CARD_BG);
        rosterList.setForeground(Color.WHITE);
        rosterList.setFont(FONT_BODY);
        rosterList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel("#" + value.getId() + "  " + value.getName() + "  (" + value.getType1() + ")");
            label.setForeground(isSelected ? ACCENT_GOLD : Color.WHITE);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setBackground(isSelected ? new Color(50, 50, 65) : CARD_BG);
            label.setOpaque(true);
            return label;
        });
        JScrollPane scroll = new JScrollPane(rosterList);
        scroll.setPreferredSize(new Dimension(440, 150));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        DatabaseManager.TeamAnalytics stats = db.getTeamAnalytics(selected.getId());
        JPanel analyticsPanel = buildAnalyticsPanel(stats);
        Map<String, Integer> typeDist = db.getTeamTypeDistributionMap(selected.getId());
        JPanel typePanel = buildTypeCoveragePanel(typeDist);
        JButton removeBtn     = new JButton("Remove Selected");
        JButton cloneBtn      = new JButton("Clone Team (ACID)");
        cloneBtn.setBackground(new Color(40, 140, 200));
        cloneBtn.setForeground(Color.WHITE);
        JButton deleteTeamBtn = new JButton("Delete Team");
        deleteTeamBtn.setBackground(POKEDEX_RED);
        deleteTeamBtn.setForeground(Color.WHITE);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        btnPanel.setBackground(BG_DARK);
        btnPanel.add(removeBtn);
        btnPanel.add(cloneBtn);
        btnPanel.add(deleteTeamBtn);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(BG_DARK);
        center.add(sectionLabel("ROSTER  (" + members.size() + "/6)"));
        center.add(scroll);
        center.add(sectionLabel("TEAM ANALYTICS"));
        center.add(analyticsPanel);
        center.add(sectionLabel("TYPE COVERAGE"));
        center.add(typePanel);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        removeBtn.addActionListener(e -> {
            Pokemon sel = rosterList.getSelectedValue();
            if (sel != null && db.removePokemonFromTeam(selected.getId(), sel.getId())) {
                listModel.removeElement(sel);
            }
        });
        cloneBtn.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(dialog, "Enter name for cloned team:", selected.getName() + " Copy");
            if (newName != null && !newName.trim().isEmpty()) {
                if (db.duplicateTeamTransaction(loggedInUserId, selected.getId(), newName.trim())) {
                    JOptionPane.showMessageDialog(dialog, "Team cloned in an atomic ACID transaction!", "Transaction Committed", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                    refreshTeamDropdown();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Transaction failed & rolled back.", "Transaction Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        deleteTeamBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(dialog, "Delete team '" + selected.getName() + "'?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
                    && db.deleteTeam(selected.getId())) {
                dialog.dispose();
                refreshTeamDropdown();
            }
        });
        dialog.setVisible(true);
    }
    private JPanel buildAnalyticsPanel(DatabaseManager.TeamAnalytics stats) {
        JPanel panel = new JPanel(new GridLayout(4, 1, 0, 6));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        panel.add(statBarRow("Avg Attack",  (int) stats.avgAttack,  255, new Color(240, 80,  60)));
        panel.add(statBarRow("Avg Defense", (int) stats.avgDefense, 255, new Color(70,  130, 220)));
        panel.add(statBarRow("Avg Speed",   (int) stats.avgSpeed,   255, new Color(100, 220, 100)));
        panel.add(statBarRow("Total Power", stats.totalBST,         2040, ACCENT_GOLD));
        return panel;
    }
    private JPanel statBarRow(String label, int value, int max, Color barColor) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(CARD_BG);
        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.LIGHT_GRAY);
        lbl.setFont(new Font("Arial", Font.BOLD, 12));
        lbl.setPreferredSize(new Dimension(100, 20));
        row.add(lbl, BorderLayout.WEST);
        JProgressBar bar = new JProgressBar(0, max);
        bar.setValue(value);
        bar.setString(String.valueOf(value));
        bar.setStringPainted(true);
        bar.setForeground(barColor);
        bar.setBackground(new Color(40, 40, 50));
        row.add(bar, BorderLayout.CENTER);
        return row;
    }
    private JPanel buildTypeCoveragePanel(Map<String, Integer> typeDist) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        if (typeDist.isEmpty()) {
            JLabel empty = new JLabel("No data");
            empty.setForeground(Color.GRAY);
            panel.add(empty);
        } else {
            for (Map.Entry<String, Integer> entry : typeDist.entrySet()) {
                panel.add(buildTypeChip(entry.getKey(), entry.getValue()));
            }
        }
        return panel;
    }
    private JLabel buildTypeChip(String type, int count) {
        JLabel chip = new JLabel(" " + type + " ×" + count + " ", SwingConstants.CENTER);
        chip.setFont(new Font("Arial", Font.BOLD, 11));
        chip.setForeground(Color.WHITE);
        chip.setBackground(typeColor(type));
        chip.setOpaque(true);
        chip.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        return chip;
    }
    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setFont(new Font("Arial", Font.BOLD, 11));
        lbl.setForeground(new Color(140, 140, 160));
        lbl.setBackground(new Color(20, 20, 28));
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return lbl;
    }
    private void showAnalyticsDialog() {
        JDialog dialog = new JDialog(this, "Database Analytics", true);
        dialog.setSize(400, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(18, 18, 24));
        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        JLabel lbl = new JLabel("Top 10 by Stat: ");
        lbl.setForeground(Color.WHITE);
        topPanel.add(lbl);
        String[] stats = {"HP", "Attack", "Defense", "Speed", "Weight"};
        JComboBox<String> statCombo = new JComboBox<>(stats);
        topPanel.add(statCombo);
        dialog.add(topPanel, BorderLayout.NORTH);
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(25, 25, 32));
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        dialog.add(scroll, BorderLayout.CENTER);
        Runnable fetch = () -> {
            String selected = (String) statCombo.getSelectedItem();
            List<Pokemon> top = db.getTopPokemonByStat(selected, 10);
            listPanel.removeAll();
            int rank = 1;
            for (Pokemon p : top) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
                JLabel nameLbl = new JLabel(rank + ". " + p.getName());
                nameLbl.setForeground(Color.WHITE);
                nameLbl.setFont(new Font("Arial", Font.BOLD, 14));
                String val = "";
                if (selected.equals("HP")) val = String.valueOf(p.getHp());
                else if (selected.equals("Attack")) val = String.valueOf(p.getAttack());
                else if (selected.equals("Defense")) val = String.valueOf(p.getDefense());
                else if (selected.equals("Speed")) val = String.valueOf(p.getSpeed());
                else if (selected.equals("Weight")) val = String.valueOf(p.getWeight()) + " kg";
                JLabel valLbl = new JLabel(val);
                valLbl.setForeground(new Color(80, 220, 100));
                valLbl.setFont(new Font("Arial", Font.BOLD, 14));
                row.add(nameLbl, BorderLayout.WEST);
                row.add(valLbl, BorderLayout.EAST);
                listPanel.add(row);
                rank++;
            }
            listPanel.revalidate();
            listPanel.repaint();
        };
        statCombo.addActionListener(e -> fetch.run());
        fetch.run();
        dialog.setVisible(true);
    }
    private void showCompareDialog() {
        JDialog dialog = new JDialog(this, "Versus Arena", true);
        dialog.setSize(520, 470);
        dialog.setLayout(null);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(new Color(18, 18, 24));
        JLabel title = new JLabel("VS", SwingConstants.CENTER);
        title.setFont(new Font("Impact", Font.ITALIC, 40));
        title.setForeground(ACCENT_GOLD);
        title.setBounds(205, 15, 110, 45);
        dialog.add(title);
        JTextField p1Field = new JTextField();
        p1Field.setBounds(25, 18, 165, 32);
        dialog.add(p1Field);
        JTextField p2Field = new JTextField();
        p2Field.setBounds(330, 18, 165, 32);
        dialog.add(p2Field);
        JPopupMenu p1Menu = new JPopupMenu();
        p1Menu.setFocusable(false);
        JPopupMenu p2Menu = new JPopupMenu();
        p2Menu.setFocusable(false);
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
        JButton fightBtn = new JButton("⚔  FIGHT!");
        fightBtn.setBounds(185, 62, 150, 34);
        fightBtn.setBackground(new Color(220, 80, 0));
        fightBtn.setForeground(Color.WHITE);
        fightBtn.setFont(new Font("Arial", Font.BOLD, 14));
        dialog.add(fightBtn);
        JPanel statsPanel = new JPanel(new GridLayout(6, 3, 8, 8));
        statsPanel.setBounds(25, 112, 465, 220);
        statsPanel.setBackground(new Color(25, 25, 32));
        dialog.add(statsPanel);
        fightBtn.addActionListener(e -> {
            statsPanel.removeAll();
            p1Menu.setVisible(false);
            p2Menu.setVisible(false);
            Pokemon p1 = db.getPokemonByName(p1Field.getText().trim());
            Pokemon p2 = db.getPokemonByName(p2Field.getText().trim());
            if (p1 == null || p2 == null) {
                JOptionPane.showMessageDialog(dialog, "Could not find one or both Pokémon. Check spelling!");
                return;
            }
            statsPanel.add(vsLabel(p1.getName(), Color.WHITE, true));
            statsPanel.add(vsLabel("STAT", new Color(150, 150, 160), true));
            statsPanel.add(vsLabel(p2.getName(), Color.WHITE, true));
            addStatRow(statsPanel, "HP",      p1.getHp(),      p2.getHp());
            addStatRow(statsPanel, "Attack",  p1.getAttack(),  p2.getAttack());
            addStatRow(statsPanel, "Defense", p1.getDefense(), p2.getDefense());
            addStatRow(statsPanel, "Speed",   p1.getSpeed(),   p2.getSpeed());
            int p1Total = p1.getHp() + p1.getAttack() + p1.getDefense() + p1.getSpeed();
            int p2Total = p2.getHp() + p2.getAttack() + p2.getDefense() + p2.getSpeed();
            addStatRow(statsPanel, "BST", p1Total, p2Total);
            statsPanel.revalidate();
            statsPanel.repaint();
        });
        dialog.setVisible(true);
    }
    private void addStatRow(JPanel panel, String stat, int v1, int v2) {
        Color c1 = (v1 > v2) ? new Color(80, 220, 100) : (v1 == v2) ? Color.WHITE : new Color(220, 80, 80);
        Color c2 = (v2 > v1) ? new Color(80, 220, 100) : (v1 == v2) ? Color.WHITE : new Color(220, 80, 80);
        panel.add(vsLabel(String.valueOf(v1), c1, false));
        panel.add(vsLabel(stat, new Color(150, 150, 160), true));
        panel.add(vsLabel(String.valueOf(v2), c2, false));
    }
    private JLabel vsLabel(String text, Color color, boolean bold) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setForeground(color);
        lbl.setFont(new Font("Arial", bold ? Font.BOLD : Font.PLAIN, 15));
        return lbl;
    }
    private static Color typeColor(String type) {
        if (type == null) return Color.GRAY;
        return switch (type.toLowerCase()) {
            case "fire"     -> new Color(240, 80,  48);
            case "water"    -> new Color(48,  144, 240);
            case "grass"    -> new Color(46,  204, 113);
            case "electric" -> new Color(245, 176, 65);
            case "ice"      -> new Color(96,  208, 224);
            case "fighting" -> new Color(192, 48,  40);
            case "poison"   -> new Color(160, 64,  160);
            case "ground"   -> new Color(224, 192, 104);
            case "flying"   -> new Color(144, 160, 240);
            case "psychic"  -> new Color(240, 64,  112);
            case "bug"      -> new Color(162, 217, 43);
            case "rock"     -> new Color(184, 160, 56);
            case "ghost"    -> new Color(112, 88,  152);
            case "dragon"   -> new Color(112, 56,  248);
            case "dark"     -> new Color(112, 88,  72);
            case "steel"    -> new Color(184, 184, 208);
            case "fairy"    -> new Color(240, 182, 188);
            default         -> new Color(168, 168, 120); 
        };
    }
}
class TypeBackgroundPanel extends JPanel {
    private Image bg;
    public void setTypeBackground(String type) {
        if (type == null || type.trim().isEmpty()) {
            bg = null;
            repaint();
            return;
        }
        String cleanType = type.trim().toLowerCase();
        File[] candidateFiles = new File[] {
            new File("backgrounds/" + cleanType + ".png"),
            new File("Virtual-Pokedex/backgrounds/" + cleanType + ".png"),
            new File(System.getProperty("user.dir"), "backgrounds/" + cleanType + ".png"),
            new File(System.getProperty("user.dir"), "Virtual-Pokedex/backgrounds/" + cleanType + ".png")
        };
        File foundFile = null;
        for (File f : candidateFiles) {
            if (f.exists()) {
                foundFile = f;
                break;
            }
        }
        try {
            if (foundFile != null) {
                bg = ImageIO.read(foundFile);
            } else {
                URL resource = getClass().getResource("/backgrounds/" + cleanType + ".png");
                if (resource == null) {
                    resource = ClassLoader.getSystemResource("backgrounds/" + cleanType + ".png");
                }
                if (resource != null) {
                    bg = ImageIO.read(resource);
                } else {
                    bg = null;
                    System.out.println("[TypeBackgroundPanel] Warning: Could not locate background image for type '" + cleanType + "'");
                }
            }
        } catch (Exception e) {
            bg = null;
            e.printStackTrace();
        }
        repaint();
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (bg != null) {
            g2d.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            GradientPaint topScrim = new GradientPaint(0, 0, new Color(0, 0, 0, 220), 0, 100, new Color(0, 0, 0, 0));
            g2d.setPaint(topScrim);
            g2d.fillRect(0, 0, getWidth(), 100);
            GradientPaint bottomScrim = new GradientPaint(0, 260, new Color(0, 0, 0, 0), 0, getHeight(), new Color(0, 0, 0, 210));
            g2d.setPaint(bottomScrim);
            g2d.fillRect(0, 260, getWidth(), getHeight() - 260);
        } else {
            GradientPaint gp = new GradientPaint(0, 0, new Color(35, 35, 48), 0, getHeight(), new Color(20, 20, 28));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
        g2d.dispose();
    }
}