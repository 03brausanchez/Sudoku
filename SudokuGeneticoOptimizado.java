import java.util.Arrays;
import java.util.Random;

/**
 * Resolución de Sudoku mediante un algoritmo genético optimizado.
 *
 * Representación:
 * - El cromosoma contiene las 81 celdas del Sudoku.
 * - Cada caja 3x3 siempre es una permutación válida de 1..9.
 * - Las pistas originales nunca se modifican.
 *
 * Aptitud:
 * - Se cuentan los valores distintos de las 9 filas y las 9 columnas.
 * - Puntaje máximo: 9*9 + 9*9 = 162.
 * - Las cajas no se evalúan porque la representación garantiza que son válidas.
 *
 * Operadores:
 * - Selección por torneo.
 * - Cruzamiento uniforme por cajas 3x3.
 * - Mutación por intercambio dentro de una caja.
 * - Mutación guiada por conflictos (búsqueda local breve).
 * - Elitismo y reinicio parcial cuando la población se estanca.
 */
public class SudokuGeneticoOptimizado {

    private static final int SIZE = 9;
    private static final int BOX_SIZE = 3;
    private static final int PERFECT_FITNESS = 162;

    private final int[][] puzzle;
    private final boolean[][] fixed;
    private final int[][] mutableIndicesByBox;
    private final Random random;
    private final Configuration configuration;
    private final int clueCount;

    /** Parámetros ajustados automáticamente según el número de pistas. */
    private static final class Configuration {
        final int populationSize;
        final int maxGenerations;
        final int eliteCount;
        final int tournamentSize;
        final int localSearchSteps;
        final int stagnationLimit;
        final double mutationRate;
        final double restartFraction;
        final String level;

        Configuration(
                int populationSize,
                int maxGenerations,
                int eliteCount,
                int tournamentSize,
                int localSearchSteps,
                int stagnationLimit,
                double mutationRate,
                double restartFraction,
                String level) {

            this.populationSize = populationSize;
            this.maxGenerations = maxGenerations;
            this.eliteCount = eliteCount;
            this.tournamentSize = tournamentSize;
            this.localSearchSteps = localSearchSteps;
            this.stagnationLimit = stagnationLimit;
            this.mutationRate = mutationRate;
            this.restartFraction = restartFraction;
            this.level = level;
        }

        static Configuration accordingToClues(int clues) {
            // Guía aproximada del documento: fácil 31, medio 24, difícil 17.
            if (clues >= 31) {
                return new Configuration(
                        120,   // población
                        1_000, // generaciones máximas
                        8,     // élites
                        4,     // torneo
                        35,    // pasos de mejora local por individuo
                        60,    // generaciones sin mejora antes de reiniciar
                        0.55,  // probabilidad de mutación
                        0.50,  // fracción reiniciada
                        "FÁCIL");
            }

            if (clues >= 24) {
                return new Configuration(
                        180,
                        2_500,
                        12,
                        4,
                        60,
                        80,
                        0.65,
                        0.55,
                        "MEDIO");
            }

            return new Configuration(
                    240,
                    5_000,
                    16,
                    5,
                    80,
                    100,
                    0.75,
                    0.60,
                    "DIFÍCIL");
        }
    }

    /** Individuo con aptitud almacenada para evitar evaluaciones repetidas. */
    private static final class Individual implements Comparable<Individual> {
        final int[] genes;
        int fitness;

        Individual(int[] genes, int fitness) {
            this.genes = genes;
            this.fitness = fitness;
        }

        Individual copy() {
            return new Individual(genes.clone(), fitness);
        }

        @Override
        public int compareTo(Individual other) {
            return Integer.compare(other.fitness, this.fitness);
        }
    }

    /** Resultado del algoritmo. */
    public static final class Result {
        private final int[][] board;
        private final int generation;
        private final int fitness;

        Result(int[][] board, int generation, int fitness) {
            this.board = board;
            this.generation = generation;
            this.fitness = fitness;
        }

        public int[][] getBoard() {
            return copyBoard(board);
        }

        public int getGeneration() {
            return generation;
        }

        public int getFitness() {
            return fitness;
        }
    }

