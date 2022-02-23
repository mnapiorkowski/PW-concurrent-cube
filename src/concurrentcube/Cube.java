/*
 * PW 2021/22
 * zadanie zaliczeniowe 1.
 * "Współbieżna kostka"
 *
 * Michał Napiórkowski
 * mn429573
 */
package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {

    public static final int SIDES_COUNT = 6;
    public static final int AXIS_COUNT = SIDES_COUNT / 2;

    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private final Square[][][] squares;

    private final Semaphore mutex;
    private final Semaphore[][] layer_s;
    private final Semaphore show_s;

    private int curr_group;
    private int performing;
    private final int[] waiting_group;
    private final int[][] waiting_layer;
    private final boolean[][] is_rotating;


    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;
        this.mutex = new Semaphore(1, true);
        this.show_s = new Semaphore(0, true);
        this.curr_group = -1; // -1 -> nic, 0 -> x, 1 -> y, 2 -> z, 3 -> show
        this.performing = 0;

        // squares[s][x][y] to kwadrat o wsp. (x,y) na ścianie s
        this.squares = new Square[SIDES_COUNT][size][size];
        // layer_s[a][l] wstrzymuje procesy obracające warstwę l w osi a
        this.layer_s = new Semaphore[AXIS_COUNT][size];
        this.waiting_layer = new int[AXIS_COUNT][size];
        this.is_rotating = new boolean[AXIS_COUNT][size];
        this.waiting_group = new int[AXIS_COUNT + 1];

        for (int s = 0; s < SIDES_COUNT; s++) {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    squares[s][x][y] = new Square(s);
                }
            }
        }

        for (int a = 0; a < AXIS_COUNT; a++) {
            waiting_group[a] = 0;
            for (int l = 0; l < size; l++) {
                layer_s[a][l] = new Semaphore(0, true);
                waiting_layer[a][l] = 0;
                is_rotating[a][l] = false;
            }
        }
        waiting_group[AXIS_COUNT] = 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < SIDES_COUNT; s++) {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    sb.append(squares[s][x][y].getColor());
                }
            }
        }
        return sb.toString();
    }


    // METODY POMOCNICZE
    public final int[] count_squares() {
        int[] counter = {0, 0, 0, 0, 0, 0};
        for (int i = 0; i < SIDES_COUNT; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    counter[squares[i][j][k].getColor()]++;
                }
            }
        }
        return counter;
    }

    private int opposite_side(int side) {
        switch (side) {
            case 0:
                return 5;
            case 1:
                return 3;
            case 2:
                return 4;
            case 3:
                return 1;
            case 4:
                return 2;
            default:
                return 0;
        }
    }

    private int next_group(int group) {
        switch (group) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            default:
                return 0;
        }
    }

    private int get_axis(int side) {
        switch (side) {
            case 0:
            case 5:
                return 0;
            case 1:
            case 3:
                return 1;
            default:
                return 2;
        }
    }

    private int get_abs_layer(int side, int layer) {
        if (side >= 0 && side <= 2) {
            return layer;
        } else {
            return size - layer - 1;
        }
    }

    private void release_first_from_group(int group) {
        curr_group = group;
        if (curr_group == 3) { // show
            show_s.release();
        } else {
            int i = 0;
            while (i < size && waiting_layer[group][i] == 0) {
                i++;
            }
            layer_s[group][i].release();
        }
    }


    // PROTOKOŁY WSTĘPNE I KOŃCOWE
    private void rotation_entry_protocol(int axis, int abs_layer) throws InterruptedException {
        mutex.acquire();
        if (curr_group == -1) {
            curr_group = axis;
        } else if (curr_group != axis || waiting_group[0] > 0 || waiting_group[1] > 0 ||
                waiting_group[2] > 0 || waiting_group[3] > 0 || is_rotating[axis][abs_layer]) {
            waiting_group[axis]++;
            waiting_layer[axis][abs_layer]++;
            mutex.release();
            layer_s[axis][abs_layer].acquire();
            waiting_layer[axis][abs_layer]--;
            waiting_group[axis]--;
        }
        performing++;
        is_rotating[axis][abs_layer] = true;
        int l = abs_layer + 1;
        while (l < size && waiting_layer[axis][l] == 0) {
            l++;
        }
        if (l < size) {
            layer_s[axis][l].release();
        } else {
            mutex.release();
        }
    }

    private void rotation_exit_protocol(int axis, int abs_layer) throws InterruptedException {
        mutex.acquire();
        is_rotating[axis][abs_layer] = false;
        performing--;
        if (performing == 0) { // to był ostatni wątek wykonujący obrót tej osi
            int next = next_group(axis);
            if (waiting_group[next] > 0) {
                release_first_from_group(next);
            } else if (waiting_group[next_group(next)] > 0) {
                release_first_from_group(next_group(next));
            } else if (waiting_group[next_group(next_group(next))] > 0) {
                release_first_from_group(next_group(next_group(next)));
            } else if (waiting_group[axis] > 0) {
                release_first_from_group(axis);
            } else {
                curr_group = -1;
                mutex.release();
            }
        } else {
            mutex.release();
        }
    }

    private void show_entry_protocol() throws InterruptedException {
        mutex.acquire();
        if (curr_group == -1) {
            curr_group = 3;
        } else if (curr_group != 3 || waiting_group[0] > 0 ||
                waiting_group[1] > 0 || waiting_group[2] > 0) {
            waiting_group[3]++;
            mutex.release();
            show_s.acquire();
            waiting_group[3]--;
        }
        performing++;
        if (waiting_group[3] > 0) {
            show_s.release();
        } else {
            mutex.release();
        }
    }

    private void show_exit_protocol() throws InterruptedException {
        mutex.acquire();
        performing--;
        if (performing == 0) { // to był ostatni wątek wykonujący show
            int next = next_group(3);
            if (waiting_group[next] > 0) {
                release_first_from_group(next);
            } else if (waiting_group[next_group(next)] > 0) {
                release_first_from_group(next_group(next));
            } else if (waiting_group[next_group(next_group(next))] > 0) {
                release_first_from_group(next_group(next_group(next)));
            } else {
                curr_group = -1;
                mutex.release();
            }
        } else {
            mutex.release();
        }
    }


    // METODA ODPOWIADAJĄCA ZA MODYFIKOWANIE KOSTKI
    private void rotation(int side, int layer) {
        switch (side) {
            case 0:
                for (int i = 0; i < size; i++) {
                    Square temp = squares[1][layer][i];
                    squares[1][layer][i] = squares[2][layer][i];
                    squares[2][layer][i] = squares[3][layer][i];
                    squares[3][layer][i] = squares[4][layer][i];
                    squares[4][layer][i] = temp;
                }
                break;
            case 1:
                for (int i = 0; i < size; i++) {
                    Square temp = squares[4][size - 1 - i][size - 1 - layer];
                    squares[4][size - 1 - i][size - 1 - layer] = squares[5][i][layer];
                    squares[5][i][layer] = squares[2][i][layer];
                    squares[2][i][layer] = squares[0][i][layer];
                    squares[0][i][layer] = temp;
                }
                break;
            case 2:
                for (int i = 0; i < size; i++) {
                    Square temp = squares[1][size - 1 - i][size - 1 - layer];
                    squares[1][size - 1 - i][size - 1 - layer] = squares[5][layer][size - 1 - i];
                    squares[5][layer][size - 1 - i] = squares[3][i][layer];
                    squares[3][i][layer] = squares[0][size - 1 - layer][i];
                    squares[0][size - 1 - layer][i] = temp;
                }
                break;
            case 3:
                for (int i = 0; i < size; i++) {
                    Square temp = squares[2][i][size - 1 - layer];
                    squares[2][i][size - 1 - layer] = squares[5][i][size - 1 - layer];
                    squares[5][i][size - 1 - layer] = squares[4][size - 1 - i][layer];
                    squares[4][size - 1 - i][layer] = squares[0][i][size - 1 - layer];
                    squares[0][i][size - 1 - layer] = temp;
                }
                break;
            case 4:
                for (int i = 0; i < size; i++) {
                    Square temp = squares[3][i][size - 1 - layer];
                    squares[3][i][size - 1 - layer] = squares[5][size - 1 - layer][size - 1 - i];
                    squares[5][size - 1 - layer][size - 1 - i] = squares[1][size - 1 - i][layer];
                    squares[1][size - 1 - i][layer] = squares[0][layer][i];
                    squares[0][layer][i] = temp;
                }
                break;
            case 5:
                for (int i = 0; i < size; i++) {
                    Square temp = squares[1][size - 1 - layer][i];
                    squares[1][size - 1 - layer][i] = squares[4][size - 1 - layer][i];
                    squares[4][size - 1 - layer][i] = squares[3][size - 1 - layer][i];
                    squares[3][size - 1 - layer][i] = squares[2][size - 1 - layer][i];
                    squares[2][size - 1 - layer][i] = temp;
                }
                break;
        }
        if (layer == 0) {
            // obróć kwadraty z przedniej ściany
            int w = 0; // warstwa kwadratów z obracanej ściany (0 to najbardziej zewnętrzna)
            while (w < size / 2) {
                for (int i = w; i < size - 1 - w; i++) {
                    Square temp = squares[side][w][i];
                    squares[side][w][i] = squares[side][size - 1 - i][w];
                    squares[side][size - 1 - i][w] = squares[side][size - 1 - w][size - 1 - i];
                    squares[side][size - 1 - w][size - 1 - i] = squares[side][i][size - 1 - w];
                    squares[side][i][size - 1 - w] = temp;
                }
                w++;
            }
        } else if (layer == size - 1) {
            // obróć kwadraty z tylnej ściany
            int w = 0; // warstwa kwadratów z obracanej ściany (0 to najbardziej zewnętrzna)
            int op_side = opposite_side(side);
            while (w < size / 2) {
                for (int i = w; i < size - 1 - w; i++) {
                    Square temp = squares[op_side][w][i];
                    squares[op_side][w][i] = squares[op_side][i][size - 1 - w];
                    squares[op_side][i][size - 1 - w] = squares[op_side][size - 1 - w][size - 1 - i];
                    squares[op_side][size - 1 - w][size - 1 - i] = squares[op_side][size - 1 - i][w];
                    squares[op_side][size - 1 - i][w] = temp;
                }
                w++;
            }
        }
    }


    // WŁAŚCIWE METODY ROTATE I SHOW
    public void rotate(int side, int layer) throws InterruptedException {
        int axis = get_axis(side);
        int abs_layer = get_abs_layer(side, layer);

        rotation_entry_protocol(axis, abs_layer);
        beforeRotation.accept(side, layer);
        rotation(side, layer);
        afterRotation.accept(side, layer);
        rotation_exit_protocol(axis, abs_layer);
    }

    public String show() throws InterruptedException {
        show_entry_protocol();
        beforeShowing.run();
        String state = this.toString();
        afterShowing.run();
        show_exit_protocol();
        return state;
    }
}