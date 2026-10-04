package wbs.editor.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.OptionalInt;
import javax.swing.AbstractAction;
import javax.swing.AbstractSpinnerModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import com.formdev.flatlaf.FlatClientProperties;
import wbs.editor.excel.WbsWorkbook;
import wbs.editor.model.Columns;
import wbs.editor.model.Hours;
import wbs.editor.model.JapaneseDates;
import wbs.editor.model.LayoutConfig;
import wbs.editor.model.WbsFilter;
import wbs.editor.model.WbsItem;
import wbs.editor.model.WbsRow;

public final class MainFrame extends JFrame {
    private static final Color PARENT_BACKGROUND = new Color(0xE7EDF2);
    private static final Color PARENT_FOREGROUND = new Color(0x4A5560);
    private static final Color INPUT_BACKGROUND = new Color(0xFFF6D8);
    private static final Color HINT_FOREGROUND = new Color(0x667085);

    private final java.util.prefs.Preferences prefs = LayoutConfig.preferences();
    private LayoutConfig layout = LayoutConfig.load(prefs);
    private final WbsTableModel model = new WbsTableModel();
    private final JTable table = createTable();
    private final JTextField fileField = new JTextField();
    private final JComboBox<String> assigneeBox = new JComboBox<>();
    private final JSpinner dateSpinner;
    private final JLabel weekdayLabel = new JLabel();
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel layoutLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("保存");
    private final JButton reloadButton = new JButton("再読込");

    private WbsWorkbook workbook;
    private Path currentFile;
    private LocalDate currentDate = LocalDate.now();
    private boolean adjusting;
    private boolean suppressDateEvents;
    private String notice = "";

