import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;


public class HotelManagementSystem extends JFrame {
    // ---- Data model ----
    private static class Booking {
        String roomNumber;
        String roomType;
        double pricePerNight;
        String guestName;        // empty when vacant
        String checkInDate;      // yyyy-MM-dd HH:mm
        String checkOutDate;     // yyyy-MM-dd HH:mm

        Booking(String roomNumber, String roomType, double pricePerNight) {
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.pricePerNight = pricePerNight;
            this.guestName = "";
            this.checkInDate = "";
            this.checkOutDate = "";
        }

        boolean isOccupied() {
            return guestName != null && !guestName.isEmpty();
        }

        String[] toRow() {
            return new String[] {
                roomNumber,
                roomType,
                String.format("%.2f", pricePerNight),
                guestName,
                checkInDate,
                checkOutDate
            };
        }

        String toCSV() {
            return escapeCSV(roomNumber) + "," + escapeCSV(roomType) + "," +
                   pricePerNight + "," + escapeCSV(guestName) + "," +
                   escapeCSV(checkInDate) + "," + escapeCSV(checkOutDate);
        }

        static String escapeCSV(String s) {
            if (s == null) return "";
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                s = s.replace("\"", "\"\"");
                return "\"" + s + "\"";
            }
            return s;
        }
    }

    // ---- UI & state ----
    private DefaultTableModel tableModel;
    private JTable table;
    private JTextField tfRoomNumber, tfRoomType, tfPrice, tfGuestName, tfSearch;
    private JTextField tfTaxPercent, tfDiscountPercent;
    private JLabel statusLabel;

    private final java.util.List<Booking> bookings = new ArrayList<>();
    private final Path dataFile = Paths.get("bookings.csv");
    private final SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // Gradient colors for buttons (Style C)
    private final Color btnStart = new Color(74,144,226); 
    private final Color btnEnd   = new Color(53,122,189); 

    public HotelManagementSystem() {
        super("Hotel Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 640);
        setLocationRelativeTo(null);
        initComponents();
        loadData();
    }

    private void initComponents() {
        
        Color bg = new Color(249, 250, 252);
        Color panelWhite = Color.WHITE;
        getContentPane().setBackground(bg);

        
        JPanel header = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth(), h = getHeight();
                GradientPaint gp = new GradientPaint(0,0,new Color(45,118,200), w, h, new Color(30,86,160));
                g2.setPaint(gp);
                g2.fillRect(0,0,w,h);
            }
        };
        header.setPreferredSize(new Dimension(900, 76));
        header.setLayout(new BorderLayout());
        JLabel title = new JLabel("Hotel Management System");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setBorder(new EmptyBorder(14, 18, 14, 18));
        header.add(title, BorderLayout.WEST);

        // Form panel
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(panelWhite);
        form.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(12,12,12,12),
                BorderFactory.createLineBorder(new Color(220,220,220))
        ));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        tfRoomNumber = new JTextField(6);
        tfRoomType = new JTextField(8);
        tfPrice = new JTextField(6);
        tfGuestName = new JTextField(12);

        // tax & discount defaults
        tfTaxPercent = new JTextField("5", 4);
        tfDiscountPercent = new JTextField("0", 4);

        // Buttons 
        JButton btnAddRoom  = createGradientButton("\u2795  Add Room");   // heavy plus
        JButton btnCheckIn  = createGradientButton("\u2708  Check In");   // plane icon
        JButton btnCheckOut = createGradientButton("\u21AA  Check Out");  // right arrow curving
        JButton btnSave     = createGradientButton("\uD83D\uDCBE  Save"); // floppy unicode
        JButton btnLoad     = createGradientButton("\u21BB  Load");       // reload

        // Layout row 0
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Room #"), c);
        c.gridx = 1; form.add(tfRoomNumber, c);

        c.gridx = 2; form.add(new JLabel("Type"), c);
        c.gridx = 3; form.add(tfRoomType, c);

        c.gridx = 4; form.add(new JLabel("Price/night"), c);
        c.gridx = 5; form.add(tfPrice, c);

        c.gridx = 6; form.add(btnAddRoom, c);

        // Row 1
        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Guest Name"), c);
        c.gridx = 1; form.add(tfGuestName, c);

        c.gridx = 2; form.add(btnCheckIn, c);
        c.gridx = 3; form.add(btnCheckOut, c);

        // tax & discount fields
        c.gridx = 4; form.add(new JLabel("Tax %"), c);
        c.gridx = 5; form.add(tfTaxPercent, c);

        c.gridx = 6; form.add(new JLabel("Discount %"), c);
        c.gridx = 7; form.add(tfDiscountPercent, c);

        
        c.gridx = 2; c.gridy = 2; form.add(btnSave, c);
        c.gridx = 3; form.add(btnLoad, c);

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        searchPanel.setBackground(panelWhite);
        tfSearch = new JTextField(28);
        JButton btnSearch = createGradientButton("\uD83D\uDD0D  Search"); // magnifier
        JButton btnShowAll = createGradientButton("\u25A3  Show All");
        searchPanel.add(new JLabel("Search (room# or guest):"));
        searchPanel.add(tfSearch);
        searchPanel.add(btnSearch);
        searchPanel.add(btnShowAll);

        // Table
        String[] columns = {"Room #", "Type", "Price/night", "Guest", "Check-in", "Check-out"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.setFillsViewportHeight(true);
        table.setShowGrid(true);
        table.setGridColor(new Color(230,230,230));
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        // Status bar
        statusLabel = new JLabel(" Ready");
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(45,118,200));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(new EmptyBorder(8,12,8,12));

        // Layout center
        JPanel center = new JPanel(new BorderLayout(10,10));
        center.setBackground(bg);
        JPanel topStack = new JPanel(new BorderLayout());
        topStack.setBackground(bg);
        topStack.add(form, BorderLayout.NORTH);
        topStack.add(searchPanel, BorderLayout.SOUTH);
        center.add(topStack, BorderLayout.NORTH);
        center.add(tableScroll, BorderLayout.CENTER);

        //  components to frame
        Container cp = getContentPane();
        cp.setLayout(new BorderLayout(10,10));
        cp.add(header, BorderLayout.NORTH);
        cp.add(center, BorderLayout.CENTER);
        cp.add(statusLabel, BorderLayout.SOUTH);
        cp.setBackground(bg);

        // Action listeners
        btnAddRoom.addActionListener(e -> addRoom());
        btnCheckIn.addActionListener(e -> checkIn());
        btnCheckOut.addActionListener(e -> checkOut());
        btnSave.addActionListener(e -> saveData());
        btnLoad.addActionListener(e -> { loadData(); status("Data loaded."); });
        btnSearch.addActionListener(e -> search());
        btnShowAll.addActionListener(e -> refreshTable());

        //  auto-fill fields
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int r = table.getSelectedRow();
                    if (r >= 0) {
                        tfRoomNumber.setText(tableModel.getValueAt(r,0).toString());
                        tfRoomType.setText(tableModel.getValueAt(r,1).toString());
                        tfPrice.setText(tableModel.getValueAt(r,2).toString());
                        tfGuestName.setText(tableModel.getValueAt(r,3).toString());
                    }
                }
            }
        });

        // Demo rooms if no data exists
        if (!Files.exists(dataFile) && bookings.isEmpty()) {
            bookings.add(new Booking("101","Single",1200));
            bookings.add(new Booking("102","Single",1200));
            bookings.add(new Booking("201","Double",1800));
            bookings.add(new Booking("301","Deluxe",3000));
            refreshTable();
        }
    }

    
    private JButton createGradientButton(String text) {
        RoundedGradientButton btn = new RoundedGradientButton(text, btnStart, btnEnd);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(140, 38));
        return btn;
    }

    
    private static class RoundedGradientButton extends JButton {
        private Color colorA, colorB;
        private boolean hover = false;
        private final int arc = 18;

        RoundedGradientButton(String text, Color a, Color b) {
            super(text);
            this.colorA = a;
            this.colorB = b;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setMargin(new Insets(6,12,6,12));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mousePressed(MouseEvent e) { setLocation(getX(), getY()+1); }
                public void mouseReleased(MouseEvent e) { setLocation(getX(), getY()-1); }
            });
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();

            
            Color start = hover ? colorA.brighter() : colorA;
            Color end   = hover ? colorB.brighter() : colorB;

            
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            
            GradientPaint gp = new GradientPaint(0, 0, start, 0, h, end);
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.14f));
            g2.setPaint(Color.WHITE);
            g2.fillRoundRect(0, 0, w, h/2, arc, arc);

            
            g2.setComposite(AlphaComposite.SrcOver);
            FontMetrics fm = g2.getFontMetrics();
            Rectangle r = new Rectangle(0,0,w,h);
            int tx = (r.width - fm.stringWidth(getText())) / 2;
            int ty = (r.height - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(getForeground());
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }

        
        public boolean contains(int x, int y) {
            int w = getWidth(), h = getHeight();
            Shape round = new java.awt.geom.RoundRectangle2D.Float(0,0,w,h,arc,arc);
            return round.contains(x,y);
        }
    }

    //  Application logic 
    private void addRoom() {
        String rn = tfRoomNumber.getText().trim();
        String rt = tfRoomType.getText().trim();
        String pr = tfPrice.getText().trim();
        if (rn.isEmpty() || rt.isEmpty() || pr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter room number, type and price.", "Missing", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (findBookingByRoom(rn) != null) {
            JOptionPane.showMessageDialog(this, "Room already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        double price;
        try { price = Double.parseDouble(pr); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid price.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        bookings.add(new Booking(rn, rt, price));
        refreshTable();
        clearInputs();
        status("Added room " + rn);
    }

    private void checkIn() {
        String rn = tfRoomNumber.getText().trim();
        String guest = tfGuestName.getText().trim();
        if (rn.isEmpty() || guest.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Provide room number and guest name.", "Missing", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Booking b = findBookingByRoom(rn);
        if (b == null) {
            JOptionPane.showMessageDialog(this, "Room not found. Add the room first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (b.isOccupied()) {
            JOptionPane.showMessageDialog(this, "Room is already occupied by " + b.guestName, "Occupied", JOptionPane.WARNING_MESSAGE);
            return;
        }
        b.guestName = guest;
        b.checkInDate = now();
        b.checkOutDate = "";
        refreshTable();
        clearInputs();
        status("Checked in " + guest + " to room " + rn);
    }

    
    private void checkOut() {
        String rn = tfRoomNumber.getText().trim();
        if (rn.isEmpty()) {
            int r = table.getSelectedRow();
            if (r >= 0) rn = tableModel.getValueAt(r,0).toString();
        }
        if (rn.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a room or enter room number to check out.", "Missing", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Booking b = findBookingByRoom(rn);
        if (b == null) {
            JOptionPane.showMessageDialog(this, "Room not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!b.isOccupied()) {
            JOptionPane.showMessageDialog(this, "Room is already vacant.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        double taxPercent = parseDoubleSafe(tfTaxPercent.getText().trim(), 0.0);
        double discountPercent = parseDoubleSafe(tfDiscountPercent.getText().trim(), 0.0);

        // compute nights
        String cinStr = b.checkInDate;
        Date checkInDate;
        try {
            if (cinStr == null || cinStr.trim().isEmpty())
                throw new ParseException("No check-in timestamp", 0);
            checkInDate = dtFormat.parse(cinStr);
        } catch (ParseException ex) {
            checkInDate = new Date(System.currentTimeMillis() - (1000L * 60 * 60 * 24));
        }
        Date checkOutNow = new Date();
        long diffMs = checkOutNow.getTime() - checkInDate.getTime();
        double diffDays = (double) diffMs / (1000.0 * 60.0 * 60.0 * 24.0);
        int nights = (int) Math.max(1, Math.ceil(diffDays));
        double subtotal = nights * b.pricePerNight;

        double discountAmount = subtotal * (discountPercent / 100.0);
        double taxable = subtotal - discountAmount;
        double taxAmount = taxable * (taxPercent / 100.0);
        double finalTotal = taxable + taxAmount;

        // Build bill text
        StringBuilder bill = new StringBuilder();
        bill.append("Bill for room ").append(b.roomNumber).append("\n");
        bill.append("-------------------------------\n");
        bill.append(String.format("Guest: %s\n", b.guestName));
        bill.append(String.format("Room type: %s\n", b.roomType));
        bill.append(String.format("Price per night: %.2f\n", b.pricePerNight));
        bill.append(String.format("Check-in: %s\n", b.checkInDate));
        bill.append(String.format("Check-out: %s\n", dtFormat.format(checkOutNow)));
        bill.append(String.format("Nights charged: %d\n", nights));
        bill.append("\n");
        bill.append(String.format("Subtotal (n×price): %.2f\n", subtotal));
        bill.append(String.format("Discount (%.2f%%): -%.2f\n", discountPercent, discountAmount));
        bill.append(String.format("Taxable amount: %.2f\n", taxable));
        bill.append(String.format("Tax (%.2f%%): +%.2f\n", taxPercent, taxAmount));
        bill.append("-------------------------------\n");
        bill.append(String.format("Total payable: %.2f\n", finalTotal));

        JTextArea ta = new JTextArea(bill.toString());
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(520, 320));
        int option = JOptionPane.showConfirmDialog(this, sp, "Bill - Confirm Checkout and Payment", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.YES_OPTION) {
            status("Checkout cancelled for room " + rn);
            return;
        }

        // finalize checkout
        b.checkOutDate = dtFormat.format(checkOutNow);
        b.guestName = "";
        b.checkInDate = "";
        refreshTable();
        status(String.format("Checked out room %s | Paid: %.2f", rn, finalTotal));
        JOptionPane.showMessageDialog(this, String.format("Checkout complete. Total paid: %.2f", finalTotal), "Paid", JOptionPane.INFORMATION_MESSAGE);
    }

    private double parseDoubleSafe(String s, double fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return fallback; }
    }

    private void search() {
        String q = tfSearch.getText().trim().toLowerCase();
        if (q.isEmpty()) { refreshTable(); return; }
        tableModel.setRowCount(0);
        for (Booking b : bookings) {
            if (b.roomNumber.toLowerCase().contains(q) || b.guestName.toLowerCase().contains(q)) {
                tableModel.addRow(b.toRow());
            }
        }
        status("Search results for \"" + q + "\"");
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Booking b : bookings) tableModel.addRow(b.toRow());
        status("Refreshed. " + bookings.size() + " rooms.");
    }

    private void saveData() {
        try (BufferedWriter w = Files.newBufferedWriter(dataFile)) {
            w.write("roomNumber,roomType,price,guestName,checkIn,checkOut\n");
            for (Booking b : bookings) {
                w.write(b.toCSV()); w.write("\n");
            }
            status("Saved to " + dataFile.toAbsolutePath());
            JOptionPane.showMessageDialog(this, "Data saved to " + dataFile.getFileName(), "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            status("Save failed.");
        }
    }

    private void loadData() {
        bookings.clear();
        if (!Files.exists(dataFile)) { refreshTable(); status("No data file. Starting fresh or with demo rooms."); return; }
        try (BufferedReader r = Files.newBufferedReader(dataFile)) {
            r.readLine(); // skip header
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = parseCSVLine(line);
                String rn = parts.length>0 ? parts[0] : "";
                String rt = parts.length>1 ? parts[1] : "";
                double price = 0;
                if (parts.length>2) {
                    try { price = Double.parseDouble(parts[2]); } catch (Exception e) { price = 0; }
                }
                String guest = parts.length>3 ? parts[3] : "";
                String cin = parts.length>4 ? parts[4] : "";
                String cout = parts.length>5 ? parts[5] : "";

                Booking b = new Booking(rn, rt, price);
                b.guestName = guest == null ? "" : guest;
                b.checkInDate = cin == null ? "" : cin;
                b.checkOutDate = cout == null ? "" : cout;
                bookings.add(b);
            }
            refreshTable();
            status("Loaded " + bookings.size() + " records from " + dataFile.getFileName());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            status("Load failed.");
        }
    }

    
    private static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i=0;i<line.length();i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i+1 < line.length() && line.charAt(i+1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuotes = false;
                    }
                } else cur.append(ch);
            } else {
                if (ch == '"') { inQuotes = true; }
                else if (ch == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else { cur.append(ch); }
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private Booking findBookingByRoom(String roomNumber) {
        for (Booking b : bookings) if (b.roomNumber.equalsIgnoreCase(roomNumber)) return b;
        return null;
    }

    private void clearInputs() {
        tfRoomNumber.setText("");
        tfRoomType.setText("");
        tfPrice.setText("");
        tfGuestName.setText("");
    }

    private void status(String s) {
        statusLabel.setText(" " + s);
    }

    private String now() {
        return dtFormat.format(new Date());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            HotelManagementSystem app = new HotelManagementSystem();
            app.setVisible(true);
        });
    }
}
