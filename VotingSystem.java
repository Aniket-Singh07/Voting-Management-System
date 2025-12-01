import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.io.FileWriter; // File banane ke liye
import java.io.IOException;
import java.time.LocalDateTime; // Time stamp ke liye
import java.time.format.DateTimeFormatter;

public class VotingSystem extends JFrame {

    // Database Settings
    private static final String DB_URL = "jdbc:mysql://localhost:3306/voting_db?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USER = "root";     
    private static final String DB_PASS = "root"; 

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private User currentUser; 

    // Fonts
    private Font emojiFont = new Font("Segoe UI Emoji", Font.BOLD, 24);
    private Font headerFont = new Font("Segoe UI", Font.BOLD, 28);
    private Font textFont = new Font("Arial", Font.PLAIN, 18);

    public VotingSystem() {
        setTitle("Smart Voting System - Final Year Project");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Screens
        mainPanel.add(createLoginPanel(), "LOGIN");
        mainPanel.add(createRegisterPanel(), "REGISTER");
        mainPanel.add(createAdminPanel(), "ADMIN");
        mainPanel.add(createVoterPanel(), "VOTER");

        add(mainPanel);
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // --- 1. LOGIN SCREEN ---
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("Smart Voting System Login");
        title.setFont(headerFont);
        title.setForeground(new Color(25, 25, 112));
        
        JTextField userField = new JTextField(15); userField.setFont(textFont);
        JPasswordField passField = new JPasswordField(15); passField.setFont(textFont);
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("New User? Register Here");

        styleButton(loginBtn, new Color(70, 130, 180));
        registerBtn.setForeground(new Color(0, 100, 0));
        registerBtn.setContentAreaFilled(false);
        registerBtn.setBorderPainted(false);
        registerBtn.setFont(new Font("Arial", Font.BOLD, 14));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; panel.add(title, gbc);
        gbc.gridwidth = 1; gbc.gridy = 1; panel.add(new JLabel("Username:"), gbc); gbc.gridx = 1; panel.add(userField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Password:"), gbc); gbc.gridx = 1; panel.add(passField, gbc);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; panel.add(loginBtn, gbc);
        gbc.gridy = 4; panel.add(registerBtn, gbc);

        loginBtn.addActionListener(e -> authenticateUser(userField.getText(), new String(passField.getPassword())));
        registerBtn.addActionListener(e -> { userField.setText(""); passField.setText(""); cardLayout.show(mainPanel, "REGISTER"); });
        return panel;
    }

    // --- 2. REGISTER SCREEN ---
    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(255, 250, 240));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("Voter Registration");
        title.setFont(headerFont);
        
        JTextField nameField = new JTextField(15); nameField.setFont(textFont);
        JTextField userField = new JTextField(15); userField.setFont(textFont);
        JPasswordField passField = new JPasswordField(15); passField.setFont(textFont);
        JButton submitBtn = new JButton("Register Now");
        JButton backBtn = new JButton("Back to Login");

        styleButton(submitBtn, new Color(60, 179, 113));
        styleButton(backBtn, new Color(220, 20, 60));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; panel.add(title, gbc);
        gbc.gridwidth = 1; gbc.gridy = 1; panel.add(new JLabel("Full Name:"), gbc); gbc.gridx = 1; panel.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Set Username:"), gbc); gbc.gridx = 1; panel.add(userField, gbc);
        gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Set Password:"), gbc); gbc.gridx = 1; panel.add(passField, gbc);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; panel.add(submitBtn, gbc);
        gbc.gridy = 5; panel.add(backBtn, gbc);