    public MainFrame(Path initialFile) {
        super("WBS実績入力");
        dateSpinner = createDateSpinner(currentDate);
        weekdayLabel.setText("（" + JapaneseDates.weekday(currentDate) + "）");
        buildMenu();
        buildContent();
        buildActions();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }
        });
        setSize(1040, 680);
        setMinimumSize(new Dimension(860, 520));
        setLocationRelativeTo(null);
        if (initialFile != null) {
            loadFile(initialFile);
        } else {
            openLastFile();
        }
        updateStatus();
    }

    public void openFile(Path path) {
        if (workbook != null && workbook.isDirty() && !confirmSaveIfDirty("別のファイルを開きます。")) {
            return;
        }
        loadFile(path);
    }

    private void buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("ファイル");
        file.add(menuItem("開く", KeyEvent.VK_O, event -> chooseFile()));
        file.add(menuItem("再読込", KeyEvent.VK_R, event -> reload()));
        file.add(menuItem("保存", KeyEvent.VK_S, event -> save()));
        file.addSeparator();
        file.add(menuItem("終了", KeyEvent.VK_Q, event -> closeWindow()));
        JMenu settings = new JMenu("設定");
        settings.add(menuItem("シート配置", 0, event -> editSettings()));
        bar.add(file);
        bar.add(settings);
        setJMenuBar(bar);
    }

    private void buildContent() {
        fileField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Excelファイルのパス");
        fileField.addActionListener(event -> openTypedFile());
        assigneeBox.setEditable(true);
        assigneeBox.setPreferredSize(new Dimension(180, assigneeBox.getPreferredSize().height));
        if (assigneeBox.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "担当者名");
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    applyAssignee();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    applyAssignee();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    applyAssignee();
                }
            });
        }

        JButton openButton = new JButton("開く");
        openButton.addActionListener(event -> chooseFile());
        reloadButton.addActionListener(event -> reload());
        saveButton.addActionListener(event -> save());
        JButton settingsButton = new JButton("配置設定");
        settingsButton.addActionListener(event -> editSettings());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(reloadButton);
        buttons.add(saveButton);
        buttons.add(settingsButton);

        JLabel hint = new JLabel("<html>黄色いセルに、選んだ日の予定と実績を入力します。"
                + "グレーの行は親WBSです。担当が違っても、自分の作業の上位は表示します。</html>");
        hint.setForeground(HINT_FOREGROUND);

        JPanel form = new JPanel(new GridBagLayout());
        form.add(new JLabel("Excel"), constraints(0, 0, 0));
        GridBagConstraints fileConstraints = constraints(1, 0, 1);
        fileConstraints.gridwidth = 4;
        fileConstraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(fileField, fileConstraints);
        form.add(openButton, constraints(5, 0, 0));
        form.add(new JLabel("担当"), constraints(0, 1, 0));
        form.add(assigneeBox, constraints(1, 1, 0));
        form.add(new JLabel("日付"), constraints(2, 1, 0));
        form.add(dateSpinner, constraints(3, 1, 0));
        form.add(weekdayLabel, constraints(4, 1, 0));
        form.add(buttons, constraints(5, 1, 0));
        GridBagConstraints hintConstraints = constraints(0, 2, 1);
        hintConstraints.gridwidth = 6;
        hintConstraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(hint, hintConstraints);

        layoutLabel.setForeground(HINT_FOREGROUND);
        layoutLabel.setFont(layoutLabel.getFont().deriveFont(12f));
        JPanel status = new JPanel(new GridLayout(2, 1, 0, 2));
        status.add(summaryLabel);
        status.add(layoutLabel);

        JScrollPane scroll = new JScrollPane(table);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(form, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);

        TransferHandler drop = new FileDropHandler();
        setTransferHandler(drop);
        table.setTransferHandler(drop);
        scroll.setTransferHandler(drop);
    }

    private void buildActions() {
        dateSpinner.addChangeListener(event -> onDateChanged());
        model.addTableModelListener(event -> {
            if (event.getType() == TableModelEvent.UPDATE && event.getColumn() >= 0) {
                notice = "";
            }
            updateStatus();
        });
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateStatus();
            }
        });
        table.getActionMap().put("clearHours", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                int row = table.getSelectedRow();
                int column = table.getSelectedColumn();
                if (row < 0 || column < 4 || table.isEditing() || !table.isCellEditable(row, column)) {
                    return;
                }
                table.setValueAt("", row, column);
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "clearHours");
    }

    private JTable createTable() {
        JTable created = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                int modelRow = convertRowIndexToModel(row);
                WbsRow item = model.row(modelRow);
                if (!isCellSelected(row, column)) {
                    if (!item.owned()) {
                        component.setBackground(PARENT_BACKGROUND);
                        component.setForeground(PARENT_FOREGROUND);
                    } else if (column >= 4) {
                        component.setBackground(INPUT_BACKGROUND);
                        component.setForeground(getForeground());
                    } else {
                        component.setBackground(getBackground());
                        component.setForeground(getForeground());
                    }
                }
                if (component instanceof JComponent label) {
                    int left = column == 2 ? 8 + item.depth() * 18 : 6;
                    label.setBorder(BorderFactory.createEmptyBorder(0, left, 0, 6));
                    if (!item.owned()) {
                        label.setToolTipText("親WBSです。表示のみで、この画面では入力しません。");
                    } else if (column >= 4) {
                        label.setToolTipText("時間を入力します。空欄にするとクリアできます。");
                    } else {
                        label.setToolTipText(null);
                    }
                }
                return component;
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                if (row < 0) {
                    return null;
                }
                WbsRow item = model.row(convertRowIndexToModel(row));
                if (!item.owned()) {
                    return "親WBSです。表示のみで、この画面では入力しません。";
                }
                if (column >= 4) {
                    return "時間を入力します。空欄にするとクリアできます。";
                }
                return null;
            }
        };
        created.setRowHeight(30);
        created.setFillsViewportHeight(true);
        created.setCellSelectionEnabled(true);
        created.setSurrendersFocusOnKeystroke(true);
        created.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        created.getTableHeader().setReorderingAllowed(false);
        created.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        DefaultTableCellRenderer number = new DefaultTableCellRenderer();
        number.setHorizontalAlignment(JLabel.RIGHT);
        created.getColumnModel().getColumn(4).setCellRenderer(number);
        created.getColumnModel().getColumn(5).setCellRenderer(number);
        created.getColumnModel().getColumn(4).setCellEditor(new HoursEditor());
        created.getColumnModel().getColumn(5).setCellEditor(new HoursEditor());
        created.getColumnModel().getColumn(0).setPreferredWidth(70);
        created.getColumnModel().getColumn(1).setPreferredWidth(110);
        created.getColumnModel().getColumn(2).setPreferredWidth(360);
        created.getColumnModel().getColumn(3).setPreferredWidth(120);
        created.getColumnModel().getColumn(4).setPreferredWidth(90);
        created.getColumnModel().getColumn(5).setPreferredWidth(90);
        return created;
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("WBSのExcelを開く");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel ファイル (*.xlsx, *.xls)", "xlsx", "xls"));
        String directory = prefs.get("lastDir", "");
        if (!directory.isBlank()) {
            chooser.setCurrentDirectory(new File(directory));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile().toPath());
        }
    }

    private void openTypedFile() {
        String text = fileField.getText().trim();
        if (text.isEmpty()) {
            chooseFile();
            return;
        }
        openFile(Path.of(text));
    }

    private void openLastFile() {
        String last = prefs.get("lastFile", "");
        if (last.isBlank()) {
            return;
        }
        Path path = Path.of(last);
        if (Files.isRegularFile(path)) {
            loadFile(path);
        }
    }

    private void loadFile(Path path) {
        notice = "";
        try {
            WbsWorkbook opened = WbsWorkbook.open(path, layout);
            if (workbook != null) {
                workbook.close();
            }
            workbook = opened;
            currentFile = path.toAbsolutePath();
            fileField.setText(currentFile.toString());
            setTitle("WBS実績入力 - " + currentFile.getFileName());
            prefs.put("lastFile", currentFile.toString());
            if (currentFile.getParent() != null) {
                prefs.put("lastDir", currentFile.getParent().toString());
            }
            String selected = assigneeText();
            if (selected.isBlank()) {
                selected = prefs.get("lastAssignee", "");
            }
            adjusting = true;
            try {
                assigneeBox.removeAllItems();
                for (String name : opened.assignees()) {
                    assigneeBox.addItem(name);
                }
                assigneeBox.setSelectedItem(selected);
                if (assigneeBox.getEditor().getEditorComponent() instanceof JTextField editor) {
                    editor.setText(selected);
                }
            } finally {
                adjusting = false;
            }
            opened.loadDay(currentDate);
            refreshTable(true);
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
            error("ファイルを開けません。\nExcelで開いているときは閉じてください。\n" + message(ex));
        }
    }

    private void reload() {
        if (currentFile == null) {
            return;
        }
        if (workbook != null && workbook.isDirty() && !confirmSaveIfDirty("ファイルを読み込み直します。")) {
            return;
        }
        loadFile(currentFile);
    }

    private boolean save() {
        if (workbook == null || currentFile == null) {
            return false;
        }
        if (!commitTableEdit()) {
            return false;
        }
        LocalDate typed = readSpinnerCommit();
        if (!typed.equals(currentDate)) {
            if (workbook.isDirty() && !writeCurrent()) {
                setDate(currentDate);
                return false;
            }
            currentDate = typed;
            weekdayLabel.setText("（" + JapaneseDates.weekday(currentDate) + "）");
            workbook.loadDay(currentDate);
            refreshTable(false);
            return true;
        }
        if (!workbook.isDirty()) {
            notice = "変更はありません。";
            updateStatus();
            return true;
        }
        return writeCurrent();
    }

    private boolean writeCurrent() {
        try {
            workbook.writeDay(currentDate);
            workbook.save(currentFile);
            prefs.put("lastAssignee", assigneeText());
            notice = "保存しました。";
            updateStatus();
            JOptionPane.showMessageDialog(
                    this,
                    "保存しました。\n" + currentFile.getFileName(),
                    "WBS実績入力",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
            error("保存できませんでした。\nExcelで開いているときは閉じてください。\n" + message(ex));
            return false;
        }
    }

    private void editSettings() {
        List<String> names = workbook == null ? List.of() : workbook.sheetNames();
        SettingsDialog dialog = new SettingsDialog(this, layout.copy(), names);
        dialog.setVisible(true);
        LayoutConfig updated = dialog.result();
        if (updated == null) {
            return;
        }
        if (workbook != null && workbook.isDirty() && !confirmSaveIfDirty("配置を変更して読み込み直します。")) {
            return;
        }
        layout = updated;
        layout.save(prefs);
        if (currentFile != null) {
            loadFile(currentFile);
        } else {
            updateStatus();
        }
    }

    private void applyAssignee() {
        if (adjusting) {
            return;
        }
        notice = "";
        refreshTable(true);
    }

    private void onDateChanged() {
        if (adjusting || suppressDateEvents) {
            return;
        }
        LocalDate next = readDateRaw();
        weekdayLabel.setText("（" + JapaneseDates.weekday(next) + "）");
        if (next.equals(currentDate)) {
            return;
        }
        if (workbook != null && workbook.isDirty() && !confirmSaveIfDirty("日付を変えます。")) {
            setDate(currentDate);
            return;
        }
        currentDate = next;
        notice = "";
        if (workbook != null) {
            workbook.loadDay(currentDate);
            refreshTable(false);
        } else {
            updateStatus();
        }
    }

    private void refreshTable(boolean structureChanged) {
        if (structureChanged) {
            String person = assigneeText();
            model.setRows(workbook == null ? List.of() : WbsFilter.visible(workbook.items(), person));
        } else {
            model.refreshValues();
        }
        updateStatus();
    }

    private void updateStatus() {
        List<WbsRow> rows = model.rows();
        String message;
        if (workbook == null) {
            message = "Excelファイルを開いてください。";
        } else if (workbook.items().isEmpty()) {
            message = "WBSを読み取れませんでした。配置設定のデータ開始行と列を確認してください。";
        } else if (assigneeText().isBlank()) {
            message = "担当者を指定してください。ファイルには " + workbook.items().size() + " 件あります。";
        } else if (rows.isEmpty()) {
            message = "担当「" + assigneeText() + "」のWBSはありません。ファイルには "
                    + workbook.items().size() + " 件あります。";
        } else {
            message = summary(rows);
        }
        if (!notice.isEmpty()) {
            message = notice + "  " + message;
        }
        summaryLabel.setText(message);
        summaryLabel.setToolTipText(message);
        String resolved = workbook == null
                ? (layout.sheetName == null || layout.sheetName.isBlank() ? "先頭のシート" : layout.sheetName)
                : workbook.sheetName();
        String layoutText = layout.summary(resolved);
        layoutLabel.setText(layoutText);
        layoutLabel.setToolTipText(layoutText);
        boolean opened = workbook != null;
        saveButton.setEnabled(opened);
        reloadButton.setEnabled(opened);
    }

    private String summary(List<WbsRow> rows) {
        int owned = 0;
        int missingActual = 0;
        double plan = 0;
        double actual = 0;
        for (WbsRow row : rows) {
            if (!row.owned()) {
                continue;
            }
            owned++;
            if (row.item().actualRow() < 0) {
                missingActual++;
            }
            if (row.item().plan() != null) {
                plan += row.item().plan();
            }
            if (row.item().actual() != null) {
                actual += row.item().actual();
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append(rows.size()).append("件（入力 ").append(owned)
                .append(" / 親 ").append(rows.size() - owned).append("）");
        builder.append("   予定合計 ").append(Hours.format(plan));
        builder.append("   実績合計 ").append(Hours.format(actual));
        appendSelection(builder, rows);
        if (!workbook.hasDate(currentDate)) {
            builder.append("    この日付の列はありません。保存すると追加します。");
        }
        if (missingActual > 0) {
            builder.append("    実績行がない項目が ").append(missingActual).append(" 件あります。");
        }
        return builder.toString();
    }

    private void appendSelection(StringBuilder builder, List<WbsRow> rows) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(selected);
        if (modelRow < 0 || modelRow >= rows.size()) {
            return;
        }
        WbsItem item = rows.get(modelRow).item();
        OptionalInt column = workbook.columnOf(currentDate);
        String name = item.name().isBlank() ? item.wbsNo() : item.name();
        builder.append("    ").append(name);
        builder.append("  予定 ").append(address(item.planRow(), column));
        builder.append("  実績 ").append(address(item.actualRow(), column));
    }

    private String address(int row, OptionalInt column) {
        if (row < 0) {
            return "—";
        }
        int resolved = column.isPresent() ? column.getAsInt() : layout.firstDateColumn;
        String text = Columns.name(resolved) + (row + 1);
        return column.isEmpty() ? text + "（新規）" : text;
    }

    private boolean confirmSaveIfDirty(String lead) {
        if (workbook == null || !workbook.isDirty()) {
            return true;
        }
        String[] options = {"保存する", "保存しない", "キャンセル"};
        int choice = JOptionPane.showOptionDialog(
                this,
                lead + "\n編集中の予定・実績を保存しますか？",
                "確認",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) {
            return save();
        }
        return choice == 1;
    }

    private boolean commitTableEdit() {
        if (!table.isEditing()) {
            return true;
        }
        if (table.getCellEditor().stopCellEditing()) {
            return true;
        }
        error("時間は 0 以上の数値で入力してください。空欄にするとクリアします。");
        return false;
    }

    private void closeWindow() {
        if (!confirmSaveIfDirty("終了します。")) {
            return;
        }
        prefs.put("lastAssignee", assigneeText());
        dispose();
        if (workbook != null) {
            workbook.close();
            workbook = null;
        }
    }

    private void setDate(LocalDate date) {
        adjusting = true;
        try {
            dateSpinner.setValue(date);
            currentDate = date;
            weekdayLabel.setText("（" + JapaneseDates.weekday(date) + "）");
        } finally {
            adjusting = false;
        }
    }

    private LocalDate readSpinnerCommit() {
        suppressDateEvents = true;
        try {
            try {
                dateSpinner.commitEdit();
            } catch (ParseException ignored) {
                // Keep the last valid date.
            }
            return readDateRaw();
        } finally {
            suppressDateEvents = false;
        }
    }

    private LocalDate readDateRaw() {
        return (LocalDate) dateSpinner.getValue();
    }

    private String assigneeText() {
        if (assigneeBox.getEditor().getEditorComponent() instanceof JTextField editor) {
            return editor.getText().trim();
        }
        Object value = assigneeBox.getSelectedItem();
        return value == null ? "" : value.toString().trim();
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "WBS実績入力", JOptionPane.ERROR_MESSAGE);
    }

    private static String message(Throwable error) {
        String text = error.getMessage();
        return text == null || text.isBlank() ? error.toString() : text;
    }

    private static JSpinner createDateSpinner(LocalDate date) {
        JSpinner spinner = new JSpinner(new DaySpinnerModel(date));
        spinner.setEditor(new DaySpinnerEditor(spinner));
        Dimension size = spinner.getPreferredSize();
        spinner.setPreferredSize(new Dimension(120, size.height));
        return spinner;
    }

    private static final class DaySpinnerModel extends AbstractSpinnerModel {
        private LocalDate date;

        DaySpinnerModel(LocalDate date) {
            this.date = date;
        }

        @Override
        public Object getValue() {
            return date;
        }

        @Override
        public void setValue(Object value) {
            if (!(value instanceof LocalDate next)) {
                throw new IllegalArgumentException("LocalDate required");
            }
            if (!next.equals(date)) {
                date = next;
                fireStateChanged();
            }
        }

        @Override
        public Object getNextValue() {
            return date.plusDays(1);
        }

        @Override
        public Object getPreviousValue() {
            return date.minusDays(1);
        }
    }

    private static final class DaySpinnerEditor extends JSpinner.DefaultEditor {
        DaySpinnerEditor(JSpinner spinner) {
            super(spinner);
            JFormattedTextField field = getTextField();
            field.setFormatterFactory(new DefaultFormatterFactory(new YyyymmddFormatter()));
            field.setHorizontalAlignment(JTextField.LEFT);
            field.setColumns(8);
            field.setValue(spinner.getValue());
            OverwriteOnType.install(field);
        }
    }

    /** First printable key after focus replaces the whole value instead of appending. */
    private static final class OverwriteOnType {
        private OverwriteOnType() {
        }

        static void install(JTextField field) {
            final boolean[] replace = {true};
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent event) {
                    replace[0] = true;
                    javax.swing.SwingUtilities.invokeLater(field::selectAll);
                }
            });
            field.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyTyped(java.awt.event.KeyEvent event) {
                    char ch = event.getKeyChar();
                    if (!replace[0] || Character.isISOControl(ch) || ch == KeyEvent.CHAR_UNDEFINED) {
                        return;
                    }
                    replace[0] = false;
                    field.setText(String.valueOf(ch));
                    event.consume();
                }
            });
        }
    }

    private static final class YyyymmddFormatter extends JFormattedTextField.AbstractFormatter {
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

        @Override
        public Object stringToValue(String text) throws ParseException {
            try {
                return LocalDate.parse(text.trim(), FORMAT);
            } catch (DateTimeParseException ex) {
                throw new ParseException(ex.getMessage(), 0);
            }
        }

        @Override
        public String valueToString(Object value) {
            return value == null ? "" : FORMAT.format((LocalDate) value);
        }
    }

    private static GridBagConstraints constraints(int x, int y, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = weight;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(3, 4, 3, 4);
        return constraints;
    }

    private JMenuItem menuItem(String label, int key, ActionListener action) {
        JMenuItem item = new JMenuItem(label);
        if (key != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        item.addActionListener(action);
        return item;
    }

    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (!(data instanceof List<?> files) || files.isEmpty() || !(files.get(0) instanceof File file)) {
                    return false;
                }
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
                    error("Excelファイル（.xlsx / .xls）をドロップしてください。");
                    return false;
                }
                openFile(file.toPath());
                return true;
            } catch (Exception ex) {
                error("ファイルを開けません。\n" + message(ex));
                return false;
            }
        }
    }

    private static final class HoursEditor extends DefaultCellEditor {
        private final JTextField field;
        private boolean replaceOnType = true;
        private boolean startedByKey;

        HoursEditor() {
            super(new JTextField());
            field = (JTextField) getComponent();
            field.setHorizontalAlignment(JTextField.RIGHT);
            field.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            setClickCountToStart(1);
            field.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyTyped(java.awt.event.KeyEvent event) {
                    char ch = event.getKeyChar();
                    if (!replaceOnType || Character.isISOControl(ch) || ch == KeyEvent.CHAR_UNDEFINED) {
                        return;
                    }
                    replaceOnType = false;
                    field.setText(String.valueOf(ch));
                    event.consume();
                }
            });
        }

        @Override
        public boolean isCellEditable(java.util.EventObject event) {
            startedByKey = event instanceof KeyEvent;
            return super.isCellEditable(event);
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            replaceOnType = true;
            Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            if (startedByKey) {
                // Let the key that started editing become the whole new value.
                field.setText("");
            } else {
                javax.swing.SwingUtilities.invokeLater(field::selectAll);
            }
            startedByKey = false;
            return component;
        }

        @Override
        public boolean stopCellEditing() {
            if (!Hours.parsable(field.getText())) {
                field.setBackground(new Color(0xFDECEC));
                field.selectAll();
                replaceOnType = true;
                return false;
            }
            field.setBackground(UIManager.getColor("TextField.background"));
            return super.stopCellEditing();
        }

        @Override
        public void cancelCellEditing() {
            field.setBackground(UIManager.getColor("TextField.background"));
            super.cancelCellEditing();
        }
    }
}
