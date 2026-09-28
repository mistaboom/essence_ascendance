package dev.essence.packtester;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

final class PackTesterWindow {
    private final Path project;
    private final LocalSettings settings;
    private final JFrame frame = new JFrame("Essence Ascendance — Pack Tester");
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Display name", "Instance ID", "Minecraft", "Loader", "Loader version", "Status", "Mods directory"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextArea details = new JTextArea(4, 70), output = new JTextArea(16, 100);
    private final JLabel state = new JLabel("Discovering instances…");
    private final JCheckBox closed = new JCheckBox("I confirm the selected game's Minecraft is closed and will stay closed until completion.");
    private final JButton build = new JButton("Build & Deploy"), launch = new JButton("Build, Deploy & Launch"), cancel = new JButton("Cancel"), refresh = new JButton("Refresh"), browse = new JButton("Browse…"), prism = new JButton("Prism executable…"), recover = new JButton("Recover interrupted deployment");
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private List<PrismDiscovery.Instance> instances = List.of();
    private List<Compatibility.Verdict> verdicts = List.of();
    private boolean busy;
    private boolean updatingPicker;
    private Cancellation cancellation;
    private javax.swing.Timer outputTimer;
    PackTesterWindow(Path project, LocalSettings settings) { this.project = project; this.settings = settings; }
    void show() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (busy) { if (cancellation != null) cancellation.cancel(); log("Close requested: waiting for work/rollback to finish. Close again when idle."); }
                else { outputTimer.stop(); frame.dispose(); }
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Discovery already provides a stable name/path order. Keep row indices stable
        // while replacing the picker model on Refresh.
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !updatingPicker) selection(); });
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        output.setEditable(false); output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refresh); top.add(browse); top.add(prism);
        JButton folder = new JButton("Open Instance Folder"), latest = new JButton("Open latest.log"), reports = new JButton("Open reports");
        top.add(folder); top.add(latest); top.add(reports);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(build); controls.add(launch); controls.add(cancel); controls.add(recover);
        JPanel middle = new JPanel(new BorderLayout(4, 4));
        middle.add(new JScrollPane(details), BorderLayout.NORTH); middle.add(closed, BorderLayout.CENTER); middle.add(controls, BorderLayout.SOUTH);
        JPanel picker = new JPanel(new BorderLayout(5, 5)); picker.add(top, BorderLayout.NORTH); picker.add(new JScrollPane(table), BorderLayout.CENTER); picker.add(middle, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, picker, new JScrollPane(output)); split.setResizeWeight(0.55);
        frame.add(split); frame.add(state, BorderLayout.SOUTH);
        refresh.addActionListener(e -> refresh()); browse.addActionListener(e -> browse()); prism.addActionListener(e -> executable());
        build.addActionListener(e -> execute(false, false)); launch.addActionListener(e -> execute(true, false)); recover.addActionListener(e -> execute(false, true));
        cancel.addActionListener(e -> { if (cancellation != null) { cancellation.cancel(); cancel.setEnabled(false); state.setText("Cancelling; waiting for this build/rollback to finish safely…"); log("Cancellation requested. The current Gradle invocation will finish; no shared daemon is terminated."); } });
        closed.addActionListener(e -> buttons());
        folder.addActionListener(e -> open("instance")); latest.addActionListener(e -> open("log")); reports.addActionListener(e -> open("reports"));
        outputTimer = new javax.swing.Timer(120, e -> {
            StringBuilder batch = new StringBuilder();
            for (String line; batch.length() < 65536 && (line = lines.poll()) != null;) batch.append(line).append('\n');
            if (!batch.isEmpty()) {
                output.append(batch.toString());
                if (output.getDocument().getLength() > 1_000_000) output.replaceRange("", 0, output.getDocument().getLength() - 750_000);
                output.setCaretPosition(output.getDocument().getLength());
            }
        });
        outputTimer.start(); frame.setSize(1240, 820); frame.setLocationByPlatform(true); frame.setVisible(true); refresh();
    }
    private void log(String line) { lines.add(line); }
    private int index() { return table.getSelectedRow() < 0 ? -1 : table.convertRowIndexToModel(table.getSelectedRow()); }
    private PrismDiscovery.Instance selected() { int i = index(); return i < 0 || i >= instances.size() ? null : instances.get(i); }
    private void buttons() {
        int row = index(); boolean supported = row >= 0 && row < verdicts.size() && verdicts.get(row).status().equals("SUPPORTED");
        build.setEnabled(!busy && supported && closed.isSelected());
        launch.setEnabled(!busy && supported && closed.isSelected());
        recover.setEnabled(!busy && selected() != null && closed.isSelected());
        cancel.setEnabled(busy && cancellation != null); refresh.setEnabled(!busy); browse.setEnabled(!busy); prism.setEnabled(!busy); table.setEnabled(!busy); closed.setEnabled(!busy);
    }
    private void selection() {
        closed.setSelected(false);
        var target = selected();
        if (target == null) details.setText("Select an instance explicitly. A missing remembered instance is never replaced by another target.");
        else {
            details.setText(target + "\nDestination: " + target.mods() + "\n" + target.loader() + " " + target.loaderVersion() + " / Minecraft " + target.minecraft() + "\n" + verdicts.get(index()));
            settings.selected = target.directory().toString(); save();
        }
        buttons();
    }
    private void save() { try { settings.save(project); } catch (IOException e) { log("Could not save local settings: " + e); } }
    private void refresh() {
        busy = true; buttons(); state.setText("Reading Prism metadata and required mod metadata…");
        String remembered = settings.selected;
        new SwingWorker<List<PrismDiscovery.Instance>, Void>() {
            List<Compatibility.Verdict> checks;
            protected List<PrismDiscovery.Instance> doInBackground() {
                var list = new PrismDiscovery().discover(settings, PackTesterWindow.this::log);
                Compatibility compatibility = new Compatibility(project);
                checks = list.stream().map(compatibility::instance).toList(); return list;
            }
            protected void done() {
                updatingPicker = true;
                try {
                    table.clearSelection(); model.setRowCount(0); instances = get(); verdicts = checks;
                    for (int i = 0; i < instances.size(); i++) { var t = instances.get(i); model.addRow(new Object[]{t.name(), t.id(), t.minecraft(), t.loader(), t.loaderVersion(), verdicts.get(i).status(), t.mods()}); }
                    if (remembered != null) for (int i = 0; i < instances.size(); i++) if (instances.get(i).directory().toString().equals(remembered)) table.setRowSelectionInterval(table.convertRowIndexToView(i), table.convertRowIndexToView(i));
                    state.setText(instances.size() + " instance(s). " + (remembered != null && selected() == null ? "Remembered target is unavailable; choose explicitly." : "Review the exact destination before starting."));
                } catch (Exception e) { error(e); }
                finally { updatingPicker = false; busy = false; selection(); }
            }
        }.execute();
    }
    private void browse() {
        JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a Prism application root, instances directory, or an instance");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (Files.exists(path.resolve("prismlauncher.cfg"))) { if (!settings.roots.contains(path.toString())) settings.roots.add(path.toString()); }
        else {
            if (!Files.exists(path.resolve("instance.cfg")) && path.getFileName() != null && path.getFileName().toString().equals("mods") && path.getParent().getParent() != null) path = path.getParent().getParent();
            if (!Files.exists(path.resolve("instance.cfg")) && path.getFileName() != null && Set.of("minecraft", ".minecraft").contains(path.getFileName().toString())) path = path.getParent();
            if (!settings.browsed.contains(path.toString())) settings.browsed.add(path.toString());
            if (Files.exists(path.resolve("instance.cfg"))) settings.selected = path.toString();
        }
        save(); refresh();
    }
    private void executable() {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Choose installed prismlauncher.exe");
        if (settings.executable != null) chooser.setSelectedFile(Path.of(settings.executable).toFile());
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) { settings.executable = chooser.getSelectedFile().getAbsolutePath(); save(); log("Prism executable selection saved locally; identity will be verified before Build, Deploy & Launch."); }
    }
    private void execute(boolean launchGame, boolean recovery) {
        var target = selected(); if (target == null || busy || !closed.isSelected()) return;
        Path exe = settings.executable == null ? null : Path.of(settings.executable);
        String operation = recovery ? "Recover interrupted deployment" : launchGame ? "Build, Deploy & Launch" : "Build & Deploy";
        if (JOptionPane.showConfirmDialog(frame, operation + "\n" + target.name() + " [" + target.id() + "]\n" + target.loader() + " " + target.loaderVersion() + "\n" + target.mods()
                + "\n\nRunning state may be unknown. You confirmed this game's Minecraft is closed.\nKeep it closed until completion.", operation, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        cancellation = new Cancellation(); Cancellation token = cancellation;
        busy = true; buttons(); state.setText(operation + " — " + target.id());
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                PackService service = new PackService(project);
                if (recovery) service.recover(target, true, PackTesterWindow.this::log);
                else service.execute(target, true, launchGame, exe, token, PackTesterWindow.this::log);
                return null;
            }
            protected void done() {
                try { get(); state.setText(operation + " completed for " + target.id() + ". See output for destination and SHA-256."); }
                catch (Exception e) { error(e); }
                finally { busy = false; cancellation = null; closed.setSelected(false); buttons(); }
            }
        }.execute();
    }
    private void error(Exception e) {
        Throwable cause = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
        StringWriter text = new StringWriter(); cause.printStackTrace(new PrintWriter(text)); log(text.toString());
        state.setText("Stopped: " + cause.getMessage());
        JOptionPane.showMessageDialog(frame, cause.getMessage() + "\nSee output for details.", "Pack Tester", JOptionPane.ERROR_MESSAGE);
    }
    private void open(String type) {
        var target = selected(); if (target == null) return;
        Path path = switch (type) { case "log" -> target.game().resolve("logs/latest.log"); case "reports" -> target.game().resolve("config/essence_ascendance/reports"); default -> target.directory(); };
        if (!Files.exists(path)) { log("Not present yet: " + path); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { Desktop.getDesktop().open(path.toFile()); return null; }
            protected void done() { try { get(); } catch (Exception e) { error(e); } }
        }.execute();
    }
}
