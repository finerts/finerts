package kr.ac.korea.rts;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.engine.discovery.ClassNameFilter;

public class ExperimentalLauncher {
    public static void main(String[] args) throws IOException {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();

        Path surefireProp = Paths.get(args[0]);
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(surefireProp)) {
            props.load(in);
        }

        for (int index = 0;;index++) {
            String key = "tc." + index;
            String tc = (String)props.get(key);
            if (tc == null) break;

            builder.selectors(selectClass(tc));
        }

        List<String> excludes = new ArrayList<>();
        for (int index = 0;;index++) {
            String key = "excludes" + index;
            String tc = (String)props.get(key);
            if (tc == null) break;

            excludes.add(tc);
        }

        if (!excludes.isEmpty()) {
            builder.filters(ClassNameFilter.excludeClassNamePatterns(excludes.toArray(new String[0])));
            builder.filters(TagFilter.excludeTags("TestcontainersTests"));
        }

        if (args.length == 2) {
            Path selectedProp = Paths.get(args[1]);
            List<String> includes = Files.readAllLines(selectedProp);
            builder.filters(ClassNameFilter.includeClassNamePatterns(includes.toArray(new String[0])));
        }

        JUnit5Listener listener = new JUnit5Listener();
        try (LauncherSession session = LauncherFactory.openSession()) {
            Launcher launcher = session.getLauncher();
            launcher.registerTestExecutionListeners(listener);
            TestPlan testPlan = launcher.discover(builder.build());
            launcher.execute(testPlan);
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run () {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }

                // Handle resource leaks in tests.
                System.exit(listener.hasFailure() ? 1 : 0);
             }
        });

        t.setDaemon(true);
        t.start();

        if (listener.hasFailure()) {
            throw new RuntimeException("Has a failure");
        }
    }
}
