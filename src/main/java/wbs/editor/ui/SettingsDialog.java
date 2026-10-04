package wbs.editor.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import wbs.editor.model.Columns;
import wbs.editor.model.LayoutConfig;

final class SettingsDialog extends JDialog {
    private final JComboBox<String> sheetBox = new JComboBox<>();
    private final JSpinner dateRowSpinner = spinner(2, 1);
    private final JSpinner weekdayRowSpinner = spinner(3, 0);
    private final JSpinner dataStartSpinner = spinner(4, 1);
    private final JTextField levelField = columnField();
    private final JTextField wbsField = columnField();
    private final JTextField nameField = columnField();
    private final JTextField assigneeField = columnField();
    private final JTextField kindField = columnField();
    private final JTextField dateColumnField = columnField();
    private final JTextField planLabelField = new JTextField(8);
    private final JTextField actualLabelField = new JTextField(8);
    private final JLabel preview = new JLabel(" ");
    private LayoutConfig result;
    private boolean updatingPreview;

    SettingsDialog(Frame owner, LayoutConfig current, List<String> sheetNames) {
        super(owner, "シート配置", true);
        sheetBox.setEditable(true);
        sheetBox.addItem("");
        for (String name : sheetNames) {
            if (name != null && !name.isBlank() && !containsSheet(name)) {
                sheetBox.addItem(name);
            }
        }
        if (current.sheetName != null && !current.sheetName.isBlank() && !containsSheet(current.sheetName)) {
            sheetBox.addItem(current.sheetName);
        }

        JLabel explain = new JLabel("<html><body style='width:520px'>"
                + "シート名と、各項目の行・列を指定します。行は 1 から、列は A や F、または 1 からの番号です。"
                + "初期値は、日付が 2 行目、曜日が 3 行目、データが 4 行目、"
                + "Level が A、WBS No が B、項目名が C、担当が D、予定／実績が E、日付が F 列からです。"
                + "<br>各WBSは「予定」の行と、その次の「実績」の行を 1 件として読み書きします。"
                + "</body></html>");

        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        row = add(form, row, "シート名", sheetBox, "空欄なら先頭のシート");
        row = add(form, row, "日付の行", dateRowSpinner, "10月1日 などの行");
        row = add(form, row, "曜日の行", weekdayRowSpinner, "0 なら曜日は書き込まない");
        row = add(form, row, "データ開始行", dataStartSpinner, "最初のWBSがある行");
        row = add(form, row, "Level", levelField, null);
        row = add(form, row, "WBS No", wbsField, null);
        row = add(form, row, "項目名", nameField, null);
        row = add(form, row, "担当", assigneeField, null);
        row = add(form, row, "予定／実績", kindField, "予定・実績と書いている列");
        row = add(form, row, "日付の開始列", dateColumnField, "この列から右へ日付が並びます");
        row = add(form, row, "予定と書く文字", planLabelField, null);
        add(form, row, "実績と書く文字", actualLabelField, null);

        JButton reset = new JButton("初期値に戻す");
        JButton cancel = new JButton("キャンセル");
        JButton ok = new JButton("保存");
        reset.addActionListener(event -> show(new LayoutConfig()));
        cancel.addActionListener(event -> close(null));
        ok.addActionListener(event -> approve());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(reset);
        buttons.add(cancel);
        buttons.add(ok);

        preview.setForeground(new Color(0x667085));
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(preview, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(explain, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);

        listen(sheetBox);
        listen(dateRowSpinner);
        listen(weekdayRowSpinner);
        listen(dataStartSpinner);
        listen(levelField);
        listen(wbsField);
        listen(nameField);
        listen(assigneeField);
        listen(kindField);
        listen(dateColumnField);
        listen(planLabelField);
        listen(actualLabelField);

        show(current);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    LayoutConfig result() {
        return result;
    }

    private void approve() {
        try {
            LayoutConfig config = readForm();
            String error = config.validate();
            if (error != null) {
                JOptionPane.showMessageDialog(this, error, "シート配置", JOptionPane.WARNING_MESSAGE);
                return;
            }
            close(config);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "シート配置", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void close(LayoutConfig config) {
        result = config;
        setVisible(false);
        dispose();
    }

    private void show(LayoutConfig config) {
        sheetBox.setSelectedItem(config.sheetName == null ? "" : config.sheetName);
        if (sheetBox.getEditor().getEditorComponent() instanceof JTextField field) {
            field.setText(config.sheetName == null ? "" : config.sheetName);
        }
        dateRowSpinner.setValue(config.dateRow);
        weekdayRowSpinner.setValue(config.weekdayRow);
        dataStartSpinner.setValue(config.dataStartRow);
        levelField.setText(Columns.name(config.levelColumn));
        wbsField.setText(Columns.name(config.wbsColumn));
        nameField.setText(Columns.name(config.nameColumn));
        assigneeField.setText(Columns.name(config.assigneeColumn));
        kindField.setText(Columns.name(config.kindColumn));
        dateColumnField.setText(Columns.name(config.firstDateColumn));
        planLabelField.setText(config.planLabel);
        actualLabelField.setText(config.actualLabel);
        updatePreview();
    }

    private void updatePreview() {
        if (updatingPreview) {
            return;
        }
        updatingPreview = true;
        try {
            updatePreviewText();
        } finally {
            updatingPreview = false;
        }
    }

    private void updatePreviewText() {
        try {
            LayoutConfig config = readForm();
            String error = config.validate();
            if (error != null) {
                preview.setForeground(new Color(0xB42318));
                preview.setText(error);
                return;
            }
            String sheet = config.sheetName.isBlank() ? "先頭のシート" : config.sheetName;
            preview.setForeground(new Color(0x667085));
            preview.setText("<html>" + config.summary(sheet) + "</html>");
        } catch (IllegalArgumentException ex) {
            preview.setForeground(new Color(0xB42318));
            preview.setText(ex.getMessage());
        }
    }

    private LayoutConfig readForm() {
        LayoutConfig config = new LayoutConfig();
        config.sheetName = sheetName();
        config.dateRow = number(dateRowSpinner);
        config.weekdayRow = number(weekdayRowSpinner);
        config.dataStartRow = number(dataStartSpinner);
        config.levelColumn = Columns.parse(levelField.getText());
        config.wbsColumn = Columns.parse(wbsField.getText());
        config.nameColumn = Columns.parse(nameField.getText());
        config.assigneeColumn = Columns.parse(assigneeField.getText());
        config.kindColumn = Columns.parse(kindField.getText());
        config.firstDateColumn = Columns.parse(dateColumnField.getText());
        config.planLabel = planLabelField.getText().trim();
        config.actualLabel = actualLabelField.getText().trim();
        return config;
    }

    private String sheetName() {
        if (sheetBox.getEditor().getEditorComponent() instanceof JTextField field) {
            return field.getText().trim();
        }
        Object value = sheetBox.getSelectedItem();
        return value == null ? "" : value.toString().trim();
    }

    private int number(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException ex) {
            throw new IllegalArgumentException("行番号を確認してください。");
        }
        return ((Number) spinner.getValue()).intValue();
    }

    private boolean containsSheet(String name) {
        for (int i = 0; i < sheetBox.getItemCount(); i++) {
            if (name.equals(sheetBox.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void listen(JComponent component) {
        if (component instanceof JSpinner spinner) {
            spinner.addChangeListener(event -> updatePreview());
            return;
        }
        if (component instanceof JComboBox<?> combo) {
            combo.addActionListener(event -> updatePreview());
            if (combo.getEditor().getEditorComponent() instanceof JTextField field) {
                listen(field);
            }
            return;
        }
        if (component instanceof JTextField field) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    updatePreview();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    updatePreview();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    updatePreview();
                }
            });
        }
    }

    private static int add(JPanel panel, int row, String label, JComponent field, String hint) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 0, 4, 12);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 0, 4, 12);
        panel.add(field, fieldConstraints);

        if (hint != null) {
            GridBagConstraints hintConstraints = new GridBagConstraints();
            hintConstraints.gridx = 2;
            hintConstraints.gridy = row;
            hintConstraints.anchor = GridBagConstraints.WEST;
            hintConstraints.insets = new Insets(4, 0, 4, 0);
            JLabel hintLabel = new JLabel(hint);
            hintLabel.setForeground(new Color(0x667085));
            panel.add(hintLabel, hintConstraints);
        }
        return row + 1;
    }

    private static JSpinner spinner(int value, int minimum) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, 100000, 1));
    }

    private static JTextField columnField() {
        JTextField field = new JTextField(6);
        return field;
    }
}
