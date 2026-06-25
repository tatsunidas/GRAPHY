package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

/**
 * Editable manager dialog for representative WW/WL presets.
 *
 * <p>Presets are stored in {@code graphy.properties} under a single key
 * ({@link #PRESET_PROP_KEY}). When the key is missing, empty, or cannot be
 * parsed, a built-in default set is used instead so the feature always works.
 *
 * @author tatsunidas
 */
public class WwWlPresets extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Property key holding all presets, serialized as a single string. */
    public static final String PRESET_PROP_KEY = "WwWlPresets";

    // Serialization delimiters: records (presets) are split by ';', fields by ','
    private static final String RECORD_SEP = ";";
    private static final String FIELD_SEP = ",";

    private static WwWlPresets instance;

    private DefaultListModel<WwWlPreset> listModel;
    private JList<WwWlPreset> presetList;

    /**
     * Immutable value object describing a single WW/WL preset. The values are
     * expressed on the calibrated (physical) brightness scale, e.g. HU for CT.
     */
    public static class WwWlPreset {
        public final String name;
        public final double wl; // window center (level)
        public final double ww; // window width

        public WwWlPreset(String name, double wl, double ww) {
            this.name = name;
            this.wl = wl;
            this.ww = ww;
        }

        @Override
        public String toString() {
            return String.format("%s  (WL %s / WW %s)", name, formatNum(wl), formatNum(ww));
        }
    }

    // ==========================================================
    // Public API
    // ==========================================================

    /** Opens the (singleton) preset editor dialog. */
    public static void showDialog(Window owner) {
        if (instance == null) {
            instance = new WwWlPresets(owner);
        }
        instance.reloadModel();
        instance.setLocationRelativeTo(owner);
        instance.setVisible(true);
        instance.toFront();
    }

    /**
     * Returns the representative built-in defaults. Used as a fallback when no
     * valid presets are stored in the properties file.
     */
    public static List<WwWlPreset> getDefaultPresets() {
        List<WwWlPreset> list = new ArrayList<>();
        list.add(new WwWlPreset("CT Brain", 40, 80));
        list.add(new WwWlPreset("CT Soft Tissue", 40, 400));
        list.add(new WwWlPreset("CT Mediastinum", 50, 350));
        list.add(new WwWlPreset("CT Liver", 60, 160));
        list.add(new WwWlPreset("CT Lung", -600, 1500));
        list.add(new WwWlPreset("CT Bone", 300, 1500));
        return list;
    }

    /**
     * Loads the presets from {@code graphy.properties}. Falls back to the
     * built-in defaults when the value is absent or unreadable.
     */
    public static List<WwWlPreset> loadPresets() {
        try {
            String raw = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(), PRESET_PROP_KEY);
            if (raw == null || raw.trim().isEmpty()) {
                return getDefaultPresets();
            }
            List<WwWlPreset> result = new ArrayList<>();
            for (String record : raw.split(RECORD_SEP)) {
                if (record.trim().isEmpty()) {
                    continue;
                }
                String[] fields = record.split(FIELD_SEP);
                if (fields.length != 3) {
                    continue;
                }
                String name = fields[0].trim();
                double wl = Double.parseDouble(fields[1].trim());
                double ww = Double.parseDouble(fields[2].trim());
                if (!name.isEmpty()) {
                    result.add(new WwWlPreset(name, wl, ww));
                }
            }
            // If parsing yielded nothing usable, treat it as "no presets"
            return result.isEmpty() ? getDefaultPresets() : result;
        } catch (Exception e) {
            Log.logger.warning("Failed to read WW/WL presets, using defaults: " + e);
            return getDefaultPresets();
        }
    }

    /** Persists the given presets into {@code graphy.properties}. */
    public static void savePresets(List<WwWlPreset> presets) {
        StringBuilder sb = new StringBuilder();
        for (WwWlPreset p : presets) {
            if (sb.length() > 0) {
                sb.append(RECORD_SEP);
            }
            sb.append(sanitize(p.name)).append(FIELD_SEP)
              .append(formatNum(p.wl)).append(FIELD_SEP)
              .append(formatNum(p.ww));
        }
        try {
            PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props.toString(), PRESET_PROP_KEY, sb.toString());
        } catch (Exception e) {
            Log.logger.warning("Failed to save WW/WL presets: " + e);
        }
    }

    /**
     * Applies a preset to the given Praparat. The preset values are interpreted
     * on the calibrated brightness scale and converted back to raw pixel values
     * before being applied, mirroring {@link WwWlAdjusterDialog}'s direct input.
     */
    public static void applyPreset(Praparat pp, WwWlPreset preset) {
        if (pp == null || preset == null) {
            return;
        }
        SlideGlass sg = pp.getCurrentSlide();
        if (sg == null || sg.getOriginalImage() == null) {
            return;
        }

        // Window width must be positive to avoid a zero/negative display range
        double ww = preset.ww < 1.0 ? 1.0 : preset.ww;
        double physMin = preset.wl - (ww / 2.0);
        double physMax = preset.wl + (ww / 2.0);

        // Convert calibrated values back to raw pixel values for the model
        ij.measure.Calibration cal = sg.getOriginalCalibration();
        double rawMin = (cal != null && cal.calibrated()) ? cal.getRawValue(physMin) : physMin;
        double rawMax = (cal != null && cal.calibrated()) ? cal.getRawValue(physMax) : physMax;

        // A negative-slope calibration can flip the order
        if (rawMax < rawMin) {
            double tmp = rawMin;
            rawMin = rawMax;
            rawMax = tmp;
        }

        // -1 = all channels (monochrome window/level)
        pp.updateSliderContrast(pp.getCurrentSlidePos(), -1, rawMin, rawMax);
    }

    // ==========================================================
    // Dialog UI
    // ==========================================================

    private WwWlPresets(Window owner) {
        super(owner, Resources.i18n("WwWlPresets.title"), ModalityType.MODELESS);
        initComponents();
        setSize(360, 320);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));

        listModel = new DefaultListModel<>();
        presetList = new JList<>(listModel);
        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(presetList), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(5, 1, 0, 6));
        JButton newBtn = new JButton(Resources.i18n("WwWlPresets.button.new"));
        JButton editBtn = new JButton(Resources.i18n("WwWlPresets.button.edit"));
        JButton deleteBtn = new JButton(Resources.i18n("WwWlPresets.button.delete"));
        JButton resetBtn = new JButton(Resources.i18n("WwWlPresets.button.reset"));
        JButton closeBtn = new JButton(Resources.i18n("WwWlPresets.button.close"));
        btnPanel.add(newBtn);
        btnPanel.add(editBtn);
        btnPanel.add(deleteBtn);
        btnPanel.add(resetBtn);
        btnPanel.add(closeBtn);

        JPanel btnWrapper = new JPanel(new BorderLayout());
        btnWrapper.add(btnPanel, BorderLayout.NORTH);
        add(btnWrapper, BorderLayout.EAST);

        newBtn.addActionListener(e -> onNew());
        editBtn.addActionListener(e -> onEdit());
        deleteBtn.addActionListener(e -> onDelete());
        resetBtn.addActionListener(e -> onReset());
        closeBtn.addActionListener(e -> setVisible(false));
    }

    /** Reloads the list model from the persisted presets. */
    private void reloadModel() {
        listModel.clear();
        for (WwWlPreset p : loadPresets()) {
            listModel.addElement(p);
        }
    }

    /** Writes the current list model back to the properties file. */
    private void persistModel() {
        List<WwWlPreset> presets = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            presets.add(listModel.get(i));
        }
        savePresets(presets);
    }

    private void onNew() {
        WwWlPreset preset = promptForPreset(null, Resources.i18n("WwWlPresets.dialog.newTitle"));
        if (preset != null) {
            listModel.addElement(preset);
            persistModel();
        }
    }

    private void onEdit() {
        int idx = presetList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("WwWlPresets.info.selectFirst"),
                    Resources.i18n("dialog.title.information"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        WwWlPreset edited = promptForPreset(listModel.get(idx), Resources.i18n("WwWlPresets.dialog.editTitle"));
        if (edited != null) {
            listModel.set(idx, edited);
            persistModel();
        }
    }

    private void onDelete() {
        int idx = presetList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("WwWlPresets.info.selectFirst"),
                    Resources.i18n("dialog.title.information"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int res = JOptionPane.showConfirmDialog(this,
                Resources.i18n("WwWlPresets.confirm.delete"),
                Resources.i18n("dialog.title.confirm"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            listModel.remove(idx);
            persistModel();
        }
    }

    private void onReset() {
        int res = JOptionPane.showConfirmDialog(this,
                Resources.i18n("WwWlPresets.confirm.reset"),
                Resources.i18n("dialog.title.confirm"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            savePresets(getDefaultPresets());
            reloadModel();
        }
    }

    /**
     * Shows a small form to create or edit a preset. Returns null if the user
     * cancels. Re-prompts on invalid input rather than discarding it.
     *
     * @param base  preset to pre-fill the form with, or null for a new one
     * @param title dialog title
     */
    private WwWlPreset promptForPreset(WwWlPreset base, String title) {
        JTextField nameField = new JTextField(base != null ? base.name : "");
        JTextField wlField = new JTextField(base != null ? formatNum(base.wl) : "");
        JTextField wwField = new JTextField(base != null ? formatNum(base.ww) : "");

        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.add(new JLabel(Resources.i18n("WwWlPresets.label.name")));
        panel.add(nameField);
        panel.add(new JLabel(Resources.i18n("WwWlPresets.label.wl")));
        panel.add(wlField);
        panel.add(new JLabel(Resources.i18n("WwWlPresets.label.ww")));
        panel.add(wwField);

        while (true) {
            int res = JOptionPane.showConfirmDialog(this, panel, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) {
                return null;
            }
            String name = sanitize(nameField.getText().trim());
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        Resources.i18n("WwWlPresets.error.nameRequired"),
                        Resources.i18n("dialog.title.inputWarning"),
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            try {
                double wl = Double.parseDouble(wlField.getText().trim());
                double ww = Double.parseDouble(wwField.getText().trim());
                return new WwWlPreset(name, wl, ww);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        Resources.i18n("WwWlPresets.error.invalidValue"),
                        Resources.i18n("dialog.title.inputWarning"),
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    // ==========================================================
    // Helpers
    // ==========================================================

    /** Removes serialization delimiters from a user-entered preset name. */
    private static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ").replace("=", " ").trim();
    }

    /** Formats a number without a trailing ".0" for whole values. */
    private static String formatNum(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
