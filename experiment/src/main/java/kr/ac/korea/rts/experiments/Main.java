package kr.ac.korea.rts.experiments;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String... args) throws IOException {
        switch (args[0]) {
            case "compare":
                CompareTask.run(args[1], args[2]);
                break;
            case "test-compare":
                TestCompareTask.run(args[1], args[2], args[3]);
                break;
            case "select":
                TestSelector.run(args[1], args[2], args[3], args[4]);
                break;
            case "signature":
                CreateSignatures.run(args[1], Arrays.asList(args).subList(2, args.length));
                break;
            default:
                throw new IllegalArgumentException(Arrays.toString(args));
        }
    }
}
