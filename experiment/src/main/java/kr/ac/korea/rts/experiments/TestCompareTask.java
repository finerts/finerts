package kr.ac.korea.rts.experiments;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.Gson;

import kr.ac.korea.esel.rts.hash.ClassLevel;
import kr.ac.korea.esel.rts.hash.MethodLevel;
import kr.ac.korea.esel.rts.hash.hyRTSf;
import kr.ac.korea.esel.rts.hash.encoder.ClassHashItem;
import kr.ac.korea.rts.experiments.ChangeComputer.ChangeListener;
import kr.ac.korea.rts.experiments.ChangeComputer.ChangeType;
import kr.ac.korea.rts.experiments.ChangeComputer.ClassItem;
import kr.ac.korea.rts.experiments.ChangeComputer.MethodItem;

public class TestCompareTask {
    public static void run(String head, String base, String level) throws IOException {
        Function<byte[], ClassHashItem> func;
        switch (level) {
            case "class":
                func = ClassLevel::create;
                break;
            case "hyrts":
                func = hyRTSf::create;
                break;
            case "proposed":
                func = MethodLevel::create;
                break;
            default:
                throw new IllegalArgumentException(level);
        }
        

        ChangeComputer computer = new ChangeComputer(head, base, func);
        ChangeSet changes = new ChangeSet();
        computer.run(changes);
        System.out.println(new Gson().toJson(changes, ChangeSet.class));
    }

    private static final class ChangeSet implements ChangeListener {
        private Map<ChangeType, Set<String>> classes = new HashMap<>();
        private Map<ChangeType, Map<String,Set<String>>> methods = new HashMap<>();

        public void addClass(ChangeType type, ClassItem item) {
            classes.computeIfAbsent(type, (k) -> new HashSet<>()).add(item.getItem().internalClassName());
        }

        public void addMethod(ChangeType type, MethodItem item) {
            ClassItem classItem = item.getParent();
            boolean isTestScope = classItem.isTestScope();
            if (isTestScope && type == ChangeType.Added) {
                addClass(ChangeType.Modified, classItem);
            } else {
                methods.computeIfAbsent(type, (k) -> new HashMap<>()).computeIfAbsent(classItem.getItem().internalClassName(), (k) -> new HashSet<>()).add(item.getMethod().methodSignature());
            }
        }
    }
}
