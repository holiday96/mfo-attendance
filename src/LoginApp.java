import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class LoginApp extends JFrame {

    // ================= CONFIG =================
    static final String BASE_URL = "http://mfapi.818long.com";

    record Account(String username, String password) {
    }

    // ================= DATA =================
    private final List<Account> accounts = new ArrayList<>();
    private JList<Account> accountList;

    private String token;
    private String userId;

    // ================= UI =================
    private JLabel captchaLabel = new JLabel();
    private JTextField captchaField = new JTextField();
    private JLabel statusLabel = new JLabel("Ready");
    private JProgressBar progressBar = new JProgressBar(0, 100);
    private JTextArea logArea = new JTextArea();
    private JScrollPane logScrollPane;

    // animation
    private Timer progressTimer;
    private int currentValue = 0;
    private int targetValue = 0;

    // colors
    private static final Color COLOR_IDLE = new Color(180, 180, 180);
    private static final Color COLOR_LOGIN = new Color(70, 130, 180);
    private static final Color COLOR_SIGNIN = new Color(255, 165, 0);
    private static final Color COLOR_TASK = new Color(138, 43, 226);
    private static final Color COLOR_SUCCESS = new Color(60, 179, 113);
    private static final Color COLOR_ERROR = new Color(220, 20, 60);

    // ================= MAIN =================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginApp::new);
    }

    public LoginApp() {
        loadAccountsFromFile("accounts.txt");
        setTitle("Auto Login Reward - MFO v1.2");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // set icon (icon.png cùng thư mục src)
        try {
            Image icon = Toolkit.getDefaultToolkit().getImage(LoginApp.class.getResource("/icon.png"));
            setIconImage(icon);
        } catch (Exception ignored) {
        }

        buildUI();
        fetchCaptcha(true);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ================= UI BUILD =================
    private void buildUI() {
        // -------- LEFT PANEL --------
        DefaultListModel<Account> listModel = new DefaultListModel<>();
        accounts.forEach(listModel::addElement);

        accountList = new JList<>(listModel);
        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setSelectedIndex(0);
        accountList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.username);
            label.setOpaque(true);
            label.setBackground(isSelected ? new Color(200, 200, 255) : Color.WHITE);
            label.setForeground(Color.BLACK);
            label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            return label;
        });

        JScrollPane scroll = new JScrollPane(accountList);
        scroll.setPreferredSize(new Dimension(180, 200));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton runBtn = new JButton("LOGIN & RUN");
        runBtn.setMaximumSize(new Dimension(140, 28));
        runBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        runBtn.addActionListener(e -> runWithProgress());

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.add(scroll);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(runBtn);

        // -------- RIGHT PANEL --------
        captchaLabel.setPreferredSize(new Dimension(140, 60));
        captchaLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        captchaField.setMaximumSize(new Dimension(140, 26));
        captchaField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton refreshBtn = new JButton("Refresh Captcha");
        refreshBtn.setMaximumSize(new Dimension(140, 26));
        refreshBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        refreshBtn.addActionListener(e -> fetchCaptcha(true));

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.add(captchaLabel);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(captchaField);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(refreshBtn);

        // -------- MAIN --------
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        mainPanel.setPreferredSize(new Dimension(380, 240));
        mainPanel.add(leftPanel);
        mainPanel.add(rightPanel);

        // -------- PROGRESS --------
        progressBar.setUI(new GradientProgressUI());
        progressBar.setPreferredSize(new Dimension(380, 18));
        progressBar.setStringPainted(true);
        progressBar.setBackground(new Color(230, 230, 230));
        progressBar.setForeground(COLOR_IDLE);
        progressBar.setValue(0);
        progressBar.setString("Ready");

        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(progressBar, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        // -------- LOG AREA --------
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(380, 120));
        logScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel logPanelWrapper = new JPanel(new BorderLayout());
        logPanelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        logPanelWrapper.add(logScrollPane, BorderLayout.CENTER);

        // wrapper panel
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        wrapper.add(mainPanel, BorderLayout.CENTER);
        wrapper.add(bottom, BorderLayout.NORTH); // progress + status
        wrapper.add(logPanelWrapper, BorderLayout.SOUTH); // log

        setContentPane(wrapper);
    }

    // ================= PROGRESS ANIMATION =================
    private void animateProgress(int value, String text, Color color) {
        targetValue = value;
        progressBar.setString(text);
        progressBar.setForeground(color);

        if (progressTimer != null && progressTimer.isRunning()) progressTimer.stop();

        progressTimer = new Timer(10, e -> {
            if (currentValue < targetValue) currentValue += 1;
            else if (currentValue > targetValue) currentValue -= 1;
            else ((Timer) e.getSource()).stop();

            progressBar.setValue(currentValue);
        });

        progressTimer.start();
    }

    // ================= FLOW =================
    private void runWithProgress() {
        animateProgress(0, "Starting...", COLOR_IDLE);
        appendLog("===================== Starting =====================");
        statusLabel.setText("🔄 Đang xử lý");
        appendLog("🔄 Đang xử lý");

        SwingWorker<Void, String> worker = new SwingWorker<>() {

            @Override
            protected Void doInBackground() {
                try {
                    // --- LOGIN ---
                    publish("Login...", "login");
                    if (!doLogin()) {
                        publish("Login fail", "error");
                        return null;
                    }

                    // --- SIGNIN ---
                    publish("Signin...", "signin");

                    int dateNo = getSignInDay();
                    int today = getTodayDateNo();
                    int lastDay = getLastDayOfMonth();

                    int type = (dateNo != today) ? SignType.BACK : SignType.TODAY;

                    if (dateNo > 0) {
                        boolean signinOk = doSignin(dateNo, type);

                        if (signinOk) {
                            // VỪA ĐỦ NGÀY → NHẬN FULL PRIZE
                            if (dateNo == lastDay) {
                                publish("Get full month prize...", "task");
                                getFullPrize();

                            }
                            // ĐÃ VƯỢT NGÀY CUỐI THÁNG → COI NHƯ ĐÃ NHẬN
                            else if (dateNo > lastDay) {
                                appendLog("⚠️ Thưởng đủ ngày tháng này đã nhận rồi");
                                statusLabel.setText("⚠️ Đã nhận thưởng đủ ngày tháng");
                            }
                            // CHƯA ĐỦ NGÀY
                            else {
                                appendLog("ℹ️ Chưa đủ ngày (" + (dateNo - 1) + "/" + lastDay + "), bỏ qua full prize");
                            }
                        }
                    }

                    // --- TASK NGÀY ---
                    publish("Get daily task prize...", "task");
                    doTask();

                } catch (Exception e) {
                    e.printStackTrace();
                    publish("Error", "error");
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                // Lấy chunk cuối cùng để update UI
                String last = chunks.get(chunks.size() - 1);
                switch (last) {
                    case "login" -> animateProgress(25, "Login...", COLOR_LOGIN);
                    case "signin" -> animateProgress(60, "Signin...", COLOR_SIGNIN);
                    case "task" -> animateProgress(85, "Get reward...", COLOR_TASK);
                    case "error" -> animateProgress(100, "Error", COLOR_ERROR);
                }
            }
        };

        worker.execute();
    }

    // ================= API =================
    // ================= HTTP CLIENT CHUNG =================
    private final HttpClient httpClient = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();

    // ================= CAPTCHA =================
    private void fetchCaptcha(boolean showStatus) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/webapi/login/getCaptcha")).GET().build();

            HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            captchaLabel.setIcon(new ImageIcon(res.body()));
            captchaField.setText("");

            if (showStatus) {
                statusLabel.setText("⌨️ Nhập captcha hiển thị bên phải");
                appendLog("================== Load captcha ==================");
                appendLog("⌨️ Nhập captcha hiển thị bên phải");
            }
        } catch (Exception e) {
            statusLabel.setText("❌ Lỗi fetch captcha");
            appendLog("❌ Lỗi fetch captcha");
            e.printStackTrace();
        }
    }

    // ================= LOGIN =================
    private boolean doLogin() {
        Account acc = getSelectedAccount();
        String captcha = captchaField.getText().strip();

        if (captcha.isEmpty()) {
            statusLabel.setText("⚠️️️️ Vui lòng nhập captcha");
            appendLog("⚠️️️ Vui lòng nhập captcha");
            return false;
        }

        String body = """
                {
                  "username":"%s",
                  "password":"%s",
                  "code":"%s",
                  "type":1,
                  "source":"web"
                }
                """.formatted(acc.username, acc.password, captcha);
        appendLog("🔑 Đang đăng nhập user ➡️ " + acc.username);

        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/webapi/login/doLogin")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.body().contains("\"state\":100002")) {
                statusLabel.setText("❌ Login fail, mã xác thực sai");
                appendLog("❌ Login fail, mã xác thực sai");
                fetchCaptcha(false);
                return false;
            } else if (res.body().contains("\"state\":500")) {
                statusLabel.setText("❌ Login fail, Tên người dùng hoặc mật khẩu sai");
                appendLog("❌ Login fail, Tên người dùng hoặc mật khẩu sai");
                fetchCaptcha(false);
                return false;
            } else if (!res.body().contains("\"state\":200")) {
                statusLabel.setText("❌ Login fail, Lỗi không xác định");
                appendLog("❌ Login fail, Lỗi không xác định");
                fetchCaptcha(false);
                return false;
            }

            token = extract(res.body(), "token");
            userId = extract(res.body(), "userId");

            return true;
        } catch (Exception e) {
            fetchCaptcha(false);
            statusLabel.setText("❌ Lỗi login");
            appendLog("❌ Lỗi login");
            e.printStackTrace();
            return false;
        }
    }

    private int getSignInDay() {
        try {
            String body = """
                    {
                      "activityName": "signin",
                      "userId": %s,
                      "platForm": "web"
                    }
                    """.formatted(userId);

            HttpResponse<String> res = post("/webapi/signIn/getSignInList", body, token);

            // parse signDay
            String dataSection = extractJsonObject(res.body(), "data");
            if (dataSection.contains("signDay")) {
                String dayStr = dataSection.substring(dataSection.indexOf("signDay") + 9).split("[,}]")[0].replaceAll("[\" ]", "");
                return Integer.parseInt(dayStr) + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // trả về -1 nếu lỗi
    }

    /**
     * Hàm điểm danh
     *
     * @param dateNo ngày điểm danh
     * @param type   1 = điểm danh bình thường, 2 = điểm danh bù
     * @return
     * @throws Exception
     */
    private boolean doSignin(int dateNo, int type) throws Exception {
        String body = """
                {
                  "dateNo": %d,
                  "userId": %s,
                  "platForm":"web",
                  "signInType": %d
                }
                """.formatted(dateNo > getLastDayOfMonth() ? dateNo - 1 : dateNo, userId, type);

        HttpResponse<String> res = post("/webapi/signIn/doSignin", body, token);

        if (res.body().contains("\"state\":200")) {
            statusLabel.setText("✅ Điểm danh thành công");
            appendLog("✅ Điểm danh thành công");
            return true;
        } else if (res.body().contains("\"state\":100024")) {
            statusLabel.setText("⚠️ Ngày " + (dateNo - 1) + " đã điểm danh bù, tiếp tục...");
            appendLog("⚠️ Ngày " + (dateNo - 1) + " đã điểm danh bù, tiếp tục...");
            return true; // vẫn trả về true để tiếp tục doTask()
        } else if (res.body().contains("\"state\":10002") || res.body().contains("\"state\":100007")) {
            statusLabel.setText("⚠️ Ngày " + (dateNo - 1) + " đã điểm danh, tiếp tục...");
            appendLog("⚠️ Ngày " + (dateNo - 1) + " đã điểm danh, tiếp tục...");
            return true; // vẫn trả về true để tiếp tục doTask()
        } else {
            statusLabel.setText("❌ Lỗi điểm danh");
            appendLog("❌ Lỗi điểm danh");
            return false;
        }
    }

    private void doTask() {
        try {
            String body = """
                    {
                      "taskId":1,
                      "platForm":"web",
                      "userId":%s
                    }
                    """.formatted(userId);

            HttpResponse<String> res = post("/webapi/task/getTaskPrize", body, token);

            SwingUtilities.invokeLater(() -> {
                if (res.body().contains("\"state\":200")) {
                    animateProgress(100, "Hoàn thành", COLOR_SUCCESS);
                    statusLabel.setText("✅ Hoàn thành");
                    appendLog("💎 Nhận kim cương thành công");
                    appendLog("✅ Hoàn thành");
                } else {
                    animateProgress(100, "Quà đã nhận, không thể nhận thêm", COLOR_TASK);
                    statusLabel.setText("⚠️ Quà đã nhận, không thể nhận thêm");
                    appendLog("⚠️ Quà đã nhận, không thể nhận thêm");
                }

                // Tự động reload captcha nhưng không thay đổi statusLabel
                fetchCaptcha(false);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                animateProgress(100, "Lỗi", COLOR_ERROR);
                statusLabel.setText("❌ Lỗi nhận quà");
                appendLog("❌ Lỗi nhận quà");
                fetchCaptcha(false);
            });
            e.printStackTrace();
        }
    }

    /**
     * Nhận thưởng đủ ngày trong tháng
     */
    private boolean getFullPrize() {
        try {
            // yyyyMM
            Calendar cal = Calendar.getInstance();
            String month = String.format("%04d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);

            String body = """
                    {
                      "month":"%s",
                      "platForm":"web",
                      "userId":%s
                    }
                    """.formatted(month, userId);

            HttpResponse<String> res = post("/webapi/signIn/getfullPrize", body, token);

            if (res.body().contains("\"state\":200")) {
                appendLog("🎁 Nhận thưởng đủ ngày thành công (" + month + ")");
                statusLabel.setText("🎁 Đã nhận thưởng đủ ngày");
                return true;
            } else {
                appendLog("⚠️ Không thể nhận thưởng đủ ngày");
                return false;
            }
        } catch (Exception e) {
            appendLog("❌ Lỗi getfullPrize");
            e.printStackTrace();
            return false;
        }
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));

        if (token != null) builder.header("token", token);

        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Account getSelectedAccount() {
        Account selected = accountList.getSelectedValue();
        if (selected == null) throw new RuntimeException("Chưa chọn account");
        return selected;
    }

    private String extract(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return "";
        return json.substring(json.indexOf(":", i) + 1).split("[,}]")[0].replaceAll("[\" ]", "");
    }

    private String extractJsonObject(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int start = json.indexOf("{", i);
        if (start < 0) return "";
        int end = start;
        int count = 1; // đếm số ngoặc
        while (count > 0 && ++end < json.length()) {
            char c = json.charAt(end);
            if (c == '{') count++;
            else if (c == '}') count--;
        }
        return json.substring(start, end + 1);
    }

    private void loadAccountsFromFile(String filename) {
        accounts.clear();
        try (var br = new java.io.BufferedReader(new java.io.FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;

                // tách theo dấu |
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    accounts.add(new Account(parts[0].strip(), parts[1].strip()));
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể load account từ file: " + e.getMessage());
        }
    }

    /**
     * Hàm lấy số ngày hôm nay (1-31)
     */
    private int getTodayDateNo() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Lấy ngày cuối cùng của tháng hiện tại
     */
    private int getLastDayOfMonth() {
        Calendar cal = Calendar.getInstance();
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ================= CUSTOM UI =================
    static class GradientProgressUI extends BasicProgressBarUI {
        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Insets i = progressBar.getInsets();
            int w = progressBar.getWidth() - i.left - i.right;
            int h = progressBar.getHeight() - i.top - i.bottom;
            int fill = getAmountFull(i, w, h);

            g2.setColor(progressBar.getBackground());
            g2.fillRoundRect(0, 0, w, h, h, h);

            Color base = progressBar.getForeground();
            GradientPaint gp = new GradientPaint(0, 0, base.brighter(), w, 0, base.darker());

            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, fill, h, h, h);
            g2.dispose();

            if (progressBar.isStringPainted()) paintString(g, i.left, i.top, w, h, fill, i);
        }
    }
}
