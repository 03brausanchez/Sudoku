import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Lee y valida un Sudoku almacenado en un archivo de texto.
 *
 * El archivo debe contener exactamente 81 números enteros entre 0 y 9.
 * El número 0 representa una celda vacía.
 */
public final class SudokuFileReader {

    private static final int SIZE = 9;
    private static final int BOX_SIZE = 3;

    private SudokuFileReader() {
        // Evita crear objetos de esta clase utilitaria.
    }

    public static LoadedPuzzle read(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("No se seleccionó ningún archivo.");
        }

        if (!Files.exists(file)) {
            throw new IOException("El archivo no existe: " + file);
        }

        if (!Files.isRegularFile(file)) {
            throw new IOException("La ruta seleccionada no corresponde a un archivo.");
        }

        String fileName = file.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".txt")) {
            throw new IllegalArgumentException(
                    "El problema debe estar almacenado en un archivo .txt.");
        }

        List<Integer> values = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(reader)) {

            while (scanner.hasNext()) {
                if (scanner.hasNextInt()) {
                    values.add(scanner.nextInt());
                } else {
                    // Permite encabezados opcionales como FACIL o MEDIO.
                    scanner.next();
                }
            }
        }

        if (values.size() != SIZE * SIZE) {
            throw new IllegalArgumentException(
                    "El archivo debe contener exactamente 81 números; "
                            + "se encontraron " + values.size() + ".");
        }

        int[][] board = new int[SIZE][SIZE];
        for (int index = 0; index < values.size(); index++) {
            int value = values.get(index);

            if (value < 0 || value > 9) {
                throw new IllegalArgumentException(
                        "Todos los valores deben estar entre 0 y 9. "
                                + "Se encontró: " + value + ".");
            }

            board[index / SIZE][index % SIZE] = value;
        }

        validateClues(board);

        int clueCount = countClues(board);
        String detectedLevel = detectLevel(clueCount);

        return new LoadedPuzzle(
                file.toAbsolutePath().normalize(),
                board,
                clueCount,
                detectedLevel);
    }

    private static int countClues(int[][] board) {
        int clues = 0;

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] != 0) {
                    clues++;
                }
            }
        }

        return clues;
    }

    private static String detectLevel(int clues) {
        if (clues >= 31) {
            return "FÁCIL";
        }

        if (clues >= 24) {
            return "MEDIO";
        }

        return "DIFÍCIL";
    }

    private static void validateClues(int[][] board) {
        for (int row = 0; row < SIZE; row++) {
            boolean[] seen = new boolean[SIZE + 1];

            for (int col = 0; col < SIZE; col++) {
                int value = board[row][col];

                if (value != 0 && seen[value]) {
                    throw new IllegalArgumentException(
                            "La fila " + (row + 1)
                                    + " contiene repetido el número " + value + ".");
                }

                if (value != 0) {
                    seen[value] = true;
                }
            }
        }

        for (int col = 0; col < SIZE; col++) {
            boolean[] seen = new boolean[SIZE + 1];

            for (int row = 0; row < SIZE; row++) {
                int value = board[row][col];

                if (value != 0 && seen[value]) {
                    throw new IllegalArgumentException(
                            "La columna " + (col + 1)
                                    + " contiene repetido el número " + value + ".");
                }

                if (value != 0) {
                    seen[value] = true;
                }
            }
        }

        for (int boxRow = 0; boxRow < SIZE; boxRow += BOX_SIZE) {
            for (int boxCol = 0; boxCol < SIZE; boxCol += BOX_SIZE) {
                boolean[] seen = new boolean[SIZE + 1];

                for (int row = boxRow; row < boxRow + BOX_SIZE; row++) {
                    for (int col = boxCol; col < boxCol + BOX_SIZE; col++) {
                        int value = board[row][col];

                        if (value != 0 && seen[value]) {
                            throw new IllegalArgumentException(
                                    "Una caja 3x3 contiene repetido el número "
                                            + value + ".");
                        }

                        if (value != 0) {
                            seen[value] = true;
                        }
                    }
                }
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

    /** Información obtenida después de leer y validar el archivo. */
    public static final class LoadedPuzzle {
        private final Path file;
        private final int[][] board;
        private final int clueCount;
        private final String detectedLevel;

        private LoadedPuzzle(
                Path file,
                int[][] board,
                int clueCount,
                String detectedLevel) {

            this.file = file;
            this.board = copyBoard(board);
            this.clueCount = clueCount;
            this.detectedLevel = detectedLevel;
        }

        public Path getFile() {
            return file;
        }

        public int[][] getBoard() {
            return copyBoard(board);
        }

        public int getClueCount() {
            return clueCount;
        }

        public String getDetectedLevel() {
            return detectedLevel;
        }
    }
}