    public SudokuGeneticoOptimizado(int[][] puzzle) {
        this(puzzle, System.nanoTime());
    }

    public SudokuGeneticoOptimizado(int[][] puzzle, long seed) {
        validateInitialPuzzle(puzzle);

        this.puzzle = copyBoard(puzzle);
        this.fixed = new boolean[SIZE][SIZE];
        this.random = new Random(seed);

        int clues = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                fixed[row][col] = puzzle[row][col] != 0;
                if (fixed[row][col]) {
                    clues++;
                }
            }
        }

        this.clueCount = clues;
        this.configuration = Configuration.accordingToClues(clues);
        this.mutableIndicesByBox = buildMutableIndicesByBox();
    }

    public Result solve() {
        Individual[] population = createInitialPopulation();
        Arrays.sort(population);

        Individual globalBest = population[0].copy();
        int stagnation = 0;

        if (globalBest.fitness == PERFECT_FITNESS) {
            return new Result(toBoard(globalBest.genes), 0, globalBest.fitness);
        }

        for (int generation = 1;
             generation <= configuration.maxGenerations;
             generation++) {

            Individual[] nextGeneration = new Individual[configuration.populationSize];

            // Elitismo: conserva los mejores individuos sin modificarlos.
            for (int i = 0; i < configuration.eliteCount; i++) {
                nextGeneration[i] = population[i].copy();
            }

            for (int i = configuration.eliteCount;
                 i < nextGeneration.length;
                 i++) {

                Individual parentA = tournamentSelect(population);
                Individual parentB = tournamentSelect(population);

                int[] childGenes = crossoverByBoxes(parentA.genes, parentB.genes);

                if (random.nextDouble() < configuration.mutationRate) {
                    randomSwapMutation(childGenes);
                }

                int childFitness = evaluate(childGenes);
                childFitness = improveByConflicts(
                        childGenes,
                        childFitness,
                        configuration.localSearchSteps);

                nextGeneration[i] = new Individual(childGenes, childFitness);

                if (childFitness == PERFECT_FITNESS) {
                    return new Result(
                            toBoard(childGenes),
                            generation,
                            childFitness);
                }
            }

            Arrays.sort(nextGeneration);
            population = nextGeneration;

            if (population[0].fitness > globalBest.fitness) {
                globalBest = population[0].copy();
                stagnation = 0;
            } else {
                stagnation++;
            }

            if (stagnation >= configuration.stagnationLimit) {
                Result restartResult = restartPartOfPopulation(
                        population,
                        generation);

                if (restartResult != null) {
                    return restartResult;
                }

                Arrays.sort(population);

                if (population[0].fitness > globalBest.fitness) {
                    globalBest = population[0].copy();
                }

                stagnation = 0;
            }
        }

        // No se presenta una aproximación como si fuera una solución válida.
        return null;
    }

    private Individual[] createInitialPopulation() {
        Individual[] population = new Individual[configuration.populationSize];

        for (int i = 0; i < population.length; i++) {
            int[] genes = createRandomIndividual();
            int fitness = evaluate(genes);

            // En la población inicial se usa una mejora breve. La mejora
            // más intensa se aplica a los descendientes dentro del ciclo genético.
            fitness = improveByConflicts(
                    genes,
                    fitness,
                    Math.max(5, configuration.localSearchSteps / 3));

            population[i] = new Individual(genes, fitness);
        }

        return population;
    }

    /**
     * Construye un individuo en el que cada caja 3x3 contiene exactamente
     * los números del 1 al 9, respetando las pistas.
     */
    private int[] createRandomIndividual() {
        int[] genes = new int[SIZE * SIZE];

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (fixed[row][col]) {
                    genes[row * SIZE + col] = puzzle[row][col];
                }
            }
        }

        for (int box = 0; box < SIZE; box++) {
            boolean[] used = new boolean[SIZE + 1];
            int startRow = (box / BOX_SIZE) * BOX_SIZE;
            int startCol = (box % BOX_SIZE) * BOX_SIZE;

            for (int row = startRow; row < startRow + BOX_SIZE; row++) {
                for (int col = startCol; col < startCol + BOX_SIZE; col++) {
                    int value = genes[row * SIZE + col];
                    if (value != 0) {
                        used[value] = true;
                    }
                }
            }

            int[] mutableCells = mutableIndicesByBox[box];
            int[] missingValues = new int[mutableCells.length];
            int position = 0;

            for (int value = 1; value <= SIZE; value++) {
                if (!used[value]) {
                    missingValues[position++] = value;
                }
            }

            shuffle(missingValues);

            for (int i = 0; i < mutableCells.length; i++) {
                genes[mutableCells[i]] = missingValues[i];
            }
        }

        return genes;
    }

    /** Cada caja completa se hereda de uno de los dos padres. */
    private int[] crossoverByBoxes(int[] parentA, int[] parentB) {
        int[] child = new int[SIZE * SIZE];

        for (int box = 0; box < SIZE; box++) {
            int[] source = random.nextBoolean() ? parentA : parentB;
            int startRow = (box / BOX_SIZE) * BOX_SIZE;
            int startCol = (box % BOX_SIZE) * BOX_SIZE;

            for (int row = startRow; row < startRow + BOX_SIZE; row++) {
                int startIndex = row * SIZE + startCol;
                System.arraycopy(
                        source,
                        startIndex,
                        child,
                        startIndex,
                        BOX_SIZE);
            }
        }

        return child;
    }

    /** Intercambia dos celdas no fijas de una misma caja. */
    private void randomSwapMutation(int[] genes) {
        int box;

        do {
            box = random.nextInt(SIZE);
        } while (mutableIndicesByBox[box].length < 2);

        int[] mutableCells = mutableIndicesByBox[box];
        int first = random.nextInt(mutableCells.length);
        int second;

        do {
            second = random.nextInt(mutableCells.length);
        } while (first == second);

        swap(genes, mutableCells[first], mutableCells[second]);
    }

    /**
     * Mutación guiada por conflictos.
     *
     * 1. Localiza una celda mutable cuyo valor se repite en su fila o columna.
     * 2. Prueba intercambios con otras celdas mutables de la misma caja.
     * 3. Prefiere el intercambio que más aumenta la aptitud.
     * 4. Permite ocasionalmente movimientos neutros o aleatorios para escapar
     *    de óptimos locales.
     */
    private int improveByConflicts(
            int[] genes,
            int currentFitness,
            int maximumSteps) {

        int staleSteps = 0;

        for (int step = 0;
             step < maximumSteps && currentFitness < PERFECT_FITNESS;
             step++) {

            int maximumConflict = -1;
            int candidateCount = 0;
            int[] candidateCells = new int[SIZE * SIZE];

            for (int box = 0; box < SIZE; box++) {
                for (int index : mutableIndicesByBox[box]) {
                    int conflict = cellConflict(genes, index);

                    if (conflict > maximumConflict) {
                        maximumConflict = conflict;
                        candidateCount = 0;
                        candidateCells[candidateCount++] = index;
                    } else if (conflict == maximumConflict) {
                        candidateCells[candidateCount++] = index;
                    }
                }
            }

            if (candidateCount == 0) {
                break;
            }

            int firstIndex = candidateCells[
                    random.nextInt(candidateCount)];

            int row = firstIndex / SIZE;
            int col = firstIndex % SIZE;
            int box = (row / BOX_SIZE) * BOX_SIZE + col / BOX_SIZE;
            int[] mutableCells = mutableIndicesByBox[box];

            if (mutableCells.length < 2) {
                continue;
            }

            int bestDelta = Integer.MIN_VALUE;
            int bestCount = 0;
            int[] bestPartners = new int[SIZE];

            for (int secondIndex : mutableCells) {
                if (secondIndex == firstIndex) {
                    continue;
                }

                int delta = swapFitnessDelta(
                        genes,
                        firstIndex,
                        secondIndex);

                if (delta > bestDelta) {
                    bestDelta = delta;
                    bestCount = 0;
                    bestPartners[bestCount++] = secondIndex;
                } else if (delta == bestDelta) {
                    bestPartners[bestCount++] = secondIndex;
                }
            }

            if (bestCount == 0) {
                continue;
            }

            int secondIndex = bestPartners[random.nextInt(bestCount)];

            boolean accept =
                    bestDelta > 0
                    || (bestDelta == 0 && random.nextDouble() < 0.25)
                    || random.nextDouble() < 0.025;

            if (accept) {
                swap(genes, firstIndex, secondIndex);
                currentFitness += bestDelta;

                if (bestDelta > 0) {
                    staleSteps = 0;
                } else {
                    staleSteps++;
                }
            } else {
                staleSteps++;
            }

            // Pequeña perturbación cuando la búsqueda local se estanca.
            if (staleSteps > 25) {
                randomSwapMutation(genes);
                currentFitness = evaluate(genes);
                staleSteps = 0;
            }
        }

        return currentFitness;
    }

    private Individual tournamentSelect(Individual[] population) {
        Individual best = population[random.nextInt(population.length)];

        for (int i = 1; i < configuration.tournamentSize; i++) {
            Individual candidate = population[
                    random.nextInt(population.length)];

            if (candidate.fitness > best.fitness) {
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Reinicia los peores individuos cuando no existe mejora durante varias
     * generaciones, pero conserva la parte superior de la población.
     */
    private Result restartPartOfPopulation(
            Individual[] population,
            int generation) {

        int startIndex = Math.max(
                configuration.eliteCount,
                (int) Math.round(
                        population.length
                        * (1.0 - configuration.restartFraction)));

        for (int i = startIndex; i < population.length; i++) {
            int[] genes = createRandomIndividual();
            int fitness = evaluate(genes);

            fitness = improveByConflicts(
                    genes,
                    fitness,
                    configuration.localSearchSteps * 2);

            population[i] = new Individual(genes, fitness);

            if (fitness == PERFECT_FITNESS) {
                return new Result(toBoard(genes), generation, fitness);
            }
        }

        return null;
    }

    /** Aptitud máxima: 81 puntos de filas + 81 de columnas = 162. */
    private int evaluate(int[] genes) {
        int score = 0;

        for (int index = 0; index < SIZE; index++) {
            score += uniqueValuesInRow(genes, index);
            score += uniqueValuesInColumn(genes, index);
        }

        return score;
    }

    /** Calcula el cambio de aptitud sin reevaluar todo el cromosoma. */
    private int swapFitnessDelta(
            int[] genes,
            int firstIndex,
            int secondIndex) {

        int firstRow = firstIndex / SIZE;
        int firstCol = firstIndex % SIZE;
        int secondRow = secondIndex / SIZE;
        int secondCol = secondIndex % SIZE;

        int before = uniqueValuesInRow(genes, firstRow)
                + uniqueValuesInColumn(genes, firstCol);

        if (secondRow != firstRow) {
            before += uniqueValuesInRow(genes, secondRow);
        }

        if (secondCol != firstCol) {
            before += uniqueValuesInColumn(genes, secondCol);
        }

        swap(genes, firstIndex, secondIndex);

        int after = uniqueValuesInRow(genes, firstRow)
                + uniqueValuesInColumn(genes, firstCol);

        if (secondRow != firstRow) {
            after += uniqueValuesInRow(genes, secondRow);
        }

        if (secondCol != firstCol) {
            after += uniqueValuesInColumn(genes, secondCol);
        }

        swap(genes, firstIndex, secondIndex);

        return after - before;
    }

    private int uniqueValuesInRow(int[] genes, int row) {
        int mask = 0;

        for (int col = 0; col < SIZE; col++) {
            mask |= 1 << genes[row * SIZE + col];
        }

        return Integer.bitCount(mask);
    }

    private int uniqueValuesInColumn(int[] genes, int col) {
        int mask = 0;

        for (int row = 0; row < SIZE; row++) {
            mask |= 1 << genes[row * SIZE + col];
        }

        return Integer.bitCount(mask);
    }

    private int cellConflict(int[] genes, int index) {
        int row = index / SIZE;
        int col = index % SIZE;
        int value = genes[index];
        int conflicts = 0;

        for (int currentCol = 0; currentCol < SIZE; currentCol++) {
            if (currentCol != col
                    && genes[row * SIZE + currentCol] == value) {
                conflicts++;
            }
        }

        for (int currentRow = 0; currentRow < SIZE; currentRow++) {
            if (currentRow != row
                    && genes[currentRow * SIZE + col] == value) {
                conflicts++;
            }
        }

        return conflicts;
    }

    private int[][] buildMutableIndicesByBox() {
        int[][] result = new int[SIZE][];

        for (int box = 0; box < SIZE; box++) {
            int startRow = (box / BOX_SIZE) * BOX_SIZE;
            int startCol = (box % BOX_SIZE) * BOX_SIZE;
            int count = 0;

            for (int row = startRow; row < startRow + BOX_SIZE; row++) {
                for (int col = startCol; col < startCol + BOX_SIZE; col++) {
                    if (!fixed[row][col]) {
                        count++;
                    }
                }
            }

            int[] indices = new int[count];
            int position = 0;

            for (int row = startRow; row < startRow + BOX_SIZE; row++) {
                for (int col = startCol; col < startCol + BOX_SIZE; col++) {
                    if (!fixed[row][col]) {
                        indices[position++] = row * SIZE + col;
                    }
                }
            }

            result[box] = indices;
        }

        return result;
    }

    private void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int randomIndex = random.nextInt(i + 1);
            swap(values, i, randomIndex);
        }
    }

    private static void swap(int[] values, int first, int second) {
        int temporary = values[first];
        values[first] = values[second];
        values[second] = temporary;
    }

    private static int[][] toBoard(int[] genes) {
        int[][] board = new int[SIZE][SIZE];

        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(
                    genes,
                    row * SIZE,
                    board[row],
                    0,
                    SIZE);
        }

        return board;
    }

    private static int[][] copyBoard(int[][] source) {
        int[][] copy = new int[SIZE][SIZE];

        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, SIZE);
        }

        return copy;
    }

    private static void validateInitialPuzzle(int[][] board) {
        if (board == null || board.length != SIZE) {
            throw new IllegalArgumentException(
                    "El Sudoku debe contener exactamente 9 filas.");
        }

        for (int row = 0; row < SIZE; row++) {
            if (board[row] == null || board[row].length != SIZE) {
                throw new IllegalArgumentException(
                        "Cada fila debe contener exactamente 9 valores.");
            }

            for (int col = 0; col < SIZE; col++) {
                int value = board[row][col];

                if (value < 0 || value > 9) {
                    throw new IllegalArgumentException(
                            "Valor inválido en fila " + (row + 1)
                            + ", columna " + (col + 1)
                            + ": " + value + ".");
                }
            }
        }

        for (int row = 0; row < SIZE; row++) {
            boolean[] seen = new boolean[SIZE + 1];

            for (int col = 0; col < SIZE; col++) {
                int value = board[row][col];

                if (value != 0 && seen[value]) {
                    throw new IllegalArgumentException(
                            "La fila " + (row + 1)
                            + " contiene la pista repetida " + value + ".");
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
                            + " contiene la pista repetida " + value + ".");
                }

                if (value != 0) {
                    seen[value] = true;
                }
            }
        }

        for (int boxRow = 0;
             boxRow < SIZE;
             boxRow += BOX_SIZE) {

            for (int boxCol = 0;
                 boxCol < SIZE;
                 boxCol += BOX_SIZE) {

                boolean[] seen = new boolean[SIZE + 1];

                for (int row = boxRow;
                     row < boxRow + BOX_SIZE;
                     row++) {

                    for (int col = boxCol;
                         col < boxCol + BOX_SIZE;
                         col++) {

                        int value = board[row][col];

                        if (value != 0 && seen[value]) {
                            throw new IllegalArgumentException(
                                    "Una caja 3x3 contiene la pista "
                                    + "repetida " + value + ".");
                        }

                        if (value != 0) {
                            seen[value] = true;
                        }
                    }
                }
            }
        }
    }

    public int getClueCount() {
        return clueCount;
    }

    public String getDetectedLevel() {
        return configuration.level;
    }
}