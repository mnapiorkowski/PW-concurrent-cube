package concurrentcube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.*;
import org.junit.Test;

public class CubeTest {

    private String beginning_output(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        return cube.show();
    }

    private String colliding_test_output1(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        cube.rotate(0, 1);
        cube.rotate(1, 2);
        cube.rotate(2, 0);
        return cube.show();
    }
    private String colliding_test_output2(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        cube.rotate(0, 1);
        cube.rotate(2, 0);
        cube.rotate(1, 2);
        return cube.show();
    }
    private String colliding_test_output3(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        cube.rotate(1, 2);
        cube.rotate(0, 1);
        cube.rotate(2, 0);
        return cube.show();
    }
    private String colliding_test_output4(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        cube.rotate(1, 2);
        cube.rotate(2, 0);
        cube.rotate(0, 1);
        return cube.show();
    }
    private String colliding_test_output5(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        cube.rotate(2, 0);
        cube.rotate(1, 2);
        cube.rotate(0, 1);
        return cube.show();
    }
    private String colliding_test_output6(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        cube.rotate(2, 0);
        cube.rotate(0, 1);
        cube.rotate(1, 2);
        return cube.show();
    }

    @Test
    // 50k razy odpalam 3 wątki, które wykonują kolidujące ze sobą obroty.
    // Sprawdzam, czy końcowy stan kostki zawsze jest jednym z możliwych stanów,
    // wygenerowanych powyższymi przeplotami sekwencyjnymi.
    public void colliding_test() {
        final int THREADS = 3;
        final int SIZE = 5;
        final int HOWMANY = 50000;
        class Helper implements Runnable {

            private final int id;
            private Cube cube;

            public Helper(int id, Cube cube) {
                this.id = id;
                this.cube = cube;
            }

            @Override
            public void run() {
                try {
                    switch (id) {
                        case 0 : cube.rotate(0, 1);
                        case 1 : cube.rotate(1, 2);
                        default : cube.rotate(2, 0);
                    }
                } catch (InterruptedException e) {
                    Thread t = Thread.currentThread();
                    t.interrupt();
                    System.err.println(t.getName() + " interrupted");
                }
            }
        }

        for (int j = 0; j < HOWMANY; j++) {
            Cube cube = new Cube(SIZE,
                    (x, y) -> {},
                    (x, y) -> {},
                    () -> {},
                    () -> {}
            );

            ArrayList<Thread> threads = new ArrayList<>(THREADS);
            for (int i = 0; i < THREADS; ++i) {
                Thread t = new Thread(new Helper(i, cube), "Helper" + i);
                threads.add(t);
            }
            for (Thread t : threads) {
                t.start();
            }
            try {
                for (Thread t : threads) {
                    t.join();
                }
                String[] possible_outputs = {colliding_test_output1(SIZE), colliding_test_output2(SIZE),
                        colliding_test_output3(SIZE), colliding_test_output4(SIZE),
                        colliding_test_output5(SIZE), colliding_test_output6(SIZE)};
                assertTrue(Arrays.asList(possible_outputs).contains(cube.show()));
            } catch (InterruptedException e) {
                System.err.println("Main interrupted");
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    // 100 wątków wykonuje po 1 obrocie kostki 100x100,
    // tak że żadne dwa obroty nie kolidują ze sobą.
    // Na barierze sprawdzamy, czy wszystkie obroty wykonają się współbieżnie.
    public void concurrent_rotate_test() {
        final int THREADS = 100;
        final int SIZE = 100;
        class Helper implements Runnable {

            private final int id;
            private Cube cube;

            public Helper(int id, Cube cube) {
                this.id = id;
                this.cube = cube;
            }

            @Override
            public void run() {
                try {
                    cube.rotate(0, id);
                } catch (InterruptedException e) {
                    Thread t = Thread.currentThread();
                    t.interrupt();
                    System.err.println(t.getName() + " interrupted");
                }
            }
        }

        var counter = new Object() { int value = 0; };
        CyclicBarrier barrier = new CyclicBarrier(THREADS, () -> {counter.value = THREADS;});

        Cube cube = new Cube(SIZE,
                (x, y) -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                },
                (x, y) -> {},
                () -> {},
                () -> {}
        );

        ArrayList<Thread> threads = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; ++i) {
            Thread t = new Thread(new Helper(i, cube), "Helper" + i);
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
            assertEquals(counter.value, THREADS);
        } catch (InterruptedException e) {
            System.err.println("Main interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Test
    // To samo co concurrent_rotate_test(), tylko dla show().
    public void concurrent_show_test() {
        final int THREADS = 100;
        final int SIZE = 100;
        class Helper implements Runnable {

            private final int id;
            private Cube cube;

            public Helper(int id, Cube cube) {
                this.id = id;
                this.cube = cube;
            }

            @Override
            public void run() {
                try {
                    cube.show();
                } catch (InterruptedException e) {
                    Thread t = Thread.currentThread();
                    t.interrupt();
                    System.err.println(t.getName() + " interrupted");
                }
            }
        }

        var counter = new Object() { int value = 0; };
        CyclicBarrier barrier = new CyclicBarrier(THREADS, () -> {counter.value = THREADS;});

        Cube cube = new Cube(SIZE,
                (x, y) -> {},
                (x, y) -> {},
                () -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                },
                () -> {}
        );

        ArrayList<Thread> threads = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; ++i) {
            Thread t = new Thread(new Helper(i, cube), "Helper" + i);
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
            assertEquals(counter.value, THREADS);
        } catch (InterruptedException e) {
            System.err.println("Main interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Test
    // 100 wątków wykonuje po 50k losowych obrotów kostką 7x7.
    // Sprawdzamy bezpieczeństwo - czy po tych obrotach będzie
    // zgadzała się liczba kwadratów w poszczególnych kolorach.
    // Sprawdzamy żywotność - czy wszystkie akcje zostaną wykonane.
    public void big_random_test() {
        final int THREADS = 100;
        final int SIZE = 7;
        final int ROTATIONS = 50000;
        class Helper implements Runnable {

            private final int id;
            private Cube cube;

            public Helper(int id, Cube cube) {
                this.id = id;
                this.cube = cube;
            }

            @Override
            public void run() {
                try {
                    for (int i = 0; i < ROTATIONS; i++) {
                        int side = (int)(Math.random() * 6);
                        int layer = (int)(Math.random() * SIZE);
                        cube.rotate(side, layer);
                    }
                } catch (InterruptedException e) {
                    Thread t = Thread.currentThread();
                    t.interrupt();
                    System.err.println(t.getName() + " interrupted");
                }
            }
        }

        var counter = new Object() { int before_rotation = 0; int before_showing = 0;
            int after_rotation = 0; int after_showing = 0;};
        Semaphore mutex = new Semaphore(1, true);
        Cube cube = new Cube(SIZE,
                (x, y) -> {
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    counter.before_rotation++; mutex.release();},
                (x, y) -> {
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    counter.after_rotation++; mutex.release();},
                () -> {
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    counter.before_showing++; mutex.release();},
                () -> {
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    counter.after_showing++; mutex.release();}
        );

        ArrayList<Thread> threads = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; ++i) {
            Thread t = new Thread(new Helper(i, cube), "Helper" + i);
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
            int s = SIZE * SIZE;
            assertEquals(Arrays.toString(cube.count_squares()),
                    Arrays.toString(new int[]{s, s, s, s, s, s}));
            assertEquals(counter.before_rotation, counter.after_rotation);
            assertEquals(counter.before_showing, counter.after_showing);
            assertEquals(counter.after_rotation + counter.after_showing, THREADS * ROTATIONS);
        } catch (InterruptedException e) {
            System.err.println("Main interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Test
    // Jeden wątek 50k razy obraca jedną warstwą zgodnie z ruchem wskazówek zegara,
    // a drugi tyle samo razy przeciwnie.
    // Sprawdzam, czy na koniec kostka jest w stanie początkowym.
    public void one_layer_test() {
        final int THREADS = 2;
        final int SIZE = 5;
        final int HOWMANY = 50000;
        class Helper implements Runnable {

            private final int id;
            private Cube cube;

            public Helper(int id, Cube cube) {
                this.id = id;
                this.cube = cube;
            }

            @Override
            public void run() {
                try {
                    if (id == 0) {
                        for (int i = 0; i < HOWMANY; i++) {
                            cube.rotate(0, 0);
                        }
                    } else {
                        for (int i = 0; i < HOWMANY; i++) {
                            cube.rotate(5, SIZE - 1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread t = Thread.currentThread();
                    t.interrupt();
                    System.err.println(t.getName() + " interrupted");
                }
            }
        }

        Cube cube = new Cube(SIZE,
                (x, y) -> {},
                (x, y) -> {},
                () -> {},
                () -> {}
        );

        ArrayList<Thread> threads = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; ++i) {
            Thread t = new Thread(new Helper(i, cube), "Helper" + i);
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
            assertEquals(cube.show(), beginning_output(SIZE));
        } catch (InterruptedException e) {
            System.err.println("Main interrupted");
            Thread.currentThread().interrupt();
        }
    }
}
