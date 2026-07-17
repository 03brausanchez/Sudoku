import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Componente reutilizable para mostrar un tablero de Sudoku. */
public class SudokuBoardPanel extends JPanel {

    private static final int SIZE = 9;

    private static final Color BOARD_BACKGROUND = new Color(28, 28, 30);
    private static final Color BOX_BACKGROUND = new Color(34, 34, 38);
    private static final Color GRID_COLOR = new Color(220, 220, 225);
    private static final Color FIXED_NUMBER_COLOR = new Color(118, 150, 255);
    private static final Color GENERATED_NUMBER_COLOR = new Color(245, 245, 245);

    private int[][] board = new int[SIZE][SIZE];
    private int[][] original = new int[SIZE][SIZE];

    public SudokuBoardPanel() {
        setOpaque(true);
        setBackground(new Color(18, 18, 18));
        setPreferredSize(new Dimension(520, 520));
        setMinimumSize(new Dimension(320, 320));
    }

    public void setBoard(int[][] board) {
        setBoard(board, board);
    }

    /**
     * @param board tablero que se mostrará.
     * @param original tablero inicial; permite distinguir las pistas originales.
     */
    public void setBoard(int[][] board, int[][] original) {
        validateBoard(board);
        validateBoard(original);

        this.board = copyBoard(board);
        this.original = copyBoard(original);
        repaint();
    }

    public void clearBoard() {
        board = new int[SIZE][SIZE];
        original = new int[SIZE][SIZE];
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int padding = 10;
            int available = Math.min(getWidth(), getHeight()) - 2 * padding;
            int cellSize = Math.max(1, available / SIZE);
            int boardSize = cellSize * SIZE;
            int startX = (getWidth() - boardSize) / 2;
            int startY = (getHeight() - boardSize) / 2;

            g2.setColor(BOARD_BACKGROUND);
            g2.fillRect(startX, startY, boardSize, boardSize);

            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    if (((row / 3) + (col / 3)) % 2 == 0) {
                        g2.setColor(BOX_BACKGROUND);
                        g2.fillRect(
                                startX + col * cellSize,
                                startY + row * cellSize,
                                cellSize,
                                cellSize);
                    }
                }
            }

            g2.setColor(GRID_COLOR);
            for (int index = 0; index <= SIZE; index++) {
                float thickness = index % 3 == 0 ? 3.2f : 1.0f;
                g2.setStroke(new BasicStroke(thickness));

                int x = startX + index * cellSize;
                int y = startY + index * cellSize;

                g2.drawLine(x, startY, x, startY + boardSize);
                g2.drawLine(startX, y, startX + boardSize, y);
            }

            int fontSize = Math.max(18, (int) (cellSize * 0.48));
            Font normalFont = getFont().deriveFont(Font.PLAIN, fontSize);
            Font fixedFont = getFont().deriveFont(Font.BOLD, fontSize);

            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    int value = board[row][col];
                    if (value == 0) {
                        continue;
                    }

                    boolean fixed = original[row][col] != 0;
                    g2.setFont(fixed ? fixedFont : normalFont);
                    g2.setColor(fixed
                            ? FIXED_NUMBER_COLOR
                            : GENERATED_NUMBER_COLOR);

                    String text = Integer.toString(value);
                    FontMetrics metrics = g2.getFontMetrics();
                    int textX = startX + col * cellSize
                            + (cellSize - metrics.stringWidth(text)) / 2;
                    int textY = startY + row * cellSize
                            + (cellSize - metrics.getHeight()) / 2
                            + metrics.getAscent();

                    g2.drawString(text, textX, textY);
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static void validateBoard(int[][] board) {
        if (board == null || board.length != SIZE) {
            throw new IllegalArgumentException("El tablero debe tener 9 filas.");
        }

        for (int row = 0; row < SIZE; row++) {
            if (board[row] == null || board[row].length != SIZE) {
                throw new IllegalArgumentException(
                        "Cada fila del tablero debe tener 9 columnas.");
            }
        }
    }

    private static int[][] copyBoard(int[][] source) {
        int[][] copy = new int[SIZE][SIZE];

        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, SIZE);
        }

        return copy;
    }
}

