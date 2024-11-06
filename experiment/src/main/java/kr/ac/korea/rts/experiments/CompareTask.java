package kr.ac.korea.rts.experiments;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import kr.ac.korea.esel.rts.hash.MethodLevel;
import kr.ac.korea.rts.experiments.ChangeComputer.ChangeListener;
import kr.ac.korea.rts.experiments.ChangeComputer.ChangeType;
import kr.ac.korea.rts.experiments.ChangeComputer.ClassItem;
import kr.ac.korea.rts.experiments.ChangeComputer.MethodItem;

public class CompareTask {
    public static void run(String head, String base) throws IOException {
        ChangeComputer computer = new ChangeComputer(head, base, MethodLevel::create, true);
        ChangeSet changes = new ChangeSet();
        computer.run(changes);
        System.out.println(new Gson().toJson(changes, ChangeSet.class));
    }

    private static final class ChangeSet implements ChangeListener {
        private Map<ChangeType, Set<String>> classes = new HashMap<>(4096);
        private Map<ChangeType, Map<String,Set<String>>> methods = new HashMap<>(4096);

        public void addClass(ChangeType type, ClassItem item) {
            classes.computeIfAbsent(type, (k) -> new HashSet<>(4096)).add(item.getItem().internalClassName());
        }

        public void addMethod(ChangeType type, MethodItem item) {
            methods.computeIfAbsent(type, (k) -> new HashMap<>(4096)).computeIfAbsent(item.getParent().getItem().internalClassName(), (k) -> new HashSet<>(4096)).add(item.getMethod().methodSignature());
        }
    }
}
