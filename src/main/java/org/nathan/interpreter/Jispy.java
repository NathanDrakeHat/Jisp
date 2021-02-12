package org.nathan.interpreter;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexFormat;
import org.apache.commons.math3.exception.MathParseException;
import org.nathan.interpreter.Env.Lambda;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.nathan.interpreter.Symbol.*;

@SuppressWarnings("unused")
public class Jispy {

    private static final boolean TIMER_ON = false;

    static class ArgumentsCountException extends RuntimeException {
        public ArgumentsCountException() {
            super();
        }

        public ArgumentsCountException(String s) {
            super(s);
        }
    }


    static class SyntaxException extends RuntimeException {
        public SyntaxException() {
            super();
        }

        public SyntaxException(String s) {
            super(s);
        }
    }

    private static class RuntimeWarning extends RuntimeException{
        Object returnValue;
        RuntimeWarning(){}
        RuntimeWarning(String m){
            super(m);
        }
    }


    static final List<Object> Nil = Collections.emptyList();

    static final Env GlobalEnv = Env.NewStandardEnv();

    public static void repl() {
        var prompt = "Jis.py>";
        var reader = new BufferedReader(new InputStreamReader(System.in));
        //noinspection InfiniteLoopStatement
        while (true) {
            String s;
            System.out.print(prompt);
            try {
                s = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            if (s.equals("")) {
                continue;
            }
            Object val = null;
            try {
                long t1, t2;
                if (TIMER_ON) t1 = System.nanoTime();
                val = runScheme(s);
                if (TIMER_ON) {
                    t2 = System.nanoTime();
                    System.out.println((t2 - t1) / Math.pow(10, 6));
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            if (val != null) System.out.println(val);
        }
    }

    private static void repl(String prompt, Reader inPort, Writer out) {
        try {
            System.err.write("Jispy version 2.0\\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        while (true) {
            try {
                if (prompt != null) {
                    try {
                        System.err.write(prompt.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                }
                var x = parse(inPort);
                if (x.equals(eof)) return;
                var val = eval(x);
                if (val != null && out != null) {
                    out.write(toString(val));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static Object runScheme(String program) {
        return eval(parse(program));
    }

    static Object parse(String program) {
        return readFromTokens(tokenize(program));
    }

    private static Object parse(Object in) {
        if (in instanceof String) in = new InPort((String) in);
        return expand(read((InPort) in), true);
    }

    private static Object eval(Object x) {
        return eval(x, GlobalEnv);
    }

    private static Object eval(Object x, Env env) {
        if (x.getClass().equals(String.class)) return env.find(x).get(x);
        else if (!x.getClass().equals(ArrayList.class)) return x;
        List<Object> list = (List<Object>) (x);
        String op = (String) (list.get(0));
        var args = list.subList(1, list.size());
        switch (op) {
            case "if" -> {
                Boolean test = (Boolean) (eval(args.get(0), env));
                var conseq = args.get(1);
                var alt = args.get(2);
                if (test == null) {
                    throw new NullPointerException();
                }
                var exp = test ? conseq : alt;
                return eval(exp, env);
            }
            case "define" -> {
                var symbol = args.get(0);
                var exp = args.get(1);
                env.put(symbol, eval(exp, env));
                return null;
            }
            case "lambda" -> {
                var parameter = args.get(0);
                var body = args.get(1);
                return (Lambda) arguments -> eval(body, new Env((Iterable<Object>) (parameter), arguments, env));
            }
            default -> {
                Lambda proc = (Lambda) (eval(op, env));
                var vals = args.stream().map(arg -> eval(arg, env)).collect(Collectors.toList());
                if (proc == null) {
                    throw new ClassCastException("null is not function");
                }
                return proc.apply(vals);
            }
        }
    }

    private static Object readFromTokens(Queue<String> tokens) {
        if (tokens.size() == 0) throw new SyntaxException("unexpected EOF");
        var token = tokens.poll();
        if (token.equals("(")) {
            var l = new ArrayList<>();
            //noinspection ConstantConditions
            while (!tokens.peek().equals(")")) l.add(readFromTokens(tokens));
            tokens.poll();
            return l;
        }
        else if (token.equals(")")) throw new SyntaxException("unexpected ')'");
        else return toAtom(token);
    }

    private static Queue<String> tokenize(String program) {
        var t = Arrays.stream(program.replace("(", " ( ").replace(")", " ) ").split(" ")).collect(Collectors.toList());
        t.removeIf(s -> s.equals(""));
        return new LinkedList<>(t);
    }

    private static Object toAtom(String x) {
        if (x.equals("#t")) return true;
        else if (x.equals("#f")) return false;
        else if (x.startsWith("\\")) return x.substring(1, x.length() - 1);
        else {
            boolean isInt = false;
            int t = 0;
            try {
                t = Integer.parseInt(x);
                isInt = true;
            } catch (NumberFormatException ignore) {

            }
            if (!isInt) {
                boolean isDouble = false;
                double t1 = 0;
                try {
                    t1 = Double.parseDouble(x);
                    isDouble = true;
                } catch (NumberFormatException ignore) {
                }
                if (!isDouble) {
                    try {
                        return ComplexFormat.getInstance().parse(x);
                    } catch (MathParseException ignore) {
                        return new Symbol(x);
                    }
                }
                else return t1;

            }
            else return t;

        }
    }

    private static Object readChar(InPort inPort) {
        if (!inPort.line.equals("")) {
            var c = inPort.line.charAt(0);
            inPort.line = inPort.line.substring(1);
            return c;
        }
        else {
            String c;
            try {
                c = Character.toString(inPort.file.read());
            } catch (IOException ignore) {
                return eof;
            }
            return c;
        }
    }

    private static Object read(InPort inPort) {
        var token = inPort.nextToken();
        if (token.equals(eof)) return eof;
        else return readAhead(token, inPort);

    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private static Object readAhead(Object token, InPort inPort) {
        if (token.equals("(")) {
            List<Object> l = new ArrayList<>();
            while (true) {
                token = inPort.nextToken();
                if (token.equals(")")) {
                    return l;
                }
                else {
                    l.add(readAhead(token, inPort));
                }
            }
        }
        else if (token.equals(")")) throw new SyntaxException("unexpected )");
        else if (quotes.containsKey(token)) return Arrays.asList(quotes.get(token), read(inPort));
        else if (token.equals(eof)) throw new SyntaxException("unexpected EOF in list");
        else return toAtom((String) token);
    }

    private static String toString(Object x) {
        if (x.equals(true)) return "#t";
        else if (x.equals(false)) return "#f";
        else if (x instanceof Symbol) return x.toString();
        else if (x instanceof String) return ((String) x).substring(1, ((String) x).length() - 1);
        else if (x instanceof List) {
            var s = new StringBuilder("(");
            for (var i : (ArrayList<Object>) x) {
                s.append(toString(i));
                s.append(" ");
            }
            s.delete(s.length() - 1, s.length());
            s.append(")");
            return s.toString();
        }
        else if (x instanceof Complex) return ComplexFormat.getInstance().format((Complex) x);
        else return x.toString();
    }

    private static Object expand(Object x) {
        return expand(x, false);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private static Object expand(Object x, boolean topLevel) {
        require(x, x != Nil);
        if (!(x instanceof ArrayList)) return x;
        List<Object> l = (ArrayList<Object>) x;
        if (l.get(0).equals(_quote)) {
            require(x, l.size() == 2);
            return l;
        }
        else if (l.get(0).equals(_if)) {
            if (l.size() == 3) l.add(null);
            require(x, l.size() == 4);
            return l.stream().map(Jispy::expand).collect(Collectors.toList());
        }
        else if (l.get(0).equals(_set)) {
            require(x, l.size() == 3);
            var v = l.get(1);
            require(x, v instanceof Symbol, "can set! only a symbol");
            return Arrays.asList(_set, v, expand(l.get(2)));
        }
        else if (l.get(0).equals(_define) || l.get(0).equals(_define_macro)) {
            require(x, l.size() >= 3);
            var _def = l.get(0);
            var v = l.get(1);
            var body = l.subList(2, l.size());
            if (v instanceof ArrayList && !((ArrayList<?>) v).isEmpty()) {
                List<Object> lv = (ArrayList<Object>) v;
                var f = lv.get(0);
                var args = lv.subList(1, lv.size());
                return expand(Arrays.asList(_def, f, Arrays.asList(_lambda, args, body)));
            }
            else {
                require(x, l.size() == 3);
                require(x, v instanceof Symbol, "can define only a symbol");
                var exp = expand(l.get(2));
                if (_def.equals(_define_macro)) {
                    require(x, topLevel, "define-macro only allowed at top level");
                    var proc = eval(exp);
                    require(x, proc instanceof Lambda, "macro must be a procedure");
                    macro_table.put((Symbol) v, (Lambda) proc);
                    return null;
                }
                return Arrays.asList(_define,v,exp);
            }
        }
        else if (l.get(0).equals(_begin)) {
            if (l.size() == 1) return null;
            else return l.stream().map(i -> expand(i, topLevel)).collect(Collectors.toList());
        }
        else if (l.get(0).equals(_lambda)) {
            require(x, l.size() >= 3);
            var vars = l.get(1);
            var body = l.subList(2, l.size());
            require(x, (vars instanceof ArrayList &&
                    ((ArrayList<Object>) vars).stream().allMatch(v -> v instanceof Symbol)));
            Object exp;
            if (body.size() == 1) exp = body.get(0);
            else {
                List<Object> t = new ArrayList<>();
                exp = t;
                t.add(_begin);
                t.addAll(body);
            }
            return Arrays.asList(_lambda, vars, expand(exp));
        }
        else if (l.get(0).equals(_quasi_quote)) {
            require(x, l.size() == 2);
            return expandQuasiQuote(l.get(1));
        }
        else if(l.get(0) instanceof Symbol && macro_table.containsKey(l.get(0))){
            return expand(macro_table.get(l.get(0)).apply(l.subList(1,l.size())),topLevel);
        }
        else return l.stream().map(Jispy::expand).collect(Collectors.toList());
    }

    private static Object expandQuasiQuote(Object x){
        if(!isPair(x)){
            return Arrays.asList(_quote,x);
        }
        List<Object> l = (ArrayList<Object>)x;
        require(x, !l.get(0).equals(_unquote_splicing), "can't splice here");
        if(l.get(0).equals(_unquote)){
            require(x, l.size() == 2);
            return l.get(1);
        }
        else if(isPair(l.get(0)) && ((ArrayList<?>)l.get(0)).get(0).equals(_unquote_splicing)){
            require(l.get(0),((ArrayList<?>) l.get(0)).size() == 2);
            return Arrays.asList(_append,((ArrayList<?>) l.get(0)).get(1), expandQuasiQuote(l.subList(1, l.size())));
        }
        else return Arrays.asList(_cons, expandQuasiQuote(l.get(0)), expandQuasiQuote(l.subList(1,l.size())));
    }

    private static boolean isPair(Object x){
        if(x instanceof List){
            return !((ArrayList<?>) x).isEmpty();
        }
        return false;
    }

    private static void require(Object x, boolean predicate) {
        require(x, predicate, "wrong length");
    }

    private static void require(Object x, boolean predicate, String m) {
        if (!predicate) {
            throw new SecurityException(x.toString() + m);
        }
    }

    static Object let(Object... arguments) {
        var args = Arrays.stream(arguments).collect(Collectors.toList());
        List<Object> x = Arrays.asList(_let);
        x.add(args);
        if (x.size() <= 1) throw new SyntaxException(x.toString() + "wrong length");
        var bindings = args.get(0);
        var body = args.subList(1, args.size());
        for (var b : (Iterable<Object>) bindings) {
            if (!(b instanceof ArrayList) ||
                    ((ArrayList<?>) b).size() != 2 ||
                    !(((ArrayList<?>) b).get(0) instanceof Symbol)) {
                throw new SyntaxException(toString(x) + "illegal binding list");
            }
        }
        var bd = (ArrayList<Object>) bindings;
        var vars = bd.get(0);
        var vals = bd.get(1);
        var listVars = Arrays.asList(vars);
        listVars.addAll(body.stream().map(Jispy::expand).collect(Collectors.toList()));
        List<Object> res = Arrays.asList(Arrays.asList(_lambda, listVars));
        res.addAll(((ArrayList<Object>) vals).stream().map(Jispy::expand).collect(Collectors.toList()));
        return res;
    }

    private static Object callcc(Lambda proc){
        var ball = new RuntimeWarning("Sorry, can't continue this continuation any longer.");
        try{
            return proc.apply(r->raise(r, ball));
        }
        catch (RuntimeWarning w){
            if(w.equals(ball)){
                return ball.returnValue;
            }
            else{
                throw w;
            }
        }

    }

    private static Object raise(Object r, RuntimeWarning ball){
        ball.returnValue = r;
        throw ball;
    }


}
