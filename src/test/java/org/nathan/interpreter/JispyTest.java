package org.nathan.interpreter;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import static org.nathan.interpreter.Jispy.*;
import static org.junit.jupiter.api.Assertions.*;

public class JispyTest {

    @Test
    public void parseTest() {
        List<Object> expected = Arrays.asList(new Symbol("begin"),
                Arrays.asList(new Symbol("define"), new Symbol("r"), 10),
                Arrays.asList(new Symbol("*"), new Symbol("pi"),
                        Arrays.asList(new Symbol("*"), new Symbol("r"), new Symbol("r"))));
        var t = (List<Object>) (parse("(begin (define r 10) (* pi (* r r)))"));
        assertTrue(Utils.treeListEqual(expected, t));
    }

    @Test
    public void beginTest() {
        assertEquals(314.1592653589793, runScheme("(begin (define r 10) (* pi (* r r)))"));
    }

    @Test
    public void ifTest() {
        assertEquals(42, runScheme("(if (> (* 11 11) 120) (* 7 6) oops)"));
    }

    @Test
    public void listTest() {
        var res = runScheme("(list (+ 1 1) (+ 2 2) (* 2 3) (expt 2 3))");
        var b = new ArrayList<>(Arrays.asList(2, 4, 6, 8.0));
        assertEquals(b, res);
    }

    @Test
    public void lambdaTest1() {
        assertEquals(120, runScheme("(begin " +
                "(define fact (lambda (n) (if (<= n 1) 1 (* n (fact (- n 1)))))) " +
                "(fact 5))"));
    }

    @Test
    public void lambdaTest2() {
        assertEquals(13, runScheme("(begin " +
                "(define fib (lambda (n) (if (< n 2) 1 (+ (fib (- n 1)) (fib (- n 2)))))) " +
                "(fib 6))"));
    }

    @Test
    public void lambdaTest3() {
        var res = runScheme("(begin " +
                "(define count (lambda (item L) (if (null? L) 0 (+ (if (equal? item (car L)) 1 0) (count item (cdr L)" +
                "))))) " +
                "(count 0 (list 0 1 2 3 0 0)))");
        assertEquals(3, res);
    }

    @Test
    public void consTest() {
        var b = new ArrayList<>(Arrays.asList(1, 4, 9, 16));
        assertEquals(b, runScheme("(begin " +
                "(define square (lambda (x) (* x x))) " +
                "(define range (lambda (a b) (if (= a b) nil (cons a (range (+ a 1) b))))) " +
                "(map square (range 1 5)))"));
    }

    @Test
    public void mapTest() {
        List<Object> expected = new ArrayList<>(Arrays.asList(4, 6, 8, 10));
        assertEquals(expected, (runScheme("(begin " +
                "(define two (lambda (a b) (+ a b 2))) " +
                "(define l (list 1 2 3 4)) " +
                "(map two l l))")));
    }

    @Test
    public void appendTest() {
        var expected = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
        var res = runScheme("(append (list 1 2) (list 3 4) (list 5 6))");
        assertEquals(expected, res);
    }

    @Test
    public void tailRecursionTest() {
        assertEquals(500500, runScheme("(begin " +
                "(define (sum2 n acc)" +
                "  (if (= n 0)" +
                "      acc" +
                "      (sum2 (- n 1) (+ n acc)))) " +
                "(sum2 1000 0) )"));
    }

    @Test
    public void expandTest() {
        assertEquals(1000, runScheme("(begin (define (cube x) (* x x x)) (cube 10))"));
    }

    @Test
    public void exceptionTest() {
        assertThrows(SyntaxException.class, () -> runScheme("(if 1 2 3 4 5)"));
    }

    @Test
    public void callccTest() {
        assertEquals(35, runScheme("(call/cc (lambda (throw) (+ 5 (* 10 " +
                "(call/cc (lambda (escape) (* 100 (escape 3))))))))"));
        assertEquals(3, runScheme("(call/cc (lambda (throw) " +
                "(+ 5 (* 10 (call/cc (lambda (escape) (* 100 (throw 3))))))))"));
    }

    @Test
    public void loadTest() {
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Arrays.asList(1, 5));
        expected.add(Arrays.asList(2, 6));
        expected.add(Arrays.asList(3, 7));
        expected.add(Arrays.asList(4, 8));
        assertEquals(expected, runScheme("(begin (load 'src/main/resources/functions.ss) " +
                "(zip (list 1 2 3 4) (list 5 6 7 8)))"));
    }

    @Test
    public void setTest(){
        assertEquals(3, runScheme("(begin (define x 1) (set! x (+ x 1)) (+ x 1))"));
    }

    // TODO test all functions
}
