import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Aplicación gráfica del solucionador de Sudoku.
 *
 * Utiliza únicamente Swing y clases estándar de Java. No requiere FXML,
 * JavaFX, Maven, Gradle ni bibliotecas externas.
 */
public class SudokuApp extends JFrame {

    private static final String CARD_MENU = "MENU";
    private static final String CARD_PREVIEW = "PREVIEW";
    private static final String CARD_RESULT = "RESULT";

    private static final long DEFAULT_SEED = 42L;

    private static final Color BACKGROUND = new Color(18, 18, 18);
    private static final Color PANEL_COLOR = new Color(28, 28, 30);
    private static final Color TEXT_COLOR = new Color(245, 245, 245);
    private static final Color SECONDARY_TEXT = new Color(190, 190, 195);
    private static final Color ACCENT = new Color(105, 135, 255);
    private static final Color BUTTON_BACKGROUND = new Color(205, 205, 210);
    private static final Color BUTTON_TEXT = new Color(20, 20, 22);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContainer = new JPanel(cardLayout);

    private final MenuPanel menuPanel = new MenuPanel();
    private final PreviewPanel previewPanel = new PreviewPanel();
    private final ResultPanel resultPanel = new ResultPanel();

    private Mode selectedMode;
    private SudokuFileReader.LoadedPuzzle loadedPuzzle;
    private long currentSeed = DEFAULT_SEED;

