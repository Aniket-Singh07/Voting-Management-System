import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class VotingSystem extends JFrame {

    // --- FINAL FIX: Colon (:) Laga Diya Hai ---
    // Dhyan se dekho: "jdbc:mysql://" ab sahi hai
    private static final String DB_URL = "jdbc:mysql://localhost:3306/voting_db?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USER = "root";     
    private static final String DB_PASS = "root"; 

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private User currentUser; 

    public VotingSystem() {
        setTitle("Smart Voting System - Final Year Project");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(createLoginPanel(), "LOGIN");
        mainPanel.add(createAdminPanel(), "ADMIN");
        mainPanel.add(createVoterPanel(), "VOTER");

        add(mainPanel);
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // ================= 1. LOGIN SCREEN =================
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("Voting System Login");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        
        JTextField userField = new JTextField(15);
        JPasswordField passField = new JPasswordField(15);
        JButton loginBtn = new JButton("Login");

        loginBtn.setBackground(new Color(70, 130, 180));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFont(new Font("Arial", Font.BOLD, 14));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1; gbc.gridy = 1;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        panel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(loginBtn, gbc);

        loginBtn.addActionListener(e -> {
            String u = userField.getText();
            String p = new String(passField.getPassword());
            authenticateUser(u, p);
        });

        return panel;
    }

    private void authenticateUser(String username, String password) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE username=? AND password=?")) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                currentUser = new User(rs.getInt("id"), rs.getString("name"), rs.getString("role"), rs.getBoolean("has_voted"));
                
                if (currentUser.role.equalsIgnoreCase("admin")) {
                    JOptionPane.showMessageDialog(this, "Welcome Admin: " + currentUser.name);
                    cardLayout.show(mainPanel, "ADMIN");
                    refreshAdminStats();
                } else {
                    JOptionPane.showMessageDialog(this, "Welcome Voter: " + currentUser.name);
                    cardLayout.show(mainPanel, "VOTER");
                    loadCandidatesForVoter();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Invalid Username or Password!", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database Connection Error: " + ex.getMessage());
        }
    }

    // ================= 2. ADMIN PANEL =================
    private JTextArea resultsArea;

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel header = new JPanel();
        header.setBackground(new Color(46, 139, 87));
        JLabel title = new JLabel("Admin Dashboard - Live Election Results");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        header.add(title);

        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 15));
        resultsArea.setMargin(new Insets(20, 20, 20, 20));

        JPanel footer = new JPanel();
        JButton refreshBtn = new JButton("Refresh Results");
        JButton addCandidateBtn = new JButton("Add Candidate");
        JButton logoutBtn = new JButton("Logout");

        refreshBtn.addActionListener(e -> refreshAdminStats());
        addCandidateBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog("Enter Candidate Name:");
            String party = JOptionPane.showInputDialog("Enter Party Name:");
            if(name != null && party != null && !name.trim().isEmpty()) {
                addCandidate(name, party);
            }
        });
        logoutBtn.addActionListener(e -> {
            currentUser = null;
            cardLayout.show(mainPanel, "LOGIN");
        });

        footer.add(addCandidateBtn);
        footer.add(refreshBtn);
        footer.add(logoutBtn);

        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(resultsArea), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void addCandidate(String name, String party) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO candidates (name, party_name) VALUES (?, ?)")) {
            stmt.setString(1, name);
            stmt.setString(2, party);
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "New Candidate Added!");
            refreshAdminStats();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void refreshAdminStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("LIVE ELECTION RESULTS\n");
        sb.append("----------------------------------------------------------------\n");
        sb.append(String.format("%-5s %-25s %-25s %-10s\n", "ID", "CANDIDATE NAME", "PARTY", "VOTES"));
        sb.append("----------------------------------------------------------------\n");

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM candidates ORDER BY votes DESC")) {

            while (rs.next()) {
                sb.append(String.format("%-5d %-25s %-25s %-10d\n", 
                    rs.getInt("id"), rs.getString("name"), rs.getString("party_name"), rs.getInt("votes")));
            }
            resultsArea.setText(sb.toString());
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    // ================= 3. VOTER PANEL =================
    private JPanel candidatesPanel;
    private ButtonGroup voteGroup;
    private JButton submitVoteBtn;

    private JPanel createVoterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel header = new JPanel();
        header.setBackground(new Color(100, 149, 237));
        JLabel title = new JLabel("Cast Your Vote - Use Your Right");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        header.add(title);

        candidatesPanel = new JPanel();
        candidatesPanel.setLayout(new BoxLayout(candidatesPanel, BoxLayout.Y_AXIS));
        candidatesPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));
        
        JPanel footer = new JPanel();
        submitVoteBtn = new JButton("Submit Vote");
        JButton logoutBtn = new JButton("Logout");

        submitVoteBtn.setBackground(new Color(50, 205, 50));
        submitVoteBtn.setForeground(Color.WHITE);
        submitVoteBtn.setFont(new Font("Arial", Font.BOLD, 16));

        submitVoteBtn.addActionListener(e -> castVote());
        logoutBtn.addActionListener(e -> {
            currentUser = null;
            cardLayout.show(mainPanel, "LOGIN");
        });

        footer.add(submitVoteBtn);
        footer.add(logoutBtn);

        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(candidatesPanel), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void loadCandidatesForVoter() {
        candidatesPanel.removeAll();
        voteGroup = new ButtonGroup();

        if (currentUser.hasVoted) {
            JLabel msg = new JLabel("You have already voted! Thank you.");
            msg.setFont(new Font("Arial", Font.BOLD, 20));
            msg.setForeground(Color.RED);
            msg.setAlignmentX(Component.CENTER_ALIGNMENT);
            candidatesPanel.add(Box.createVerticalStrut(50));
            candidatesPanel.add(msg);
            submitVoteBtn.setEnabled(false);
        } else {
            submitVoteBtn.setEnabled(true);
            try (Connection conn = connect();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM candidates")) {
                
                while (rs.next()) {
                    JRadioButton rb = new JRadioButton(rs.getString("name") + " (" + rs.getString("party_name") + ")");
                    rb.setActionCommand(String.valueOf(rs.getInt("id"))); 
                    rb.setFont(new Font("Arial", Font.PLAIN, 18));
                    rb.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
                    voteGroup.add(rb);
                    candidatesPanel.add(rb);
                    candidatesPanel.add(Box.createVerticalStrut(10));
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        candidatesPanel.revalidate();
        candidatesPanel.repaint();
    }

    private void castVote() {
        if (voteGroup.getSelection() == null) {
            JOptionPane.showMessageDialog(this, "Please select a candidate first!");
            return;
        }

        int candidateId = Integer.parseInt(voteGroup.getSelection().getActionCommand());

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("UPDATE candidates SET votes = votes + 1 WHERE id = ?")) {
                ps1.setInt(1, candidateId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement("UPDATE users SET has_voted = TRUE WHERE id = ?")) {
                ps2.setInt(1, currentUser.id);
                ps2.executeUpdate();
            }
            conn.commit();
            currentUser.hasVoted = true;
            JOptionPane.showMessageDialog(this, "Vote Cast Successfully! Thank you.");
            loadCandidatesForVoter(); 
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    class User {
        int id;
        String name;
        String role;
        boolean hasVoted;

        public User(int id, String name, String role, boolean hasVoted) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.hasVoted = hasVoted;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) { e.printStackTrace(); }
            new VotingSystem().setVisible(true);
        });
    }
}