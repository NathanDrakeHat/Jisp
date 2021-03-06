package org.nathan.interpreter;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

import static org.nathan.interpreter.Symbol.*;

class InputPort implements Closeable {
    final BufferedReader file;
    String line = "";
    final Queue<String> queue = new LinkedList<>();
    static final String tokenizer = "\\s*(,@|[('`,)]|\"(?:[\\\\].|[^\\\\\"])*\"|;.*|[^\\s('\"`,;)]*)";
    static final Pattern pattern = Pattern.compile(tokenizer);


    InputPort(@NotNull InputStream in) {
        file = new BufferedReader(new InputStreamReader(in));
    }

    InputPort(@NotNull String in) {
        file = new BufferedReader(new StringReader(in));
    }

    InputPort(@NotNull File file) {
        try {
            this.file = new BufferedReader(new FileReader(file));
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return string or Symbol
     */
    Object nextToken() {
        while (true) {
            if (!queue.isEmpty()) {
                return queue.poll();
            }
            else if (line.equals("")) {
                try {
                    line = file.readLine();
                }
                catch (IOException e) {
                    e.printStackTrace(System.err);
                    throw new RuntimeException(e);
                }
            }

            if (line == null) {
                return eof;
            }
            else if (line.equals("")) { continue; }

            var matcher = pattern.matcher(line);
            int idx = 0;
            while (matcher.find(idx)) {
                var s = matcher.start(1);
                var e = matcher.end(1);
                if (s == e) {break;}
                var token = line.substring(s, e);
                idx = e;
                if (!token.startsWith(";")) {
                    queue.add(token);
                }
            }
            line = "";
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