    public SudokuApp() {
        super("Solucionador de Sudoku mediante algoritmo genético");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 680));
        setSize(1100, 780);
        setLocationRelativeTo(null);

        cardContainer.setBackground(BACKGROUND);
        cardContainer.add(menuPanel, CARD_MENU);
        cardContainer.add(previewPanel, CARD_PREVIEW);
        cardContainer.add(resultPanel, CARD_RESULT);

        setContentPane(cardContainer);
        showCard(CARD_MENU);
    }

    private void choosePuzzleFile(Mode mode) {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle(
                "Selecciona el archivo del nivel " + mode.displayName);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Archivos de Sudoku (*.txt)",
                "txt"));

        int response = chooser.showOpenDialog(this);
        if (response != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selectedFile = chooser.getSelectedFile().toPath();

        try {
            SudokuFileReader.LoadedPuzzle candidate =
                    SudokuFileReader.read(selectedFile);

            if (!mode.expectedDetectedLevel.equals(
                    candidate.getDetectedLevel())) {

                int confirmation = JOptionPane.showConfirmDialog(
                        this,
                        "Seleccionaste el modo " + mode.displayName
                                + ", pero el archivo contiene "
                                + candidate.getClueCount()
                                + " pistas y fue detectado como "
                                + candidate.getDetectedLevel() + ".\n\n"
                                + "¿Deseas continuar de todas formas?",
                        "El nivel no coincide",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (confirmation != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            selectedMode = mode;
            loadedPuzzle = candidate;
            currentSeed = DEFAULT_SEED;

            previewPanel.setPuzzle(
                    selectedMode,
                    loadedPuzzle);
            showCard(CARD_PREVIEW);

        } catch (IOException | IllegalArgumentException exception) {
            showError("No fue posible cargar el Sudoku", exception.getMessage());
        }
    }

    private void solveCurrentPuzzle() {
        if (loadedPuzzle == null) {
            showError(
                    "No hay Sudoku seleccionado",
                    "Primero selecciona un archivo de texto válido.");
            return;
        }

        final int[][] puzzle = loadedPuzzle.getBoard();
        final long seed = currentSeed;
        previewPanel.setSolving(true);

        SwingWorker<ExecutionResult, Void> worker =
                new SwingWorker<ExecutionResult, Void>() {

            @Override
            protected ExecutionResult doInBackground() {
                SudokuGeneticoOptimizado solver =
                        new SudokuGeneticoOptimizado(puzzle, seed);

                long startTime = System.nanoTime();
                SudokuGeneticoOptimizado.Result result = solver.solve();
                long elapsedNanoseconds = System.nanoTime() - startTime;

                return new ExecutionResult(
                        result,
                        elapsedNanoseconds,
                        seed,
                        solver.getClueCount(),
                        solver.getDetectedLevel());
            }

            @Override
            protected void done() {
                previewPanel.setSolving(false);

                try {
                    ExecutionResult execution = get();

                    if (execution.result == null) {
                        showError(
                                "No se encontró una solución",
                                "El algoritmo alcanzó el límite configurado "
                                        + "sin obtener una aptitud de 162.");
                        return;
                    }

                    resultPanel.setResult(
                            execution,
                            loadedPuzzle,
                            selectedMode);
                    showCard(CARD_RESULT);

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(
                            "Ejecución interrumpida",
                            "La resolución del Sudoku fue interrumpida.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showError(
                            "Error durante la resolución",
                            cause == null
                                    ? exception.getMessage()
                                    : cause.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void chooseAnotherFile() {
        if (selectedMode == null) {
            showCard(CARD_MENU);
            return;
        }

        choosePuzzleFile(selectedMode);
    }

    private void returnToMenu() {
        selectedMode = null;
        loadedPuzzle = null;
        currentSeed = DEFAULT_SEED;
        previewPanel.clear();
        resultPanel.clear();
        showCard(CARD_MENU);
    }

    private void showCard(String cardName) {
        cardLayout.show(cardContainer, cardName);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(
                this,
                message == null ? "Ocurrió un error inesperado." : message,
                title,
                JOptionPane.ERROR_MESSAGE);
    }

    private static JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        button.setForeground(BUTTON_TEXT);
        button.setBackground(BUTTON_BACKGROUND);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(235, 235, 235), 1),
                BorderFactory.createEmptyBorder(11, 22, 11, 22)));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private static JLabel createTitle(String text, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(TEXT_COLOR);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private static JPanel createInfoCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(PANEL_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 225, 230), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setForeground(SECONDARY_TEXT);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel.setForeground(TEXT_COLOR);
        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(valueLabel);
        return card;
    }

    private final class MenuPanel extends JPanel {

        MenuPanel() {
            setLayout(new GridBagLayout());
            setBackground(BACKGROUND);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(BACKGROUND);
            content.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));

            JLabel title = createTitle(
                    "Bienvenidos al solucionador de Sudoku",
                    38);
            JLabel subtitle = createTitle("Selecciona un modo", 25);

            JButton easyButton = createButton("Fácil");
            JButton mediumButton = createButton("Intermedio");
            JButton hardButton = createButton("Difícil");

            easyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            mediumButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            hardButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            Dimension buttonSize = new Dimension(260, 55);
            easyButton.setMaximumSize(buttonSize);
            mediumButton.setMaximumSize(buttonSize);
            hardButton.setMaximumSize(buttonSize);

            easyButton.addActionListener(event -> choosePuzzleFile(Mode.EASY));
            mediumButton.addActionListener(event -> choosePuzzleFile(Mode.MEDIUM));
            hardButton.addActionListener(event -> choosePuzzleFile(Mode.HARD));

            content.add(title);
            content.add(Box.createVerticalStrut(45));
            content.add(subtitle);
            content.add(Box.createVerticalStrut(45));
            content.add(easyButton);
            content.add(Box.createVerticalStrut(28));
            content.add(mediumButton);
            content.add(Box.createVerticalStrut(28));
            content.add(hardButton);

            add(content);
        }
    }

    private final class PreviewPanel extends JPanel {

        private final JLabel titleLabel = createTitle("", 31);
        private final JLabel fileLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
        private final SudokuBoardPanel boardPanel = new SudokuBoardPanel();
        private final JButton solveButton = createButton("Resolver Sudoku");
        private final JButton anotherFileButton = createButton("Elegir otro archivo");
        private final JButton menuButton = createButton("Volver al menú");
        private final JProgressBar progressBar = new JProgressBar();

        PreviewPanel() {
            setLayout(new BorderLayout(20, 15));
            setBackground(BACKGROUND);
            setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));

            JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setBackground(BACKGROUND);

            fileLabel.setForeground(SECONDARY_TEXT);
            fileLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            fileLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            header.add(titleLabel);
            header.add(Box.createVerticalStrut(8));
            header.add(fileLabel);

            JPanel boardWrapper = new JPanel(new GridBagLayout());
            boardWrapper.setBackground(BACKGROUND);
            boardWrapper.add(boardPanel);

            JPanel footer = new JPanel(new BorderLayout(10, 10));
            footer.setBackground(BACKGROUND);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
            buttons.setBackground(BACKGROUND);
            buttons.add(solveButton);
            buttons.add(anotherFileButton);
            buttons.add(menuButton);

            statusLabel.setForeground(SECONDARY_TEXT);
            statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));

            progressBar.setIndeterminate(true);
            progressBar.setVisible(false);
            progressBar.setPreferredSize(new Dimension(260, 8));

            JPanel statusPanel = new JPanel();
            statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
            statusPanel.setBackground(BACKGROUND);
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
            statusPanel.add(statusLabel);
            statusPanel.add(Box.createVerticalStrut(5));
            statusPanel.add(progressBar);

            footer.add(statusPanel, BorderLayout.NORTH);
            footer.add(buttons, BorderLayout.SOUTH);

            solveButton.addActionListener(event -> solveCurrentPuzzle());
            anotherFileButton.addActionListener(event -> chooseAnotherFile());
            menuButton.addActionListener(event -> returnToMenu());

            add(header, BorderLayout.NORTH);
            add(boardWrapper, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
        }

        void setPuzzle(
                Mode mode,
                SudokuFileReader.LoadedPuzzle puzzle) {

            titleLabel.setText(
                    "Excelente, escogiste nivel "
                            + mode.displayName.toLowerCase(Locale.ROOT));
            fileLabel.setText(
                    "Archivo: " + puzzle.getFile().getFileName()
                            + "  |  Pistas: " + puzzle.getClueCount()
                            + "  |  Nivel detectado: "
                            + puzzle.getDetectedLevel());
            boardPanel.setBoard(puzzle.getBoard());
            statusLabel.setText("Sudoku sin resolver");
        }

        void setSolving(boolean solving) {
            solveButton.setEnabled(!solving);
            anotherFileButton.setEnabled(!solving);
            menuButton.setEnabled(!solving);
            progressBar.setVisible(solving);
            statusLabel.setText(solving
                    ? "Resolviendo mediante algoritmo genético..."
                    : "Sudoku sin resolver");
        }

        void clear() {
            titleLabel.setText("");
            fileLabel.setText("");
            statusLabel.setText(" ");
            boardPanel.clearBoard();
            setSolving(false);
        }
    }

    private final class ResultPanel extends JPanel {

        private final SudokuBoardPanel boardPanel = new SudokuBoardPanel();

        private final JLabel cluesValue = new JLabel("-");
        private final JLabel levelValue = new JLabel("-");
        private final JLabel seedValue = new JLabel("-");
        private final JLabel generationValue = new JLabel("-");
        private final JLabel fitnessValue = new JLabel("-");
        private final JLabel timeValue = new JLabel("-");

        ResultPanel() {
            setLayout(new BorderLayout(24, 20));
            setBackground(BACKGROUND);
            setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));

            JLabel title = createTitle("La solución del Sudoku es:", 33);
            add(title, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(1, 2, 28, 0));
            center.setBackground(BACKGROUND);

            JPanel boardWrapper = new JPanel(new GridBagLayout());
            boardWrapper.setBackground(BACKGROUND);
            boardWrapper.add(boardPanel);

            JPanel information = new JPanel(new GridLayout(6, 1, 0, 12));
            information.setBackground(BACKGROUND);
            information.add(createInfoCard("Pistas", cluesValue));
            information.add(createInfoCard("Nivel", levelValue));
            information.add(createInfoCard("Semilla", seedValue));
            information.add(createInfoCard("Generación encontrada", generationValue));
            information.add(createInfoCard("Aptitud", fitnessValue));
            information.add(createInfoCard("Tiempo de ejecución", timeValue));

            center.add(boardWrapper);
            center.add(information);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
            buttons.setBackground(BACKGROUND);

            JButton anotherFileButton = createButton("Elegir otro archivo");
            JButton menuButton = createButton("Volver al menú");
            JButton exitButton = createButton("Salir");

            anotherFileButton.addActionListener(event -> chooseAnotherFile());
            menuButton.addActionListener(event -> returnToMenu());
            exitButton.addActionListener(event -> dispose());

            buttons.add(anotherFileButton);
            buttons.add(menuButton);
            buttons.add(exitButton);

            add(center, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
        }

        void setResult(
                ExecutionResult execution,
                SudokuFileReader.LoadedPuzzle puzzle,
                Mode mode) {

            boardPanel.setBoard(
                    execution.result.getBoard(),
                    puzzle.getBoard());

            cluesValue.setText(Integer.toString(execution.clueCount));
            levelValue.setText(mode.displayName);
            seedValue.setText(Long.toString(execution.seed));
            generationValue.setText(
                    Integer.toString(execution.result.getGeneration()));
            fitnessValue.setText(
                    execution.result.getFitness() + " / 162");
            timeValue.setText(String.format(
                    Locale.US,
                    "%.3f ms",
                    execution.elapsedNanoseconds / 1_000_000.0));
        }

        void clear() {
            boardPanel.clearBoard();
            cluesValue.setText("-");
            levelValue.setText("-");
            seedValue.setText("-");
            generationValue.setText("-");
            fitnessValue.setText("-");
            timeValue.setText("-");
        }
    }

    private enum Mode {
        EASY("FÁCIL", "FÁCIL"),
        MEDIUM("INTERMEDIO", "MEDIO"),
        HARD("DIFÍCIL", "DIFÍCIL");

        final String displayName;
        final String expectedDetectedLevel;

        Mode(String displayName, String expectedDetectedLevel) {
            this.displayName = displayName;
            this.expectedDetectedLevel = expectedDetectedLevel;
        }
    }

    private static final class ExecutionResult {
        final SudokuGeneticoOptimizado.Result result;
        final long elapsedNanoseconds;
        final long seed;
        final int clueCount;
        final String detectedLevel;

        ExecutionResult(
                SudokuGeneticoOptimizado.Result result,
                long elapsedNanoseconds,
                long seed,
                int clueCount,
                String detectedLevel) {

            this.result = result;
            this.elapsedNanoseconds = elapsedNanoseconds;
            this.seed = seed;
            this.clueCount = clueCount;
            this.detectedLevel = detectedLevel;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Swing utilizará su apariencia predeterminada.
            }

            SudokuApp application = new SudokuApp();
            application.setVisible(true);
        });
    }
}