        submitBtn.addActionListener(e -> {
            try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (name, username, password, role) VALUES (?, ?, ?, 'voter')")) {
                stmt.setString(1, nameField.getText()); stmt.setString(2, userField.getText()); stmt.setString(3, new String(passField.getPassword()));
                stmt.executeUpdate(); JOptionPane.showMessageDialog(this, "Registration Successful!"); cardLayout.show(mainPanel, "LOGIN");
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: Username exists!"); }
        });
        backBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));
        return panel;
    }

    private void authenticateUser(String username, String password) {
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE username=? AND password=?")) {
            stmt.setString(1, username); stmt.setString(2, password); ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                currentUser = new User(rs.getInt("id"), rs.getString("name"), rs.getString("role"), rs.getBoolean("has_voted"));
                if (currentUser.role.equalsIgnoreCase("admin")) { cardLayout.show(mainPanel, "ADMIN"); refreshAdminStats(); } 
                else { cardLayout.show(mainPanel, "VOTER"); loadCandidatesForVoter(); }
            } else { JOptionPane.showMessageDialog(this, "Invalid Credentials!"); }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Connection Error: " + ex.getMessage()); }
    }

    // --- 3. ADMIN PANEL (With EXPORT Button) ---
    private JTextArea resultsArea;
    private JLabel winnerLabel;

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setBackground(new Color(46, 139, 87));
        
        JLabel title = new JLabel("Admin Dashboard - Election Status", SwingConstants.CENTER);
        title.setForeground(Color.WHITE); title.setFont(headerFont);
        
        winnerLabel = new JLabel("Waiting for results...", SwingConstants.CENTER);
        winnerLabel.setFont(new Font("Segoe UI Emoji", Font.BOLD, 22));
        winnerLabel.setForeground(new Color(255, 255, 224));
        winnerLabel.setBorder(BorderFactory.createEmptyBorder(10,0,10,0));

        headerPanel.add(title);
        headerPanel.add(winnerLabel);

        resultsArea = new JTextArea(); resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        resultsArea.setMargin(new Insets(20, 20, 20, 20));

        JPanel footer = new JPanel();
        JButton refreshBtn = new JButton("Refresh");
        JButton addBtn = new JButton("Add Candidate");
        JButton downloadBtn = new JButton("📄 Download Report"); // Naya Button
        JButton logoutBtn = new JButton("Logout");

        styleButton(refreshBtn, new Color(100, 149, 237));
        styleButton(addBtn, new Color(255, 140, 0));
        styleButton(downloadBtn, new Color(128, 0, 128)); // Purple Color
        styleButton(logoutBtn, new Color(220, 20, 60));

        refreshBtn.addActionListener(e -> refreshAdminStats());
        logoutBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));
        
        addBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog("Enter Candidate Name:");
            String party = JOptionPane.showInputDialog("Enter Party Name:");
            String[] symbols = {"🔫", "📺", "📖", "⭕", "🚲", "🐘", "⏰", "👓", "🏏", "🚘", "🌹"};
            String symbol = (String) JOptionPane.showInputDialog(this, "Select Symbol:", "Symbol", JOptionPane.QUESTION_MESSAGE, null, symbols, symbols[0]);
            if(name != null && symbol != null) addCandidate(name, party, symbol);
        });

        // DOWNLOAD LOGIC
        downloadBtn.addActionListener(e -> downloadReport());

        footer.add(addBtn); footer.add(refreshBtn); footer.add(downloadBtn); footer.add(logoutBtn);
        panel.add(headerPanel, BorderLayout.NORTH); panel.add(new JScrollPane(resultsArea), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void downloadReport() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "Election_Report_" + timestamp + ".txt";
            FileWriter writer = new FileWriter(fileName);
            
            writer.write("=========================================\n");
            writer.write("       SMART VOTING SYSTEM REPORT        \n");
            writer.write("=========================================\n");
            writer.write("Date: " + LocalDateTime.now() + "\n\n");
            writer.write(resultsArea.getText()); // Jo screen par hai wo file mein likh do
            writer.write("\n=========================================\n");
            writer.write("Generated by Admin Panel\n");
            writer.close();
            
            JOptionPane.showMessageDialog(this, "✅ Report Downloaded Successfully!\nCheck file: " + fileName);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
        }
    }

    private void addCandidate(String name, String party, String symbol) {
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO candidates (name, party_name, symbol) VALUES (?, ?, ?)")) {
            stmt.setString(1, name); stmt.setString(2, party); stmt.setString(3, symbol);
            stmt.executeUpdate(); refreshAdminStats();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void refreshAdminStats() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-40s %-10s\n", "ID", "CANDIDATE DETAILS", "VOTES"));
        sb.append("---------------------------------------------------------------------------------\n");
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM candidates ORDER BY votes DESC")) {
            while (rs.next()) {
                String details = rs.getString("symbol") + " (" + rs.getString("party_name") + ") - " + rs.getString("name");
                sb.append(String.format("%-5d %-40s %-10d\n", rs.getInt("id"), details, rs.getInt("votes")));
            }
            resultsArea.setText(sb.toString());
        } catch (Exception ex) { ex.printStackTrace(); }
        updateWinner();
    }

    private void updateWinner() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM candidates ORDER BY votes DESC LIMIT 1")) {
            if (rs.next()) {
                int votes = rs.getInt("votes");
                String winnerName = rs.getString("symbol") + " " + rs.getString("name");
                if(votes > 0) winnerLabel.setText("🏆 Current Leader: " + winnerName + " (" + votes + " Votes)");
                else winnerLabel.setText("🗳️ Elections Open - No Votes Yet");
            }
        } catch (Exception ex) { winnerLabel.setText("Error fetching winner"); }
    }

    // --- 4. VOTER PANEL ---
    private JPanel candidatesPanel;
    private ButtonGroup voteGroup;
    private JButton submitVoteBtn;

    private JPanel createVoterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel header = new JPanel(); header.setBackground(new Color(100, 149, 237));
        JLabel title = new JLabel("Cast Your Vote");
        title.setForeground(Color.WHITE); title.setFont(headerFont);
        header.add(title);

        candidatesPanel = new JPanel();
        candidatesPanel.setLayout(new BoxLayout(candidatesPanel, BoxLayout.Y_AXIS));
        candidatesPanel.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        
        JPanel footer = new JPanel();
        submitVoteBtn = new JButton("Submit Vote");
        JButton logoutBtn = new JButton("Logout");
        styleButton(submitVoteBtn, new Color(50, 205, 50));
        styleButton(logoutBtn, new Color(220, 20, 60));

        submitVoteBtn.addActionListener(e -> castVote());
        logoutBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        footer.add(submitVoteBtn); footer.add(logoutBtn);
        panel.add(header, BorderLayout.NORTH); panel.add(new JScrollPane(candidatesPanel), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void loadCandidatesForVoter() {
        candidatesPanel.removeAll(); voteGroup = new ButtonGroup();
        if (currentUser.hasVoted) {
            JLabel msg = new JLabel("✅ You have already voted! Thank you.");
            msg.setFont(emojiFont); msg.setForeground(new Color(0, 128, 0));
            msg.setAlignmentX(Component.CENTER_ALIGNMENT);
            candidatesPanel.add(Box.createVerticalStrut(80)); candidatesPanel.add(msg); submitVoteBtn.setEnabled(false);
        } else {
            submitVoteBtn.setEnabled(true);
            try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM candidates")) {
                while (rs.next()) {
                    String text = "   " + rs.getString("symbol") + "  (" + rs.getString("party_name") + ")  -  " + rs.getString("name");
                    JRadioButton rb = new JRadioButton(text);
                    rb.setActionCommand(String.valueOf(rs.getInt("id")));
                    rb.setFont(emojiFont);
                    rb.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                    voteGroup.add(rb); candidatesPanel.add(rb);
                    candidatesPanel.add(Box.createVerticalStrut(10));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
        candidatesPanel.revalidate(); candidatesPanel.repaint();
    }

    private void castVote() {
        if (voteGroup.getSelection() == null) { JOptionPane.showMessageDialog(this, "Select a candidate!"); return; }
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("UPDATE candidates SET votes = votes + 1 WHERE id = ?")) {
                ps1.setInt(1, Integer.parseInt(voteGroup.getSelection().getActionCommand())); ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement("UPDATE users SET has_voted = TRUE WHERE id = ?")) {
                ps2.setInt(1, currentUser.id); ps2.executeUpdate();
            }
            conn.commit(); currentUser.hasVoted = true;
            JOptionPane.showMessageDialog(this, "🎉 Vote Cast Successfully!"); loadCandidatesForVoter();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void styleButton(JButton btn, Color color) {
        btn.setBackground(color); btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Arial", Font.BOLD, 14)); btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(150, 40));
    }

    class User {
        int id; String name; String role; boolean hasVoted;
        public User(int id, String name, String role, boolean hasVoted) { this.id = id; this.name = name; this.role = role; this.hasVoted = hasVoted; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> { new VotingSystem().setVisible(true); });
    }
}
